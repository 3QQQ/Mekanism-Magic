package com.example.mekanismmagic.integration.pipez;

/** Offline executable test; it deliberately has no external test dependency. */
public final class PipezItemHandlerCompatSelfTest {
    private PipezItemHandlerCompatSelfTest() {
    }

    public static void main(String[] args) {
        detectsOnlyExtractableOversizedSlots();
        bulkContextIsScopedAndRestored();
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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

}
