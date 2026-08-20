package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceMachineBlockEntity;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.bar.GuiBar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Compact Mekanism-style vertical bar for Ars Nouveau Source.
 */
public final class GuiSourceBar
        extends GuiBar<GuiBar.IBarInfoHandler> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                    "textures/gui/source_bar.png");
    private final int barHeight;

    public GuiSourceBar(IGuiWrapper gui,
                        ArsSourceMachineBlockEntity tile,
                        int x, int y, int height) {
        super(TEXTURE, gui, new IBarInfoHandler() {
            @Override
            public double getLevel() {
                return tile.getSourceScale();
            }

            @Override
            public Component getTooltip() {
                return Component.translatable(
                        "gui.mekanism_magic.source_amount",
                        tile.getSource(), tile.getMaxSource());
            }
        }, x, y, 4, height, false);
        barHeight = height;
    }

    @Override
    protected void renderBarOverlay(GuiGraphics graphics, int mouseX,
                                    int mouseY, float partialTick,
                                    double level) {
        int scaled = calculateScaled(level, barHeight);
        if (scaled <= 0) {
            return;
        }
        graphics.blit(getResource(), relativeX + 1,
                relativeY + height - 1 - scaled,
                4, scaled, 0, barHeight - scaled,
                4, scaled, 4, barHeight);
    }
}
