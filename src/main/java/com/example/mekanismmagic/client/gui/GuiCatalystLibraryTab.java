package com.example.mekanismmagic.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiInsetToggleElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

public final class GuiCatalystLibraryTab<TILE extends TileEntityMekanism>
        extends GuiInsetToggleElement<TILE> {
    private static final int TAB_Y = 90;

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "ars_nouveau", "textures/item/source_gem.png");
    private final Runnable toggle;

    public GuiCatalystLibraryTab(IGuiWrapper gui,
                                 TILE tile,
                                 BooleanSupplier open, Runnable toggle) {
        // ME pattern automation occupies the right-side y=62..87 tab. Use
        // the next standard 28-pixel tab row so the two never overlap.
        super(gui, tile, gui.getXSize(), TAB_Y, 26, 18, false,
                ICON, ICON, open);
        this.toggle = toggle;
        setTooltip(Tooltip.create(Component.translatable(
                "gui.mekanism_magic.catalyst_library")));
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        MekanismRenderer.color(graphics,
                MagicGuiTheme.accentSource());
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        toggle.run();
    }
}
