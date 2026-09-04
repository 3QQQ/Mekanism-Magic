package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * One of sixteen fixed container slots backed by a dynamic catalyst page.
 *
 * <p>The authoritative server delegates every operation to the dynamic
 * storage. The client deliberately uses {@link #current} only as a display
 * cache: a page-number update and its vanilla slot packets may arrive in
 * either order, and a client packet must never write a new page's stack into
 * storage under the old page number.</p>
 */
public final class CatalystLibraryWindowSlot extends BasicInventorySlot {
    private final CatalystLibraryStorage.PageWindow window;
    private final int windowSlot;
    private final BooleanSupplier clientSide;
    private final BooleanSupplier active;
    private final BiPredicate<ItemStack, AutomationType> canExtract;
    private final BiPredicate<ItemStack, AutomationType> canInsert;
    private final Predicate<ItemStack> validator;
    private final int x;
    private final int y;

    public CatalystLibraryWindowSlot(
            CatalystLibraryStorage.PageWindow window,
            int windowSlot,
            BooleanSupplier clientSide,
            BooleanSupplier active,
            BiPredicate<ItemStack, AutomationType> canExtract,
            BiPredicate<ItemStack, AutomationType> canInsert,
            Predicate<ItemStack> validator,
            int x, int y) {
        super(Objects.requireNonNull(canExtract, "canExtract"),
                Objects.requireNonNull(canInsert, "canInsert"),
                Objects.requireNonNull(validator, "validator"),
                null, x, y);
        if (windowSlot < 0
                || windowSlot >= CatalystLibraryStorage.PAGE_SIZE) {
            throw new IndexOutOfBoundsException(
                    "Catalyst page slot must be in [0, "
                            + CatalystLibraryStorage.PAGE_SIZE + "): "
                            + windowSlot);
        }
        this.window = Objects.requireNonNull(window, "window");
        this.windowSlot = windowSlot;
        this.clientSide = Objects.requireNonNull(
                clientSide, "clientSide");
        this.active = Objects.requireNonNull(active, "active");
        this.canExtract = canExtract;
        this.canInsert = canInsert;
        this.validator = validator;
        this.x = x;
        this.y = y;
        setSlotType(ContainerSlotType.INPUT);
    }

    /** Creates the usual player-only sixteen-slot catalyst page. */
    public static List<CatalystLibraryWindowSlot> createManualWindow(
            CatalystLibraryStorage.PageWindow window,
            BooleanSupplier clientSide,
            BooleanSupplier active,
            Predicate<ItemStack> validator,
            IntUnaryOperator x,
            IntUnaryOperator y) {
        List<CatalystLibraryWindowSlot> slots = new ArrayList<>(
                CatalystLibraryStorage.PAGE_SIZE);
        for (int slot = 0;
             slot < CatalystLibraryStorage.PAGE_SIZE; slot++) {
            slots.add(new CatalystLibraryWindowSlot(window, slot,
                    clientSide, active,
                    (stack, automation) ->
                            automation == AutomationType.MANUAL,
                    (stack, automation) ->
                            automation == AutomationType.MANUAL,
                    validator, x.applyAsInt(slot), y.applyAsInt(slot)));
        }
        return List.copyOf(slots);
    }

    public int windowSlot() {
        return windowSlot;
    }

    public int absoluteIndex() {
        return window.absoluteIndex(windowSlot);
    }

    public boolean isVisible() {
        return window.isVisible(windowSlot);
    }

    private boolean isAccessible() {
        return active.getAsBoolean() && isVisible();
    }

    @Override
    public ItemStack getStack() {
        return clientSide.getAsBoolean()
                ? current : window.get(windowSlot);
    }

    @Override
    public void setStack(ItemStack stack) {
        if (clientSide.getAsBoolean()) {
            super.setStack(stack);
        } else if (stack.isEmpty() || validator.test(stack)) {
            window.set(windowSlot, stack);
        } else {
            throw new RuntimeException(
                    "Invalid stack for catalyst window slot: " + stack);
        }
    }

    @Override
    public void setStackUnchecked(ItemStack stack) {
        if (clientSide.getAsBoolean()) {
            // Vanilla menu synchronization terminates here on the client.
            // Never delegate a received display stack into dynamic storage.
            super.setStackUnchecked(stack);
        } else {
            window.set(windowSlot, stack);
        }
    }

    @Override
    public ItemStack insertItem(ItemStack stack, Action action,
                                AutomationType automationType) {
        if (clientSide.getAsBoolean()) {
            return isAccessible()
                    ? super.insertItem(stack, action, automationType) : stack;
        }
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stored = window.get(windowSlot);
        int needed = getLimit(stack) - stored.getCount();
        if (!isAccessible() || needed <= 0
                || !validator.test(stack)
                || !canInsert.test(stack, automationType)
                || !stored.isEmpty()
                && !ItemStack.isSameItemSameComponents(stored, stack)) {
            return stack;
        }
        int inserted = Math.min(stack.getCount(), needed);
        if (action.execute()) {
            ItemStack replacement = stored.isEmpty()
                    ? stack.copyWithCount(inserted) : stored.copy();
            if (!stored.isEmpty()) {
                replacement.grow(inserted);
            }
            window.set(windowSlot, replacement);
        }
        return stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int amount, Action action,
                                 AutomationType automationType) {
        if (clientSide.getAsBoolean()) {
            return isAccessible()
                    ? super.extractItem(amount, action, automationType)
                    : ItemStack.EMPTY;
        }
        ItemStack stored = window.get(windowSlot);
        if (!isAccessible() || stored.isEmpty() || amount < 1
                || !canExtract.test(stored, automationType)) {
            return ItemStack.EMPTY;
        }
        int extracted = Math.min(amount,
                Math.min(stored.getCount(), stored.getMaxStackSize()));
        ItemStack result = stored.copyWithCount(extracted);
        if (action.execute()) {
            ItemStack replacement = stored.copy();
            replacement.shrink(extracted);
            window.set(windowSlot, replacement);
        }
        return result;
    }

    @Override
    public int setStackSize(int amount, Action action) {
        if (clientSide.getAsBoolean()) {
            return super.setStackSize(amount, action);
        }
        ItemStack stored = window.get(windowSlot);
        if (stored.isEmpty()) {
            return 0;
        }
        int target = Math.max(0, Math.min(amount, getLimit(stored)));
        if (action.execute() && target != stored.getCount()) {
            window.set(windowSlot, target == 0 ? ItemStack.EMPTY
                    : stored.copyWithCount(target));
        }
        return target;
    }

    @Override
    public int growStack(int amount, Action action) {
        if (clientSide.getAsBoolean()) {
            return super.growStack(amount, action);
        }
        ItemStack stored = window.get(windowSlot);
        int currentCount = stored.getCount();
        if (currentCount == 0) {
            return 0;
        }
        if (amount > 0) {
            amount = Math.min(amount, getLimit(stored));
        }
        return setStackSize(currentCount + amount, action) - currentCount;
    }

    @Override
    public boolean isEmpty() {
        return getStack().isEmpty();
    }

    @Override
    public int getCount() {
        return getStack().getCount();
    }

    /** Dynamic storage owns persistence; window caches serialize nowhere. */
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    /** Dynamic storage owns persistence; physical window data is ignored. */
    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag nbt) {
    }

    @Override
    public InventoryContainerSlot createContainerSlot() {
        return new InventoryContainerSlot(this, x, y, getSlotType(),
                getSlotOverlay(), warning -> {
                }, this::setStackUnchecked) {
            @Override
            public boolean isActive() {
                return isAccessible();
            }
        };
    }
}
