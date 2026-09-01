package com.example.mekanismmagic.integration.pipez;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Supplier;

/**
 * Adapts high-capacity item slots to Pipez' ordered extraction pass.
 *
 * <p>Pipez 1.21.1 (1.2.17+) visits each source slot only once per
 * destination. A
 * normal item handler must return at most one legal stack from each
 * {@code extractItem} call, so a Mekanism Magic slot containing more than a
 * normal stack would otherwise be limited to 64 items per transfer pass.
 * This view keeps the real slot count and marks Pipez' simulated/committed
 * extraction call as bulk-aware. Mekanism Magic can then atomically shrink
 * the requested amount instead of manufacturing thousands of virtual slots.
 * Pipez' configured rate and destination acceptance remain authoritative.</p>
 */
public final class PipezItemHandlerCompat {
    private static final ThreadLocal<Boolean> BULK_EXTRACTION =
            ThreadLocal.withInitial(() -> false);

    private PipezItemHandlerCompat() {
    }

    public static IItemHandler wrapOrderedSource(IItemHandler source) {
        if (source == null || source instanceof BulkExtractionView) {
            return source;
        }
        try {
            return createView(source);
        } catch (RuntimeException ignored) {
            // A third-party handler is allowed to implement stricter access
            // rules. If it cannot be inspected safely, retain Pipez' native
            // behavior rather than making the connection unusable.
            return source;
        }
    }

    private static IItemHandler createView(IItemHandler source) {
        int sourceSlots = source.getSlots();
        if (sourceSlots <= 0) {
            return source;
        }

        boolean needsBulkView = false;
        for (int slot = 0; slot < sourceSlots; slot++) {
            ItemStack stored = source.getStackInSlot(slot);
            if (stored.isEmpty()) {
                continue;
            }
            boolean extractable = !source.extractItem(slot, 1, true).isEmpty();
            needsBulkView |= requiresBulkExtraction(stored.getCount(),
                    stored.getMaxStackSize(), extractable);
        }
        return needsBulkView ? new BulkExtractionView(source) : source;
    }

    static boolean requiresBulkExtraction(int storedCount, int normalLimit,
                                          boolean extractable) {
        return extractable && storedCount > Math.max(1, normalLimit);
    }

    /** Called only by the shared machine inventory while Pipez is extracting. */
    public static boolean isBulkExtractionActive() {
        return BULK_EXTRACTION.get();
    }

    private static ItemStack extractBulk(
            IItemHandler source, int slot, int amount, boolean simulate) {
        return withBulkExtraction(
                () -> source.extractItem(slot, amount, simulate));
    }

    static <T> T withBulkExtraction(Supplier<T> action) {
        boolean previous = BULK_EXTRACTION.get();
        BULK_EXTRACTION.set(true);
        try {
            return action.get();
        } finally {
            BULK_EXTRACTION.set(previous);
        }
    }

    private static final class BulkExtractionView implements IItemHandler {
        private final IItemHandler delegate;

        private BulkExtractionView(IItemHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack,
                                    boolean simulate) {
            // This temporary view is used only as Pipez' extraction source.
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount,
                                     boolean simulate) {
            return extractBulk(delegate, slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
