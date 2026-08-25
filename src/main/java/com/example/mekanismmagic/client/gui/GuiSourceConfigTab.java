package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.integration.arsnouveau.ArsSourceModeHost;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiInsetToggleElement;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/**
 * Mekanism-style side tab opening the Ars Source side configuration window.
 */
public final class GuiSourceConfigTab
        extends GuiInsetToggleElement<ArsSourceModeHost> {
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "ars_nouveau", "textures/item/source_gem.png");
    private final Runnable toggle;

    public GuiSourceConfigTab(IGuiWrapper gui,
                              ArsSourceModeHost tile,
                              BooleanSupplier open, Runnable toggle) {
        super(gui, tile, gui.getXSize(), 104, 26, 18, false,
                ICON, ICON, open);
        this.toggle = toggle;
        setTooltip(Tooltip.create(Component.translatable(
                "gui.mekanism_magic.source_config")));
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        MekanismRenderer.color(graphics, 0xFF8B6CFF);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        toggle.run();
    }
}
