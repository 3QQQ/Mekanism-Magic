package com.example.mekanismmagic.integration.common.energy;

/** Transient two-tick debit ledger. Never infer work from animation or net battery delta. */
public final class ProcessingEnergyUsage {
    private boolean initialized;
    private long tick;
    private long current;
    private long completed;

    public void recordExtraction(long now, long amount) {
        if (amount <= 0) return;
        advance(now);
        current = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
    }

    /** Refund of an operation reserved in this same tick; ordinary charging is NOT a refund. */
    public void recordRefund(long now, long amount) {
        if (amount <= 0) return;
        advance(now);
        current = amount >= current ? 0 : current - amount;
    }

    /** A complete world tick avoids tile/event/menu ordering affecting the displayed sample. */
    public long lastCompletedTick(long now) {
        advance(now);
        return completed;
    }

    public void clear() {
        initialized = false;
        tick = current = completed = 0;
    }

    private void advance(long now) {
        if (initialized && now == tick) return;
        completed = initialized && tick != Long.MAX_VALUE && now == tick + 1 ? current : 0;
        current = 0;
        tick = now;
        initialized = true;
    }
}
