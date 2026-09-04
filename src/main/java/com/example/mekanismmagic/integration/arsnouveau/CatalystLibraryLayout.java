package com.example.mekanismmagic.integration.arsnouveau;

/** Shared slot coordinates for the imbuement catalyst library module. */
public final class CatalystLibraryLayout {
    public static final int PAGE_SIZE = 16;
    public static final int COLUMNS = 4;
    public static final int SLOT_SPACING = 18;
    public static final int PANEL_TOP = 88;
    public static final int PANEL_WIDTH = 98;
    public static final int PANEL_HEIGHT = 112;
    public static final int SLOT_TOP = PANEL_TOP + 16;

    private CatalystLibraryLayout() {
    }

    public static int panelLeft(int imageWidth) {
        return imageWidth + 28;
    }

    public static int slotLeft(int imageWidth) {
        return panelLeft(imageWidth) + 4;
    }

    public static int slotX(int imageWidth, int pageSlot) {
        return slotLeft(imageWidth)
                + pageSlot % COLUMNS * SLOT_SPACING;
    }

    public static int slotY(int pageSlot) {
        return SLOT_TOP + pageSlot / COLUMNS * SLOT_SPACING;
    }

}
