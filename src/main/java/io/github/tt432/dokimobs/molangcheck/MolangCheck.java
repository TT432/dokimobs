package io.github.tt432.dokimobs.molangcheck;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.tt432.dokimobs.molang.DokiMolangQueries;
import io.github.tt432.eyelib.bridge.molang.MolangBuiltInQuery;
import io.github.tt432.eyelib.client.molang.MolangQuery;
import io.github.tt432.eyelib.molang.compiler.binding.BindDiagnostic;
import io.github.tt432.eyelib.molang.compiler.binding.BindDiagnosticsMode;
import io.github.tt432.eyelib.molang.compiler.binding.MolangBinder;
import io.github.tt432.eyelib.molang.compiler.common.MolangRootAliasCanonicalizer;
import io.github.tt432.eyelib.molang.compiler.frontend.MolangParserFrontendResult;
import io.github.tt432.eyelib.molang.compiler.frontend.MolangParserFrontends;
import io.github.tt432.eyelib.molang.compiler.frontend.ast.MolangAst;
import io.github.tt432.eyelib.molang.mapping.MolangMath;
import io.github.tt432.eyelib.molang.mapping.MolangToplevel;
import io.github.tt432.eyelib.molang.mapping.api.MolangMappingDiscovery;
import io.github.tt432.eyelib.molang.mapping.api.MolangMappingRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 快速 Molang 检查器：复用 eyelib 的解析器/绑定器与运行时映射表。
 *
 * <p>检查两类问题：
 * <ol>
 *   <li>语法/语义：eyelib 解析器与 {@code MolangCompilerImpl} 是否接受该表达式；</li>
 *   <li>是否存在：表达式里引用的 {@code query.*}/{@code math.*} 函数或字段是否在
 *       eyelib 的 {@link MolangMappingRegistries} 注册表中（未注册的查询在运行时会
 *       静默返回 null/0，不报错，这是本工具的主要用途）。</li>
 * </ol>
 *
 * <p>用法：
 * <pre>
 *   gradlew molangCheck                         # 扫描 assets/dokimobs（含 pack.cospack）
 *   gradlew molangCheck -PmolangTarget=path     # 扫描指定文件/目录
 *   gradlew molangCheckExpr -PmolangExpr='q.is_moving &amp;&amp; q.health &gt; 0'
 * </pre>
 *
 * <p>独立运行时映射表按 eyelib 的内置映射类初始化；新增映射类需要同步加入
 * {@link #MAPPING_CLASSES}。游戏内运行时可改用已初始化的全局映射表。
 *
 * @author TT432
 */
public final class MolangCheck {
    /**
     * eyelib 内置映射类 + dokimobs 自己的映射类。
     * 与 Forge 的 {@code @MolangMapping} 扫描结果保持一致（不止 query：math/toplevel 也要）。
     */
    private static final List<MolangMappingDiscovery.MolangMappingClassEntry> MAPPING_CLASSES = List.of(
            new MolangMappingDiscovery.MolangMappingClassEntry("query", MolangBuiltInQuery.class, false),
            new MolangMappingDiscovery.MolangMappingClassEntry("query", MolangQuery.class, false),
            new MolangMappingDiscovery.MolangMappingClassEntry("query", DokiMolangQueries.class, false),
            new MolangMappingDiscovery.MolangMappingClassEntry("math", MolangMath.class, true),
            new MolangMappingDiscovery.MolangMappingClassEntry("", MolangToplevel.class, true)
    );

    /** 可能是 Molang 的字符串的最小特征（避免把动画 id/贴图路径当表达式检查）。 */
    private static final Pattern MOLANG_HINT = Pattern.compile(
            "(^|[^A-Za-z0-9_.])(query|variable|temp|context|math|Array)\\.|(^|[^A-Za-z0-9_])(q|v|t|c)\\.|->");

    private static final MolangBinder BINDER = new MolangBinder();

    private static final Set<String> DYNAMIC_ROOTS = Set.of("variable", "temp", "context", "array", "texture", "geometry", "material", "particle");
    private static final Set<String> NAMESPACE_ROOTS = Set.of("query", "q", "math", "variable", "v", "temp", "t", "context", "c", "array", "texture", "geometry", "material", "particle");

    private MolangCheck() {
    }

    public enum Severity {
        OK,
        ERROR
    }

    public record Finding(String source, String expression, Severity severity, List<String> messages) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "--expr".equals(args[0])) {
            String expression = args.length > 1 ? args[1] : "";
            initMapping();
            Finding finding = checkExpression("<cli>", expression);
            print(List.of(finding), true);
            System.exit(finding.severity() == Severity.ERROR ? 1 : 0);
        }

        Path target = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/dokimobs");
        initMapping();
        List<Finding> findings = checkPath(target);
        print(findings, false);
        boolean hasError = findings.stream().anyMatch(f -> f.severity() == Severity.ERROR);
        System.exit(hasError ? 1 : 0);
    }

    public static void initMapping() {
        // 重复初始化是幂等的；游戏内已经由 eyelib 初始化过时，这里会以同一组类重建，
        // 不影响运行期状态（检查器只在开发工具路径调用）。
        MolangMappingRegistries.setupMappingTree(() -> MAPPING_CLASSES);
    }

    public static List<Finding> checkPath(Path target) throws IOException {
        Map<String, Finding> bySource = new LinkedHashMap<>();
        if (Files.isRegularFile(target)) {
            scanFile(target, bySource);
        } else if (Files.isDirectory(target)) {
            try (var stream = Files.walk(target)) {
                stream.filter(Files::isRegularFile)
                      .filter(MolangCheck::isScannable)
                      .sorted()
                      .forEach(path -> {
                          try {
                              scanFile(path, bySource);
                          } catch (IOException e) {
                              throw new RuntimeException(e);
                          }
                      });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException io) {
                    throw io;
                }
                throw e;
            }
        } else {
            throw new IOException("Not found: " + target.toAbsolutePath());
        }
        return new ArrayList<>(bySource.values());
    }

    private static boolean isScannable(Path path) {
        String name = path.getFileName().toString();
        // .bbmodel 也是动画来源（eyelib 直接加载其内嵌动画，剪辑关键帧里可写 Molang），
        // 例如 wolf.坐下 的 `q.is_riding ? -3 : -9`；漏扫它会让这类表达式逃过存在性检查。
        return name.endsWith(".json") || name.endsWith(".cospack") || name.endsWith(".bbmodel");
    }

    private static void scanFile(Path path, Map<String, Finding> out) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            out.put(path + ":<json>", new Finding(path + ":<json>", "<file>", Severity.ERROR,
                    List.of("JSON 解析失败: " + e.getMessage())));
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        collectStrings(root, path.toString(), seen);
        for (String expression : seen) {
            if (!MOLANG_HINT.matcher(expression).find()) continue;
            Finding finding = checkExpression(path.toString(), expression);
            out.putIfAbsent(finding.source() + " :: " + expression, finding);
        }
    }

    private static void collectStrings(JsonElement element, String source, Set<String> out) {
        if (element == null) return;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (!value.isBlank()) out.add(value);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectStrings(child, source, out);
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectStrings(entry.getValue(), source, out);
            }
        }
    }

    public static Finding checkExpression(String source, String expression) {
        List<String> messages = new ArrayList<>();
        if (expression == null || expression.isBlank()) {
            return new Finding(source, String.valueOf(expression), Severity.ERROR, List.of("空表达式"));
        }

        MolangParserFrontendResult parseResult = MolangParserFrontends.active().parseExprSet(expression);
        if (parseResult.ast().isEmpty()) {
            messages.add("语法错误：解析失败");
            return new Finding(source, expression, Severity.ERROR, messages);
        }

        // 不走 MolangCompilerImpl：字节码发射需要运行时才有的 classfile 库，
        // 且它同样不解析 query/math 是否存在（未知函数静默变 null）。
        // 解析 + 绑定已能判定语法/控制流语义，函数存在性由下面的 AST 遍历判定。
        var bindResult = BINDER.bind(parseResult.ast().get(), BindDiagnosticsMode.NORMAL);
        for (var diagnostic : bindResult.diagnostics()) {
            if (diagnostic.severity() == BindDiagnostic.Severity.ERROR) {
                messages.add(diagnostic.code() + ": " + diagnostic.message());
            }
        }
        if (!messages.isEmpty()) {
            return new Finding(source, expression, Severity.ERROR, messages);
        }

        AstChecker checker = new AstChecker();
        checker.visitExpr(parseResult.ast().get().root(), false);
        messages.addAll(checker.messages);
        return new Finding(source, expression, messages.isEmpty() ? Severity.OK : Severity.ERROR, messages);
    }

    private static void print(List<Finding> findings, boolean verbose) {
        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        System.out.printf(Locale.ROOT, "Molang check: %d expressions, %d error(s)%n", findings.size(), errors);
        for (Finding finding : findings) {
            if (finding.severity() == Severity.OK && !verbose) continue;
            System.out.printf(Locale.ROOT, "[%s] %s%n    %s%n", finding.severity(), finding.source(), finding.expression());
            for (String message : finding.messages()) {
                System.out.printf(Locale.ROOT, "      - %s%n", message);
            }
        }
    }

    /**
     * 遍历 AST，检查标识符/调用是否存在于映射表。
     * 动态命名空间（variable/temp/context）与渲染数组（Array.*）不做存在性检查。
     */
    private static final class AstChecker {
        private final List<String> messages = new ArrayList<>();
        private final Set<String> reported = new LinkedHashSet<>();

        void visitExpr(MolangAst.Expr expr, boolean arrowLeft) {
            if (expr == null) return;
            if (expr instanceof MolangAst.UnknownExpr unknown) {
                error("语法错误：无法识别的片段 [" + unknown.text() + "]");
                return;
            }
            if (expr instanceof MolangAst.IdentifierExpr identifier) {
                if (!arrowLeft) checkSymbol(identifier.name(), false);
                return;
            }
            if (expr instanceof MolangAst.MemberAccessExpr member) {
                checkSymbol(flatten(member), false);
                visitExpr(member.owner(), false);
                return;
            }
            if (expr instanceof MolangAst.CallExpr call) {
                String name = flatten(call.callee());
                if (name != null) checkSymbol(name, true);
                visitExpr(call.callee(), false);
                for (MolangAst.Expr argument : call.arguments()) visitExpr(argument, false);
                return;
            }
            if (expr instanceof MolangAst.UnaryExpr unary) {
                visitExpr(unary.expression(), false);
                return;
            }
            if (expr instanceof MolangAst.BinaryExpr binary) {
                visitExpr(binary.left(), false);
                visitExpr(binary.right(), false);
                return;
            }
            if (expr instanceof MolangAst.AssignmentExpr assignment) {
                visitExpr(assignment.target(), false);
                visitExpr(assignment.value(), false);
                return;
            }
            if (expr instanceof MolangAst.NullCoalesceExpr coalesce) {
                visitExpr(coalesce.left(), false);
                visitExpr(coalesce.right(), false);
                return;
            }
            if (expr instanceof MolangAst.ArrowAccessExpr arrow) {
                // 左式是实体引用（宿主），不是注册表函数；右式仍按普通表达式检查。
                visitExpr(arrow.left(), true);
                visitExpr(arrow.right(), false);
                return;
            }
            if (expr instanceof MolangAst.IndexExpr index) {
                visitExpr(index.owner(), false);
                visitExpr(index.index(), false);
                return;
            }
            if (expr instanceof MolangAst.GroupingExpr grouping) {
                visitExpr(grouping.expression(), false);
                return;
            }
            if (expr instanceof MolangAst.BlockExpr block) {
                for (MolangAst.Stmt statement : block.statements()) visitStmt(statement);
                return;
            }
            if (expr instanceof MolangAst.LoopExpr loop) {
                visitExpr(loop.count(), false);
                visitExpr(loop.body(), false);
                return;
            }
            if (expr instanceof MolangAst.ForEachExpr forEach) {
                // foreach 的循环变量是动态绑定，左侧不查注册表
                visitExpr(forEach.variable(), true);
                visitExpr(forEach.collection(), false);
                visitExpr(forEach.body(), false);
                return;
            }
            if (expr instanceof MolangAst.TernaryConditionalExpr ternary) {
                visitExpr(ternary.condition(), false);
                visitExpr(ternary.whenTrue(), false);
                visitExpr(ternary.whenFalse(), false);
            } else if (expr instanceof MolangAst.BinaryConditionalExpr binaryConditional) {
                visitExpr(binaryConditional.condition(), false);
                visitExpr(binaryConditional.whenFalse(), false);
            }
            // NumberLiteral/StringLiteral/ThisExpr/Break/Continue 无需检查
        }

        private void visitStmt(MolangAst.Stmt statement) {
            if (statement instanceof MolangAst.ExprStmt exprStmt) {
                visitExpr(exprStmt.expression(), false);
            } else if (statement instanceof MolangAst.ReturnStmt returnStmt) {
                visitExpr(returnStmt.expression(), false);
            } else if (statement instanceof MolangAst.BreakStmt breakStmt) {
                visitExpr(breakStmt.valueExpr(), false);
            } else if (statement instanceof MolangAst.ContinueStmt continueStmt) {
                visitExpr(continueStmt.valueExpr(), false);
            }
        }

        private void checkSymbol(String rawName, boolean call) {
            if (rawName == null || rawName.isBlank()) return;
            String name = MolangRootAliasCanonicalizer.canonicalizeQualifiedNameRoot(rawName);
            int dot = name.indexOf('.');
            String root = (dot == -1 ? name : name.substring(0, dot)).toLowerCase(Locale.ROOT);
            if (DYNAMIC_ROOTS.contains(root)) return;
            if (dot == -1 && NAMESPACE_ROOTS.contains(root)) return;

            var tree = MolangMappingRegistries.mappingTree();
            if (tree.findField(name) != null || tree.findMethod(name) != null) return;

            // 允许结构体/数组的二级成员：只要求最长已注册前缀存在。
            if (dot != -1) {
                String prefix = name;
                while (true) {
                    int lastDot = prefix.lastIndexOf('.');
                    if (lastDot <= 0) break;
                    prefix = prefix.substring(0, lastDot);
                    if (tree.findField(prefix) != null || tree.findMethod(prefix) != null) return;
                }
            }

            String kind = call ? "函数" : "字段/查询";
            error("不存在的 Molang " + kind + "：" + rawName + "（运行时将静默返回 0/null）");
        }

        private void error(String message) {
            if (reported.add(message)) messages.add(message);
        }

        private static String flatten(MolangAst.Expr expr) {
            if (expr instanceof MolangAst.IdentifierExpr identifier) return identifier.name();
            if (expr instanceof MolangAst.MemberAccessExpr member) {
                String owner = flatten(member.owner());
                return owner == null ? null : owner + "." + member.memberName();
            }
            if (expr instanceof MolangAst.ThisExpr) return "this";
            return null;
        }
    }
}
