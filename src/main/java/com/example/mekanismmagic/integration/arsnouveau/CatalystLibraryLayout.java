package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.container.DockedPanelLayout;

/** Shared slot coordinates for the imbuement catalyst library module. */
public final class CatalystLibraryLayout {
    public static final int PAGE_SIZE = 16;
    public static final int COLUMNS = 4;
    public static final int SLOT_SPACING = 18;
    public static final int PANEL_TOP = 88;
    public static final int PANEL_WIDTH = 98;
    // A full-height header accommodates the selector with equal top/bottom margins.
    public static final int FRAME_TOP = PANEL_TOP - 22;
    public static final int HEADER_HEIGHT = 2 * DockedPanelLayout.PADDING + SLOT_SPACING;
    public static final int SLOT_TOP = FRAME_TOP + HEADER_HEIGHT + DockedPanelLayout.PADDING + 1;
    public static final int FOOTER_TOP = SLOT_TOP - 1 + 4 * SLOT_SPACING + DockedPanelLayout.PADDING;
    public static final int FRAME_HEIGHT = FOOTER_TOP + SLOT_SPACING + DockedPanelLayout.PADDING - FRAME_TOP;
    public static final int PANEL_HEIGHT = FRAME_TOP + FRAME_HEIGHT - PANEL_TOP;
    public static final int ACTIVE_SLOT_Y = FRAME_TOP + DockedPanelLayout.PADDING + 1;

    private CatalystLibraryLayout() {
    }

    public static int panelLeft(int imageWidth) {
        return imageWidth + DockedPanelLayout.GAP;
    }

    public static int slotLeft(int imageWidth) {
        return panelLeft(imageWidth) + (PANEL_WIDTH - COLUMNS * SLOT_SPACING) / 2 + 1;
    }

    public static int activeSlotX(int imageWidth) {
        return panelLeft(imageWidth) + PANEL_WIDTH - DockedPanelLayout.PADDING - SLOT_SPACING + 1;
    }

    public static DockedPanelLayout panel(int imageWidth) {
        return new DockedPanelLayout(panelLeft(imageWidth), FRAME_TOP, PANEL_WIDTH, FRAME_HEIGHT, HEADER_HEIGHT);
    }

    /** Hit-test the same complete 18px borders that Mekanism renders. */
    public static int pageSlotAt(int imageWidth, double x, double y) {
        double gridX = x - (slotLeft(imageWidth) - 1), gridY = y - (SLOT_TOP - 1);
        if (gridX < 0 || gridX >= COLUMNS * SLOT_SPACING || gridY < 0 || gridY >= 4 * SLOT_SPACING) return -1;
        return (int) (gridY / SLOT_SPACING) * COLUMNS + (int) (gridX / SLOT_SPACING);
    }

    public static boolean containsActiveSlot(int imageWidth, double x, double y) {
        int left = activeSlotX(imageWidth) - 1, top = ACTIVE_SLOT_Y - 1;
        return x >= left && x < left + 18 && y >= top && y < top + 18;
    }

    public static int slotX(int imageWidth, int pageSlot) {
        return slotLeft(imageWidth)
                + pageSlot % COLUMNS * SLOT_SPACING;
    }

    public static int slotY(int pageSlot) {
        return SLOT_TOP + pageSlot / COLUMNS * SLOT_SPACING;
    }

}
