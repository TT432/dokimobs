package io.github.tt432.dokimobs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.eyelib.Eyelib;
import io.github.tt432.eyelib.client.animation.Animation;
import io.github.tt432.eyelib.client.animation.bedrock.BrAnimationEntry;
import io.github.tt432.eyelib.client.animation.bedrock.controller.BrAnimationController;
import io.github.tt432.eyelib.client.entity.BrClientEntity;
import io.github.tt432.eyelib.client.loader.BrClientEntityLoader;
import io.github.tt432.eyelib.client.model.bedrock.BrModelEntry;
import io.github.tt432.eyelib.client.render.controller.RenderControllerEntry;
import io.github.tt432.eyelib.util.client.NativeImages;
import io.github.tt432.eyelib.util.codec.CodecHelper;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

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

    record FileEntry(
            String identifier,
            BrClientEntity entity,
            Map<String, Animation> animations,
            Map<String, RenderControllerEntry> renderControllers,
            List<BrModelEntry> models,
            Map<String, String> textureBase64s
    ) {
        public static final Codec<FileEntry> CODEC = RecordCodecBuilder.create(ins -> ins.group(
                Codec.STRING.fieldOf("identifier").forGetter(FileEntry::identifier),
                BrClientEntity.CODEC.fieldOf("entity").forGetter(FileEntry::entity),
                CodecHelper.dispatchedMap(Codec.STRING, name -> (Codec<Animation>) (Object) (name.startsWith("animation") ? BrAnimationEntry.codec(name) : BrAnimationController.CODEC)).fieldOf("animations").forGetter(o -> o.animations),
                Codec.unboundedMap(Codec.STRING, RenderControllerEntry.CODEC).fieldOf("renderControllers").forGetter(FileEntry::renderControllers),
                BrModelEntry.CODEC.listOf().fieldOf("models").forGetter(FileEntry::models),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("textures").forGetter(FileEntry::textureBase64s)
        ).apply(ins, FileEntry::new));

        public void onLoad() {
            BrClientEntityLoader.INSTANCE.getEntities().put(new ResourceLocation(entity.identifier()), entity);
            animations.forEach((k, v) -> Eyelib.getAnimationManager().put(k, v));
            models.forEach(m -> Eyelib.getModelManager().put(m.name(), m));
            renderControllers.forEach((k, v) -> Eyelib.getRenderControllerManager().put(k, v));
            textureBase64s.forEach((k, v) -> {
                try {
                    var ni = NativeImage.read(Base64.getDecoder().decode(v));
                    Minecraft.getInstance().getTextureManager().register(new ResourceLocation(k), new DynamicTexture(ni));
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
                animations.put(value, Eyelib.getAnimationManager().get(value));
            }

            List<BrModelEntry> models = new ArrayList<>();

            for (String value : entity.geometry().values()) {
                if (Eyelib.getModelManager().get(value) instanceof BrModelEntry model)
                    models.add(model);
            }

            Map<String, RenderControllerEntry> renderControllers = new HashMap<>();

            for (String renderController : entity.render_controllers()) {
                renderControllers.put(renderController, Eyelib.getRenderControllerManager().get(renderController));
            }

            Map<String, String> textureBase64s = new HashMap<>();

            for (String value : entity.textures().values()) {
                String base64 = NativeImages.downloadImage(new ResourceLocation(value), ni -> {
                    try {
                        return Base64.getEncoder().encodeToString(ni.asByteArray());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                if (base64 == null) {
                    base64 = NativeImages.downloadImage(new ResourceLocation(replacePng(value, ".png", ".tga")), ni -> {
                        try {
                            return Base64.getEncoder().encodeToString(ni.asByteArray());
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
            JsonElement orThrow = jsonElementDataResult.getOrThrow(false, IllegalArgumentException::new);
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