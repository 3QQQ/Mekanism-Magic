package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicInteger;

/** Offline assertions for parallel Aura commits and per-lane recipe locks. */
public final class NatureAltarFactorySemanticsSelfTest {
    private static final int AURA_PER_RECIPE = 10_000;

    private NatureAltarFactorySemanticsSelfTest() {
    }

    public static void main(String[] args) {
        testParallelAuraCommits(3);
        testParallelAuraCommits(5);
        testParallelAuraCommits(7);
        testParallelAuraCommits(9);
        testParallelAuraCommits(11);
        testParallelAuraCommits(13);
        testParallelAuraCommits(15);
        testParallelAuraCommits(17);
        testCatalystContextIsPersistentAndPerLane();
        testReloadReconcilesLocksAndProgress();
    }

    private static void testParallelAuraCommits(int lanes) {
        int exactTotal = lanes * AURA_PER_RECIPE;
        AtomicInteger exactChanges = new AtomicInteger();
        SafeAuraContainer exact = storage(exactTotal, exactChanges);
        require(exact.storeExact(exactTotal, false),
                "failed to seed exact shared Aura for " + lanes + " lanes");
        int successful = drainAllLanes(exact, lanes);
        require(successful == lanes,
                "not all exactly funded lanes committed: " + lanes);
        require(exact.getStoredAura() == 0,
                "exactly funded lanes did not leave zero Aura: " + lanes);
        require(exactChanges.get() == lanes + 1,
                "listener count differs from successful exact commits");

        int constrainedTotal = exactTotal - 1;
        AtomicInteger constrainedChanges = new AtomicInteger();
        SafeAuraContainer constrained = storage(
                constrainedTotal, constrainedChanges);
        require(constrained.storeExact(constrainedTotal, false),
                "failed to seed constrained shared Aura");
        successful = drainAllLanes(constrained, lanes);
        require(successful == lanes - 1,
                "an unfunded same-tick lane committed: " + lanes);
        require(constrained.getStoredAura() == AURA_PER_RECIPE - 1,
                "failed commit mutated the shared Aura balance: " + lanes);
        require(constrainedChanges.get() == lanes,
                "failed exact commit notified the storage listener");
    }

    private static int drainAllLanes(SafeAuraContainer storage, int lanes) {
        int successful = 0;
        for (int lane = 0; lane < lanes; lane++) {
            if (storage.drainExact(AURA_PER_RECIPE, false)) {
                successful++;
            }
        }
        return successful;
    }

    private static void testCatalystContextIsPersistentAndPerLane() {
        int catalystCount = 1;
        int first = NatureAltarSelectionPolicy.select(3, true,
                index -> true,
                index -> index != 0,
                index -> index == 1);
        int second = NatureAltarSelectionPolicy.select(3, true,
                index -> true,
                index -> index != 0,
                index -> index == 2);
        require(first == 1 && second == 2,
                "different catalyst contexts did not select distinct recipes");

        NatureAltarFactoryLanePolicy.LaneState<String> firstLane =
                NatureAltarFactoryLanePolicy.reconcile(
                        null, "recipe_" + first, 0);
        NatureAltarFactoryLanePolicy.LaneState<String> secondLane =
                NatureAltarFactoryLanePolicy.reconcile(
                        null, "recipe_" + second, 0);
        require(!firstLane.lockedRecipe().equals(
                        secondLane.lockedRecipe()),
                "per-lane recipe locks collapsed into one shared lock");
        require(NatureAltarSelectionPolicy.lockedRecipeRemainsValid(
                        true, true)
                        && NatureAltarSelectionPolicy
                        .lockedRecipeRemainsValid(true, true),
                "removing the shared catalyst invalidated locked lanes");
        firstLane = NatureAltarFactoryLanePolicy.reconcile(
                firstLane.lockedRecipe(), firstLane.lockedRecipe(), 17);
        secondLane = NatureAltarFactoryLanePolicy.reconcile(
                secondLane.lockedRecipe(), secondLane.lockedRecipe(), 29);
        require(firstLane.progress() == 17 && secondLane.progress() == 29,
                "changing shared catalyst crossed or reset lane locks");
        require(catalystCount == 1,
                "selecting or retaining a recipe consumed its catalyst");
    }

    private static void testReloadReconcilesLocksAndProgress() {
        NatureAltarFactoryLanePolicy.LaneState<String> unchanged =
                NatureAltarFactoryLanePolicy.reconcile(
                        "same_recipe", "same_recipe", 73);
        require("same_recipe".equals(unchanged.lockedRecipe())
                        && unchanged.progress() == 73,
                "unchanged recipe lost its saved lane progress");

        NatureAltarFactoryLanePolicy.LaneState<String> removed =
                NatureAltarFactoryLanePolicy.reconcile(
                        "removed_recipe", null, 73);
        require(removed.lockedRecipe() == null && removed.progress() == 0,
                "removed reload recipe retained lock or progress");

        NatureAltarFactoryLanePolicy.LaneState<String> replaced =
                NatureAltarFactoryLanePolicy.reconcile(
                        "removed_recipe", "replacement_recipe", 73);
        require("replacement_recipe".equals(replaced.lockedRecipe())
                        && replaced.progress() == 0,
                "replacement recipe inherited stale progress");

        NatureAltarFactoryLanePolicy.LaneState<String> completed =
                NatureAltarFactoryLanePolicy.completed();
        require(completed.lockedRecipe() == null
                        && completed.progress() == 0,
                "successful completion retained stale lane context");
    }

    private static SafeAuraContainer storage(
            int capacity, AtomicInteger changes) {
        FakeAuraType type = new FakeAuraType(
                ResourceLocation.fromNamespaceAndPath("test", "overworld"),
                0x44AA66);
        // A transfer rate of one proves recipe commits intentionally use the
        // exact internal transaction rather than external pipe throttling.
        return new SafeAuraContainer(() -> type, capacity,
                1, 1, changes::incrementAndGet);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record FakeAuraType(
            ResourceLocation name, int color) implements IAuraType {
        @Override
        public ResourceLocation getName() {
            return name;
        }

        @Override
        public boolean isPresentInLevel(Level level) {
            return false;
        }

        @Override
        public int getColor() {
            return color;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public boolean isSimilar(IAuraType other) {
            return other != null && name.equals(other.getName());
        }
    }
}
