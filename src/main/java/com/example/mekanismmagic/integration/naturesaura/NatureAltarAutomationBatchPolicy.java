package com.example.mekanismmagic.integration.naturesaura;

/** Bounded, deterministic all-or-nothing allocation for factory AE batches. */
public final class NatureAltarAutomationBatchPolicy {
    private NatureAltarAutomationBatchPolicy() {
    }

    /**
     * Returns one allocation per lane, or {@code null} when the complete
     * request cannot fit. Negative capacities are treated as zero.
     */
    public static int[] plan(int requested, int[] capacities) {
        if (requested <= 0 || capacities == null
                || capacities.length == 0) {
            return null;
        }
        int[] allocation = new int[capacities.length];
        long remaining = requested;
        for (int lane = 0; lane < capacities.length
                && remaining > 0; lane++) {
            int capacity = Math.max(0, capacities[lane]);
            int accepted = (int) Math.min(remaining, (long) capacity);
            allocation[lane] = accepted;
            remaining -= accepted;
        }
        return remaining == 0 ? allocation : null;
    }
}
