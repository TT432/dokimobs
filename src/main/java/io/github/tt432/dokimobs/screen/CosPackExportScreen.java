package io.github.tt432.dokimobs.screen;

import io.github.tt432.dokimobs.CosPackFileManager;
import io.github.tt432.dokimobs.CosPackFileV1;
import io.github.tt432.dokimobs.DMKeys;
import io.github.tt432.eyelib.Eyelib;
import io.github.tt432.eyelib.client.entity.BrClientEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * @author TT432
 */
public class CosPackExportScreen extends Screen {
    protected CosPackExportScreen() {
        super(Component.literal("CosPack Export Screen"));
    }

    @EventBusSubscriber(Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void onEvent(RenderFrameEvent.Post event) {
            if (DMKeys.openExportScreen.consumeClick()) {
                Minecraft.getInstance().setScreen(new CosPackExportScreen());
            }
        }
    }

    CosPackEntitiesListPanel panel;
    int border;

    @Override
    protected void init() {
        super.init();
        border = Math.round(height * 0.1F);
        int inputHeight = Math.round(Minecraft.getInstance().font.lineHeight / 0.614F);
        int leftAreaWidth = 120;
        EditBox input = new EditBox(this.minecraft.fontFilterFishy, border, border, leftAreaWidth, inputHeight, Component.empty());
        input.setMaxLength(256);
        input.setBordered(true);
        input.setResponder(this::onEdited);
        input.setCanLoseFocus(true);
        this.addRenderableWidget(input);
        int padding = Math.round(inputHeight * .1F);
        addRenderableWidget(panel = new CosPackEntitiesListPanel(Minecraft.getInstance(), leftAreaWidth, Math.round(height * 0.8F) - (inputHeight + padding), border + (inputHeight + padding), border));
        addRenderableWidget(new CosPackSelectedListView(Minecraft.getInstance(), leftAreaWidth, Math.round(height * 0.8F), border, border + leftAreaWidth + border, panel.getSelected()));
        addRenderableWidget(new Button.Builder(Component.literal("只显示有 entity 文件的生物"), b -> {
            panel.onlyEntity = true;
            panel.update();
        })
                .pos(border * 3 + leftAreaWidth * 2, height - inputHeight - border - inputHeight/2 - inputHeight)
                .size(width - (leftAreaWidth * 2 + border * 4), inputHeight)
                .build());

        addRenderableWidget(new Button.Builder(Component.literal("导出为 cospack"), b -> {
            var file = CosPackFileV1.from(panel.getSelected().stream().map(s -> Eyelib.getClientEntityLoader().get(ResourceLocation.parse(s.key))).toArray(BrClientEntity[]::new));
            CosPackFileManager.export(file);
        })
                .pos(border * 3 + leftAreaWidth * 2, height - inputHeight - border)
                .size(width - (leftAreaWidth * 2 + border * 4), inputHeight)
                .build());
    }

    void onEdited(String input) {
        panel.onEdited(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
