package com.example.mekanismmagic.integration.naturesaura;

/** Pure counter rules for exact recipe context batches. */
public final class NatureAltarContextCountPolicy {
    private NatureAltarContextCountPolicy() {
    }

    public static int onAccepted(int current, int added) {
        if (current < 0 || added <= 0) {
            return -1;
        }
        try {
            return Math.addExact(current, added);
        } catch (ArithmeticException overflow) {
            return -1;
        }
    }

    public static int afterCompletion(int current) {
        return current <= 0 ? 0 : current - 1;
    }

    public static int clampToInput(int current, int inputCount) {
        return Math.min(Math.max(0, current), Math.max(0, inputCount));
    }
}
