package com.example.mekanismmagic.integration.occultism;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stable mapping between Occultism spirit jobs and machine work modes. */
final class OccultismSpiritJobPolicy {
    static final String OCCULTISM = "occultism:";

    enum WorkMode {
        CRUSHING,
        CRYSTALLIZE,
        SMELTING,
        TRADE,
        WORLD_TASK
    }

    record Profile(WorkMode mode, int entityTier) {
    }

    private static final Map<String, Profile> PROFILES = createProfiles();

    private OccultismSpiritJobPolicy() {
    }

    static Optional<Profile> profile(String jobId) {
        return Optional.ofNullable(PROFILES.get(normalizeJobId(jobId)));
    }

    static int knownJobCount() {
        return PROFILES.size();
    }

    static int executableJobCount() {
        return (int) PROFILES.values().stream()
                .filter(profile -> profile.mode() != WorkMode.WORLD_TASK)
                .count();
    }

    static int recognizedWorldTaskCount() {
        return (int) PROFILES.values().stream()
                .filter(profile -> profile.mode() == WorkMode.WORLD_TASK)
                .count();
    }

    static int entityTier(String entityPath) {
        return switch (entityPath == null ? "" : entityPath) {
            case "foliot" -> 1;
            case "djinni" -> 2;
            case "afrit" -> 3;
            case "marid" -> 4;
            default -> 0;
        };
    }

    static List<String> recipeOrder(String jobId, int entityTier) {
        Optional<Profile> profile = profile(jobId)
                .filter(value -> value.entityTier() == entityTier);
        if (profile.isEmpty()) {
            return List.of("spirit_fire");
        }
        return switch (profile.get().mode()) {
            case CRUSHING -> List.of("crushing", "spirit_fire");
            case CRYSTALLIZE -> List.of("crystallize", "spirit_fire");
            case SMELTING -> List.of("smelting", "blasting",
                    "smoking", "campfire_cooking", "spirit_fire");
            case TRADE -> List.of("spirit_trade", "spirit_fire");
            case WORLD_TASK -> List.of("spirit_fire");
        };
    }

    static boolean permits(String jobId, int entityTier,
                           String recipeType) {
        if ("spirit_fire".equals(recipeType)) {
            return entityTier >= 1 && entityTier <= 4;
        }
        Optional<Profile> profile = profile(jobId)
                .filter(value -> value.entityTier() == entityTier);
        if (profile.isEmpty()) {
            return false;
        }
        return switch (recipeType) {
            case "crushing" -> profile.get().mode() == WorkMode.CRUSHING;
            case "crystallize" ->
                    profile.get().mode() == WorkMode.CRYSTALLIZE;
            case "smelting", "blasting", "smoking",
                 "campfire_cooking" ->
                    profile.get().mode() == WorkMode.SMELTING;
            case "spirit_trade" -> profile.get().mode() == WorkMode.TRADE;
            default -> false;
        };
    }

    static String jobFor(String recipeType, int tier) {
        if (tier < 1 || tier > 4) {
            return "";
        }
        return switch (recipeType) {
            case "crushing" -> OCCULTISM + "crush_tier" + tier;
            case "crystallize" -> OCCULTISM + "crystal_tier" + tier;
            case "smelting", "blasting", "smoking",
                 "campfire_cooking" ->
                    OCCULTISM + "smelt_tier" + tier;
            default -> "";
        };
    }

    static boolean isTrader(String jobId) {
        return profile(jobId)
                .map(Profile::mode)
                .filter(mode -> mode == WorkMode.TRADE)
                .isPresent();
    }

    static boolean isGambler(String jobId) {
        return (OCCULTISM + "gambler").equals(normalizeJobId(jobId));
    }

    static String traderConfigField(String jobId) {
        return switch (normalizeJobId(jobId)) {
            case OCCULTISM + "trader_otherworld_saplings" ->
                    "traderSapling";
            case OCCULTISM + "trader_otherstone" ->
                    "traderOtherstone";
            case OCCULTISM + "trader_otherrock" ->
                    "traderOtherrock";
            case OCCULTISM + "gambler" -> "traderGem";
            default -> "";
        };
    }

    static String normalizeJobId(String jobId) {
        return (OCCULTISM + "trader_gem").equals(jobId)
                ? OCCULTISM + "gambler"
                : jobId == null ? "" : jobId;
    }

    static int weightedIndex(int[] weights, int roll) {
        if (weights == null || roll < 0) {
            return -1;
        }
        for (int index = 0; index < weights.length; index++) {
            int weight = Math.max(0, weights[index]);
            if (roll < weight) {
                return index;
            }
            roll -= weight;
        }
        return -1;
    }

    /**
     * Converts Occultism's batched trader round into one independent draw.
     * Rounding upward avoids making the machine faster than the configured
     * average throughput, while keeping every completed item as a new roll.
     */
    static int singleTradeDuration(int roundTicks, int tradesPerRound) {
        long ticks = Math.max(1L, roundTicks);
        long trades = Math.max(1L, tradesPerRound);
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, (ticks + trades - 1L) / trades));
    }

    static boolean requiresRandomTrade(int matchingCandidates) {
        return matchingCandidates > 1;
    }

    private static Map<String, Profile> createProfiles() {
        Map<String, Profile> profiles = new LinkedHashMap<>();
        for (int tier = 1; tier <= 4; tier++) {
            profiles.put(OCCULTISM + "crush_tier" + tier,
                    new Profile(WorkMode.CRUSHING, tier));
            profiles.put(OCCULTISM + "crystal_tier" + tier,
                    new Profile(WorkMode.CRYSTALLIZE, tier));
            profiles.put(OCCULTISM + "smelt_tier" + tier,
                    new Profile(WorkMode.SMELTING, tier));
        }

        add(profiles, 1, WorkMode.WORLD_TASK,
                "cleaner", "farmer", "lumberjack", "transport_items");
        add(profiles, 1, WorkMode.TRADE,
                "trader_otherrock", "trader_otherstone",
                "trader_otherworld_saplings");
        add(profiles, 2, WorkMode.WORLD_TASK,
                "clear_weather", "day_time", "manage_machine",
                "night_time");
        add(profiles, 2, WorkMode.TRADE, "gambler");
        add(profiles, 3, WorkMode.WORLD_TASK,
                "rain_weather", "thunder_weather");
        return Map.copyOf(profiles);
    }

    private static void add(Map<String, Profile> profiles, int tier,
                            WorkMode mode, String... paths) {
        for (String path : paths) {
            profiles.put(OCCULTISM + path, new Profile(mode, tier));
        }
    }
}
