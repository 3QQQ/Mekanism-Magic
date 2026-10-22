package com.example.mekanismmagic.container;

/** Arrow geometry is measured between complete 18px slot borders, not item icons. */
public final class MachineArrowLayout {
    public static final int SLOT_GAP = 3;
    public static final int FACTORY_DOWN_Y = 36;
    public static final int FACTORY_DOWN_WIDTH = 8;
    public static final int FACTORY_DOWN_HEIGHT = 17;
    public static final int FACTORY_SHAFT_CUT_START = 8;
    public static final int FACTORY_SHAFT_CUT_LENGTH = 3;

    private MachineArrowLayout() { }

    /** X is the existing native arrow centerline position (input item X + 4). */
    public static MachinePanelLayout.Box factoryDown(int arrowX) {
        return new MachinePanelLayout.Box(arrowX, FACTORY_DOWN_Y, FACTORY_DOWN_WIDTH, FACTORY_DOWN_HEIGHT);
    }
}
