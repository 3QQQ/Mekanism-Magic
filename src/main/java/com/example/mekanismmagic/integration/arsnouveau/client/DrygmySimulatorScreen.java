package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.gui.MagicItemCountRenderer;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class DrygmySimulatorScreen
        extends ArsSourceMachineScreen<DrygmySimulatorBlockEntity> {
    private static final int COMPACT_RESOURCE_BAR_HEIGHT = 76;

    public DrygmySimulatorScreen(
            MekanismTileContainer<DrygmySimulatorBlockEntity> container,
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
        return COMPACT_RESOURCE_BAR_HEIGHT;
    }

    @Override
    protected int sourceBarHeight() {
        return COMPACT_RESOURCE_BAR_HEIGHT;
    }

    @Override
    protected boolean usesCompactSlotCount(ItemStack stack, Slot slot) {
        return slot instanceof InventoryContainerSlot inventorySlot
                && getTileEntity().isDrygmyOutputSlot(
                        inventorySlot.getInventorySlot())
                && MagicItemCountRenderer.needsCompactCount(stack.getCount());
    }
}
