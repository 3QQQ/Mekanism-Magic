package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge.SpiritJeiData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Installed native recipe data coverage; presentation-only grouping must conserve every original row. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritJeiGroupingGameTests {
    private SpiritJeiGroupingGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void everyNativeRecipeAppearsInExactlyOneProfession(GameTestHelper helper) {
        var rows = OccultismRecipeBridge.spiritJeiRecipes(helper.getLevel());
        helper.assertTrue(!rows.isEmpty(), "Occultism recipe catalog is required for this test");
        var savedSpirits = rows.stream().map(row -> row.spirit().copy()).toList();
        var savedOutputs = rows.stream().map(row -> row.output().copy()).toList();
        var savedInputs = rows.stream().map(row -> List.of(row.input().getItems()).stream().map(ItemStack::copy).toList()).toList();
        var groups = SpiritRecipeGroup.partition(rows);
        Set<SpiritJeiData> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var group : SpiritRecipeGroup.values()) {
            var grouped = groups.get(group);
            if (group == SpiritRecipeGroup.OTHER) helper.assertTrue(grouped.isEmpty(), "Known native professions fell into fallback");
            else helper.assertTrue(!grouped.isEmpty(), "Missing native recipes for " + group);
            for (var row : grouped) {
                helper.assertTrue(seen.add(row), "Recipe registered twice: " + row.recipeId());
                helper.assertTrue(SpiritRecipeGroup.classify(row) == group, "Recipe on the wrong profession page");
            }
            MekanismMagic.LOGGER.info("Spirit JEI profession {}: {} native display rows", group, grouped.size());
        }
        helper.assertTrue(seen.size() == rows.size(), "Grouping lost native recipes");
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            helper.assertTrue(ItemStack.matches(row.spirit(), savedSpirits.get(index))
                    && ItemStack.matches(row.output(), savedOutputs.get(index)), "Grouping changed native stack data");
            var inputs = row.input().getItems();
            helper.assertTrue(inputs.length == savedInputs.get(index).size(), "Grouping changed ingredient choices");
            for (int choice = 0; choice < inputs.length; choice++) helper.assertTrue(ItemStack.matches(inputs[choice], savedInputs.get(index).get(choice)), "Grouping changed an input");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void allSixteenJobsAndFourTiersRetainDistinctDisplayIds(GameTestHelper helper) {
        var rows = OccultismRecipeBridge.spiritJeiRecipes(helper.getLevel());
        Set<ResourceLocation> displayIds = new HashSet<>();
        Set<String> jobs = new HashSet<>();
        Map<SpiritRecipeGroup, Set<Integer>> tiers = new HashMap<>();
        Map<ResourceLocation, Set<Integer>> fireRecipeTiers = new HashMap<>();
        for (var row : rows) {
            helper.assertTrue(displayIds.add(SpiritRecipeGroup.displayId(row)), "Tier rows share a display ID: " + row.recipeId());
            helper.assertTrue(SpiritRecipeGroup.displayId(row).equals(SpiritRecipeGroup.displayId(
                    new SpiritJeiData(row.recipeId(), row.recipeType(), row.input(), row.spirit().copy(), row.output().copy(), row.weight()))),
                    "Display IDs depend on object identity");
            int tier = OccultismRecipeBridge.spiritTier(row.spirit());
            helper.assertTrue(tier >= 1 && tier <= 4, "Recipe lost its real spirit tier");
            String job = OccultismRecipeBridge.spiritJobId(row.spirit());
            var group = SpiritRecipeGroup.classify(row);
            tiers.computeIfAbsent(group, ignored -> new HashSet<>()).add(tier);
            if (!job.isEmpty()) {
                jobs.add(job);
                helper.assertTrue(OccultismSpiritJobPolicy.permits(job, tier, row.recipeType()), "Recipe assigned to an incompatible worker");
            }
            if (group == SpiritRecipeGroup.SPIRIT_FIRE) {
                helper.assertTrue(job.isEmpty(), "General spirit fire page became profession-specific");
                fireRecipeTiers.computeIfAbsent(row.recipeId(), ignored -> new HashSet<>()).add(tier);
            }
        }
        helper.assertTrue(jobs.size() == OccultismSpiritJobPolicy.executableJobCount(), "Not all 16 native processing jobs are represented");
        for (var group : List.of(SpiritRecipeGroup.CRUSHING, SpiritRecipeGroup.CRYSTALLIZING,
                SpiritRecipeGroup.SMELTING, SpiritRecipeGroup.SPIRIT_FIRE)) {
            helper.assertTrue(Set.of(1, 2, 3, 4).equals(tiers.get(group)), "A profession lost one of the four spirit tiers: " + group);
        }
        helper.assertTrue(!fireRecipeTiers.isEmpty() && fireRecipeTiers.values().stream().allMatch(Set.of(1, 2, 3, 4)::equals), "Spirit fire tier rows were collapsed");
        helper.succeed();
    }
}
