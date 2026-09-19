package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.gui.MagicItemCountRenderer;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.client.gui.element.slot.GuiSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;

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
    protected void addSlots() {
        super.addSlots();
        for (var child : children()) {
            if (!(child instanceof GuiSlot slot)) continue;
            int x = slot.getRelativeX() + 1, y = slot.getRelativeY() + 1;
            if (getTileEntity().mekanismMagicPersistentInputs().stream()
                    .anyMatch(inventory -> isGuiSlotForInventory(slot, inventory))) {
                Slot inventorySlot = getMenu().slots.stream().filter(entry -> entry.x == x && entry.y == y).findFirst().orElse(null);
                slot.hover(ignored -> inventorySlot != null && !inventorySlot.hasItem()
                        ? List.of(Component.translatable("tooltip.mekanism_magic.drygmy.sample")) : List.of());
                slot.active = false;
            }
        }
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
