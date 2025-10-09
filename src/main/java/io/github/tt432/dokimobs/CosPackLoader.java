package io.github.tt432.dokimobs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.github.tt432.eyelib.client.loader.SimpleJsonWithSuffixResourceReloadListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author TT432
 */
@Getter
@Slf4j
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CosPackLoader extends SimpleJsonWithSuffixResourceReloadListener {
    public static final CosPackLoader INSTANCE = new CosPackLoader();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEvent(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(INSTANCE);
    }

    private final Map<ResourceLocation, CosPackFile> files = new HashMap<>();

    private CosPackLoader() {
        super(new GsonBuilder().setLenient().create(), "dokimobs/cospack", "cospack");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, @NotNull ResourceManager pResourceManager, @NotNull ProfilerFiller pProfiler) {
        files.clear();

        pObject.forEach((k, v) -> {
            CosPackFile file = CosPackFileManager.load(v.getAsJsonObject());
            file.onLoad();
            files.put(k, file);
        });
    }
}
