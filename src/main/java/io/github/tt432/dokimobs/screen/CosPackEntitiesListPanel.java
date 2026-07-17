//? if >=1.21 {
package io.github.tt432.dokimobs.screen;

import io.github.tt432.eyelib.bridge.Eyelib;
import io.github.tt432.eyelib.bridge.client.ClientFrameTimePort;
import io.github.tt432.eyelib.bridge.client.ClientTickHandler;
import io.github.tt432.eyelib.client.gui.manager.EyelibManagerScreen;
import io.github.tt432.eyelib.client.manager.ClientEntityManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
//? if !modern {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
//?} else {
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ScrollPanel;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author TT432
 */
public class CosPackEntitiesListPanel extends ScrollPanel {
    @RequiredArgsConstructor
    static class GuiAnimator {
        public final int animTime;
        private float startStamp, timer, timerStamp;
        private boolean fadeIn;

        public final float getTime(int tick, float partialTicks, boolean state) {
            float time = tick + partialTicks;

            if (state) {
                if (!fadeIn) {
                    fadeIn = true;
                    timerStamp = timer;
                    startStamp = time;
                }
            } else if (fadeIn) {
                fadeIn = false;
                timerStamp = timer;
                startStamp = time;
            }

            timer = Math.clamp(timerStamp + (fadeIn ? 1 : -1) * (time - startStamp), 0, animTime);
            return Math.clamp(timer / animTime, 0, 1);
        }
    }

    @RequiredArgsConstructor
    static class EntityButton {
        final String key;
        final Component name;
        //? if !modern {
        final ResourceLocation icon;
        //?} else {
        final Identifier icon;
        //?}
        final GuiAnimator animator = new GuiAnimator(5);
    }

    @Getter
    private final List<EntityButton> selected = new ArrayList<>();

    private final List<EntityButton> allEntitiesList = new ArrayList<>();
    private final List<EntityButton> filtedEntitiesList = new ArrayList<>();
    private boolean finished = false;
    public boolean onlyEntity = false;
    private Font font;

    {
        CompletableFuture.runAsync(() -> {
            //? if !modern {
            for (ResourceLocation resourceLocation : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                allEntitiesList.add(new EntityButton(
                        resourceLocation.toString(),
                        BuiltInRegistries.ENTITY_TYPE.get(resourceLocation).getDescription(),
                        ResourceLocation.fromNamespaceAndPath(Eyelib.MOD_ID, "icons/entities/" + resourceLocation.toString().replace(":", "/"))
                ));
            }
            //?} else {
            for (Identifier resourceLocation : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                allEntitiesList.add(new EntityButton(
                        resourceLocation.toString(),
                        BuiltInRegistries.ENTITY_TYPE.getValue(resourceLocation).getDescription(),
                        Identifier.fromNamespaceAndPath(Eyelib.MOD_ID, "icons/entities/" + resourceLocation.toString().replace(":", "/"))
                ));
            }
            //?}
            filtedEntitiesList.addAll(allEntitiesList);
            finished = true;
        });
    }

    public CosPackEntitiesListPanel(Minecraft client, Font font, int width, int height, int top, int left) {
        super(client, width, height, top, left);
        this.font = font;
    }

    String lastSearch = "";

    public void update() {
        onEdited(lastSearch);
    }

    void onEdited(String input) {
        filtedEntitiesList.clear();
        for (EntityButton entityButton : allEntitiesList) {
            if ((StringUtils.contains(entityButton.key, input) || StringUtils.contains(entityButton.name.getString(), input)) &&
                    (onlyEntity == (ClientEntityManager.INSTANCE.get(entityButton.key) != null))) {
                filtedEntitiesList.add(entityButton);
            }
        }
        scrollDistance = 0;
        lastSearch = input;
    }

    private int buttonPreLine() {
        return 4;
    }

    private int lineAmount() {
        return (filtedEntitiesList.size() - 1) / buttonPreLine();
    }

    private int slotSize() {
        return (width - (border * 2)) / 4;
    }

    @Override
    protected int getContentHeight() {
        return (lineAmount() + 1) * slotSize();
    }

    @Getter
    private EntityButton hoverButton;

    //? if !modern {
    @Override
    protected void drawPanel(GuiGraphics guiGraphics, int entryRight, int relativeY, Tesselator tess, int mouseX, int mouseY) {
    //?} else {
    @Override
    protected void drawPanel(GuiGraphicsExtractor guiGraphics, int entryRight, int relativeY, int mouseX, int mouseY) {
    //?}
        if (!finished) {
            centeredString(guiGraphics, font, "正在加载", left, top, width, height);
        } else {
            var slotSize = slotSize();
            var renderLine = (height - border * 2) / slotSize + 2;
            var iconBorder = slotSize / 30F;
            var iconSize = slotSize - (iconBorder * 2);
            int line = (int) (scrollDistance / slotSize);

            hoverButton = null;

            if (lineAmount() >= line) {
                for (int i = 0; i < renderLine; i++) {
                    int index;
                    for (int i1 = 0; i1 < buttonPreLine() && (index = (line + i) * buttonPreLine() + i1) < filtedEntitiesList.size(); i1++) {
                        var x = Math.round(left + border + i1 * slotSize + iconBorder);
                        var y = Math.round(top + border - (scrollDistance % slotSize) + i * slotSize + iconBorder);
                        var s = Math.round(iconSize);

                        EntityButton entityButton = filtedEntitiesList.get(index);
                        boolean hover = EyelibManagerScreen.hover(x, y, slotSize, slotSize, mouseX, mouseY);
                        float a;

                        if (!selected.contains(entityButton)) {
                            a = entityButton.animator.getTime(ClientTickHandler.getTick(), ClientFrameTimePort.getGameTimeDeltaPartialTick(), hover);
                        } else {
                            a = 1;
                        }

                        renderEntityButton(guiGraphics, x, y, s, a, entityButton);
                        if (EyelibManagerScreen.hover(x, y, iconSize, iconSize, mouseX, mouseY)) {
                            hoverButton = entityButton;
                        }
                    }
                }
            }
        }
    }

    //? if !modern {
    public static void renderEntityButton(GuiGraphics guiGraphics, int x, int y, int s, float a, EntityButton entityButton) {
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath(Eyelib.MOD_ID, "gui_bg_nine"), x, y, s, s);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, a);
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath(Eyelib.MOD_ID, "gui_bg_nine_selected"), x, y, s, s);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        guiGraphics.blitSprite(entityButton.icon, x + 4, y + 4, s - 8, s - 8);
    }
    //?} else {
    public static void renderEntityButton(GuiGraphicsExtractor guiGraphics, int x, int y, int s, float a, EntityButton entityButton) {
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(Eyelib.MOD_ID, "gui_bg_nine"), x, y, s, s);
        // 选中/悬停渐显：颜色乘法 alpha（26.1 的 RenderSystem.setShaderColor 已移除）
        int alphaColor = ((int) (a * 255) << 24) | 0xFFFFFF;
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(Eyelib.MOD_ID, "gui_bg_nine_selected"), x, y, s, s, alphaColor);

        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, entityButton.icon, x + 4, y + 4, s - 8, s - 8);
    }
    //?}

    //? if !modern {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button))
            return true;

        if (hoverButton != null) {
            selected.add(hoverButton);
            return true;
        }
        return false;
    }
    //?} else {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick))
            return true;

        if (hoverButton != null) {
            selected.add(hoverButton);
            return true;
        }
        return false;
    }
    //?}

    //? if !modern {
    private static void centeredString(GuiGraphics guiGraphics, Font font, String string, int x, int y, int w, int h) {
        int th = font.lineHeight;

        int tw = font.width(string);
        guiGraphics.drawString(font, string, x + w / 2 - tw / 2, y + h / 2 - th / 2, 0xFFFFFFFF);
    }
    //?} else {
    private static void centeredString(GuiGraphicsExtractor guiGraphics, Font font, String string, int x, int y, int w, int h) {
        int th = font.lineHeight;

        int tw = font.width(string);
        guiGraphics.text(font, string, x + w / 2 - tw / 2, y + h / 2 - th / 2, 0xFFFFFFFF);
    }
    //?}

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {

    }
}
//?}
