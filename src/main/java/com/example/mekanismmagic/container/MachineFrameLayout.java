package com.example.mekanismmagic.container;

/** Physical machine case around the unchanged logical content/menu coordinates. */
public final class MachineFrameLayout {
    public static final int CASE_PADDING = 3;
    public static final int WORK_INVENTORY_GAP = 3;

    private MachineFrameLayout() { }

    public static MachinePanelLayout.Box outerBounds(int left, int top, int contentWidth, int contentHeight) {
        return new MachinePanelLayout.Box(left - CASE_PADDING, top - CASE_PADDING,
                contentWidth + 2 * CASE_PADDING, contentHeight + 2 * CASE_PADDING);
    }

    public static boolean containsFrame(double mouseX, double mouseY, int left, int top,
                                         int contentWidth, int contentHeight) {
        var bounds = outerBounds(left, top, contentWidth, contentHeight);
        return mouseX >= bounds.x() && mouseX < bounds.right()
                && mouseY >= bounds.y() && mouseY < bounds.bottom();
    }

    public static int sideTabX(int contentX, int contentWidth) {
        return contentX < 0 ? contentX - CASE_PADDING
                : contentX >= contentWidth ? contentX + CASE_PADDING : contentX;
    }
}
