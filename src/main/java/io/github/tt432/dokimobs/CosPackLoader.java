package io.github.tt432.dokimobs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.github.tt432.eyelib.bridge.client.loader.SimpleJsonWithSuffixResourceReloadListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
//? if legacy {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} elif !modern {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.minecraft.resources.Identifier;
//?}
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author TT432
 */
@Getter
@Slf4j
//? if legacy {
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
//?} elif !modern {
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
//?} else {
@EventBusSubscriber(value = Dist.CLIENT)
//?}
public class CosPackLoader extends SimpleJsonWithSuffixResourceReloadListener {
    public static final CosPackLoader INSTANCE = new CosPackLoader();

    //? if !modern {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEvent(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(INSTANCE);
    }
    //?} else {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEvent(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Dokimobs.MOD_ID, "cospack_loader"), INSTANCE);
    }
    //?}

    private final Map<String, CosPackFile> files = new HashMap<>();

    private CosPackLoader() {
        super(new GsonBuilder().setLenient().create(), "dokimobs/cospack", "cospack");
    }

    @Override
    protected void applyJson(Map<String, JsonElement> source, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        files.clear();

        source.forEach((k, v) -> {
            CosPackFile file = CosPackFileManager.load(v.getAsJsonObject());
            file.onLoad();
            files.put(k, file);
        });
    }
}
