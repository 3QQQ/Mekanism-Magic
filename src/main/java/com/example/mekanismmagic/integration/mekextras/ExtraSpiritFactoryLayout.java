package com.example.mekanismmagic.integration.mekextras;

/** Exact Mekanism Extras coordinates for its four extended factory tiers. */
public final class ExtraSpiritFactoryLayout {
    private ExtraSpiritFactoryLayout() {
    }

    public static int imageWidth(int tierOrdinal) {
        return 176 + 36 * (tierOrdinal + 2) + 2 * tierOrdinal;
    }

    public static int inventoryX(int tierOrdinal) {
        return 22 * (tierOrdinal + 2) - 3 * tierOrdinal;
    }

    public static int progressX(int process) {
        return 31 + process * 19;
    }
}
