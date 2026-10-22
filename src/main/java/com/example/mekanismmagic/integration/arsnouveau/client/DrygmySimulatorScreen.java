package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.gui.MagicItemCountRenderer;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.container.RetainedSlotAccess;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.client.gui.element.slot.GuiSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.ArrayList;

public final class DrygmySimulatorScreen
        extends ArsSourceMachineScreen<DrygmySimulatorBlockEntity> {
    private static final int COMPACT_RESOURCE_BAR_HEIGHT = 76;
    private List<InventoryContainerSlot> workSlots = List.of();

    public DrygmySimulatorScreen(
            MekanismTileContainer<DrygmySimulatorBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, MachinePanelLayout.DRYGMY_HEIGHT);
        imageWidth = 210;
        inventoryLabelX = 26;
        inventoryLabelY = MachinePanelLayout.inventoryLabelY(MachinePanelLayout.DRYGMY_HEIGHT);
    }

    @Override
    protected void prepareMachineGuiElements() {
        restoreWorkSlots();
        var tile = getTileEntity();
        List<IInventorySlot> inventories = new ArrayList<>();
        List<MachinePanelLayout.Box> positions = new ArrayList<>();
        inventories.add(tile.getNativeEnergySlot());
        positions.add(MachinePanelLayout.DRYGMY_ENERGY);
        for (int index = 0; index < DrygmySimulatorBlockEntity.JAR_SLOT_COUNT; index++) {
            inventories.add(tile.getLogicalSlot(DrygmySimulatorBlockEntity.JAR_SLOT_START + index));
            positions.add(new MachinePanelLayout.Box(24 + index * 18 + MachinePanelLayout.DRYGMY_GRID_SHIFT_X,
                    16, 16, 16));
        }
        for (int index = 0; index < DrygmySimulatorBlockEntity.OUTPUT_SLOT_COUNT; index++) {
            inventories.add(tile.getLogicalSlot(DrygmySimulatorBlockEntity.OUTPUT_SLOT_START + index));
            positions.add(new MachinePanelLayout.Box(24 + index % 9 * 18 + MachinePanelLayout.DRYGMY_GRID_SHIFT_X,
                    46 + index / 9 * 18, 16, 16));
        }
        List<InventoryContainerSlot> bindings = new ArrayList<>(inventories.size());
        for (IInventorySlot inventory : inventories) {
            var matches = menu.getInventoryContainerSlots().stream()
                    .filter(slot -> slot.getInventorySlot() == inventory).toList();
            if (inventory == null || matches.size() != 1 || !(matches.getFirst() instanceof RetainedSlotAccess)) {
                throw new IllegalStateException("Drygmy work deck needs one original menu slot per role");
            }
            bindings.add(matches.getFirst());
        }
        workSlots = List.copyOf(bindings);
        for (int index = 0; index < workSlots.size(); index++) {
            var position = positions.get(index);
            ((RetainedSlotAccess) workSlots.get(index)).mekanismMagic$positionRetainedSlot(
                    position.x(), position.y(), true);
        }
    }

    private void restoreWorkSlots() {
        for (InventoryContainerSlot slot : workSlots) {
            ((RetainedSlotAccess) slot).mekanismMagic$restoreRetainedSlot();
        }
        workSlots = List.of();
    }

    @Override
    public void removed() {
        restoreWorkSlots();
        super.removed();
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
