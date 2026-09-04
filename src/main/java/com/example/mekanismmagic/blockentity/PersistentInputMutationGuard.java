package com.example.mekanismmagic.blockentity;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.world.item.ItemStack;

/**
 * Applies an inventory slot's insertion/extraction contract to the otherwise
 * unrestricted {@code IItemHandlerModifiable#setStackInSlot} entry point.
 */
public final class PersistentInputMutationGuard {
    private PersistentInputMutationGuard() {
    }

    public static boolean permits(IInventorySlot slot,
                                  ItemStack replacement) {
        if (slot == null || replacement == null) {
            return false;
        }
        ItemStack current = slot.getStack();
        boolean same = !current.isEmpty() && !replacement.isEmpty()
                && ItemStack.isSameItemSameComponents(current, replacement);
        Mutation mutation = classify(current.isEmpty(), replacement.isEmpty(),
                same, current.getCount(), replacement.getCount());
        if (mutation == Mutation.NONE) {
            return true;
        }

        int remove = switch (mutation) {
            case SHRINK -> current.getCount() - replacement.getCount();
            case CLEAR, REPLACE -> current.getCount();
            default -> 0;
        };
        if (remove > 0) {
            ItemStack extracted = slot.extractItem(remove, Action.SIMULATE,
                    AutomationType.EXTERNAL);
            if (extracted.getCount() != remove
                    || !ItemStack.isSameItemSameComponents(
                    extracted, current)) {
                return false;
            }
        }

        int insert = switch (mutation) {
            case INSERT, REPLACE -> replacement.getCount();
            case GROW -> replacement.getCount() - current.getCount();
            default -> 0;
        };
        if (insert <= 0) {
            return true;
        }
        if (replacement.getCount() > slot.getLimit(replacement)) {
            return false;
        }
        if (mutation == Mutation.REPLACE
                && slot instanceof BasicInventorySlot basic) {
            return basic.isItemValidForInsertion(
                    replacement, AutomationType.EXTERNAL);
        }
        ItemStack remainder = slot.insertItem(
                replacement.copyWithCount(insert), Action.SIMULATE,
                AutomationType.EXTERNAL);
        return remainder.isEmpty();
    }

    /**
     * Mirrors the slot's real external insertion predicate for capability
     * {@code isItemValid} queries. In particular, a null-sided capability is
     * still external; Mekanism's {@code AutomationType.handler(null)} would
     * otherwise classify it as internal and expose manual-only inputs.
     */
    public static boolean permitsExternalInsertion(IInventorySlot slot,
                                                    ItemStack stack) {
        if (slot == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (slot instanceof BasicInventorySlot basic) {
            return basic.isItemValidForInsertion(
                    stack, AutomationType.EXTERNAL);
        }
        return slot.isItemValid(stack);
    }

    static Mutation classify(boolean currentEmpty,
                             boolean replacementEmpty,
                             boolean sameItemAndComponents,
                             int currentCount, int replacementCount) {
        if (currentEmpty) {
            return replacementEmpty ? Mutation.NONE : Mutation.INSERT;
        }
        if (replacementEmpty) {
            return Mutation.CLEAR;
        }
        if (!sameItemAndComponents) {
            return Mutation.REPLACE;
        }
        if (replacementCount > currentCount) {
            return Mutation.GROW;
        }
        return replacementCount < currentCount
                ? Mutation.SHRINK : Mutation.NONE;
    }

    enum Mutation {
        NONE,
        INSERT,
        GROW,
        SHRINK,
        CLEAR,
        REPLACE
    }
}
