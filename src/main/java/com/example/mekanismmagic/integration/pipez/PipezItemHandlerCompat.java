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
 * the requested amount from either a legacy oversized slot or the machines'
 * persistent long-output buffer instead of manufacturing thousands of
 * virtual slots.
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

        // Always provide the scoped view. Current long-buffer machines keep
        // their visible slots at the legal item stack size, so inspecting
        // getStackInSlot cannot reveal that additional output is available.
        // Do not probe every source slot here: Pipez is already going to
        // simulate the slots it needs, and a redundant scan was costly for
        // machines with many output slots. For every other handler the
        // ThreadLocal is inert and extraction delegates unchanged.
        return new BulkExtractionView(source);
    }

    public static boolean requiresBulkExtraction(
            int storedCount, int normalLimit, boolean extractable) {
        return extractable && storedCount > Math.max(1, normalLimit);
    }

    /** Integer-safe amount shared by simulation and commit implementations. */
    public static int boundedExtractionAmount(int requested, long available) {
        if (requested <= 0 || available <= 0) {
            return 0;
        }
        return (int) Math.min((long) requested,
                Math.min((long) Integer.MAX_VALUE, available));
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
