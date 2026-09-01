package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.resources.ResourceLocation;

/**
 * Balance constants shared by the Ars Nouveau machine integration.
 */
public final class ArsNouveauMachineConfig {
    public static final ResourceLocation SOURCE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source");
    /** Shared internal tank for every Source-capable machine and factory. */
    public static final int SOURCE_CAPACITY = 100_000;
    /** Keeps the original 10:1 capacity-to-transfer ratio after expansion. */
    public static final int SOURCE_TRANSFER_RATE = 10_000;
    public static final int NEARBY_SOURCE_PULL_INTERVAL = 20;
    public static final int SOURCE_INTERACTION_RADIUS = 10;
    public static final int IMBUEMENT_SOURCE_INTERACTION_RADIUS = 2;
    public static final int SOURCE_AMPLIFICATION_RADIUS = 4;
    public static final int SOURCE_AMPLIFICATION_DURATION = 20;
    public static final int RAW_SOURCE_PER_OPERATION = 100;
    public static final int AMPLIFIED_SOURCE_PER_OPERATION = 150;
    public static final int SOURCE_AMPLIFICATION_SPEED_BONUS_PERCENT = 10;
    public static final long IMBUEMENT_FACTORY_FE_PER_TICK = 600L;
    public static final int ENERGYLESS_TICK_INTERVAL = 5;
    public static final int SOURCE_CONVERTER_DURATION = 100;
    // SOURCE_CAPACITY was expanded from the original 10k design to 100k.
    // Scale one conversion batch and its FE cost together so filling a base
    // converter still takes the intended ~500 seconds while preserving the
    // 500 FE / Source conversion ratio.
    public static final int SOURCE_CONVERTER_FE_PER_TICK = 5_000;
    public static final long SOURCE_CONVERTER_ENERGY_CAPACITY = 40_000_000L;
    public static final int SOURCE_CONVERTER_SOURCE_PER_OPERATION = 1_000;
    public static final int IMBUEMENT_DURATION = 100;
    public static final int APPARATUS_DURATION = 100;
    /** Machine apparatus always requires some Source, even for legacy Ars recipes that declare zero. */
    public static final int APPARATUS_MINIMUM_SOURCE_COST = 100;

    public static int apparatusSourceCost(int declaredCost) {
        return Math.max(APPARATUS_MINIMUM_SOURCE_COST, declaredCost);
    }

    private ArsNouveauMachineConfig() {
    }
}
