package com.example.mekanismmagic.integration.arsnouveau;

/**
 * Source transport uses the same default capacity and active-pull curve as
 * Mekanism's mechanical pipes, expressed in Source units. Values stay separate
 * from Mekanism's fluid configuration so changing an mB setting cannot
 * silently alter Source flow.
 */
public final class MagicSourcePipeTierStats {
    private MagicSourcePipeTierStats() {
    }

    public static int capacity(MagicSourcePipeTier tier) {
        return tier.capacity();
    }

    public static int pullRate(MagicSourcePipeTier tier) {
        return tier.pullRate();
    }
}
