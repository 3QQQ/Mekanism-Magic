package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared Mekanism work-machine screen. Concrete screens only add their
 * machine-specific slot widgets.
 */
public abstract class NativeMagicMachineScreen<
        TILE extends NativeMagicMachineBlockEntity>
        extends GuiConfigurableTile<TILE, MekanismTileContainer<TILE>> {

    protected NativeMagicMachineScreen(MekanismTileContainer<TILE> container,
                                       Inventory inventory, Component title,
                                       int imageHeight) {
        super(container, inventory, title);
        imageWidth = 176;
        this.imageHeight = imageHeight;
        inventoryLabelY = imageHeight - 94;
        dynamicSlots = true;
    }

    @Override
    protected final void addGuiElements() {
        super.addGuiElements();
        if (showUpArrow()) {
            addRenderableWidget(new GuiUpArrow(this, workArrowX(), workArrowY()));
        }
        addRenderableWidget(new GuiVerticalPowerBar(this,
                getTileEntity().getNativeEnergyContainer(), energyBarX(), 16,
                energyBarHeight()));
        addRenderableWidget(new GuiProgress(getTileEntity()::getProgress,
                progressType(), this, workProgressX(), workProgressY()));
        addMachineGuiElements();
    }

    protected void addMachineGuiElements() {
    }

    protected int workArrowX() {
        return 68;
    }

    protected int workArrowY() {
        return 38;
    }

    protected boolean showUpArrow() {
        return true;
    }

    protected int energyBarX() {
        return imageWidth - 12;
    }

    protected int energyBarHeight() {
        return 52;
    }

    protected int workProgressX() {
        return 86;
    }

    protected int workProgressY() {
        return 38;
    }

    protected ProgressType progressType() {
        return ProgressType.BAR;
    }

    @Override
    protected void drawForegroundText(net.minecraft.client.gui.GuiGraphics gui,
                                      int mouseX, int mouseY) {
        renderTitleText(gui);
        gui.drawString(font, playerInventoryTitle, inventoryLabelX,
                inventoryLabelY, titleTextColor(), false);
    }

}
