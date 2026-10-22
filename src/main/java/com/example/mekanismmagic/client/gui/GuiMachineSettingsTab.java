package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.container.MachinePanelLayout.Box;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The one native settings entry remains available even when every optional network mod is absent. */
public final class GuiMachineSettingsTab extends GuiInsetElement<TileEntityMekanism> {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/configuration.png");
    private final TileEntityMekanism tile;

    public GuiMachineSettingsTab(IGuiWrapper gui, TileEntityMekanism tile, Box position) {
        super(ICON, gui, tile, position.x(), position.y(), 26, 18, true);
        this.tile = tile;
        setTooltip(Tooltip.create(Component.translatable("gui.mekanism_magic.settings.title")));
        visible = true;
        active = true;
    }

    @Override protected void colorTab(GuiGraphics graphics) { MekanismRenderer.resetColor(graphics); }

    @Override public void onClick(double x, double y, int button) {
        if (visible && active && tile != null && gui() instanceof GuiMekanism<?> screen) {
            GuiMachineSettingsWindow.open(screen, tile);
        }
    }
}
