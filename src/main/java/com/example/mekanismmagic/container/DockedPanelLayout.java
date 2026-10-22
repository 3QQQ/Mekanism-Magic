package com.example.mekanismmagic.container;

/** Shared sidebar frame and slot-border geometry. Coordinates describe full 18px slots, not just their 16px items. */
public record DockedPanelLayout(int x, int y, int width, int height, int headerHeight) {
    public static final int BORDER = 2;
    public static final int PADDING = 4;
    public static final int HEADER = 16;
    public static final int SLOT = 18;
    public static final int GAP = 28 + MachineFrameLayout.CASE_PADDING;
    public static final int RITUAL_SCREEN_WIDTH = 210;
    public static final DockedPanelLayout CHALK = grid(MachinePanelLayout.MINI_RITUAL_WIDTH, 88, 16);
    public static final DockedPanelLayout DICTIONARY = grid(RITUAL_SCREEN_WIDTH, 88, 1);

    public DockedPanelLayout {
        if (width <= 2 * BORDER || height <= headerHeight + BORDER || headerHeight < BORDER) {
            throw new IllegalArgumentException("Sidebar frame is too small");
        }
    }

    public static DockedPanelLayout grid(int imageWidth, int top, int count) {
        checkCount(count);
        int columns = Math.min(4, count);
        return new DockedPanelLayout(imageWidth + GAP, top, Math.max(62, columns * SLOT + 2 * PADDING),
                HEADER + 2 * PADDING + Math.ceilDiv(count, columns) * SLOT, HEADER);
    }

    public int slotX(int index, int count) {
        checkIndex(index, count);
        int columns = Math.min(4, count);
        int rowSize = Math.min(columns, count - index / columns * columns);
        return x + (width - rowSize * SLOT) / 2 + 1 + index % columns * SLOT;
    }

    public int slotY(int index, int count) {
        checkIndex(index, count);
        return y + headerHeight + PADDING + 1 + index / Math.min(4, count) * SLOT;
    }

    public boolean isGridSlot(int itemX, int itemY, int count) {
        for (int index = 0; index < count; index++) if (itemX == slotX(index, count) && itemY == slotY(index, count)) return true;
        return false;
    }

    public boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    public Rect frame() { return new Rect(x, y, width, height); }
    public Rect interior() { return new Rect(x + BORDER, y + BORDER, width - 2 * BORDER, height - 2 * BORDER); }
    public Rect header() { return new Rect(x + BORDER, y + BORDER, width - 2 * BORDER, headerHeight - BORDER); }
    public Rect body() { return new Rect(x + BORDER, y + headerHeight, width - 2 * BORDER, height - headerHeight - BORDER); }
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }
    private static void checkCount(int count) {
        if (count < 1 || count > 16) throw new IllegalArgumentException("Sidebar grid supports 1..16 slots");
    }
    private static void checkIndex(int index, int count) {
        checkCount(count);
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
    }
}
