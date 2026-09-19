package com.example.mekanismmagic.gametest;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Release-boundary checks, including runs with the excluded source mods installed. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FixReleaseScopeGameTests {
    private FixReleaseScopeGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void publishedMachineSetAndCaptureRecipeArePreserved(GameTestHelper helper) throws Exception {
        var baseline = baseline();
        Set<ResourceLocation> expected = StreamSupport.stream(baseline.getAsJsonArray("machineIds").spliterator(), false)
                .map(value -> ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, value.getAsString()))
                .collect(Collectors.toSet());
        Set<ResourceLocation> actual = BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> id.getNamespace().equals(MekanismMagic.MOD_ID)).collect(Collectors.toSet());
        helper.assertTrue(actual.equals(expected), "Maintenance machine set changed: expected=" + expected + ", actual=" + actual);
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "mini_ritual_blank")).isPresent(),
                "The explicitly retained blank capture recipe is missing");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void excludedIntegrationsCannotRegisterContent(GameTestHelper helper) throws Exception {
        if (Boolean.getBoolean("mekanism_magic.verify_excluded_mods")) {
            helper.assertTrue(ModList.get().isLoaded("naturesaura") && ModList.get().isLoaded("forbidden_arcanus")
                            && ModList.get().isLoaded("valhelsia_core"),
                    "Excluded-mod compatibility run did not actually load both source mods");
        }
        helper.assertTrue(!ModCompatibility.naturesAuraLoaded() && !ModCompatibility.forbiddenArcanusIntegrationEnabled(),
                "An excluded addon integration became enabled");
        var baseline = baseline();
        for (String array : new String[]{"excludedMachineIds", "excludedItems"}) {
            for (var entry : baseline.getAsJsonArray(array)) {
                var id = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, entry.getAsString());
                helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(id) && !BuiltInRegistries.ITEM.containsKey(id),
                        "Excluded maintenance content registered: " + id);
            }
        }
        for (String path : new String[]{"naturesaura/NaturesAuraContentModule", "forbiddenarcanus/ForbiddenArcanusContentModule",
                "ae2/storage/MagicStorageCells", "ae2/Ae2AuraInterfaceStorage"}) {
            helper.assertTrue(FixReleaseScopeGameTests.class.getClassLoader().getResource(
                    "com/example/mekanismmagic/integration/" + path + ".class") == null,
                    "Excluded integration class is still on the release classpath: " + path);
        }
        helper.succeed();
    }

    private static JsonObject baseline() throws Exception {
        try (var stream = FixReleaseScopeGameTests.class.getClassLoader().getResourceAsStream("fixtures/release-1.0.6-baseline.json")) {
            if (stream == null) throw new IllegalStateException("Release baseline fixture is missing");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
