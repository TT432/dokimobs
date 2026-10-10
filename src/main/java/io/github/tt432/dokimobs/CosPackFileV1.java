package io.github.tt432.dokimobs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.eyelib.animation.Animation;
import io.github.tt432.eyelib.animation.AnimationRegistries;
import io.github.tt432.eyelib.animation.bedrock.BrAnimationEntry;
import io.github.tt432.eyelib.animation.bedrock.controller.BrAnimationController;
import io.github.tt432.eyelib.bridge.client.render.texture.adapter.NativeImageIO;
import io.github.tt432.eyelib.client.manager.ClientEntityManager;
import io.github.tt432.eyelib.client.manager.ModelManager;
import io.github.tt432.eyelib.client.manager.RenderControllerManager;
import io.github.tt432.eyelib.client.render.controller.RenderControllerEntry;
import io.github.tt432.eyelib.importer.animation.bedrock.controller.BrAnimationControllerSchema;
import io.github.tt432.eyelib.importer.entity.BrClientEntity;
import io.github.tt432.eyelib.model.Model;
//? if <1.20.6 {
import io.github.tt432.eyelib.util.codec.CodecHelper;
//?}
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author TT432
 */
@Slf4j
public record CosPackFileV1(
        String version,
        List<FileEntry> entries
) implements CosPackFile {
    public static final Codec<CosPackFileV1> CODEC = RecordCodecBuilder.create(ins -> ins.group(
            Codec.STRING.fieldOf("version").forGetter(CosPackFileV1::version),
            FileEntry.CODEC.listOf().fieldOf("entries").forGetter(CosPackFileV1::entries)
    ).apply(ins, CosPackFileV1::new));

    @Override
    public Collection<String> identifiers() {
        // 与 FileEntry.onLoad 写入 ClientEntityManager 的 key 保持一致
        return entries.stream().map(e -> e.entity().identifier()).toList();
    }

    /**
     * 提取 PNG 编码字节。26.1 移除了 {@code NativeImage#asByteArray()}（仅剩 writeToFile），
     * 经临时文件换取等价字节。
     */
    private static byte[] toPngBytes(com.mojang.blaze3d.platform.NativeImage ni) throws IOException {
        //? if !modern {
        return ni.asByteArray();
        //?} else {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("dokimobs_tex", ".png");
        try {
            ni.writeToFile(tmp);
            return java.nio.file.Files.readAllBytes(tmp);
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
        //?}
    }

    record FileEntry(
            String identifier,
            BrClientEntity entity,
            Map<String, Animation> animations,
            Map<String, RenderControllerEntry> renderControllers,
            List<Model> models,
            Map<String, String> textureBase64s
    ) {
        /**
         * 动画条目按名称前缀分发：animation.* 是动画片段，其余是动画控制器。
         * 控制器在 eyelib 21.1.14 起以 BrAnimationControllerSchema 为序列化形式。
         */
        private static Codec<Animation> animationEntryCodec(String name) {
            if (name.startsWith("animation")) {
                return (Codec<Animation>) (Object) BrAnimationEntry.codec(name);
            } else {
                return (Codec<Animation>) (Object) BrAnimationControllerSchema.CODEC.xmap(
                        schema -> BrAnimationController.fromSchema(name, schema),
                        BrAnimationController::toSchema);
            }
        }

        public static final Codec<FileEntry> CODEC = RecordCodecBuilder.create(ins -> ins.group(
                Codec.STRING.fieldOf("identifier").forGetter(FileEntry::identifier),
                BrClientEntity.CODEC.fieldOf("entity").forGetter(FileEntry::entity),
                //? if <1.20.6 {
                CodecHelper.dispatchedMap(Codec.STRING, FileEntry::animationEntryCodec).fieldOf("animations").forGetter(o -> o.animations),
                //?} else {
                Codec.dispatchedMap(Codec.STRING, FileEntry::animationEntryCodec).fieldOf("animations").forGetter(o -> o.animations),
                //?}
                Codec.unboundedMap(Codec.STRING, RenderControllerEntry.CODEC).fieldOf("renderControllers").forGetter(FileEntry::renderControllers),
                Model.CODEC.listOf().fieldOf("models").forGetter(FileEntry::models),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("textures").forGetter(FileEntry::textureBase64s)
        ).apply(ins, FileEntry::new));

        public void onLoad() {
            ClientEntityManager.INSTANCE.put(entity.identifier(), entity);
            animations.forEach((k, v) -> AnimationRegistries.animation().put(k, v));
            models.forEach(m -> ModelManager.INSTANCE.put(m.name(), m));
            renderControllers.forEach((k, v) -> RenderControllerManager.INSTANCE.put(k, v));
            textureBase64s.forEach((k, v) -> {
                try {
                    var ni = NativeImage.read(Base64.getDecoder().decode(v));
                    NativeImageIO.upload(k, ni);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        public void onExport() {
            animations.entrySet().removeIf(e -> e.getValue() == null);
        }
    }

    public static CosPackFileV1 from(BrClientEntity... entities) {
        List<FileEntry> entries = new ArrayList<>();

        for (BrClientEntity entity : entities) {
            if (entity == null) continue;
            Map<String, Animation> animations = new HashMap<>();

            for (String value : entity.animations().values()) {
                animations.put(value, AnimationRegistries.animation().get(value));
            }

            List<Model> models = new ArrayList<>();

            for (String value : entity.geometry().values()) {
                Model model = ModelManager.INSTANCE.get(value);
                if (model != null)
                    models.add(model);
            }

            Map<String, RenderControllerEntry> renderControllers = new HashMap<>();

            for (String renderController : entity.render_controllers()) {
                renderControllers.put(renderController, RenderControllerManager.INSTANCE.get(renderController));
            }

            Map<String, String> textureBase64s = new HashMap<>();

            for (String value : entity.textures().values()) {
                String base64 = NativeImageIO.download(value, ni -> {
                    try {
                        return Base64.getEncoder().encodeToString(toPngBytes(ni));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                if (base64 == null) {
                    base64 = NativeImageIO.download(replacePng(value, ".png", ".tga"), ni -> {
                        try {
                            return Base64.getEncoder().encodeToString(toPngBytes(ni));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                if (base64 != null)
                    textureBase64s.put(value, base64);
            }

            entries.add(new FileEntry(entity.identifier(), entity, animations, renderControllers, models, textureBase64s));
        }

        return new CosPackFileV1("1", entries);
    }

    static String replacePng(String originalString, String old, String newStr) {
        int lastIndexOfDot = originalString.lastIndexOf(old);

        if (lastIndexOfDot != -1) {
            String beforeDot = originalString.substring(0, lastIndexOfDot);
            return beforeDot + newStr;
        } else {
            return originalString;
        }
    }

    @Override
    public void export(String path) {
        if (!new File(path).exists()) {
            try {
                new File(path).createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        entries.forEach(FileEntry::onExport);

        try (FileOutputStream fos = new FileOutputStream(path)) {
            DataResult<JsonElement> jsonElementDataResult = CODEC.encodeStart(JsonOps.INSTANCE, this);
            //? if <1.20.6 {
            JsonElement orThrow = jsonElementDataResult.getOrThrow(false, IllegalArgumentException::new);
            //?} else {
            JsonElement orThrow = jsonElementDataResult.getOrThrow(s -> {
                log.error(s);
                return new IllegalArgumentException(s);
            });
            //?}
            fos.write(new Gson().toJson(orThrow).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public void onLoad() {
        entries.forEach(FileEntry::onLoad);
    }
}
