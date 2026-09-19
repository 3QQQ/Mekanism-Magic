package com.example.mekanismmagic.container;

/** Shared right-side rows. Keep the optional ME pattern tab's native row free. */
public final class MachineSidebarLayout {
    public static final int TAB_HEIGHT = 26;
    public static final int TAB_GAP = 2;
    public static final int PATTERN_TAB_Y = 62;
    private static final int BOTTOM_MARGIN = 3;

    private MachineSidebarLayout() { }

    public static int moduleY(boolean precedingModule) {
        return PATTERN_TAB_Y + (precedingModule ? 2 : 1) * (TAB_HEIGHT + TAB_GAP);
    }

    /** The original 166px screen stays at 137; taller machines use their own lower edge. */
    public static int redstoneY(int imageHeight) {
        return Math.max(137, imageHeight - TAB_HEIGHT - BOTTOM_MARGIN);
    }
}
