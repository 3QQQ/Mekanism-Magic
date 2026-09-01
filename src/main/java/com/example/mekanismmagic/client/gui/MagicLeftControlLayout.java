package com.example.mekanismmagic.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiEnergyTab;

/** Bottom anchors shared by the two lower-left theme controls. */
public final class MagicLeftControlLayout {
    private static final int TAB_HEIGHT = 26;
    private static final int BOTTOM_MARGIN = 3;
    private static final int TAB_GAP = 7;
    private static final int NATIVE_ENERGY_Y = 137;

    private MagicLeftControlLayout() {
    }

    public static int energyY(IGuiWrapper gui) {
        return gui.getYSize() - TAB_HEIGHT - BOTTOM_MARGIN;
    }

    public static int themeY(IGuiWrapper gui) {
        return energyY(gui) - TAB_HEIGHT - TAB_GAP;
    }

    /** Moves Mekanism's fixed-y energy tab to this screen's lower edge. */
    public static GuiEnergyTab alignEnergyTab(IGuiWrapper gui,
                                               GuiEnergyTab tab) {
        tab.move(0, energyY(gui) - NATIVE_ENERGY_Y);
        return tab;
    }
}
