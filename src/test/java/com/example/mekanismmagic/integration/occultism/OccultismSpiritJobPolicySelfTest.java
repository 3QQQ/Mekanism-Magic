package com.example.mekanismmagic.integration.occultism;

import java.util.LinkedHashMap;
import java.util.Map;

/** Offline assertions for the complete 1.222/1.224 SpiritJob matrix. */
public final class OccultismSpiritJobPolicySelfTest {
    private OccultismSpiritJobPolicySelfTest() {
    }

    public static void main(String[] args) {
        Map<String, Integer> jobs = new LinkedHashMap<>();
        add(jobs, 1, "cleaner", "crush_tier1", "crystal_tier1",
                "farmer", "lumberjack", "smelt_tier1",
                "trader_otherrock", "trader_otherstone",
                "trader_otherworld_saplings", "transport_items");
        add(jobs, 2, "clear_weather", "crush_tier2",
                "crystal_tier2", "day_time", "gambler",
                "manage_machine", "night_time", "smelt_tier2");
        add(jobs, 3, "crush_tier3", "crystal_tier3",
                "rain_weather", "smelt_tier3", "thunder_weather");
        add(jobs, 4, "crush_tier4", "crystal_tier4",
                "smelt_tier4");

        require(jobs.size() == 26,
                "test catalog must contain all 26 jobs");
        require(OccultismSpiritJobPolicy.knownJobCount() == jobs.size(),
                "policy must cover all 26 jobs");
        require(OccultismSpiritJobPolicy.executableJobCount() == 16
                        && OccultismSpiritJobPolicy
                        .recognizedWorldTaskCount() == 10,
                "machines execute 16 jobs and only recognize 10 world jobs");
        jobs.forEach((job, tier) -> {
            var profile = OccultismSpiritJobPolicy.profile(job)
                    .orElseThrow(() -> new AssertionError(
                            "missing job " + job));
            require(profile.entityTier() == tier,
                    "wrong tier for " + job);
        });

        for (int tier = 1; tier <= 4; tier++) {
            String crush = OccultismSpiritJobPolicy.jobFor(
                    "crushing", tier);
            String crystal = OccultismSpiritJobPolicy.jobFor(
                    "crystallize", tier);
            String smelt = OccultismSpiritJobPolicy.jobFor(
                    "smelting", tier);
            require(OccultismSpiritJobPolicy.permits(
                            crush, tier, "crushing"),
                    "crusher must crush at tier " + tier);
            require(!OccultismSpiritJobPolicy.permits(
                            crush, tier, "crystallize"),
                    "crusher must not crystallize at tier " + tier);
            require(OccultismSpiritJobPolicy.permits(
                            crystal, tier, "crystallize"),
                    "crystallizer must crystallize at tier " + tier);
            require(OccultismSpiritJobPolicy.permits(
                            smelt, tier, "blasting"),
                    "smelter must accept all cooking types at tier " + tier);
            require(!OccultismSpiritJobPolicy.permits(
                            smelt, tier == 4 ? 3 : tier + 1, "smelting"),
                    "mismatched entity tier must be rejected");
        }

        require(OccultismSpiritJobPolicy.entityTier("foliot") == 1
                        && OccultismSpiritJobPolicy.entityTier("djinni") == 2
                        && OccultismSpiritJobPolicy.entityTier("afrit") == 3
                        && OccultismSpiritJobPolicy.entityTier("marid") == 4,
                "bound spirit tiers must remain stable");
        require(OccultismSpiritJobPolicy.entityTier("afrit_wild") == 0
                        && OccultismSpiritJobPolicy.entityTier(
                        "marid_unbound") == 0,
                "hostile unbound entities must not be worker sources");
        require(OccultismSpiritJobPolicy.isGambler(
                        "occultism:trader_gem")
                        && OccultismSpiritJobPolicy.profile(
                        "occultism:trader_gem").isPresent(),
                "legacy trader_gem must migrate to gambler");
        require(OccultismSpiritJobPolicy.recipeOrder(
                        "occultism:farmer", 1).equals(
                        java.util.List.of("spirit_fire")),
                "world jobs may only use the addon spirit-fire fallback");
        require(OccultismSpiritJobPolicy.recipeOrder(
                        "occultism:smelt_tier4", 4).equals(
                        java.util.List.of("smelting", "blasting", "smoking",
                                "campfire_cooking", "spirit_fire")),
                "smelter recipe priority must match Occultism");

        int[] gamblerWeights = {16, 8, 8, 8, 4, 4, 2, 2, 1, 1, 1};
        int total = java.util.Arrays.stream(gamblerWeights).sum();
        require(total == 55, "gambler pool must retain weight 55");
        require(OccultismSpiritJobPolicy.weightedIndex(
                        gamblerWeights, 0) == 0
                        && OccultismSpiritJobPolicy.weightedIndex(
                        gamblerWeights, 15) == 0
                        && OccultismSpiritJobPolicy.weightedIndex(
                        gamblerWeights, 16) == 1
                        && OccultismSpiritJobPolicy.weightedIndex(
                        gamblerWeights, 54) == 10
                        && OccultismSpiritJobPolicy.weightedIndex(
                        gamblerWeights, 55) == -1,
                "weighted boundaries must be exhaustive and unbiased");
        require(OccultismSpiritJobPolicy.singleTradeDuration(
                        200, 16) == 13
                        && OccultismSpiritJobPolicy.singleTradeDuration(
                        10, 4) == 3
                        && OccultismSpiritJobPolicy.singleTradeDuration(
                        1, 64) == 1,
                "single weighted draws must preserve configured throughput");
        require(!OccultismSpiritJobPolicy.requiresRandomTrade(1)
                        && OccultismSpiritJobPolicy
                        .requiresRandomTrade(2),
                "weighted trade randomness depends on matching candidates");
        testPatternContract();
    }

    private static void testPatternContract() {
        require(com.example.mekanismmagic.api.IMekanismMagicAutomation
                        .API_VERSION >= 5,
                "contextual pattern validation requires automation API v5");
        try {
            Class<?> api = com.example.mekanismmagic.api
                    .IMekanismMagicAutomation.class;
            api.getMethod("mekanismMagicUsesContextualPatternValidation");
            api.getMethod("mekanismMagicMatchesPattern",
                    java.util.List.class, java.util.List.class);
        } catch (ReflectiveOperationException missingContract) {
            throw new AssertionError(
                    "contextual pattern API methods must remain available",
                    missingContract);
        }
    }

    private static void add(Map<String, Integer> target, int tier,
                            String... paths) {
        for (String path : paths) {
            target.put("occultism:" + path, tier);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
