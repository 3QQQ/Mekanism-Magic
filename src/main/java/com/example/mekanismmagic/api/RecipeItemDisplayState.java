package com.example.mekanismmagic.api;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import mekanism.api.inventory.IInventorySlot;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Server-synchronised recipe item identities used solely by block entity
 * renderers. Counts are intentionally reduced to one.
 */
public final class RecipeItemDisplayState {
    public static final String UPDATE_TAG = "magic_recipe_display_items";
    private static final int MAX_DISPLAY_ITEMS = 8;

    private List<Entry> entries = List.of();
    private long factoryFingerprint = Long.MIN_VALUE;

    public boolean updateRecipe(ItemStackHandler inventory,
                                MachineRecipeResult recipe) {
        factoryFingerprint = Long.MIN_VALUE;
        List<Entry> next = new ArrayList<>();
        ItemStack result = RecipeEntityDisplayState.representsEntity(
                recipe.output()) ? ItemStack.EMPTY
                : displayCopy(recipe.output());
        for (var use : recipe.inputs()) {
            if (next.size() >= MAX_DISPLAY_ITEMS
                    || use.slot() < 0
                    || use.slot() >= inventory.getSlots()) {
                continue;
            }
            ItemStack input = displayCopy(
                    inventory.getStackInSlot(use.slot()));
            if (!input.isEmpty()) {
                next.add(new Entry(use.slot(), input,
                        next.isEmpty() ? result : ItemStack.EMPTY));
            }
        }
        if (next.isEmpty() && !result.isEmpty()) {
            next.add(new Entry(0, ItemStack.EMPTY, result));
        }
        return update(next);
    }

    /**
     * Updates at most three visible factory lanes. Recipe lookup is invoked
     * only when an input or the shared recipe selector actually changes.
     */
    public boolean updateFactory(
            List<? extends IInventorySlot> inputSlots,
            ItemStack sharedSelector,
            IntFunction<ItemStack> outputLookup) {
        long fingerprint = stackFingerprint(sharedSelector);
        for (int process = 0; process < inputSlots.size(); process++) {
            fingerprint = 31L * fingerprint + process;
            fingerprint = 31L * fingerprint
                    + stackFingerprint(inputSlots.get(process).getStack());
        }
        if (fingerprint == factoryFingerprint) {
            return false;
        }
        factoryFingerprint = fingerprint;

        List<Entry> next = new ArrayList<>(3);
        for (int process = 0;
             process < inputSlots.size() && next.size() < 3;
             process++) {
            ItemStack input = displayCopy(
                    inputSlots.get(process).getStack());
            if (input.isEmpty()) {
                continue;
            }
            ItemStack output = outputLookup == null
                    ? ItemStack.EMPTY
                    : displayCopy(outputLookup.apply(process));
            if (RecipeEntityDisplayState.representsEntity(output)) {
                output = ItemStack.EMPTY;
            }
            next.add(new Entry(process, input, output));
        }
        return update(next);
    }

    public boolean update(List<Entry> nextEntries) {
        List<Entry> next = nextEntries == null ? List.of()
                : nextEntries.stream()
                .filter(entry -> entry != null
                        && (!entry.input().isEmpty()
                        || !entry.output().isEmpty()))
                .limit(MAX_DISPLAY_ITEMS)
                .map(Entry::copy)
                .toList();
        if (sameEntries(entries, next)) {
            return false;
        }
        entries = next;
        return true;
    }

    public boolean clear() {
        boolean changed = !entries.isEmpty();
        entries = List.of();
        factoryFingerprint = Long.MIN_VALUE;
        return changed;
    }

    /** Read-only by convention; every stack in this list is display-owned. */
    public List<Entry> entries() {
        return entries;
    }

    public void writeUpdateTag(CompoundTag updateTag,
                               HolderLookup.Provider registries) {
        if (entries.isEmpty()) {
            updateTag.remove(UPDATE_TAG);
            return;
        }
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag encoded = new CompoundTag();
            encoded.putInt("lane", entry.lane());
            if (!entry.input().isEmpty()) {
                encoded.put("input", entry.input().save(registries));
            }
            if (!entry.output().isEmpty()) {
                encoded.put("output", entry.output().save(registries));
            }
            list.add(encoded);
        }
        updateTag.put(UPDATE_TAG, list);
    }

    public void readUpdateTag(CompoundTag updateTag,
                              HolderLookup.Provider registries) {
        ListTag list = updateTag.getList(UPDATE_TAG, Tag.TAG_COMPOUND);
        List<Entry> decoded = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag encoded = list.getCompound(index);
            ItemStack input = encoded.contains("input", Tag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(registries,
                    encoded.getCompound("input")) : ItemStack.EMPTY;
            ItemStack output = encoded.contains("output", Tag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(registries,
                    encoded.getCompound("output")) : ItemStack.EMPTY;
            if (!input.isEmpty() || !output.isEmpty()) {
                decoded.add(new Entry(encoded.getInt("lane"),
                        displayCopy(input), displayCopy(output)));
            }
        }
        entries = List.copyOf(decoded);
        factoryFingerprint = Long.MIN_VALUE;
    }

    public static ItemStack displayCopy(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY
                : stack.copyWithCount(1);
    }

    private static boolean sameEntries(List<Entry> left,
                                       List<Entry> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            Entry a = left.get(index);
            Entry b = right.get(index);
            if (a.lane() != b.lane()
                    || !same(a.input(), b.input())
                    || !same(a.output(), b.output())) {
                return false;
            }
        }
        return true;
    }

    private static boolean same(ItemStack left, ItemStack right) {
        return left.isEmpty() ? right.isEmpty()
                : !right.isEmpty()
                && ItemStack.isSameItemSameComponents(left, right);
    }

    private static long stackFingerprint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        long fingerprint = net.minecraft.core.registries.BuiltInRegistries
                .ITEM.getId(stack.getItem());
        return 31L * fingerprint + stack.getComponents().hashCode();
    }

    public record Entry(int lane, ItemStack input, ItemStack output) {
        public Entry {
            input = displayCopy(input);
            output = displayCopy(output);
        }

        private Entry copy() {
            return new Entry(lane, input, output);
        }
    }
}
