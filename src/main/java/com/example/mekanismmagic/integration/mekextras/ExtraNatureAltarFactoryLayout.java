package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.integration.naturesaura.NatureAltarFactoryLayout;

/** Extras' single-row factory geometry plus the shared eight-pixel Aura strip. */
public final class ExtraNatureAltarFactoryLayout {
    public static final int IMAGE_HEIGHT = NatureAltarFactoryLayout.IMAGE_HEIGHT;
    public static final int INVENTORY_Y = NatureAltarFactoryLayout.INVENTORY_Y;
    public static final int INVENTORY_LABEL_Y = NatureAltarFactoryLayout.INVENTORY_LABEL_Y;
    public static final int WARNING_Y = NatureAltarFactoryLayout.WARNING_Y;

    private ExtraNatureAltarFactoryLayout() {
    }

    public static int imageWidth(int tierOrdinal) {
        return ExtraSpiritFactoryLayout.imageWidth(tierOrdinal)
                + NatureAltarFactoryLayout.AURA_STRIP_WIDTH;
    }

    public static int inventoryX(int tierOrdinal) {
        return com.example.mekanismmagic.container.MachinePanelLayout.inventoryX(
                imageWidth(tierOrdinal));
    }

    public static int progressX(int process) {
        return ExtraSpiritFactoryLayout.progressX(process);
    }
}
