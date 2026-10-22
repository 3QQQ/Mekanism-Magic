package com.example.mekanismmagic.client;

import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicMachineFrame;
import com.example.mekanismmagic.container.MachineSidebarLayout;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.tab.GuiRedstoneControlTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Align the inherited native control after every init/resize, only on our own screens. */
@EventBusSubscriber(modid = "mekanism_magic", value = Dist.CLIENT)
public final class MachineSidebarEvents {
    private MachineSidebarEvents() { }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof MagicThemedScreen && event.getScreen() instanceof IGuiWrapper gui) {
            alignRedstone(gui, event.getScreen().children());
            alignWarning(gui, event.getScreen().children());
            MagicMachineFrame.alignSideTabs(gui, event.getScreen().children());
        }
    }

    /** Keep Mekanism's conditional warning tab clear of settings, sorting and energy controls. */
    public static void alignWarning(IGuiWrapper gui,
                                    Iterable<? extends GuiEventListener> children) {
        java.util.List<GuiElement> elements = new java.util.ArrayList<>();
        GuiWarningTab warning = null;
        for (GuiEventListener child : children) {
            if (child instanceof GuiElement element) {
                elements.add(element);
                if (element instanceof GuiWarningTab tab
                        && tab.getRelativeX() < 0) {
                    warning = tab;
                }
            }
        }
        if (warning == null) return;
        int targetX = -26;
        int targetY = -1;
        int lastAboveSettings = MagicLeftControlLayout.settingsY(gui)
                - warning.getHeight() - 2;
        for (int y = 62; y <= lastAboveSettings; y += 28) {
            if (free(elements, warning, targetX, y)) {
                targetY = y;
                break;
            }
        }
        if (targetY < 0) {
            // Full factories have a 35px sorting tab at y=62..97. Their
            // first column has no spare 26px row; the warning alone uses an
            // upper secondary column, never the lower settings/energy row.
            targetX = -52;
            targetY = 62;
        }
        warning.move(targetX - warning.getRelativeX(),
                targetY - warning.getRelativeY());
    }

    private static boolean free(java.util.List<GuiElement> elements,
                                GuiWarningTab warning, int x, int y) {
        int right = x + warning.getWidth();
        int bottom = y + warning.getHeight();
        for (GuiElement element : elements) {
            if (element == warning || !element.visible
                    || element.getRelativeX() >= 0) continue;
            if (x < element.getRelativeRight()
                    && right > element.getRelativeX()
                    && y < element.getRelativeBottom()
                    && bottom > element.getRelativeY()) {
                return false;
            }
        }
        return true;
    }

    public static void alignRedstone(IGuiWrapper gui, Iterable<? extends GuiEventListener> children) {
        for (GuiEventListener child : children) {
            if (child instanceof GuiRedstoneControlTab tab) {
                // move updates both relative rendering coordinates and the actual hitbox.
                tab.move(0, MachineSidebarLayout.redstoneY(gui.getYSize()) - tab.getRelativeY());
            }
        }
    }
}
