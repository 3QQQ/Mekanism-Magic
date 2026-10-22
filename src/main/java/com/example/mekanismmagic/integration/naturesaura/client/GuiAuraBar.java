package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.naturesaura.AuraSideModeHost;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.bar.GuiBar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Same native border, dimensions and tooltip handling as the Source bar. */
public final class GuiAuraBar extends GuiBar<GuiBar.IBarInfoHandler> {
    private final AuraSideModeHost tile;
    private final int barHeight;

    public GuiAuraBar(IGuiWrapper gui, AuraSideModeHost tile, int x, int y, int height) {
        super(BAR, gui, new IBarInfoHandler() {
            @Override
            public double getLevel() {
                if (tile.isCreativeAuraActive()) {
                    return 1;
                }
                int capacity = tile.getAuraCapacity();
                return capacity <= 0 ? 0 : Mth.clamp(tile.getStoredAura() / (double) capacity, 0, 1);
            }

            @Override
            public Component getTooltip() {
                return Component.translatable(tile.isCreativeAuraActive()
                                ? "gui.mekanism_magic.aura_creative_active"
                                : "gui.mekanism_magic.aura_amount",
                        tile.getStoredAura(), tile.getAuraCapacity());
            }
        }, x, y, 4, height, false);
        this.tile = tile;
        this.barHeight = height;
    }

    @Override
    protected void renderBarOverlay(GuiGraphics graphics, int mouseX, int mouseY,
                                    float partialTick, double level) {
        double clamped = Mth.clamp(level, 0, 1);
        int scaled = calculateScaled(clamped, barHeight);
        if (clamped > 0 && scaled == 0) {
            scaled = 1;
        }
        if (scaled <= 0) {
            return;
        }
        int x = relativeX + 1;
        int bottom = relativeY + height - 1;
        int top = bottom - scaled;
        int color = 0xFF000000 | (tile.getAuraColor() & 0xFFFFFF);
        // Four-pixel fill with a restrained native-style highlight. Aura type
        // supplies the hue; no Ars texture or mutable shader tint is reused.
        graphics.fill(x, top, x + 4, bottom, color);
        graphics.fill(x, top, x + 1, bottom, mixWhite(color, 0.28F));
        graphics.fill(x + 3, top, x + 4, bottom, shade(color, 0.72F));
    }

    private static int mixWhite(int color, float amount) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        return 0xFF000000 | ((red + Math.round((255 - red) * amount)) << 16)
                | ((green + Math.round((255 - green) * amount)) << 8)
                | (blue + Math.round((255 - blue) * amount));
    }

    private static int shade(int color, float factor) {
        return 0xFF000000 | (Math.round((color >> 16 & 255) * factor) << 16)
                | (Math.round((color >> 8 & 255) * factor) << 8)
                | Math.round((color & 255) * factor);
    }
}
