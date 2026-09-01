package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.integration.arsnouveau.ArsSourceModeHost;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.tab.window.GuiSideConfigurationTab;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Replaces Mekanism's normal side tab with the same tab backed by a native
 * configuration window that includes a Source page.
 */
public final class ArsIntegratedSideConfig {
    private ArsIntegratedSideConfig() {
    }

    public static <TILE extends TileEntityMekanism
            & ISideConfiguration & ArsSourceModeHost>
    void install(IGuiWrapper gui, TILE tile,
                 Iterable<? extends GuiEventListener> children,
                 Consumer<GuiElement> add) {
        for (GuiEventListener child : children) {
            if (child instanceof GuiSideConfigurationTab<?>) {
                GuiElement element = (GuiElement) child;
                element.visible = false;
                element.active = false;
            }
        }
        AtomicReference<ArsSideConfigurationTab<TILE>> reference =
                new AtomicReference<>();
        ArsSideConfigurationTab<TILE> tab = new ArsSideConfigurationTab<>(
                gui, tile, reference::get);
        reference.set(tab);
        add.accept(tab);
    }
}
