package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.common.tier.FactoryTier;

/** Shared Mekanism factory coordinates plus the reserved Source strip. */
public final class ImbuementFactoryLayout {
    public static final int SOURCE_STRIP_WIDTH = 8;
    public static final int LOCK_SLOT_X = 7;
    public static final int LOCK_SLOT_Y = 57;
    public static final int BAR_Y = 16;
    public static final int BAR_HEIGHT = 52;

    private ImbuementFactoryLayout() {
    }

    public static int standardImageWidth(FactoryTier tier) {
        return 176 + (tier == FactoryTier.ULTIMATE ? 34 : 0)
                + SOURCE_STRIP_WIDTH;
    }

    public static int standardInventoryX(FactoryTier tier) {
        return (tier == FactoryTier.ULTIMATE ? 26 : 8)
                + SOURCE_STRIP_WIDTH / 2;
    }

    public static int extraImageWidth(int tierOrdinal) {
        return 176 + 36 * (tierOrdinal + 2) + 2 * tierOrdinal
                + SOURCE_STRIP_WIDTH;
    }

    public static int extraInventoryX(int tierOrdinal) {
        return 22 * (tierOrdinal + 2) - 3 * tierOrdinal
                + SOURCE_STRIP_WIDTH / 2;
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

    public static int progressX(int firstProcessX, int spacing, int process) {
        return firstProcessX + process * spacing + 4;
    }
}
