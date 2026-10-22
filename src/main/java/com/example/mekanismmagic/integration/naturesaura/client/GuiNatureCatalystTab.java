package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.container.MachineSidebarLayout;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiInsetToggleElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/** Opens the paged Nature catalyst overflow without enlarging the work GUI. */
public final class GuiNatureCatalystTab<TILE extends TileEntityMekanism>
        extends GuiInsetToggleElement<TILE> {
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("naturesaura",
                    "textures/block/conversion_catalyst.png");
    private final Runnable toggle;

    public GuiNatureCatalystTab(IGuiWrapper gui, TILE tile,
                                BooleanSupplier open, Runnable toggle) {
        super(gui, tile, gui.getXSize(),
                MachineSidebarLayout.moduleY(false), 26, 18, false,
                ICON, ICON, open);
        this.toggle = toggle;
        setTooltip(Tooltip.create(Component.translatable(
                "gui.mekanism_magic.nature_catalyst_library")));
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        MekanismRenderer.color(graphics, MagicGuiTheme.accentSource());
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        toggle.run();
    }
}
