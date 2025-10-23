package io.github.tt432.dokimobs;

import com.mojang.blaze3d.platform.InputConstants;
import lombok.experimental.UtilityClass;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * @author TT432
 */
@UtilityClass
public class DMKeys {
    public final KeyMapping openExportScreen = new KeyMapping("Open Export Scrren", KeyConflictContext.IN_GAME,
            KeyModifier.ALT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_E), Dokimobs.MOD_ID);

    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Events {
        @SubscribeEvent
        public static void onEvent(RegisterKeyMappingsEvent event) {
            event.register(openExportScreen);
        }
    }
}
