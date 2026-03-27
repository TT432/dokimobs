package io.github.tt432.dokimobs;


import io.github.tt432.dokimobs.config.DokimobsConfig;
import io.github.tt432.dokimobs.data.DokiData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Dokimobs.MOD_ID)
public class Dokimobs {

    public static final String MOD_ID = "dokimobs";

    public Dokimobs(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, DokimobsConfig.CONFIG_SPEC);
        DokiData.ATTACHMENT_TYPES.register(bus);
    }
}
