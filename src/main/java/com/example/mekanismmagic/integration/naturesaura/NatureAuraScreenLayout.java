package com.example.mekanismmagic.integration.naturesaura;

/** Shared menu/screen geometry after moving Aura side controls into their window. */
public final class NatureAuraScreenLayout {
    // Compact ordinary machines keep their three short readings above the
    // inventory caption; configuration controls live in the settings window.
    public static final int COMPACT_HEIGHT = com.example.mekanismmagic.container.MachinePanelLayout.NATURE_COMPACT_HEIGHT;
    public static final int COMPACT_INVENTORY_Y = com.example.mekanismmagic.container.MachinePanelLayout.inventoryY(COMPACT_HEIGHT);
    public static final int COMPACT_LABEL_Y = COMPACT_INVENTORY_Y - 13;
    // Factory output/catalyst slot borders end at 74. Keep warnings below them.
    public static final int COMPACT_WARNING_Y = 79;

    // The animal target and Aura readouts form two compact rows beneath its inputs.
    public static final int ANIMAL_HEIGHT = com.example.mekanismmagic.container.MachinePanelLayout.ANIMAL_HEIGHT;
    public static final int ANIMAL_INVENTORY_Y = com.example.mekanismmagic.container.MachinePanelLayout.inventoryY(ANIMAL_HEIGHT);
    public static final int ANIMAL_LABEL_Y = ANIMAL_INVENTORY_Y - 13;
    public static final int ANIMAL_WARNING_Y = 84;

    // The tree ritual has no permanent warning/readout row below its slots.
    public static final int TREE_HEIGHT = com.example.mekanismmagic.container.MachinePanelLayout.TREE_HEIGHT;
    public static final int TREE_INVENTORY_Y = com.example.mekanismmagic.container.MachinePanelLayout.inventoryY(TREE_HEIGHT);
    public static final int TREE_MATERIAL_X = 26;
    public static final int TREE_MATERIAL_Y = 35;
    public static final int TREE_SAPLING_X = 108;
    public static final int TREE_GOLD_X = 126;
    public static final int TREE_OUTPUT_X = 144;
    public static final int TREE_OUTPUT_Y = 53;
    /** A 28px arrow ends before the output slot's 18px visual border. */
    public static final int TREE_PROGRESS_X = 106;
    public static final int TREE_PROGRESS_Y = 57;

    private NatureAuraScreenLayout() {
    }
}
