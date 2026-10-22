package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryMigration;
import mekanism.api.SerializationConstants;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Preserves pre-library machine inventories without copying page aliases. */
public final class NatureAltarCatalystMigration {
    public static final String SLOT_COUNT_NBT =
            "nature_altar_catalyst_slots";

    private NatureAltarCatalystMigration() {
    }

    /** Factory energy comes after catalysts; old slot indices need shifting. */
    public static CompoundTag factoryWorldTag(
            CompoundTag tag, List<IInventorySlot> currentSlots,
            IInventorySlot secondCatalyst, IInventorySlot firstWindow) {
        int oldCount = tag.contains(SLOT_COUNT_NBT, Tag.TAG_INT)
                ? tag.getInt(SLOT_COUNT_NBT) : 1;
        if (oldCount != 1 && oldCount != 2) {
            return tag;
        }
        int insertedIndex = currentSlots.indexOf(
                oldCount == 1 ? secondCatalyst : firstWindow);
        if (insertedIndex < 0 || !tag.contains(
                SerializationConstants.ITEMS, Tag.TAG_LIST)) {
            return tag;
        }
        int shift = NatureAltarCatalystLibrary.SAVED_CATALYST_SLOTS
                - oldCount;
        CompoundTag migrated = tag.copy();
        ListTag items = migrated.getList(SerializationConstants.ITEMS,
                Tag.TAG_COMPOUND);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag item = items.getCompound(index);
            int savedIndex = Byte.toUnsignedInt(item.getByte(
                    SerializationConstants.SLOT));
            if (savedIndex >= insertedIndex) {
                int newIndex = savedIndex + shift;
                if (newIndex > 255) {
                    throw new IllegalStateException(
                            "Nature altar inventory exceeds 255 slots");
                }
                item.putByte(SerializationConstants.SLOT,
                        (byte) newIndex);
            }
        }
        return migrated;
    }

    /** Old single/two-catalyst items had no sixteen-slot page window. */
    public static AttachedItems factoryAttachment(
            AttachedItems attached, List<IInventorySlot> currentSlots,
            IInventorySlot secondCatalyst, IInventorySlot firstWindow) {
        int missing = currentSlots.size()
                - attached.containers().size();
        int insertAt = switch (missing) {
            case NatureAltarCatalystLibrary.WINDOW_SIZE ->
                    currentSlots.indexOf(firstWindow);
            case NatureAltarCatalystLibrary.WINDOW_SIZE + 1 ->
                    currentSlots.indexOf(secondCatalyst);
            default -> -1;
        };
        return withEmptySlots(attached, currentSlots.size(),
                insertAt, missing);
    }

    /** Native addon/ME slots may follow catalysts and must be shifted too. */
    public static AttachedItems processorAttachment(
            AttachedItems attached, List<IInventorySlot> currentSlots,
            IInventorySlot secondCatalyst, IInventorySlot firstWindow) {
        int missing = currentSlots.size()
                - attached.containers().size();
        int insertAt = switch (missing) {
            case NatureAltarCatalystLibrary.WINDOW_SIZE ->
                    currentSlots.indexOf(firstWindow);
            case NatureAltarCatalystLibrary.WINDOW_SIZE + 1 ->
                    currentSlots.indexOf(secondCatalyst);
            default -> -1;
        };
        return withEmptySlots(attached, currentSlots.size(),
                insertAt, missing);
    }

    /** Native base loads slots before its machine-data hook; repair addon tail. */
    public static void restoreProcessorWorldTail(
            CompoundTag tag, HolderLookup.Provider registries,
            List<IInventorySlot> currentSlots,
            IInventorySlot secondCatalyst, IInventorySlot firstWindow) {
        int firstWindowIndex = currentSlots.indexOf(firstWindow);
        int tailStart = firstWindowIndex
                + NatureAltarCatalystLibrary.WINDOW_SIZE;
        if (firstWindowIndex < 0 || tailStart >= currentSlots.size()
                || !tag.contains(SerializationConstants.ITEMS,
                Tag.TAG_LIST)) {
            return;
        }
        int oldCount = tag.contains(SLOT_COUNT_NBT, Tag.TAG_INT)
                ? tag.getInt(SLOT_COUNT_NBT) : 2;
        if (oldCount != 1 && oldCount != 2) {
            return;
        }
        int shift = NatureAltarCatalystLibrary.SAVED_CATALYST_SLOTS
                - oldCount;
        if (oldCount == 1 && secondCatalyst
                instanceof BasicInventorySlot basic) {
            basic.setStackUnchecked(ItemStack.EMPTY);
        }
        for (int newIndex = tailStart;
             newIndex < currentSlots.size(); newIndex++) {
            ItemStack saved = CatalystLibraryMigration
                    .readLegacyWorldSlot(tag, registries,
                            newIndex - shift);
            IInventorySlot slot = currentSlots.get(newIndex);
            if (slot instanceof BasicInventorySlot basic) {
                basic.setStackUnchecked(saved);
            } else {
                slot.setStack(saved);
            }
        }
    }

    private static AttachedItems withEmptySlots(
            AttachedItems attached, int targetSize,
            int insertAt, int count) {
        if (insertAt < 0 || insertAt > attached.containers().size()
                || count <= 0) {
            return attached;
        }
        List<ItemStack> stacks = new ArrayList<>(targetSize);
        stacks.addAll(attached.containers().subList(0, insertAt));
        for (int index = 0; index < count; index++) {
            stacks.add(ItemStack.EMPTY);
        }
        stacks.addAll(attached.containers().subList(insertAt,
                attached.containers().size()));
        return stacks.size() == targetSize
                ? new AttachedItems(List.copyOf(stacks)) : attached;
    }

    /** Dynamic contents live in BLOCK_ENTITY_DATA, not AttachedItems. */
    public static AttachedItems collectPhysicalItemSlots(
            DataComponentMap.Builder builder,
            List<IInventorySlot> slots,
            NatureAltarCatalystLibrary library) {
        List<ItemStack> stacks = new ArrayList<>(slots.size());
        boolean nonEmpty = false;
        for (IInventorySlot slot : slots) {
            ItemStack stack = library != null
                    && library.isWindowSlot(slot)
                    ? ItemStack.EMPTY : slot.getStack().copy();
            stacks.add(stack);
            nonEmpty |= !stack.isEmpty();
        }
        return nonEmpty ? new AttachedItems(List.copyOf(stacks)) : null;
    }

    /** Ignore stale page snapshots no matter which item-component loads first. */
    public static void applyPhysicalItemSlots(
            List<IInventorySlot> slots, AttachedItems attached,
            NatureAltarCatalystLibrary library) {
        List<ItemStack> stacks = attached.containers();
        if (stacks.size() != slots.size()) {
            return;
        }
        for (int index = 0; index < slots.size(); index++) {
            IInventorySlot slot = slots.get(index);
            if (library != null && library.isWindowSlot(slot)) {
                continue;
            }
            ItemStack stack = stacks.get(index).copy();
            if (slot instanceof BasicInventorySlot basic) {
                basic.setStackUnchecked(stack);
            } else {
                slot.setStack(stack);
            }
        }
    }
}
