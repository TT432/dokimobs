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
        return switch (object.get("version").getAsString()) {
            //? if <1.20.6 {
            case "1" -> CosPackFileV1.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow(false, IllegalArgumentException::new);
            //?} else {
            case "1" -> CosPackFileV1.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow();
            //?}
            default -> throw new IllegalStateException("Unexpected value: " + object.get("version").getAsString());
        };
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
