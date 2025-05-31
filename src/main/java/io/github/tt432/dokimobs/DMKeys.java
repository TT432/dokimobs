package io.github.tt432.dokimobs;

import com.mojang.blaze3d.platform.InputConstants;
import lombok.experimental.UtilityClass;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * @author TT432
 */
@UtilityClass
public class DMKeys {
    public final KeyMapping openExportScreen = new KeyMapping("Open Export Scrren", KeyConflictContext.IN_GAME,
            KeyModifier.ALT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_E), Dokimobs.MOD_ID);

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static final class Events {
        @SubscribeEvent
        public static void onEvent(RegisterKeyMappingsEvent event) {
            event.register(openExportScreen);
        }
    }
}
