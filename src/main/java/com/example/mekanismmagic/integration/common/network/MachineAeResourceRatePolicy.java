package com.example.mekanismmagic.integration.common.network;

/** Pure, bounded refill budgets; local capability simulation is still authoritative. */
public final class MachineAeResourceRatePolicy {
    public static final long MAX_ENERGY_J_PER_TICK = 20_000_000L;
    public static final int MAX_AURA_PER_TICK = 250_000;
    private static final long MIN_ENERGY_J_PER_TICK = 2_048L;

    private MachineAeResourceRatePolicy() { }

    public static long energyBudget(long localCapacityJ, long freeJ,
                                    long nominalJPerTick, int parallelLanes) {
        if (localCapacityJ <= 0L || freeJ <= 0L) return 0L;
        long window = ceilDivide(localCapacityJ, 40L);
        long work = multiplyClamped(Math.max(0L, nominalJPerTick),
                Math.max(1, parallelLanes));
        work = multiplyClamped(work, 4L);
        return Math.min(Math.min(freeJ, MAX_ENERGY_J_PER_TICK),
                Math.max(MIN_ENERGY_J_PER_TICK,
                        Math.max(window, work)));
    }

    public static int auraBudget(int localCapacity, int free) {
        if (localCapacity <= 0 || free <= 0) return 0;
        long window = ceilDivide(localCapacity, 20L);
        return (int) Math.min(Math.min((long) free,
                MAX_AURA_PER_TICK), Math.max(1L, window));
    }

    private static long ceilDivide(long value, long divisor) {
        return 1L + (value - 1L) / divisor;
    }

    private static long multiplyClamped(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right
                ? Long.MAX_VALUE : left * right;
    }
}
