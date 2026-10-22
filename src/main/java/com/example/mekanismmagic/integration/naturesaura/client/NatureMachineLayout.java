package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.container.MachinePanelLayout.Box;
import com.example.mekanismmagic.container.MachinePanelLayout;

/** Client-only grouping sharing dimensions and inventory offsets with the registered menus. */
public final class NatureMachineLayout {
    public static final Box COMPACT_UPPER = MachinePanelLayout.CONVERSION_RATE;
    public static final Box COMPACT_LOWER = MachinePanelLayout.FIELD_AMOUNT;
    public static final Box COMPACT_FOOTER = MachinePanelLayout.NATURE_COMPACT_FOOTER;
    public static final Box FUEL_FOOTER = MachinePanelLayout.FUEL_FOOTER;
    public static final Box ANIMAL_AURA = MachinePanelLayout.ANIMAL_AURA;

    private NatureMachineLayout() { }

    public static Box factoryFooter(int imageWidth) {
        return MachinePanelLayout.natureFactoryFooter(imageWidth);
    }
}
