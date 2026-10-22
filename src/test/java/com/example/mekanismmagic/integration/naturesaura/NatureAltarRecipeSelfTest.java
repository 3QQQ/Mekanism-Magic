package com.example.mekanismmagic.integration.naturesaura;

/** Offline assertions for altar catalyst priority and recipe locking. */
public final class NatureAltarRecipeSelfTest {
    private NatureAltarRecipeSelfTest() {
    }

    public static void main(String[] args) {
        // Recipe 0 is catalyst-free; recipe 1 has the matching catalyst.
        int selected = NatureAltarSelectionPolicy.select(2, true,
                index -> true,
                index -> index == 1,
                index -> index == 1);
        require(selected == 1,
                "exact catalyst recipe did not win over fallback");

        selected = NatureAltarSelectionPolicy.select(2, true,
                index -> true,
                index -> index == 1,
                index -> false);
        require(selected == 0,
                "catalyst-free fallback was not selected");

        selected = NatureAltarSelectionPolicy.select(1, false,
                index -> true,
                index -> true,
                index -> true);
        require(selected == -1,
                "a catalyst recipe started without catalyst context");

        selected = NatureAltarSelectionPolicy.select(2, true,
                index -> index == 0,
                index -> index == 1,
                index -> true);
        require(selected == 0,
                "an input-mismatched catalyst recipe was selected");

        // The two native catalysts may coexist. Explicit selection must win
        // over recipe-ID order, but a removed catalyst cannot remain selected.
        selected = NatureAltarSelectionPolicy.select(3, true,
                index -> true,
                index -> index != 0,
                index -> index == 1 || index == 2,
                2);
        require(selected == 2,
                "explicit choice did not select the second matching catalyst recipe");

        selected = NatureAltarSelectionPolicy.select(3, true,
                index -> true,
                index -> index != 0,
                index -> index == 1,
                2);
        require(selected == 1,
                "stale catalyst selection did not fall back to an eligible recipe");

        selected = NatureAltarSelectionPolicy.select(3, true,
                index -> true,
                index -> index != 0,
                index -> index == 1 || index == 2,
                0);
        require(selected == 0,
                "explicit catalyst-free choice was overridden by catalyst priority");

        selected = NatureAltarSelectionPolicy.select(3, false,
                index -> true,
                index -> index != 0,
                index -> false,
                2);
        require(selected == 0,
                "a selected recipe remained usable after its catalyst disappeared");

        require(NatureAltarSelectionPolicy.lockedRecipeRemainsValid(
                        true, true),
                "locked recipe was not retained for the same input");
        require(!NatureAltarSelectionPolicy.lockedRecipeRemainsValid(
                        false, true),
                "removed datapack recipe remained locked");
        require(!NatureAltarSelectionPolicy.lockedRecipeRemainsValid(
                        true, false),
                "locked recipe accepted a different main input");

        // The declared cost is consumed exactly at completion. SafeAuraContainer
        // separately tests the all-or-nothing resource transaction.
        require(NatureAuraMachineConfig.ALTAR_CAPACITY >= 135_000,
                "altar capacity cannot hold the largest 41.9 recipe cost");
        require(NatureAuraMachineConfig.AURA_RESOURCE.toString()
                        .equals("naturesaura:aura"),
                "Aura resource identity changed");

        // Keep factory semantics in the existing Nature altar verification
        // task without adding another Gradle task here.
        NatureAltarFactorySemanticsSelfTest.main(args);
        NatureAltarAutomationBatchPolicySelfTest.main(args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
