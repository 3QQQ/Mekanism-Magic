package com.example.mekanismmagic.integration.occultism;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Declares how each stable Occultism ritual implementation becomes a machine
 * result. Keeping this table shared prevents JEI and the ritual engine from
 * silently supporting different recipe subsets.
 */
enum OccultismRitualMachineMode {
    STATIC_ITEM,
    ENTITY,
    JOB_ENTITY,
    OWNED_ENTITY,
    NAMED_ITEM,
    REPAIR_ITEM,
    RESURRECT_ENTITY,
    CHANCE_ENTITY,
    MINER_ITEM,
    UPGRADE_ITEM;

    private static final String OCCULTISM = "occultism";
    private static final Map<ResourceLocation, OccultismRitualMachineMode>
            MODES = Map.ofEntries(
            entry("craft", STATIC_ITEM),
            entry("summon", ENTITY),
            entry("summon_spirit_with_job", JOB_ENTITY),
            entry("summon_tamed", OWNED_ENTITY),
            entry("summon_wild", ENTITY),
            entry("craft_with_spirit_name", NAMED_ITEM),
            entry("repair", REPAIR_ITEM),
            entry("resurrect_familiar", RESURRECT_ENTITY),
            entry("summon_with_chance_of_chicken_tamed", CHANCE_ENTITY),
            entry("craft_miner_spirit", MINER_ITEM),
            entry("upgrade", UPGRADE_ITEM));

    static Optional<OccultismRitualMachineMode> from(
            ResourceLocation ritualType) {
        return Optional.ofNullable(MODES.get(ritualType));
    }

    static int supportedTypeCount() {
        return MODES.size();
    }

    static int durationTicks(int durationSeconds) {
        return durationTicks(durationSeconds, 1D);
    }

    static int durationTicks(int durationSeconds,
                             double durationMultiplier) {
        if (durationSeconds < 0) {
            return Integer.MAX_VALUE;
        }
        // The original sacrificial bowl advances ritual time once per
        // floor(20 * multiplier) game ticks. Mirror that cadence exactly,
        // while defending against malformed third-party config values.
        double safeMultiplier = OccultismRitualConfig.sanitize(
                durationMultiplier);
        long interval = Math.max(1L, Math.min(Integer.MAX_VALUE,
                (long) (20D * safeMultiplier)));
        long seconds = Math.max(1L, durationSeconds);
        long ticks = seconds > Long.MAX_VALUE / interval
                ? Long.MAX_VALUE : seconds * interval;
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, ticks));
    }

    private static Map.Entry<ResourceLocation,
            OccultismRitualMachineMode> entry(
            String path, OccultismRitualMachineMode mode) {
        return Map.entry(ResourceLocation.fromNamespaceAndPath(
                OCCULTISM, path), mode);
    }
}
