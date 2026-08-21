package com.example.mekanismmagic.integration.jei;

import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Adds Mekanism-style automation colors and beginner-facing descriptions to
 * this mod's JEI slots without changing their ingredient roles.
 */
final class JeiSlotMarker {
    private JeiSlotMarker() {
    }

    static IRecipeSlotBuilder mark(IRecipeSlotBuilder slot, Kind kind,
                                   String slotName) {
        return slot.setSlotName(slotName)
                .setOverlay(kind.border, -1, -1)
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable(kind.translationKey)));
    }

    enum Kind {
        INPUT("jei.mekanism_magic.slot.input", 0xFFD12F2F),
        OUTPUT("jei.mekanism_magic.slot.output", 0xFF2F5FD1),
        SPIRIT("jei.mekanism_magic.slot.spirit", 0xFFFFD21F),
        ACTIVATION("jei.mekanism_magic.slot.activation", 0xFFFFD21F),
        SACRIFICE("jei.mekanism_magic.slot.sacrifice", 0xFFFF8C00),
        RITUAL_SELECTOR("jei.mekanism_magic.slot.ritual_selector", 0xFF9E9E9E),
        CHALK("jei.mekanism_magic.slot.chalk", 0xFF9E9E9E);

        private final String translationKey;
        private final IDrawable border;

        Kind(String translationKey, int color) {
            this.translationKey = translationKey;
            border = new SlotBorder(color);
        }
    }

    private static final class SlotBorder implements IDrawable {
        private final int color;

        private SlotBorder(int color) {
            this.color = color;
        }

        @Override
        public int getWidth() {
            return 18;
        }

        @Override
        public int getHeight() {
            return 18;
        }

        @Override
        public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
            graphics.fill(xOffset, yOffset, xOffset + 18, yOffset + 1, color);
            graphics.fill(xOffset, yOffset + 17, xOffset + 18,
                    yOffset + 18, color);
            graphics.fill(xOffset, yOffset + 1, xOffset + 1,
                    yOffset + 17, color);
            graphics.fill(xOffset + 17, yOffset + 1, xOffset + 18,
                    yOffset + 17, color);
        }
    }
}
