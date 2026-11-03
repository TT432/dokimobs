package io.github.tt432.dokimobs;


import io.github.tt432.dokimobs.config.DokimobsConfig;
import io.github.tt432.dokimobs.data.DokiData;
import io.github.tt432.dokimobs.item.DokimobsItems;
//? if legacy {
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//?} else {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
//?}

@Mod(Dokimobs.MOD_ID)
public class Dokimobs {

    public static final String MOD_ID = "dokimobs";

    //? if legacy {
    public Dokimobs() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DokimobsConfig.CONFIG_SPEC);
        DokiData.init();
        DokimobsItems.init(FMLJavaModLoadingContext.get().getModEventBus());
    }
    //?} else {
    public Dokimobs(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, DokimobsConfig.CONFIG_SPEC);
        DokiData.init();
        DokimobsItems.init(bus);
    }
    //?}
}
