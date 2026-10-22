package com.example.mekanismmagic.integration.naturesaura;

import java.util.function.IntPredicate;

/** Pure selection rules shared by the runtime bridge and offline tests. */
final class NatureAltarSelectionPolicy {
    private NatureAltarSelectionPolicy() {
    }

    static int select(int recipeCount, boolean catalystPresent,
                      IntPredicate inputMatches,
                      IntPredicate requiresCatalyst,
                      IntPredicate catalystMatches) {
        if (recipeCount <= 0) {
            return -1;
        }
        if (catalystPresent) {
            for (int index = 0; index < recipeCount; index++) {
                if (inputMatches.test(index)
                        && requiresCatalyst.test(index)
                        && catalystMatches.test(index)) {
                    return index;
                }
            }
        }
        for (int index = 0; index < recipeCount; index++) {
            if (inputMatches.test(index)
                    && !requiresCatalyst.test(index)) {
                return index;
            }
        }
        return -1;
    }

    static int select(int recipeCount, boolean catalystPresent,
                      IntPredicate inputMatches,
                      IntPredicate requiresCatalyst,
                      IntPredicate catalystMatches,
                      int preferredIndex) {
        if (preferredIndex >= 0 && preferredIndex < recipeCount
                && inputMatches.test(preferredIndex)
                && (!requiresCatalyst.test(preferredIndex)
                || catalystPresent && catalystMatches.test(preferredIndex))) {
            return preferredIndex;
        }
        return select(recipeCount, catalystPresent, inputMatches,
                requiresCatalyst, catalystMatches);
    }

    static boolean lockedRecipeRemainsValid(
            boolean recipeExists, boolean inputMatches) {
        return recipeExists && inputMatches;
    }
}
