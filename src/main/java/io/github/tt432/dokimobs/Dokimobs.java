package io.github.tt432.dokimobs;


import io.github.tt432.dokimobs.config.DokimobsConfig;
import io.github.tt432.dokimobs.data.DokiData;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Dokimobs.MOD_ID)
public class Dokimobs {

    public static final String MOD_ID = "dokimobs";

    public Dokimobs() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext container = ModLoadingContext.get();

        container.registerConfig(ModConfig.Type.COMMON, DokimobsConfig.CONFIG_SPEC);
        DokiData.DATA_ATTACHMENTS.register(bus);
    }
}
