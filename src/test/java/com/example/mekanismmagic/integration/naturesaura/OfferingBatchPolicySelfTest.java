package com.example.mekanismmagic.integration.naturesaura;

/** Offline conservation checks for the original whole-stack trigger. */
public final class OfferingBatchPolicySelfTest {
    private OfferingBatchPolicySelfTest() {
    }

    public static void main(String[] args) {
        require(OfferingBatchPolicy.operations(64, 1) == 64,
                "one start item must cover a full stack");
        require(OfferingBatchPolicy.outputCount(64, 2) == 128,
                "tainted gold's doubled output was lost");
        require(OfferingBatchPolicy.operations(17, 3) == 5
                        && OfferingBatchPolicy.consumedInput(17, 3) == 15,
                "partial ingredient groups must remain in the input");
        require(OfferingBatchPolicy.operations(2, 3) == 0,
                "insufficient input must not spend a start item");
        require(OfferingBatchPolicy.operations(64, 0) == 0
                        && OfferingBatchPolicy.operations(-1, 1) == 0,
                "malformed input quantities were accepted");
        require(OfferingBatchPolicy.outputCount(Integer.MAX_VALUE, 2) == 0,
                "overflowed output batch was accepted");
        require(OfferingBatchPolicy.outputCount(Integer.MAX_VALUE, 1)
                        == Integer.MAX_VALUE,
                "largest representable batch was incorrectly rejected");
        for (int random = 0; random < 5; random++) {
            require(OfferingBatchPolicy.witherRoseBonus(random) == random + 4,
                    "original inclusive 4–8 wither rose bonus changed");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
