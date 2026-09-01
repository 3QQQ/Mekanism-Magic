package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.example.mekanismmagic.client.gui.MagicItemCountRenderer;

public final class NativeDimensionMinerScreen extends NativeMagicMachineScreen<
        NativeDimensionMinerBlockEntity,
        MekanismTileContainer<NativeDimensionMinerBlockEntity>> {
    public NativeDimensionMinerScreen(
            MekanismTileContainer<NativeDimensionMinerBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = 210;
        inventoryLabelX = 26;
        inventoryLabelY = 114;
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected boolean showProgress() {
        return false;
    }

    @Override
    protected int energyBarHeight() {
        // Align the status bar with the bottom of the three output rows.
        return 80;
    }

    @Override
    protected boolean usesCompactSlotCount(ItemStack stack, Slot slot) {
        return isMinerOutputSlot(slot)
                && MagicItemCountRenderer.needsCompactCount(stack.getCount());
    }

    private boolean isMinerOutputSlot(Slot slot) {
        return slot instanceof InventoryContainerSlot inventorySlot
                && getTileEntity().isMinerOutputSlot(
                        inventorySlot.getInventorySlot());
    }

}
