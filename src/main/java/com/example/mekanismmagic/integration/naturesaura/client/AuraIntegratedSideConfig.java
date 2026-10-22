package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.naturesaura.AuraSideModeHost;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.tab.window.GuiSideConfigurationTab;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Replaces Mekanism's normal side tab with the same tab backed by a native
 * configuration window that includes an Aura page.
 */
public final class AuraIntegratedSideConfig {
    private AuraIntegratedSideConfig() {
    }

    public static <TILE extends TileEntityMekanism
            & ISideConfiguration & AuraSideModeHost>
    void install(IGuiWrapper gui, TILE tile,
                 Iterable<? extends GuiEventListener> children,
                 Consumer<GuiElement> add,
                 Consumer<GuiEventListener> removeWidget) {
        if (!(gui instanceof GuiMekanism<?> screen)) {
            throw new IllegalArgumentException(
                    "Aura side configuration requires a Mekanism screen");
        }
        boolean installed = false;
        List<GuiEventListener> nativeTabs = new ArrayList<>();
        for (GuiEventListener child : children) {
            if (child instanceof AuraSideConfigurationTab<?>) {
                installed = true;
            } else if (child instanceof GuiSideConfigurationTab<?>) {
                nativeTabs.add(child);
            }
        }
        // Hidden tabs still reopen pinned windows during Mekanism's init.
        // Remove them after iteration from the screen's widget collections.
        nativeTabs.forEach(removeWidget);
        if (installed) {
            return;
        }
        add.accept(new AuraSideConfigurationTab<>(gui, tile,
                () -> findCurrentTab(screen)));
    }

    @SuppressWarnings("unchecked")
    private static <TILE extends TileEntityMekanism
            & ISideConfiguration & AuraSideModeHost>
    AuraSideConfigurationTab<TILE> findCurrentTab(GuiMekanism<?> screen) {
        // Windows survive widget rebuilds. Resolve the replacement tab from
        // the screen each time a window closes or reattaches after a resize.
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AuraSideConfigurationTab<?> tab) {
                // All tabs installed on this screen share its tile type.
                return (AuraSideConfigurationTab<TILE>) tab;
            }
        }
        throw new IllegalStateException(
                "Aura side configuration tab is missing from its screen");
    }
}
