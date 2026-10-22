package com.example.mekanismmagic.integration.arsnouveau;

/** Bounded AE-only fast refill gate; ordinary linked/nearby jar scan cadence is unchanged. */
public interface AeSourceFastPullHost {
    /** Zero while a bound jar has priority, Source is creative, or this tile cannot receive. */
    int mekanismMagicAeFastSourceBudget();
}
