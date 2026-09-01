package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.common.tier.PipeTier;

/**
 * Complete Source-pipe progression. Capacity and pull values intentionally
 * follow the default Mechanical Pipe parameters from Mekanism and Mekanism
 * Extras. The values use Source units instead of mB, but keep the original
 * tier curve and per-segment semantics.
 */
public enum MagicSourcePipeTier {
    BASIC(PipeTier.BASIC, 2_000, 250),
    ADVANCED(PipeTier.ADVANCED, 8_000, 1_000),
    ELITE(PipeTier.ELITE, 32_000, 8_000),
    ULTIMATE(PipeTier.ULTIMATE, 128_000, 32_000),
    ABSOLUTE(PipeTier.BASIC, 1_024_000, 256_000),
    SUPREME(PipeTier.ADVANCED, 8_192_000, 2_048_000),
    COSMIC(PipeTier.ELITE, 65_536_000, 16_384_000),
    INFINITE(PipeTier.ULTIMATE, 524_288_000, 131_072_000);

    private final PipeTier mekanismTier;
    private final int capacity;
    private final int pullRate;

    MagicSourcePipeTier(PipeTier mekanismTier, int capacity,
                        int pullRate) {
        this.mekanismTier = mekanismTier;
        this.capacity = capacity;
        this.pullRate = pullRate;
    }

    public PipeTier mekanismTier() {
        return mekanismTier;
    }

    public int capacity() {
        return capacity;
    }

    public int pullRate() {
        return pullRate;
    }

    public boolean isExtendedTier() {
        return ordinal() > ULTIMATE.ordinal();
    }
}
