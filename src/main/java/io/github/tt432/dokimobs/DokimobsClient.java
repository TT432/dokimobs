package io.github.tt432.dokimobs;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * @author TT432
 */
@Mod(value = Dokimobs.MOD_ID, dist = Dist.CLIENT)
public class DokimobsClient {
    public DokimobsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
