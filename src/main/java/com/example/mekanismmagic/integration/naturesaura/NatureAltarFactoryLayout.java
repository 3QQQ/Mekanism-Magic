package com.example.mekanismmagic.integration.naturesaura;

import mekanism.common.tier.FactoryTier;

/** Mekanism factory coordinates with a narrow Aura status strip. */
public final class NatureAltarFactoryLayout {
    // Native factory lanes need their full 18px output borders and a readable
    // recipe row; ordinary Aura machines can use the shorter work deck.
    public static final int IMAGE_HEIGHT = com.example.mekanismmagic.container.MachinePanelLayout.NATURE_FACTORY_HEIGHT;
    public static final int INVENTORY_Y = com.example.mekanismmagic.container.MachinePanelLayout.inventoryY(IMAGE_HEIGHT);
    public static final int INVENTORY_LABEL_Y = INVENTORY_Y - 13;
    public static final int WARNING_Y = NatureAuraScreenLayout.COMPACT_WARNING_Y;
    public static final int AURA_STRIP_WIDTH = 8;
    public static final int CATALYST_SLOT_X = 7;
    public static final int SECOND_CATALYST_SLOT_X = 7;
    public static final int SECOND_CATALYST_SLOT_Y = 35;
    public static final int CATALYST_SLOT_Y = 57;
    public static final int BAR_Y = 16;
    public static final int BAR_HEIGHT = 52;

    private NatureAltarFactoryLayout() {
    }

    public static int imageWidth(FactoryTier tier) {
        return 176 + (tier == FactoryTier.ULTIMATE ? 34 : 0)
                + AURA_STRIP_WIDTH;
    }

    public static int inventoryX(FactoryTier tier) {
        return com.example.mekanismmagic.container.MachinePanelLayout.inventoryX(imageWidth(tier));
    }

    public static int firstProcessX(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> 55;
            case ADVANCED -> 35;
            case ELITE -> 29;
            case ULTIMATE -> 27;
        };
    }

    public static int processSpacing(FactoryTier tier) {
        return switch (tier) {
            case BASIC -> 38;
            case ADVANCED -> 26;
            case ELITE, ULTIMATE -> 19;
        };
    }
}
