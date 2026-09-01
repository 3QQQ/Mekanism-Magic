package com.example.mekanismmagic.integration.arsnouveau;

/** Coordinates for Ars machines centered in Mekanism's 210px work area. */
public final class ArsThreeByThreeMachineLayout {
    public static final int IMAGE_WIDTH = 210;
    public static final int INVENTORY_LABEL_X = 26;
    public static final int GRID_LEFT = 78;
    public static final int GRID_TOP = 40;
    public static final int SLOT_SPACING = 18;
    public static final int ENERGY_SLOT_X = 30;
    public static final int ENERGY_SLOT_Y = 35;
    public static final int STANDARD_PROGRESS_X = 140;
    public static final int SOURCE_SAFE_PROGRESS_X = 135;
    public static final int PROGRESS_Y = 62;
    public static final int OUTPUT_Y = 58;
    public static final int STANDARD_OUTPUT_X = 176;
    public static final int SOURCE_SAFE_OUTPUT_X = 166;
    public static final int RESOURCE_BAR_HEIGHT = 78;

    private ArsThreeByThreeMachineLayout() {
    }

    public static int slotX(int column) {
        return GRID_LEFT + column * SLOT_SPACING;
    }

    public static int slotY(int row) {
        return GRID_TOP + row * SLOT_SPACING;
    }
}
