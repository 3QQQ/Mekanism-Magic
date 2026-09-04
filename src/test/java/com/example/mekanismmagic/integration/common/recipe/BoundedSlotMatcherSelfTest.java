package com.example.mekanismmagic.integration.common.recipe;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline assertions for the bounded ritual ingredient assignment. */
public final class BoundedSlotMatcherSelfTest {
    private BoundedSlotMatcherSelfTest() {
    }

    public static void main(String[] args) {
        reassignsAnEarlyFlexibleIngredient();
        exposesConcreteIngredientAssignments();
        honorsStackCapacities();
        rejectsHallDeficientStressCase();
        handlesEmptyAndMalformedLayouts();
        routesConstrainedRequestsWithoutPermutationSearch();
        splitsOneRequestAcrossExclusivePorts();
    }

    private static void routesConstrainedRequestsWithoutPermutationSearch() {
        TestPort flexible = new TestPort(Map.of("a", 1L, "b", 1L));
        TestPort onlyA = new TestPort(Map.of("a", 1L));
        Map<String, Long> requests = new LinkedHashMap<>();
        requests.put("a", 1L);
        requests.put("b", 1L);
        var plan = BoundedRequestRouter.plan(requests,
                List.of(flexible, onlyA), TestPort::capacity)
                .orElseThrow();
        require(plan.size() == 2
                        && plan.stream().anyMatch(allocation ->
                        allocation.key().equals("b")
                                && allocation.port() == flexible)
                        && plan.stream().anyMatch(allocation ->
                        allocation.key().equals("a")
                                && allocation.port() == onlyA),
                "constrained request was not routed before flexible one");
    }

    private static void splitsOneRequestAcrossExclusivePorts() {
        TestPort first = new TestPort(Map.of("a", 3L));
        TestPort second = new TestPort(Map.of("a", 4L));
        var plan = BoundedRequestRouter.plan(Map.of("a", 7L),
                List.of(first, second), TestPort::capacity)
                .orElseThrow();
        require(plan.stream().mapToLong(
                        BoundedRequestRouter.Allocation::amount).sum() == 7,
                "one keyed request was not split across slots");
        require(BoundedRequestRouter.plan(
                        Map.of("a", 8L), List.of(first, second),
                        TestPort::capacity).isEmpty(),
                "over-capacity request unexpectedly routed");
    }

    private static void reassignsAnEarlyFlexibleIngredient() {
        boolean[][] compatibility = {
                {true, true},
                {true, false}
        };
        int[] used = BoundedSlotMatcher.match(
                compatibility, new int[]{1, 1}).orElseThrow();
        require(Arrays.equals(used, new int[]{1, 1}),
                "flexible ingredient was not reassigned");
    }

    private static void exposesConcreteIngredientAssignments() {
        boolean[][] compatibility = {
                {true, true},
                {true, false}
        };
        BoundedSlotMatcher.Assignment assignment =
                BoundedSlotMatcher.assign(compatibility,
                        new int[]{1, 1}).orElseThrow();
        require(assignment.ingredientSlot(0) == 1
                        && assignment.ingredientSlot(1) == 0,
                "concrete ingredient assignment disagreed with max flow");
        int[] leaked = assignment.slotUse();
        leaked[0] = 0;
        require(assignment.slotUse()[0] == 1,
                "assignment exposed its mutable slot accounting");
    }

    private static void honorsStackCapacities() {
        boolean[][] compatibility = {
                {true, false, false},
                {true, false, false},
                {true, true, false},
                {false, true, true},
                {false, false, true}
        };
        int[] capacities = {2, 2, 1};
        int[] used = BoundedSlotMatcher.match(
                compatibility, capacities).orElseThrow();
        require(Arrays.equals(used, capacities),
                "slot stack capacities were not respected");
    }

    private static void rejectsHallDeficientStressCase() {
        int ingredients = 256;
        int slots = 16;
        boolean[][] compatibility = new boolean[ingredients][slots];
        int[] capacities = new int[slots];
        Arrays.fill(capacities, 16);
        for (int ingredient = 0; ingredient < ingredients; ingredient++) {
            int limit = ingredient < 200 ? 8 : slots;
            Arrays.fill(compatibility[ingredient], 0, limit, true);
        }
        require(BoundedSlotMatcher.match(
                        compatibility, capacities).isEmpty(),
                "capacity-deficient stress layout unexpectedly matched");
        require(Arrays.stream(capacities).allMatch(value -> value == 16),
                "matcher mutated caller capacities");
    }

    private static void handlesEmptyAndMalformedLayouts() {
        int[] empty = BoundedSlotMatcher.match(
                new boolean[0][3], new int[]{1, 2, 3}).orElseThrow();
        require(Arrays.equals(empty, new int[3]),
                "empty recipe consumed inventory");
        require(BoundedSlotMatcher.match(
                new boolean[][]{{true}}, new int[]{0}).isEmpty(),
                "zero-capacity slot accepted an ingredient");
        try {
            BoundedSlotMatcher.match(
                    new boolean[][]{{true}}, new int[]{1, 1});
            throw new AssertionError("ragged compatibility matrix accepted");
        } catch (IllegalArgumentException expected) {
            // Expected programmer error.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record TestPort(Map<String, Long> capacities) {
        private long capacity(String key, long alreadyPlanned,
                              long requested) {
            long maximum = capacities.getOrDefault(key, 0L);
            return Math.max(0, Math.min(requested,
                    maximum - alreadyPlanned));
        }
    }
}
