package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Dynamically sized, page-addressable storage for catalyst identifiers.
 *
 * <p>Only non-empty entries determine retained storage size. Recipe count
 * controls how many empty entries are currently reachable, while items above
 * a reduced recipe count keep the tail reachable until the player removes
 * them. No fixed physical-slot limit is imposed.</p>
 */
public final class CatalystLibraryStorage
        implements INBTSerializable<CompoundTag> {
    public static final int PAGE_SIZE = 16;

    private static final String FORMAT_NBT = "format";
    private static final int CURRENT_FORMAT = 1;
    private static final String ENTRIES_NBT = "entries";
    private static final String INDEX_NBT = "index";
    private static final String STACK_NBT = "stack";

    private static final Runnable NOOP_LISTENER = () -> {
    };

    /** Trailing empty entries are always trimmed from this list. */
    private final List<ItemStack> stacks = new ArrayList<>();
    private final Runnable contentsChanged;

    public CatalystLibraryStorage() {
        this(NOOP_LISTENER);
    }

    public CatalystLibraryStorage(Runnable contentsChanged) {
        this.contentsChanged = Objects.requireNonNull(
                contentsChanged, "contentsChanged");
    }

    /** Number of entries retained because an item exists at or below them. */
    public int retainedSlotCount() {
        return stacks.size();
    }

    public int highestNonEmptyIndex() {
        return stacks.size() - 1;
    }

    /**
     * Logical capacity before the GUI's one-empty-slot minimum is applied.
     */
    public int capacity(int recipeCount) {
        return Math.max(Math.max(0, recipeCount), retainedSlotCount());
    }

    /** Number of slots that should currently be exposed to a user. */
    public int visibleSlotCount(int recipeCount) {
        return Math.max(1, capacity(recipeCount));
    }

    public int pageCount(int recipeCount) {
        return Math.max(1, Math.ceilDiv(
                visibleSlotCount(recipeCount), PAGE_SIZE));
    }

    public int clampPage(int page, int recipeCount) {
        return Math.max(0, Math.min(pageCount(recipeCount) - 1, page));
    }

    public boolean isVisibleSlot(int index, int recipeCount) {
        return index >= 0 && index < visibleSlotCount(recipeCount);
    }

    /** Returns a defensive copy, or an empty stack for an absent index. */
    public ItemStack get(int index) {
        if (index < 0 || index >= stacks.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = stacks.get(index);
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    /**
     * Replaces one absolute entry. The list expands only for a non-empty
     * value and contracts when its highest occupied entry is cleared.
     *
     * @return {@code true} when stored contents changed
     */
    public boolean set(int index, ItemStack stack) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(
                    "Negative catalyst library index: " + index);
        }
        ItemStack replacement = copyOrEmpty(stack);
        if (replacement.isEmpty() && index >= stacks.size()) {
            return false;
        }
        ensureSize(stacks, index + 1);
        if (ItemStack.matches(stacks.get(index), replacement)) {
            return false;
        }
        stacks.set(index, replacement);
        trimEmptyTail(stacks);
        contentsChanged.run();
        return true;
    }

    /** Deep, immutable snapshot; mutating its stacks cannot affect storage. */
    public List<ItemStack> snapshot() {
        return immutableCopies(stacks);
    }

    /**
     * Replaces all retained contents. Trailing empties are discarded, but
     * interior empty positions and every non-empty tail entry are preserved.
     *
     * @return {@code true} when stored contents changed
     */
    public boolean replace(List<? extends ItemStack> replacement) {
        List<ItemStack> normalized = mutableCopies(replacement);
        trimEmptyTail(normalized);
        if (sameContents(stacks, normalized)) {
            return false;
        }
        stacks.clear();
        stacks.addAll(normalized);
        contentsChanged.run();
        return true;
    }

    public boolean clear() {
        return replace(List.of());
    }

    /** Maps one of the sixteen physical window slots to an absolute index. */
    public static int absoluteIndex(int page, int windowSlot) {
        if (page < 0) {
            throw new IndexOutOfBoundsException(
                    "Negative catalyst library page: " + page);
        }
        checkWindowSlot(windowSlot);
        return Math.addExact(Math.multiplyExact(page, PAGE_SIZE),
                windowSlot);
    }

    public ItemStack getWindowStack(int page, int windowSlot) {
        return get(absoluteIndex(page, windowSlot));
    }

    public boolean setWindowStack(
            int page, int windowSlot, ItemStack stack) {
        return set(absoluteIndex(page, windowSlot), stack);
    }

    public boolean isVisibleWindowSlot(
            int page, int windowSlot, int recipeCount) {
        return isVisibleSlot(absoluteIndex(page, windowSlot), recipeCount);
    }

    /** Returns exactly sixteen defensive stack copies for the given page. */
    public List<ItemStack> snapshotWindow(int page) {
        List<ItemStack> result = new ArrayList<>(PAGE_SIZE);
        for (int windowSlot = 0; windowSlot < PAGE_SIZE; windowSlot++) {
            result.add(getWindowStack(page, windowSlot));
        }
        return List.copyOf(result);
    }

    /**
     * Creates a live adapter suitable for sixteen fixed container slots. Page
     * and recipe count are queried on every call, so the slots need not be
     * rebuilt when either value changes.
     */
    public PageWindow pageWindow(
            IntSupplier pageSupplier, IntSupplier recipeCountSupplier) {
        return new PageWindow(pageSupplier, recipeCountSupplier);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.putInt(FORMAT_NBT, CURRENT_FORMAT);
        ListTag entries = new ListTag();
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(INDEX_NBT, index);
            entry.put(STACK_NBT, stack.save(registries));
            entries.add(entry);
        }
        if (!entries.isEmpty()) {
            root.put(ENTRIES_NBT, entries);
        }
        return root;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider registries, CompoundTag root) {
        ListTag entries = root == null ? new ListTag()
                : root.getList(ENTRIES_NBT, Tag.TAG_COMPOUND);
        List<IndexedStack> decoded = new ArrayList<>(entries.size());
        int highestIndex = -1;
        for (int listIndex = 0; listIndex < entries.size(); listIndex++) {
            CompoundTag entry = entries.getCompound(listIndex);
            if (!entry.contains(INDEX_NBT, Tag.TAG_INT)
                    || !entry.contains(STACK_NBT, Tag.TAG_COMPOUND)) {
                continue;
            }
            int index = entry.getInt(INDEX_NBT);
            if (index < 0) {
                continue;
            }
            ItemStack stack = ItemStack.parseOptional(
                    registries, entry.getCompound(STACK_NBT));
            if (!stack.isEmpty()) {
                decoded.add(new IndexedStack(index, stack));
                highestIndex = Math.max(highestIndex, index);
            }
        }
        List<ItemStack> replacement = new ArrayList<>(
                Math.max(0, highestIndex + 1));
        ensureSize(replacement, highestIndex + 1);
        for (IndexedStack entry : decoded) {
            replacement.set(entry.index(), entry.stack().copy());
        }
        replace(replacement);
    }

    public void save(CompoundTag parent, String key,
                     HolderLookup.Provider registries) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(key, "key");
        // Always retain the format marker. Its presence distinguishes a new,
        // intentionally empty dynamic library from legacy physical slots.
        parent.put(key, serializeNBT(registries));
    }

    public boolean load(CompoundTag parent, String key,
                        HolderLookup.Provider registries) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(key, "key");
        if (!hasSerialized(parent, key)) {
            return false;
        }
        deserializeNBT(registries, parent.getCompound(key));
        return true;
    }

    public static boolean hasSerialized(CompoundTag parent, String key) {
        if (parent == null || key == null
                || !parent.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag encoded = parent.getCompound(key);
        return encoded.contains(FORMAT_NBT, Tag.TAG_INT)
                && encoded.getInt(FORMAT_NBT) > 0;
    }

    /** Live access facade for a fixed set of sixteen GUI/container slots. */
    public final class PageWindow {
        private final IntSupplier pageSupplier;
        private final IntSupplier recipeCountSupplier;

        private PageWindow(IntSupplier pageSupplier,
                           IntSupplier recipeCountSupplier) {
            this.pageSupplier = Objects.requireNonNull(
                    pageSupplier, "pageSupplier");
            this.recipeCountSupplier = Objects.requireNonNull(
                    recipeCountSupplier, "recipeCountSupplier");
        }

        public int slotCount() {
            return PAGE_SIZE;
        }

        public int page() {
            return Math.max(0, pageSupplier.getAsInt());
        }

        public int recipeCount() {
            return Math.max(0, recipeCountSupplier.getAsInt());
        }

        public int pageCount() {
            return CatalystLibraryStorage.this.pageCount(recipeCount());
        }

        public int clampedPage() {
            return CatalystLibraryStorage.this.clampPage(
                    page(), recipeCount());
        }

        public int absoluteIndex(int windowSlot) {
            return CatalystLibraryStorage.absoluteIndex(
                    page(), windowSlot);
        }

        public boolean isVisible(int windowSlot) {
            return CatalystLibraryStorage.this.isVisibleSlot(
                    absoluteIndex(windowSlot), recipeCount());
        }

        public ItemStack get(int windowSlot) {
            return CatalystLibraryStorage.this.get(
                    absoluteIndex(windowSlot));
        }

        public boolean set(int windowSlot, ItemStack stack) {
            return CatalystLibraryStorage.this.set(
                    absoluteIndex(windowSlot), stack);
        }

        public List<ItemStack> snapshot() {
            return snapshotWindow(page());
        }
    }

    private static ItemStack copyOrEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? ItemStack.EMPTY : stack.copy();
    }

    private static List<ItemStack> mutableCopies(
            List<? extends ItemStack> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<ItemStack> result = new ArrayList<>(source.size());
        for (ItemStack stack : source) {
            result.add(copyOrEmpty(stack));
        }
        return result;
    }

    private static List<ItemStack> immutableCopies(List<ItemStack> source) {
        return List.copyOf(mutableCopies(source));
    }

    private static void ensureSize(List<ItemStack> target, int size) {
        while (target.size() < size) {
            target.add(ItemStack.EMPTY);
        }
    }

    private static void trimEmptyTail(List<ItemStack> target) {
        for (int index = target.size() - 1;
             index >= 0 && target.get(index).isEmpty(); index--) {
            target.remove(index);
        }
    }

    private static boolean sameContents(
            List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!ItemStack.matches(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static void checkWindowSlot(int windowSlot) {
        if (windowSlot < 0 || windowSlot >= PAGE_SIZE) {
            throw new IndexOutOfBoundsException(
                    "Catalyst page slot must be in [0, "
                            + PAGE_SIZE + "): " + windowSlot);
        }
    }

    private record IndexedStack(int index, ItemStack stack) {
    }
}
