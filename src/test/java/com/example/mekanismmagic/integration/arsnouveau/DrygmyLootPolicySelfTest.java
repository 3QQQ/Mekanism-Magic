package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Offline assertions for filtering and special Drygmy loot candidates. */
public final class DrygmyLootPolicySelfTest {
    private DrygmyLootPolicySelfTest() {
    }

    public static void main(String[] args) throws Exception {
        filtersEquipmentBeforeSelection();
        preservesOrdinaryAndSpecialMaterials();
        honorsPolicyPrecedence();
        scalesFixedDropsOnlyByOperationCount();
        verifiesDefaultEntityMappings();
    }

    private static void filtersEquipmentBeforeSelection() {
        check(!DrygmyLootPolicy.shouldKeepClassification(
                        false, false, false, true, false, false),
                "A damageable item survived candidate filtering");
        check(!DrygmyLootPolicy.shouldKeepClassification(
                        false, false, false, false, true, false),
                "A common-tagged tool survived candidate filtering");
        check(!DrygmyLootPolicy.shouldKeepClassification(
                        false, false, false, false, false, true),
                "Common-tagged armor survived candidate filtering");
    }

    private static void preservesOrdinaryAndSpecialMaterials() {
        check(DrygmyLootPolicy.shouldKeepClassification(
                        false, false, false, false, false, false),
                "An ordinary third-party material was rejected");
    }

    private static void honorsPolicyPrecedence() {
        check(!DrygmyLootPolicy.shouldKeepClassification(
                        false, true, true, true, false, false),
                "The explicit blacklist must win over the allow tag");
        check(DrygmyLootPolicy.shouldKeepClassification(
                        false, false, true, true, true, true),
                "The damageable allow tag did not override gear heuristics");
        check(!DrygmyLootPolicy.shouldKeepClassification(
                        false, false, false, true, false, false),
                "An unapproved damageable item survived filtering");
        check(DrygmyLootPolicy.shouldKeepClassification(
                        false, false, false, false, false, false),
                "An ordinary third-party material was rejected");
    }

    private static void scalesFixedDropsOnlyByOperationCount() {
        check(DrygmyLootPolicy.fixedDropCount(1, 1) == 1,
                "A normal operation did not produce one fixed drop");
        check(DrygmyLootPolicy.fixedDropCount(4, 1) == 4,
                "Fixed drops did not follow the stack-operation count");
        check(DrygmyLootPolicy.fixedDropCount(4, 2) == 8,
                "Distinct matching entity types were not counted once each");
        check(DrygmyLootPolicy.fixedDropCount(
                        Integer.MAX_VALUE, 1) == 256,
                "Fixed drops were not capped at the supported multiplier");
        check(DrygmyLootPolicy.fixedDropCount(4, 0) == 0,
                "A non-matching entity type produced a fixed drop");
    }

    private static void verifiesDefaultEntityMappings() throws IOException {
        checkResource("nether_star", "minecraft:wither");
        checkResource("wilden_tribute", "ars_nouveau:wilden_boss");
        check(DrygmyLootPolicy.hasFixedDropMapping(
                        "drygmy_special_loot/nether_star",
                        ResourceLocation.withDefaultNamespace("nether_star")),
                "Wither fixed-drop mapping did not resolve to Nether Star");
        check(DrygmyLootPolicy.hasFixedDropMapping(
                        "drygmy_special_loot/wilden_tribute",
                        ResourceLocation.fromNamespaceAndPath(
                                "ars_nouveau", "wilden_tribute")),
                "Wilden fixed-drop mapping did not resolve to its tribute");
        String blacklist = readResource("data/mekanism_magic/tags/item/"
                + "drygmy_loot_blacklist.json");
        check(blacklist.contains("\"minecraft:saddle\"")
                        && blacklist.contains(
                        "\"minecraft:diamond_horse_armor\"")
                        && blacklist.contains("\"minecraft:wolf_armor\""),
                "Built-in non-damageable equipment blacklist is incomplete");
        readResource("data/mekanism_magic/tags/item/"
                + "drygmy_allow_damageable_loot.json");
    }

    private static void checkResource(String name, String entityId)
            throws IOException {
        String contents = readResource("data/mekanism_magic/tags/"
                + "entity_type/drygmy_special_loot/" + name + ".json");
        check(contents.contains('"' + entityId + '"'),
                "Missing default special-loot mapping for " + entityId);
    }

    private static String readResource(String path) throws IOException {
        ClassLoader loader = DrygmyLootPolicySelfTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            check(stream != null, "Missing policy resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
