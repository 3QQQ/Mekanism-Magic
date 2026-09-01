package com.example.mekanismmagic.client.gui;

import mekanism.client.gui.element.slot.GuiSlot;

/** Optional client checks that never link the core mod against ME classes. */
public final class MekEnergisticsGuiCompat {
    private static final String PATTERN_WINDOW =
            "com.beipuo.mekenergistics.client.overlay."
                    + "MePatternWindowOverlay$MePatternWindow";

    private MekEnergisticsGuiCompat() {
    }

    public static boolean isPatternWindowOpen(Iterable<?> windows) {
        for (Object window : windows) {
            if (window != null
                    && PATTERN_WINDOW.equals(window.getClass().getName())) {
                return true;
            }
        }
        return false;
    }

    /** Keeps a machine slot out of the ME pattern window's render/input layer. */
    public static boolean updateMachineSlot(Iterable<?> windows,
                                            GuiSlot slot) {
        boolean available = !isPatternWindowOpen(windows);
        if (slot != null) {
            slot.visible = available;
            slot.active = available;
        }
        return available;
    }
}
