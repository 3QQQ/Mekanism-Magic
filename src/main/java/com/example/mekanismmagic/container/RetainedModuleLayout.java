package com.example.mekanismmagic.container;

/** Chalk-style retained module geometry, independent of item storage and client rendering. */
public record RetainedModuleLayout(int panelX, int panelY, int width, int height, int columns, int slots, int tabY) {
    public static final int SPACING = 18;
    public static final int HEADER = 16;

    public static RetainedModuleLayout of(int imageWidth, int slots, boolean dictionaryNeighbour) {
        int tabY = MachineSidebarLayout.moduleY(dictionaryNeighbour);
        int y = tabY - 2;
        var panel = DockedPanelLayout.grid(imageWidth, y, slots);
        return new RetainedModuleLayout(panel.x(), panel.y(), panel.width(), panel.height(),
                Math.min(4, slots), slots, tabY);
    }

    public DockedPanelLayout panel() { return new DockedPanelLayout(panelX, panelY, width, height, HEADER); }
    public int slotX(int index) { check(index); return panel().slotX(index, slots); }
    public int slotY(int index) { check(index); return panel().slotY(index, slots); }
    public boolean contains(double x, double y) { return x >= panelX && x < panelX + width && y >= panelY && y < panelY + height; }
    private void check(int index) { if (index < 0 || index >= slots) throw new IndexOutOfBoundsException(index); }
}
