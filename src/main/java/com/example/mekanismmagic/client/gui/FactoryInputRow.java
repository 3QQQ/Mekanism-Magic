package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.container.RetainedSlotAccess;
import com.example.mekanismmagic.container.MachinePanelLayout;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FactoryInputInventorySlot;

import java.util.ArrayList;
import java.util.List;

/** Client presentation of native factory inputs, leaving the server's physical inventory untouched. */
public final class FactoryInputRow {
    public static final int ORIGINAL_Y = 13;
    public static final int PRESENTED_Y = MachinePanelLayout.FACTORY_INPUT_Y;
    private final List<InventoryContainerSlot> slots;

    private FactoryInputRow(List<InventoryContainerSlot> slots) {
        this.slots = List.copyOf(slots);
        for (InventoryContainerSlot slot : slots) {
            ((RetainedSlotAccess) slot).mekanismMagic$positionRetainedSlot(slot.x, PRESENTED_Y, true);
        }
    }

    /** Restore first on every init/resize, then bind each original role before visual slots are created. */
    public static FactoryInputRow prepare(FactoryInputRow previous, MekanismTileContainer<?> menu) {
        if (previous != null) previous.dispose();
        List<IInventorySlot> inputRoles = menu.getTileEntity() instanceof IMekanismMagicAutomation automation
                ? automation.mekanismMagicPatternInputs() : List.of();
        List<InventoryContainerSlot> slots = new ArrayList<>();
        for (InventoryContainerSlot slot : menu.getInventoryContainerSlots()) {
            if (!needsPresentation(slot.getInventorySlot(), slot.y, inputRoles)) continue;
            long matches = menu.getInventoryContainerSlots().stream()
                    .filter(other -> other.getInventorySlot() == slot.getInventorySlot()).count();
            if (matches != 1 || !(slot instanceof RetainedSlotAccess)) {
                throw new IllegalStateException("Factory input row needs one original menu slot per role");
            }
            slots.add(slot);
        }
        return new FactoryInputRow(slots);
    }

    public static boolean needsPresentation(IInventorySlot inventory, int y) {
        return needsPresentation(inventory, y, List.of());
    }

    /** Explicit input identities also cover optional factories whose slot classes do not extend Mekanism's. */
    public static boolean needsPresentation(IInventorySlot inventory, int y, List<IInventorySlot> inputRoles) {
        return y == ORIGINAL_Y && (inventory instanceof FactoryInputInventorySlot || inventory instanceof EnergyInventorySlot
                || inputRoles.stream().anyMatch(input -> input == inventory));
    }

    public void dispose() {
        for (InventoryContainerSlot slot : slots) {
            ((RetainedSlotAccess) slot).mekanismMagic$restoreRetainedSlot();
        }
    }
}
