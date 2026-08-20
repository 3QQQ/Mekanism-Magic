package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeSpiritFactoryBlockEntity;
import com.example.mekanismmagic.container.NativeSpiritFactoryContainer;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiSortingTab;
import mekanism.common.tier.FactoryTier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Native Mekanism factory composition with this addon's strongly typed
 * container.
 */
public final class NativeSpiritFactoryScreen extends GuiConfigurableTile<
        NativeSpiritFactoryBlockEntity, NativeSpiritFactoryContainer> {

    public NativeSpiritFactoryScreen(NativeSpiritFactoryContainer container,
                                     Inventory inventory, Component title) {
        super(container, inventory, title);
        inventoryLabelY = 75;
        titleLabelY = 4;
        dynamicSlots = true;
        if (getTileEntity().tier == FactoryTier.ULTIMATE) {
            imageWidth += 34;
            inventoryLabelX = 26;
        }
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiSortingTab(this, getTileEntity()));
        addRenderableWidget(new GuiVerticalPowerBar(this,
                getTileEntity().getEnergyContainer(), imageWidth - 12, 16, 52));
        addRenderableWidget(new GuiEnergyTab(this,
                getTileEntity().getEnergyContainer(),
                getTileEntity()::getLastUsage));

        int firstX = switch (getTileEntity().tier) {
            case BASIC -> 55;
            case ADVANCED -> 35;
            case ELITE -> 29;
            case ULTIMATE -> 27;
        };
        int spacing = switch (getTileEntity().tier) {
            case BASIC -> 38;
            case ADVANCED -> 26;
            case ELITE, ULTIMATE -> 19;
        };
        for (int process = 0; process < getTileEntity().tier.processes; process++) {
            int index = process;
            addRenderableWidget(new GuiProgress(
                    () -> getTileEntity().getScaledProgress(1, index),
                    ProgressType.DOWN, this,
                    4 + firstX + process * spacing, 33));
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics gui, int mouseX, int mouseY) {
        Component machineTitle = Component.translatable(
                getTileEntity().getBlockState().getBlock()
                        .getDescriptionId());
        drawTitleText(gui, machineTitle, titleLabelY);
        gui.drawString(font, playerInventoryTitle, inventoryLabelX,
                inventoryLabelY, titleTextColor(), false);
    }
}
