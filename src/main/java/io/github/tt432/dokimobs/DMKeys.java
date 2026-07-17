package io.github.tt432.dokimobs;

import com.mojang.blaze3d.platform.InputConstants;
import lombok.experimental.UtilityClass;
import net.minecraft.client.KeyMapping;
//? if legacy {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
//?}
import org.lwjgl.glfw.GLFW;

/**
 * @author TT432
 */
@UtilityClass
public class DMKeys {
    //? if !modern {
    public final KeyMapping openExportScreen = new KeyMapping("Open Export Scrren", KeyConflictContext.IN_GAME,
            KeyModifier.ALT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_E), Dokimobs.MOD_ID);
    //?} else {
    // 26.1：KeyMapping 分类改为 KeyMapping.Category（原版注册表语义）
    public final KeyMapping openExportScreen = new KeyMapping("Open Export Scrren", KeyConflictContext.IN_GAME,
            KeyModifier.ALT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_E),
            KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(Dokimobs.MOD_ID, "dokimobs")));
    //?}

    //? if legacy {
    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    //?} elif !modern {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    //?} else {
    @EventBusSubscriber
    //?}
    public static final class Events {
        @SubscribeEvent
        public static void onEvent(RegisterKeyMappingsEvent event) {
            event.register(openExportScreen);
        }
    }
}
