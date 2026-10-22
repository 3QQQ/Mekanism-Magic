package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryStorage;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryWindowSlot;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Stable sixteen-slot menu window over an unbounded altar catalyst overflow. */
public final class NatureAltarCatalystLibrary {
    public static final int PHYSICAL_CATALYST_SLOTS = 2;
    public static final int WINDOW_SIZE = CatalystLibraryStorage.PAGE_SIZE;
    public static final int SAVED_CATALYST_SLOTS =
            PHYSICAL_CATALYST_SLOTS + WINDOW_SIZE;
    public static final String STORAGE_NBT =
            "nature_altar_catalyst_overflow";
    private static final String PAGE_NBT = "nature_altar_catalyst_page";

    private final Supplier<Level> level;
    private final Runnable contentsChanged;
    private final CatalystLibraryStorage storage;
    private final List<CatalystLibraryWindowSlot> windowSlots;
    private final List<CatalystLibraryWindowSlot> absoluteViews =
            new ArrayList<>();
    private int page;
    private int syncedVisibleSlotCount;
    private boolean open;

    public NatureAltarCatalystLibrary(Supplier<Level> level,
                                      IntSupplier imageWidth,
                                      Runnable contentsChanged) {
        this.level = Objects.requireNonNull(level);
        this.contentsChanged = Objects.requireNonNull(contentsChanged);
        storage = new CatalystLibraryStorage(() -> {
            clampPage();
            this.contentsChanged.run();
        });
        CatalystLibraryStorage.PageWindow window = storage.pageWindow(
                this::page, this::capacity);
        windowSlots = CatalystLibraryWindowSlot.createManualWindow(window,
                this::clientSide,
                () -> visibleSlotCount() > 0
                        && (!clientSide() || open),
                stack -> NatureAuraRecipeBridge.isAltarCatalyst(
                        this.level.get(), stack),
                index -> CatalystLibraryLayout.slotX(
                        imageWidth.getAsInt(), index),
                CatalystLibraryLayout::slotY);
    }

    public List<CatalystLibraryWindowSlot> windowSlots() {
        return windowSlots;
    }

    public boolean isWindowSlot(IInventorySlot slot) {
        return windowSlots.contains(slot);
    }

    public List<ItemStack> stacks(ItemStack first, ItemStack second) {
        List<ItemStack> all = new ArrayList<>(
                PHYSICAL_CATALYST_SLOTS + storage.retainedSlotCount());
        all.add(first == null ? ItemStack.EMPTY : first);
        all.add(second == null ? ItemStack.EMPTY : second);
        all.addAll(storage.snapshot());
        return List.copyOf(all);
    }

    /** Absolute views never alias the currently selected GUI page. */
    public List<IInventorySlot> persistentInputs(
            IInventorySlot first, IInventorySlot second) {
        List<IInventorySlot> result = new ArrayList<>();
        if (first != null) {
            result.add(first);
        }
        if (second != null) {
            result.add(second);
        }
        int count = storage.retainedSlotCount();
        while (absoluteViews.size() < count) {
            int absolute = absoluteViews.size();
            CatalystLibraryStorage.PageWindow fixed = storage.pageWindow(
                    () -> absolute / WINDOW_SIZE, this::capacity);
            absoluteViews.add(new CatalystLibraryWindowSlot(fixed,
                    absolute % WINDOW_SIZE, () -> false, () -> true,
                    (stack, automation) ->
                            automation == AutomationType.MANUAL,
                    (stack, automation) ->
                            automation == AutomationType.MANUAL,
                    stack -> NatureAuraRecipeBridge.isAltarCatalyst(
                            level.get(), stack), 0, 0));
        }
        for (int index = 0; index < count; index++) {
            if (!storage.get(index).isEmpty()) {
                result.add(absoluteViews.get(index));
            }
        }
        return List.copyOf(result);
    }

    public int page() {
        return page;
    }

    public int pageCount() {
        if (clientSide()) {
            return Math.max(1, Math.ceilDiv(
                    visibleSlotCount(), WINDOW_SIZE));
        }
        return storage.pageCount(capacity());
    }

    public int visibleSlotCount() {
        return clientSide() ? Math.max(0, syncedVisibleSlotCount)
                : Math.max(capacity(), storage.retainedSlotCount());
    }

    public ItemStack pageStack(int pageSlot) {
        return pageSlot < 0 || pageSlot >= windowSlots.size()
                ? ItemStack.EMPTY : windowSlots.get(pageSlot).getStack();
    }

    public void cyclePage(int delta) {
        page = Math.floorMod(page + delta, pageCount());
        contentsChanged.run();
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean clampPage() {
        int next = Math.max(0, Math.min(pageCount() - 1, page));
        if (next == page) {
            return false;
        }
        page = next;
        return true;
    }

    public void addContainerTrackers(MekanismContainer container) {
        container.track(SyncableInt.create(this::visibleSlotCount,
                value -> syncedVisibleSlotCount = Math.max(0, value)));
        container.track(SyncableInt.create(this::page,
                value -> page = Math.max(0,
                        Math.min(pageCount() - 1, value))));
    }

    public void replaceOverflow(List<ItemStack> allCatalysts) {
        storage.replace(allCatalysts == null || allCatalysts.size()
                <= PHYSICAL_CATALYST_SLOTS ? List.of()
                : allCatalysts.subList(PHYSICAL_CATALYST_SLOTS,
                allCatalysts.size()));
        clampPage();
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        storage.save(tag, STORAGE_NBT, registries);
        tag.putInt(PAGE_NBT, page);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!storage.load(tag, STORAGE_NBT, registries)) {
            storage.clear();
        }
        page = Math.max(0, tag.getInt(PAGE_NBT));
        clampPage();
    }

    private int capacity() {
        Level current = level.get();
        if (current != null && current.isClientSide()) {
            return Math.max(0, syncedVisibleSlotCount);
        }
        int known = current == null ? 0
                : NatureAuraRecipeScanner.catalystKindCount(
                current.getRecipeManager());
        int enumerableOverflow = Math.max(0,
                known - PHYSICAL_CATALYST_SLOTS);
        // Only opaque custom ingredients need a spare discovery position:
        // their matching items may not be enumerable through getItems().
        boolean opaque = current != null
                && NatureAuraRecipeScanner.hasOpaqueCatalyst(
                current.getRecipeManager());
        return opaque ? Math.max(enumerableOverflow + 1,
                storage.retainedSlotCount() + 1)
                : enumerableOverflow;
    }

    private boolean clientSide() {
        Level current = level.get();
        return current != null && current.isClientSide();
    }
}
