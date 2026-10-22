package com.example.mekanismmagic.integration.naturesaura;

/** Unit-explicit arithmetic for the field interface and the spawn interval floor. */
public final class NatureMachineEnergyMath {
    private NatureMachineEnergyMath() {
    }

    /** Convert the whole committed FE batch once; a fractional Joule must never become free. */
    public static long fieldBatchJoules(int aura, long fePerAura, double joulesPerFe) {
        if (aura <= 0) {
            return 0;
        }
        if (fePerAura <= 0 || !Double.isFinite(joulesPerFe) || joulesPerFe <= 0) {
            return Long.MAX_VALUE;
        }
        return ceilPositive(aura * (double) fePerAura * joulesPerFe);
    }

    /** Maximum whole Aura amount whose single rounded batch fits the available Joules. */
    public static int affordableFieldAura(int requested, long availableJoules,
                                           long fePerAura, double joulesPerFe) {
        int low = 0;
        int high = Math.max(0, requested);
        while (low < high) {
            int candidate = low + (int) (((long) high - low + 1) / 2);
            long cost = fieldBatchJoules(candidate, fePerAura, joulesPerFe);
            if (cost < Long.MAX_VALUE && cost <= availableJoules) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    public static long averageFieldJoulesPerTick(int batchAura, int interval,
                                                 long fePerAura, double joulesPerFe) {
        if (interval <= 0) {
            return Long.MAX_VALUE;
        }
        long batch = fieldBatchJoules(batchAura, fePerAura, joulesPerFe);
        return batch / interval + (batch % interval == 0 ? 0 : 1);
    }

    public static boolean isAnimalSpeedCapped(int nativeTicks, int minimumTicks, double speedMultiplier) {
        return speedMultiplier > Math.max(1D, nativeTicks / (double) minimumTicks);
    }

    /**
     * Price the minimum-interval cap only. Uncapped work must use Mekanism's original
     * energy formula, rather than squaring an already-rounded speed multiplier.
     */
    public static long cappedAnimalJoulesPerTick(long baseJoulesPerTick, int nativeTicks,
                                                  int minimumTicks, double energyReduction) {
        if (baseJoulesPerTick <= 0) {
            return 0;
        }
        if (nativeTicks <= 0 || minimumTicks <= 0
                || !Double.isFinite(energyReduction) || energyReduction <= 0) {
            return Long.MAX_VALUE;
        }
        double power = baseJoulesPerTick;
        if (nativeTicks > minimumTicks) {
            // Multiply integer durations before dividing: (22 / 20.0)^2 introduces
            // a floating-point tail that would wrongly ceil an exact 726 J to 727 J.
            power = baseJoulesPerTick * (double) nativeTicks * nativeTicks
                    / ((double) minimumTicks * minimumTicks);
        }
        return ceilPositive(power / Math.max(1D, energyReduction));
    }

    private static long ceilPositive(double joules) {
        if (!Double.isFinite(joules) || joules >= Long.MAX_VALUE || joules <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(joules));
    }
}
