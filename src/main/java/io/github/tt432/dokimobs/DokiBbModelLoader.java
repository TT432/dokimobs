package io.github.tt432.dokimobs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.github.tt432.eyelib.bridge.client.loader.ResourceLoader;
import io.github.tt432.eyelib.bridge.client.loader.SimpleJsonWithSuffixResourceReloadListener;
import io.github.tt432.eyelib.bridge.client.render.texture.adapter.NativeImageIO;
import io.github.tt432.eyelib.client.loader.LoaderParsingOps;
import io.github.tt432.eyelib.client.manager.ModelManager;
import io.github.tt432.eyelib.importer.model.bbmodel.BBModel;
import io.github.tt432.eyelib.importer.model.importer.ModelImporter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * bbmodel 几何/贴图直载：扫描 assets/dokimobs/bbmodels/*.bbmodel，
 * 几何经 {@link ModelImporter#importBlockbench} 转入 {@link ModelManager}（键 = bbmodel 的
 * model_identifier，资源内已写为 geometry.doki_*），内嵌贴图（多张时 eyelib 已重拼为图集）
 * 上传到 {@code <ns>:textures/entity/bbmodel/<文件路径>.png}，供 client entity json 引用。
 *
 * <p>内嵌动画由 eyelib 的 BbModelAnimationLoader 同目录加载（id = dokimobs.<动画名>），
 * 本类不重复处理。逐文件失败隔离：单个坏模型只丢该模型。
 *
 * @author TT432
 */
@Slf4j
@ResourceLoader
public class DokiBbModelLoader extends SimpleJsonWithSuffixResourceReloadListener {

    DokiBbModelLoader() {
        super(new GsonBuilder().setLenient().create(), "bbmodels", "bbmodel");
    }

    @Override
    protected void applyJson(Map<String, JsonElement> source, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        Map<String, BBModel> parsed = LoaderParsingOps.parseBySourceKey(source, BBModel.CODEC, log, "bbmodel model");

        parsed.forEach((location, bbModel) -> {
            try {
                ModelImporter.ImportResult result = ModelImporter.importBlockbench(bbModel);
                ModelManager.INSTANCE.put(result.model().name(), result.model());

                if (result.atlasImageData() != null) {
                    // location = "dokimobs:wolf" → "dokimobs:textures/entity/bbmodel/wolf.png"
                    String textureKey = location.replace(":", ":textures/entity/bbmodel/") + ".png";
                    NativeImageIO.uploadFromImportedImageData(textureKey, result.atlasImageData());
                }
            } catch (Exception e) {
                log.error("can't import bbmodel model {}", location, e);
            }
        });
    }
}
