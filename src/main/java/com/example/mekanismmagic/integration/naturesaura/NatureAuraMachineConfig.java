package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import net.minecraft.resources.ResourceLocation;
import mekanism.common.tier.FactoryTier;

/** Fixed first-stage values; configurable server options can follow testing. */
public final class NatureAuraMachineConfig {
    static final ResourceLocation AURA_RESOURCE =
            ResourceLocation.fromNamespaceAndPath("naturesaura", "aura");
    static final int FIELD_CAPACITY = 500_000;
    static final int FIELD_TRANSFER = 5_000;
    static final int FIELD_INTERVAL = 20;
    static final int FIELD_MAX_BACKOFF = 200;
    static final int FIELD_BALANCE_DEAD_ZONE = 25_000;
    static final int FIELD_RADIUS = 20;
    /** Actual Forge Energy per transferred Aura, before energy upgrades. */
    static final int FIELD_FE_PER_AURA = 20;
    static final long ENERGY_CAPACITY = 4_000_000L;
    static final int ALTAR_CAPACITY = 500_000;
    static final int ALTAR_TRANSFER = 5_000;
    public static final int ALTAR_BASE_ENERGY_PER_TICK = MagicMachineEnergyProfiles.MATERIAL_PROCESSING;
    static final long ALTAR_ENERGY_CAPACITY = 2_000_000L;
    // Offering has no Aura cost. FE only runs its automated trigger/control.
    static final int OFFERING_BASE_ENERGY_PER_TICK = MagicMachineEnergyProfiles.ASSEMBLY;
    static final long OFFERING_ENERGY_CAPACITY = 2_000_000L;
    static final int OFFERING_TRIGGER_INTERVAL = 20;
    static final int OFFERING_OUTPUT_INTERVAL = 3;
    static final int TREE_RITUAL_BASE_ENERGY_PER_TICK = MagicMachineEnergyProfiles.MAGIC_REACTION;

    public static final int GENERATOR_AURA_CAPACITY = 4_000_000;
    public static final int GENERATOR_AURA_TRANSFER = 64_000;
    public static final int FE_CONVERTER_AURA_PER_TICK = 250;
    public static final int FE_CONVERTER_FE_PER_AURA = 20;
    public static final int FE_CONVERTER_BASE_FE_PER_TICK =
            FE_CONVERTER_AURA_PER_TICK * FE_CONVERTER_FE_PER_AURA;
    public static final long FE_CONVERTER_ENERGY_CAPACITY = 4_000_000L;

    static int altarFactoryCapacity(FactoryTier tier) {
        return altarFactoryCapacity(tier.processes);
    }

    static int altarFactoryTransfer(FactoryTier tier) {
        return altarFactoryTransfer(tier.processes);
    }

    static long altarFactoryEnergyCapacity(FactoryTier tier) {
        return altarFactoryEnergyCapacity(tier.processes);
    }

    public static int altarFactoryCapacity(int processes) {
        return Math.multiplyExact(ALTAR_CAPACITY, processes);
    }

    public static int altarFactoryTransfer(int processes) {
        return Math.multiplyExact(ALTAR_TRANSFER, processes);
    }

    public static long altarFactoryEnergyCapacity(int processes) {
        return Math.multiplyExact(ALTAR_ENERGY_CAPACITY, (long) processes);
    }

    private NatureAuraMachineConfig() {
    }
}
