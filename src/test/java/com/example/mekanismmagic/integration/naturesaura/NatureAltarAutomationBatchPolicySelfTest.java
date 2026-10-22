package com.example.mekanismmagic.integration.naturesaura;

import java.util.Random;

/** Offline pressure assertions for multiplied AE factory submissions. */
public final class NatureAltarAutomationBatchPolicySelfTest {
    private NatureAltarAutomationBatchPolicySelfTest() {
    }

    public static void main(String[] args) {
        require(NatureAltarAutomationBatchPolicy.plan(0,
                new int[]{1}) == null, "zero request was accepted");
        require(NatureAltarAutomationBatchPolicy.plan(2,
                new int[]{1}) == null, "partial request was accepted");
        assertPlan(7, new int[]{2, 0, 3, 9},
                new int[]{2, 0, 3, 2});
        assertPlan(Integer.MAX_VALUE,
                new int[]{Integer.MAX_VALUE},
                new int[]{Integer.MAX_VALUE});
        int[] infiniteCapacities = new int[17];
        java.util.Arrays.fill(infiniteCapacities, 64);
        assertPlan(17 * 64, infiniteCapacities, infiniteCapacities);
        require(NatureAltarAutomationBatchPolicy.plan(Integer.MAX_VALUE,
                        infiniteCapacities) == null,
                "Infinite factory accepted a batch beyond all 17 lanes");
        int[] hugeCapacities = new int[17];
        java.util.Arrays.fill(hugeCapacities, Integer.MAX_VALUE);
        int[] boundedHugePlan = new int[17];
        boundedHugePlan[0] = Integer.MAX_VALUE;
        assertPlan(Integer.MAX_VALUE, hugeCapacities, boundedHugePlan);
        require(NatureAltarLockedContextPolicy.remainsValid(
                        true, true, "same", "same"),
                "matching signed lock was rejected");
        require(NatureAltarLockedContextPolicy.remainsValid(
                        true, true, "", "current"),
                "legacy unsigned lock was not migratable");
        require(!NatureAltarLockedContextPolicy.remainsValid(
                        true, true, "old", "changed"),
                "changed recipe content retained progress");
        require(!NatureAltarLockedContextPolicy.remainsValid(
                        false, true, "same", "same"),
                "removed recipe retained progress");
        require(NatureAltarLockedContextPolicy.quarantineInvalid(
                        false, 4, 4),
                "in-flight stale batch was not quarantined");
        require(!NatureAltarLockedContextPolicy.quarantineInvalid(
                        false, 4, 0)
                        && !NatureAltarLockedContextPolicy
                        .quarantineInvalid(false, 0, 4),
                "empty or non-context input was quarantined");
        require(NatureAltarContextCountPolicy.onAccepted(3, 5) == 8,
                "context batch addition failed");
        require(NatureAltarContextCountPolicy.onAccepted(
                        Integer.MAX_VALUE, 1) == -1,
                "context count overflow was accepted");
        require(NatureAltarContextCountPolicy.afterCompletion(3) == 2,
                "context count did not decrement exactly once");
        require(NatureAltarContextCountPolicy.afterCompletion(0) == 0,
                "manual context count became negative");
        require(NatureAltarContextCountPolicy.clampToInput(7, 3) == 3,
                "external extraction did not clamp context count");
        int contextualBatch = NatureAltarContextCountPolicy
                .onAccepted(0, 3);
        contextualBatch = NatureAltarContextCountPolicy
                .afterCompletion(contextualBatch);
        require(contextualBatch == 2,
                "first completion dropped a multi-item recipe lock");
        contextualBatch = NatureAltarContextCountPolicy
                .afterCompletion(contextualBatch);
        require(contextualBatch == 1,
                "second completion dropped a multi-item recipe lock");
        contextualBatch = NatureAltarContextCountPolicy
                .afterCompletion(contextualBatch);
        require(contextualBatch == 0,
                "final completion retained a stale recipe lock");

        Random random = new Random(0x4E41545552454CL);
        for (int iteration = 0; iteration < 100_000; iteration++) {
            int lanes = 1 + random.nextInt(17);
            int requested = 1 + random.nextInt(4_096);
            int[] capacities = new int[lanes];
            long totalCapacity = 0;
            for (int lane = 0; lane < lanes; lane++) {
                capacities[lane] = random.nextInt(1_025) - 64;
                totalCapacity += Math.max(0, capacities[lane]);
            }
            int[] plan = NatureAltarAutomationBatchPolicy.plan(
                    requested, capacities);
            require((plan != null) == (totalCapacity >= requested),
                    "availability mismatch at iteration " + iteration);
            if (plan == null) {
                continue;
            }
            long allocated = 0;
            for (int lane = 0; lane < lanes; lane++) {
                require(plan[lane] >= 0
                                && plan[lane] <= Math.max(0,
                                capacities[lane]),
                        "lane over-allocation at iteration " + iteration);
                allocated += plan[lane];
            }
            require(allocated == requested,
                    "non-conserving plan at iteration " + iteration);

            int existing = random.nextInt(4_096);
            int added = 1 + random.nextInt(4_096);
            int combined = NatureAltarContextCountPolicy.onAccepted(
                    existing, added);
            require(combined == existing + added,
                    "context count addition mismatch at iteration "
                            + iteration);
            int completed = random.nextInt(
                    Math.min(combined, 32) + 1);
            int remainingContext = combined;
            for (int operation = 0; operation < completed; operation++) {
                remainingContext = NatureAltarContextCountPolicy
                        .afterCompletion(remainingContext);
            }
            require(remainingContext == combined - completed,
                    "context count completion mismatch at iteration "
                            + iteration);
        }
        System.out.println(
                "Nature altar AE batch policy self-test passed");
    }

    private static void assertPlan(int requested, int[] capacities,
                                   int[] expected) {
        int[] actual = NatureAltarAutomationBatchPolicy.plan(
                requested, capacities);
        require(java.util.Arrays.equals(actual, expected),
                "unexpected deterministic allocation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
