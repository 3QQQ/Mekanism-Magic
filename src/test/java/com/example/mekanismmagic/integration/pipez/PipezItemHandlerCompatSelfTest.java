package com.example.mekanismmagic.integration.pipez;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Offline executable test; it deliberately has no external test dependency. */
public final class PipezItemHandlerCompatSelfTest {
    private PipezItemHandlerCompatSelfTest() {
    }

    public static void main(String[] args) {
        detectsOnlyExtractableOversizedSlots();
        bulkContextIsScopedAndRestored();
        wrapsLegalVisibleSlotsForHiddenLongBuffers();
        clampsLargeExtractionWithoutIntegerOverflow();
        physicalPushBudgetBacksOffAndRecovers();
    }

    private static void bulkContextIsScopedAndRestored() {
        check(!PipezItemHandlerCompat.isBulkExtractionActive(),
                "Bulk context leaked before extraction");
        check(PipezItemHandlerCompat.withBulkExtraction(
                        PipezItemHandlerCompat::isBulkExtractionActive),
                "Bulk context was not active inside extraction");
        check(!PipezItemHandlerCompat.isBulkExtractionActive(),
                "Bulk context leaked after extraction");
    }

    private static void detectsOnlyExtractableOversizedSlots() {
        check(PipezItemHandlerCompat.requiresBulkExtraction(
                        Integer.MAX_VALUE, 64, true),
                "Oversized extractable slot was not bulk-enabled");
        check(!PipezItemHandlerCompat.requiresBulkExtraction(64, 64, true),
                "Normal slot was incorrectly bulk-enabled");
        check(!PipezItemHandlerCompat.requiresBulkExtraction(
                        Integer.MAX_VALUE, 64, false),
                "Non-extractable slot was incorrectly bulk-enabled");
    }

    private static void wrapsLegalVisibleSlotsForHiddenLongBuffers() {
        RecordingHandler source = new RecordingHandler();
        IItemHandler wrapped = PipezItemHandlerCompat.wrapOrderedSource(source);
        check(wrapped != source,
                "A legal visible slot hid the long-buffer Pipez context");
        wrapped.extractItem(0, Integer.MAX_VALUE, true);
        check(source.sawBulkContext,
                "Ordered extraction did not enter the scoped bulk context");
        check(!PipezItemHandlerCompat.isBulkExtractionActive(),
                "Ordered extraction leaked its bulk context");
    }

    private static void clampsLargeExtractionWithoutIntegerOverflow() {
        check(PipezItemHandlerCompat.boundedExtractionAmount(
                        2_000_000, 5_000_064L) == 2_000_000,
                "Infinity-rate extraction was truncated to visible slots");
        check(PipezItemHandlerCompat.boundedExtractionAmount(
                        Integer.MAX_VALUE, 5_000_064L) == 5_000_064,
                "Long-buffer remainder was not fully exposed");
        check(PipezItemHandlerCompat.boundedExtractionAmount(
                        Integer.MAX_VALUE, Long.MAX_VALUE)
                        == Integer.MAX_VALUE,
                "Long-buffer extraction overflowed the ItemStack count");
        check(PipezItemHandlerCompat.boundedExtractionAmount(-1, 10) == 0
                        && PipezItemHandlerCompat
                        .boundedExtractionAmount(10, -1) == 0,
                "Invalid extraction amounts were not rejected");
    }

    private static void physicalPushBudgetBacksOffAndRecovers() {
        int backedOff = NativeMagicMachineBlockEntity
                .adaptDirectPushCallBudget(32, 32, 32, true);
        check(backedOff == 16,
                "One-at-a-time target did not reduce the physical budget");
        backedOff = NativeMagicMachineBlockEntity
                .adaptDirectPushCallBudget(backedOff, 16, 16, true);
        check(backedOff == 8,
                "Low-acceptance budget did not continue backing off");
        check(NativeMagicMachineBlockEntity.adaptDirectPushCallBudget(
                        2, 2, 2, true) == 2,
                "Physical budget fell below the starvation-safe minimum");
        check(NativeMagicMachineBlockEntity.adaptDirectPushCallBudget(
                        8, 8, 512, true) == 16,
                "High-throughput target did not grow the bounded burst");
        check(NativeMagicMachineBlockEntity.adaptDirectPushCallBudget(
                        32, 32, 2_048, true) == 32,
                "Physical budget exceeded its hard maximum");
        check(NativeMagicMachineBlockEntity.adaptDirectPushCallBudget(
                        32, 1, 64, false) == 8,
                "Drained output did not reset the adaptive budget");
    }

    private static final class RecordingHandler implements IItemHandler {
        private boolean sawBulkContext;

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            // This offline scope test intentionally never inspects a stack;
            // constructing ItemStack would require a full NeoForge bootstrap.
            return null;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack,
                                    boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount,
                                     boolean simulate) {
            sawBulkContext = PipezItemHandlerCompat
                    .isBulkExtractionActive();
            return null;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

}
