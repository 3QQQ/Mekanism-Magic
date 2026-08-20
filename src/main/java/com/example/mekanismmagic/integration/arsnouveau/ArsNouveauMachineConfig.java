package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.resources.ResourceLocation;

/**
 * Balance constants shared by the Ars Nouveau machine integration.
 */
public final class ArsNouveauMachineConfig {
    public static final ResourceLocation SOURCE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source");
    public static final int SOURCE_CAPACITY = 10_000;
    public static final int SOURCE_TRANSFER_RATE = 1_000;
    public static final int SOURCE_AMPLIFICATION_RADIUS = 4;
    public static final int SOURCE_AMPLIFICATION_DURATION = 20;
    public static final int RAW_SOURCE_PER_OPERATION = 100;
    public static final int AMPLIFIED_SOURCE_PER_OPERATION = 150;
    public static final long FE_PER_SOURCE = 200L;
    public static final int IMBUEMENT_DURATION = 100;
    public static final int APPARATUS_DURATION = 100;

    private ArsNouveauMachineConfig() {
    }
}
