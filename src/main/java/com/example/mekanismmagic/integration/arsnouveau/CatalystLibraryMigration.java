package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.api.SerializerHelper;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Compatibility helpers for the released 30-physical-slot library. */
public final class CatalystLibraryMigration {
    public static final String STORAGE_NBT =
            "mekanism_magic_catalyst_library";

    private static final String MEKANISM_ITEMS_NBT = "items";
    private static final String MEKANISM_SLOT_NBT = "slot";
    private static final String MEKANISM_ITEM_NBT = "item";

    private CatalystLibraryMigration() {
    }

    /**
     * Reads a range from Mekanism's old item list. Slot bytes are deliberately
     * treated as unsigned so Extras machines whose old total exceeded 127 can
     * recover entries that the former reader skipped as negative indices.
     */
    public static List<ItemStack> readLegacyWorldRange(
            CompoundTag parent, HolderLookup.Provider registries,
            int firstSlot, int count) {
        List<ItemStack> result = emptyStacks(count);
        if (parent == null || count <= 0) {
            return List.copyOf(result);
        }
        ListTag items = parent.getList(
                MEKANISM_ITEMS_NBT, Tag.TAG_COMPOUND);
        for (int listIndex = 0; listIndex < items.size(); listIndex++) {
            CompoundTag entry = items.getCompound(listIndex);
            if (!entry.contains(MEKANISM_SLOT_NBT, Tag.TAG_BYTE)) {
                continue;
            }
            int slot = Byte.toUnsignedInt(
                    entry.getByte(MEKANISM_SLOT_NBT));
            int libraryIndex = slot - firstSlot;
            if (libraryIndex < 0 || libraryIndex >= count) {
                continue;
            }
            result.set(libraryIndex,
                    SerializerHelper.parseOversizedOptional(registries,
                            entry.getCompound(MEKANISM_ITEM_NBT)));
        }
        return immutableCopies(result);
    }

    public static ItemStack readLegacyWorldSlot(
            CompoundTag parent, HolderLookup.Provider registries,
            int requestedSlot) {
        ListTag items = parent == null ? new ListTag() : parent.getList(
                MEKANISM_ITEMS_NBT, Tag.TAG_COMPOUND);
        for (int listIndex = 0; listIndex < items.size(); listIndex++) {
            CompoundTag entry = items.getCompound(listIndex);
            if (entry.contains(MEKANISM_SLOT_NBT, Tag.TAG_BYTE)
                    && Byte.toUnsignedInt(entry.getByte(
                    MEKANISM_SLOT_NBT)) == requestedSlot) {
                return SerializerHelper.parseOversizedOptional(registries,
                        entry.getCompound(MEKANISM_ITEM_NBT));
            }
        }
        return ItemStack.EMPTY;
    }

    public static List<ItemStack> copyAttachedRange(
            AttachedItems attached, int firstSlot, int count) {
        if (attached == null || firstSlot < 0 || count <= 0
                || firstSlot + count > attached.containers().size()) {
            return List.of();
        }
        return immutableCopies(attached.containers().subList(
                firstSlot, firstSlot + count));
    }

    public static boolean isLegacyAttachment(
            AttachedItems attached, int currentSlotCount) {
        return attached != null && attached.containers().size()
                == currentSlotCount
                - CatalystLibraryStorage.PAGE_SIZE
                + com.example.mekanismmagic.blockentity
                .NativeMagicMachineBlockEntity
                .LEGACY_CATALYST_LIBRARY_SLOT_COUNT;
    }

    /**
     * Replaces the old 30-entry range by the fixed 16-entry page window and
     * shifts every following machine slot back into its new position.
     */
    public static AttachedItems remapLegacyAttachment(
            AttachedItems attached, int currentSlotCount,
            int firstWindowSlot) {
        if (!isLegacyAttachment(attached, currentSlotCount)
                || firstWindowSlot < 0
                || firstWindowSlot + CatalystLibraryStorage.PAGE_SIZE
                > currentSlotCount) {
            return attached;
        }
        int legacyCount = com.example.mekanismmagic.blockentity
                .NativeMagicMachineBlockEntity
                .LEGACY_CATALYST_LIBRARY_SLOT_COUNT;
        List<ItemStack> previous = attached.containers();
        List<ItemStack> remapped = new ArrayList<>(currentSlotCount);
        for (int index = 0; index < firstWindowSlot; index++) {
            remapped.add(previous.get(index).copy());
        }
        remapped.addAll(emptyStacks(CatalystLibraryStorage.PAGE_SIZE));
        for (int oldIndex = firstWindowSlot + legacyCount;
             oldIndex < previous.size(); oldIndex++) {
            remapped.add(previous.get(oldIndex).copy());
        }
        if (remapped.size() != currentSlotCount) {
            throw new IllegalStateException(
                    "Catalyst attachment migration produced "
                            + remapped.size() + " slots instead of "
                            + currentSlotCount);
        }
        return new AttachedItems(List.copyOf(remapped));
    }

    /**
     * Repairs the shifted non-library tail after Mekanism has read an old
     * world NBT list into the new 16-window layout, and returns all 30 legacy
     * catalyst entries for import into dynamic storage.
     */
    public static List<ItemStack> migrateLegacyWorldInventory(
            CompoundTag parent, HolderLookup.Provider registries,
            List<IInventorySlot> currentSlots, int firstWindowSlot) {
        int legacyCount = com.example.mekanismmagic.blockentity
                .NativeMagicMachineBlockEntity
                .LEGACY_CATALYST_LIBRARY_SLOT_COUNT;
        int shift = legacyCount - CatalystLibraryStorage.PAGE_SIZE;
        List<ItemStack> library = readLegacyWorldRange(parent, registries,
                firstWindowSlot, legacyCount);
        for (int currentIndex = firstWindowSlot
                + CatalystLibraryStorage.PAGE_SIZE;
             currentIndex < currentSlots.size(); currentIndex++) {
            ItemStack restored = readLegacyWorldSlot(parent, registries,
                    currentIndex + shift);
            IInventorySlot slot = currentSlots.get(currentIndex);
            if (slot instanceof BasicInventorySlot basic) {
                basic.setStackUnchecked(restored);
            } else {
                slot.setStack(restored);
            }
        }
        return library;
    }

    private static List<ItemStack> emptyStacks(int count) {
        List<ItemStack> stacks = new ArrayList<>(Math.max(0, count));
        for (int index = 0; index < count; index++) {
            stacks.add(ItemStack.EMPTY);
        }
        return stacks;
    }

    private static List<ItemStack> immutableCopies(
            List<ItemStack> stacks) {
        List<ItemStack> copied = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copied.add(stack == null || stack.isEmpty()
                    ? ItemStack.EMPTY : stack.copy());
        }
        return List.copyOf(copied);
    }
}
