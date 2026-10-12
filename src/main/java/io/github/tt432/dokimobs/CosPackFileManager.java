package io.github.tt432.dokimobs;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author TT432
 */
@UtilityClass
public class CosPackFileManager {
    public CosPackFile load(JsonObject object) {
        normalizeInlineInitialStates(object);
        return switch (object.get("version").getAsString()) {
            //? if <1.20.6 {
            case "1" -> CosPackFileV1.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow(false, IllegalArgumentException::new);
            //?} else {
            case "1" -> CosPackFileV1.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow();
            //?}
            default -> throw new IllegalStateException("Unexpected value: " + object.get("version").getAsString());
        };
    }

    /**
     * cospack 美术导出的动画控制器使用内联 {@code "initialState": {...}} 对象（非标准基岩格式；
     * 标准是首状态名字符串 {@code "initial_state"}）。eyelib 的 BrAnimationControllerSchema codec
     * 只认 {@code initial_state} 字符串并静默忽略未知字段：无 "default" 状态的控制器因此回退到
     * 空状态（无动画、无转移）并永久卡死——牛/羊/猪/哞菇移动与待机全丢（2026-10-12 实机 dump 实证，
     * 50 个控制器受影响）。
     * 解析前规范化：内联对象注入 states.default（default 已存在时视为重复定义，丢弃内联）；
     * camelCase 字符串形式映射到标准 initial_state。
     */
    private static void normalizeInlineInitialStates(JsonObject pack) {
        var entries = pack.getAsJsonArray("entries");
        if (entries == null) return;

        for (var entryEl : entries) {
            if (!entryEl.isJsonObject()) continue;
            var animations = entryEl.getAsJsonObject().getAsJsonObject("animations");
            if (animations == null) continue;

            for (var animEntry : animations.entrySet()) {
                if (!animEntry.getValue().isJsonObject()) continue;
                var controller = animEntry.getValue().getAsJsonObject();
                var initial = controller.get("initialState");
                if (initial == null) continue;

                if (initial.isJsonPrimitive()) {
                    if (!controller.has("initial_state")) {
                        controller.addProperty("initial_state", initial.getAsString());
                    }
                } else if (initial.isJsonObject()) {
                    var states = controller.getAsJsonObject("states");
                    if (states == null) {
                        states = new JsonObject();
                        controller.add("states", states);
                    }
                    if (!states.has("default")) {
                        states.add("default", initial);
                    }
                }
                controller.remove("initialState");
            }
        }
    }

    public void export(CosPackFile file) {
        fileSelectDialog(
                "保存文件",
                Path.of("pack.cospack"),
                "导出为 cospack",
                "*.cospack"
        ).whenComplete((path, throwable) -> {
            path.ifPresent(p -> file.export(p.toString()));
        });
    }

    private final ExecutorService FILE_DIALOG_EXECUTOR = Executors.newSingleThreadExecutor();

    private CompletableFuture<Optional<Path>> fileSelectDialog(String title, @Nullable Path origin, @Nullable String filterLabel, String... filters) {
        CompletableFuture<Optional<Path>> future = new CompletableFuture<>();

        FILE_DIALOG_EXECUTOR.submit(() -> {
            String result = null;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filterBuffer = stack.mallocPointer(filters.length);

                for (String filter : filters) {
                    filterBuffer.put(stack.UTF8(filter));
                }
                filterBuffer.flip();

                String path = origin != null ? origin.toAbsolutePath().toString() : null;

                result = TinyFileDialogs.tinyfd_saveFileDialog(title, path, filterBuffer, filterLabel);
            }

            future.complete(Optional.ofNullable(result).map(Paths::get));
        });

        return future;
    }
}
