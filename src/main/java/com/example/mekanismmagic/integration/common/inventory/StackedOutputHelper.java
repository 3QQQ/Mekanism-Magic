package com.example.mekanismmagic.integration.common.inventory;

import mekanism.api.inventory.IInventorySlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared insertion logic for machines with large, stackable output grids.
 */
public final class StackedOutputHelper {
    private StackedOutputHelper() {
    }

    public static boolean canAccept(
            List<? extends IInventorySlot> slots,
            List<ItemStack> stacks, int stackLimit) {
        List<ItemStack> simulated = slots.stream()
                .map(slot -> slot.getStack().copy())
                .collect(java.util.stream.Collectors.toCollection(
                        ArrayList::new));
        for (ItemStack stack : stacks) {
            if (!insertIntoStacks(simulated, stack, stackLimit)) {
                return false;
            }
        }
        return true;
    }

    public static boolean insertAll(
            List<? extends IInventorySlot> slots,
            List<ItemStack> stacks, int stackLimit) {
        if (!canAccept(slots, stacks, stackLimit)) {
            return false;
        }
        for (ItemStack stack : stacks) {
            insertIntoSlots(slots, stack, stackLimit);
        }
        return true;
    }

    private static boolean insertIntoStacks(
            List<ItemStack> targets, ItemStack stack,
            int stackLimit) {
        int remaining = stack.getCount();
        for (ItemStack existing : targets) {
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, stack)) {
                int moved = Math.min(remaining,
                        stackLimit - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining -= moved;
                }
            }
        }
        for (int index = 0;
             index < targets.size() && remaining > 0; index++) {
            if (targets.get(index).isEmpty()) {
                int moved = Math.min(remaining, stackLimit);
                targets.set(index, stack.copyWithCount(moved));
                remaining -= moved;
            }
        }
        return remaining == 0;
    }

    private static void insertIntoSlots(
            List<? extends IInventorySlot> slots,
            ItemStack stack, int stackLimit) {
        int remaining = stack.getCount();
        for (IInventorySlot slot : slots) {
            ItemStack existing = slot.getStack();
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, stack)) {
                int moved = Math.min(remaining,
                        stackLimit - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    slot.setStack(existing);
                    remaining -= moved;
                }
            }
        }
        for (IInventorySlot slot : slots) {
            if (remaining <= 0) {
                return;
            }
            if (slot.getStack().isEmpty()) {
                int moved = Math.min(remaining, stackLimit);
                slot.setStack(stack.copyWithCount(moved));
                remaining -= moved;
            }
        }
    }
}
