package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiInsetToggleElement;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/**
 * Mekanism-style side module for the dimensional miner's non-consumable
 * miner selector.
 */
public final class GuiMinerModuleTab
        extends GuiInsetToggleElement<NativeDimensionMinerBlockEntity> {
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "occultism",
                    "textures/item/miner_foliot_unspecialized.png");

    private final Runnable toggle;

    public GuiMinerModuleTab(IGuiWrapper gui,
                             NativeDimensionMinerBlockEntity tile,
                             BooleanSupplier open, Runnable toggle) {
        super(gui, tile, gui.getXSize(), 80, 26, 18, false,
                ICON, ICON, open);
        this.toggle = toggle;
        setTooltip(Tooltip.create(Component.translatable(
                "gui.mekanism_magic.miner_module")));
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        MekanismRenderer.color(graphics, 0xFF7D69B5);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        toggle.run();
    }
}
