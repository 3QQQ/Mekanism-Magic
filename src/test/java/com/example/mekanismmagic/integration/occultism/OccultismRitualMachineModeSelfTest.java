package com.example.mekanismmagic.integration.occultism;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Ensures JEI and machine routing retain every stable Occultism ritual type. */
public final class OccultismRitualMachineModeSelfTest {
    private OccultismRitualMachineModeSelfTest() {
    }

    public static void main(String[] args) {
        Map<String, OccultismRitualMachineMode> expected = Map.ofEntries(
                Map.entry("craft",
                        OccultismRitualMachineMode.STATIC_ITEM),
                Map.entry("summon",
                        OccultismRitualMachineMode.ENTITY),
                Map.entry("summon_spirit_with_job",
                        OccultismRitualMachineMode.JOB_ENTITY),
                Map.entry("summon_tamed",
                        OccultismRitualMachineMode.OWNED_ENTITY),
                Map.entry("summon_wild",
                        OccultismRitualMachineMode.ENTITY),
                Map.entry("craft_with_spirit_name",
                        OccultismRitualMachineMode.NAMED_ITEM),
                Map.entry("repair",
                        OccultismRitualMachineMode.REPAIR_ITEM),
                Map.entry("resurrect_familiar",
                        OccultismRitualMachineMode.RESURRECT_ENTITY),
                Map.entry("summon_with_chance_of_chicken_tamed",
                        OccultismRitualMachineMode.CHANCE_ENTITY),
                Map.entry("craft_miner_spirit",
                        OccultismRitualMachineMode.MINER_ITEM),
                Map.entry("upgrade",
                        OccultismRitualMachineMode.UPGRADE_ITEM));
        require(OccultismRitualMachineMode.supportedTypeCount()
                        == expected.size(),
                "ritual mode registry size changed");
        expected.forEach((path, mode) -> require(
                OccultismRitualMachineMode.from(
                        ResourceLocation.fromNamespaceAndPath(
                                "occultism", path)).orElseThrow() == mode,
                "missing ritual machine mode: " + path));
        require(OccultismRitualMachineMode.from(
                ResourceLocation.fromNamespaceAndPath(
                        "example", "unknown_effect")).isEmpty(),
                "unknown ritual effect was treated as a static craft");
        require(OccultismRitualMachineMode.durationTicks(5) == 100
                        && OccultismRitualMachineMode.durationTicks(780)
                        == 15_600
                        && OccultismRitualMachineMode.durationTicks(-1)
                        == Integer.MAX_VALUE,
                "ritual seconds were not converted to bounded game ticks");
        require(OccultismRitualMachineMode.durationTicks(5, 0.5D) == 50
                        && OccultismRitualMachineMode.durationTicks(
                        1, 0.1D) == 2
                        && OccultismRitualMachineMode.durationTicks(
                        5, 1.25D) == 125
                        && OccultismRitualMachineMode.durationTicks(
                        5, Double.NaN) == 100,
                "ritual duration multiplier was not mirrored safely");
        require(OccultismEntityContainerAdapter.survivesRelease("soul_gem")
                        && OccultismEntityContainerAdapter.survivesRelease(
                        "trinity_gem")
                        && !OccultismEntityContainerAdapter.survivesRelease(
                        "fragile_soul_gem"),
                "fragile soul gem remainder policy changed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
