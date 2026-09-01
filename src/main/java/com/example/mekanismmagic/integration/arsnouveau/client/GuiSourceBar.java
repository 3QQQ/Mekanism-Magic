package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.SourceDisplayHost;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.bar.GuiBar;
import net.minecraft.util.Mth;
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
                        ISourceTile tile,
                        int x, int y, int height) {
        super(TEXTURE, gui, new IBarInfoHandler() {
            @Override
            public double getLevel() {
                if (creativeSource(tile)) {
                    return 1;
                }
                int capacity = displayedCapacity(tile);
                return capacity <= 0 ? 0 : Mth.clamp(
                        tile.getSource() / (double) capacity, 0, 1);
            }

            @Override
            public Component getTooltip() {
                int capacity = displayedCapacity(tile);
                if (creativeSource(tile)) {
                    return Component.translatable(
                            "gui.mekanism_magic.source_creative_active",
                            tile.getSource(), capacity);
                }
                return Component.translatable(
                        "gui.mekanism_magic.source_amount",
                        tile.getSource(), capacity);
            }
        }, x, y, 4, height, false);
        barHeight = height;
    }

    @Override
    protected void renderBarOverlay(GuiGraphics graphics, int mouseX,
                                    int mouseY, float partialTick,
                                    double level) {
        double clamped = Mth.clamp(level, 0, 1);
        int scaled = calculateScaled(clamped, barHeight);
        if (clamped > 0 && scaled <= 0) {
            // Large upgraded buffers can contain a useful amount below one
            // pixel of the bar. Never render non-zero Source as completely
            // empty.
            scaled = 1;
        }
        if (scaled <= 0) {
            return;
        }
        graphics.blit(getResource(), relativeX + 1,
                relativeY + height - 1 - scaled,
                4, scaled, 0, barHeight - scaled,
                4, scaled, 4, barHeight);
    }

    private static boolean creativeSource(ISourceTile tile) {
        return tile instanceof SourceDisplayHost display
                && display.mekanismMagicCreativeSourceActive();
    }

    private static int displayedCapacity(ISourceTile tile) {
        return tile instanceof SourceDisplayHost display
                ? display.mekanismMagicDisplayedSourceCapacity()
                : tile.getMaxSource();
    }
}
