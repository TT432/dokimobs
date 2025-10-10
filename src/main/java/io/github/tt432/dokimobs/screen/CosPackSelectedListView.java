package io.github.tt432.dokimobs.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import io.github.tt432.eyelib.Eyelib;
import io.github.tt432.eyelib.client.ClientTickHandler;
import io.github.tt432.eyelib.client.gui.manager.EyelibManagerScreen;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.widget.ScrollPanel;

import java.util.List;

/**
 * @author TT432
 */
public class CosPackSelectedListView extends ScrollPanel {
    private final List<CosPackEntitiesListPanel.EntityButton> selected;
    private final Font font;

    public CosPackSelectedListView(Minecraft client,Font font, int width, int height, int top, int left, List<CosPackEntitiesListPanel.EntityButton> selected) {
        super(client, width, height, top, left);
        this.selected = selected;
        this.font = font;
    }

    private int buttonPreLine() {
        return 4;
    }

    private int lineAmount() {
        return (selected.size() - 1) / buttonPreLine();
    }

    private int slotSize() {
        return (width - (border * 2)) / 4;
    }

    @Override
    protected int getContentHeight() {
        return lineAmount() * slotSize();
    }

    @Getter
    private CosPackEntitiesListPanel.EntityButton hoverButton;

    @Override
    protected void drawPanel(GuiGraphics guiGraphics, int entryRight, int relativeY, Tesselator tess, int mouseX, int mouseY) {

        var slotSize = slotSize();
        var renderLine = (height - border * 2) / slotSize + 2;
        var iconBorder = slotSize / 30F;
        var iconSize = slotSize - (iconBorder * 2);
        int line = (int) (scrollDistance / slotSize);

        hoverButton = null;

        if (lineAmount() >= line) {
            for (int i = 0; i < renderLine; i++) {
                int index;
                for (int i1 = 0; i1 < buttonPreLine() && (index = (line + i) * buttonPreLine() + i1) < selected.size(); i1++) {
                    var x = Math.round(left + border + i1 * slotSize + iconBorder);
                    var y = Math.round(top + border - (scrollDistance % slotSize) + i * slotSize + iconBorder);
                    var s = Math.round(iconSize);

                    CosPackEntitiesListPanel.EntityButton entityButton = selected.get(index);
                    boolean hover = EyelibManagerScreen.hover(x, y, slotSize, slotSize, mouseX, mouseY);
                    float a;

                    if (!selected.contains(entityButton)) {
                        a = entityButton.animator.getTime(ClientTickHandler.getTick(), Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), hover);
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

    public static void renderEntityButton(GuiGraphics guiGraphics, int x, int y, int s, float a, CosPackEntitiesListPanel.EntityButton entityButton) {
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath(Eyelib.MOD_ID, "gui_bg_nine"), x, y, s, s);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, a);
        guiGraphics.blitSprite(ResourceLocation.fromNamespaceAndPath(Eyelib.MOD_ID, "gui_bg_nine_selected"), x, y, s, s);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        guiGraphics.blitSprite(entityButton.icon, x + 4, y + 4, s - 8, s - 8);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button))
            return true;

        if (hoverButton != null) {
            selected.remove(hoverButton);
            return true;
        }
        return false;
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {

    }
}