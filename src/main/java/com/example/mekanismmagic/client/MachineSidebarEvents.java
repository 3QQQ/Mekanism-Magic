package com.example.mekanismmagic.client;

import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.container.MachineSidebarLayout;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiRedstoneControlTab;
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
        }
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
