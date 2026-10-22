package com.example.mekanismmagic.integration.naturesaura;

/** Pure compatibility policy for persisted recipe-id/signature locks. */
public final class NatureAltarLockedContextPolicy {
    private NatureAltarLockedContextPolicy() {
    }

    /** Blank signatures are accepted once for migration from pre-M3 saves. */
    public static boolean remainsValid(
            boolean recipePresent, boolean inputMatches,
            String lockedSignature, String currentSignature) {
        if (!recipePresent || !inputMatches
                || currentSignature == null
                || currentSignature.isBlank()) {
            return false;
        }
        return lockedSignature == null || lockedSignature.isBlank()
                || lockedSignature.equals(currentSignature);
    }

    /** Keeps delivered exact-pattern inputs blocked after their signature dies. */
    public static boolean quarantineInvalid(
            boolean valid, int contextRemaining, int inputCount) {
        return !valid && contextRemaining > 0 && inputCount > 0;
    }
}
