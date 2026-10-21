# Molang 快速检查器

复用 eyelib 的 Molang 解析器 / 绑定器 / 运行时映射表，检查资源包里的 Molang：

1. **语法**：解析器是否接受表达式（括号、运算符、控制流、字符串等）。
2. **是否存在**：`query.*` / `math.*` / 顶层字段是否在 eyelib 映射表中。
   eyelib 对未注册的名称**静默返回 null/0**，运行时不会报错；本工具把它们报为错误。

## 用法

```bat
:: 扫描 assets/dokimobs（json + pack.cospack），只打印错误
gradlew.bat :1.20.1:molangCheck -PmolangTarget=E:/_ideaProjects/dokimobs/src/main/resources/assets/dokimobs

:: 单条表达式
gradlew.bat :1.20.1:molangCheckExpr -PmolangExpr="query.is_moving && query.health > 0"
```

`-PmolangTarget` 可以是文件或目录；默认目标是 `src/main/resources/assets/dokimobs`。
任务退出码：有错误为 1，全部通过为 0。

## 实现

- `io.github.tt432.dokimobs.molangcheck.MolangCheck`
  - `MolangParserFrontends.active().parseExprSet`：语法。
  - `MolangBinder`：控制流等语义诊断。
  - AST 遍历 + `MolangMappingRegistries.mappingTree().findField/findMethod`：函数/字段存在性。
- `MAPPING_CLASSES`：eyelib 内置映射类（`MolangBuiltInQuery`、`MolangQuery`、`MolangMath`、
  `MolangToplevel`）加上 dokimobs 自己的 `DokiMolangQueries`。新增映射类必须同步加入这里
  （游戏内 Forge 扫描会自动发现，独立运行不会）。
- 动态命名空间（`variable.`/`temp.`/`context.`）与渲染资源命名空间
  （`Array.`/`Texture.`/`Geometry.`/`Material.`）不做存在性检查。
- 不执行字节码发射：`MolangCompilerImpl` 需要运行时才有的 classfile 库，且同样不解析 query
  是否存在；语法 + 绑定 + 映射表检查已覆盖本工具目标。

## 限制

- 只检查“名字是否存在”，不检查参数个数/类型是否匹配（绑定器不解析函数签名）。
- 扫描 `.json` / `.cospack` 中所有“看起来像 Molang”的字符串（含 `query.`/`q.`/`math.` 等），
  不是按资源语义精确抽取；新增资源格式可能需要更新 `MOLANG_HINT`。
- 独立运行时映射表是硬编码的；游戏内运行时可改用已初始化的全局映射表。
