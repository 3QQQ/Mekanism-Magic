package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.container.MachineFrameLayout;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.Map;
import java.util.WeakHashMap;

/** Align only external tabs to the padded case, never move menu slots or module contents. */
public final class MagicMachineFrame {
    private static final Map<GuiInsetElement<?>, Anchor> ANCHORS = new WeakHashMap<>();

    private MagicMachineFrame() { }

    public static void alignSideTabs(IGuiWrapper gui, Iterable<? extends GuiEventListener> children) {
        for (var child : children) {
            if (!(child instanceof GuiInsetElement<?> tab)) continue;
            int current = tab.getRelativeX();
            var previous = ANCHORS.get(tab);
            int logical = previous != null && current == previous.appliedX() ? previous.contentX() : current;
            int target = MachineFrameLayout.sideTabX(logical, gui.getXSize());
            if (current != target) tab.move(target - current, 0);
            ANCHORS.put(tab, new Anchor(logical, target));
        }
    }

    private record Anchor(int contentX, int appliedX) { }
}
