package com.example.mekanismmagic.integration.common.energy;

/**
 * Fixed Mekanism 10.7 default power tiers, in Joules per working tick.
 * These are a balance reference, not a live binding to another mod's user config.
 * Recipe durations, Source/Aura fees, production ratios and storage remain separate.
 */
public final class MagicMachineEnergyProfiles {
    /** Smelter, enrichment, crusher and metallurgic infuser default tier. */
    public static final int MATERIAL_PROCESSING = 50;
    /** Formulaic assemblicator default tier; also used for three-role smithing/encoding. */
    public static final int ASSEMBLY = 100;
    /** Purification/chemical infuser/oxidizer default tier, used as a functional analogy. */
    public static final int MAGIC_REACTION = 200;
    /** Chemical injection/crystallization tier for the multi-essence Hephaestus forge. */
    public static final int ADVANCED_RITUAL = 400;
    /** Digital miner base tier. Do not invent its range/silk-touch surcharges here. */
    public static final int RESOURCE_GATHERING = 1_000;

    private MagicMachineEnergyProfiles() {
    }
}
