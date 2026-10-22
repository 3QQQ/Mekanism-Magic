package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusRecipeSelector.Selection;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.UpgradeTierResult;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Native upgrade inputs and essence costs, now applied to the machine's own tier. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HephaestusUpgradeBridgeGameTests {
    private static final BlockPos SENTINEL = new BlockPos(1, 2, 1);
    private static final ResourceLocation LIGHTNING = id("crimson_lightning_bolt");

    private HephaestusUpgradeBridgeGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fourNativeUpgradesRetainAbsoluteTargetsMaterialsAndDuration(
            GameTestHelper helper) {
        var level = helper.getLevel();
        var upgrades = HephaestusForge261Bridge.upgradeRecipes(
                level.getServer());
        helper.assertTrue(upgrades.size() == 4
                        && HephaestusForge261Bridge.itemRecipes(
                        level.getServer()).size() == 18,
                "Native four tier changes and eighteen item rituals were not separated");
        int[][] raw = {{500, 10, 6000, 0}, {1000, 50, 9000, 0},
                {2000, 100, 12000, 0}, {5000, 500, 20000, 0}};
        for (int target = 2; target <= 5; target++) {
            ResourceLocation id = id("upgrade_tier_" + target);
            var descriptor = upgrades.stream()
                    .filter(view -> view.id().equals(id))
                    .findFirst().orElseThrow();
            Ritual ritual = nativeRitual(helper, id);
            helper.assertTrue(ritual.result()
                            instanceof UpgradeTierResult upgrade
                            && upgrade.resultTier() == target
                            && descriptor.upgradeTier() == target
                            && descriptor.view().tier() == target - 1
                            && descriptor.view().exactTier()
                            && ritual.requirements().tier().test(target - 1)
                            && !ritual.requirements().tier().test(target)
                            && ritual.duration() == 500
                            && descriptor.view().duration() == 500,
                    "Native absolute target, exact old tier or duration changed: " + id);
            Fixture fixture = fixture(descriptor.view());
            Selection selected = select(helper, fixture);
            helper.assertTrue(selected.id().equals(id)
                            && selected.upgradesForge()
                            && selected.upgradeTier() == target
                            && selected.resultPreview().isEmpty()
                            && selected.inputs().equals(fixture.expectedInputs())
                            && selected.inputs().stream()
                            .mapToInt(InputUse::count).sum() == 9
                            && Arrays.equals(selected.rawCosts(), raw[target - 2])
                            && HephaestusForge261Bridge.previewUpgradeTier(
                            level.registryAccess(), id, target - 1)
                            .orElse(-1) == target,
                    "Coreless upgrade selected wrong materials or generated an item");
        }
        ResourceLocation foreign = id("package_forge");
        helper.assertTrue(HephaestusForge261Bridge.upgradeRecipes(
                        level.getServer()).stream()
                        .noneMatch(view -> view.id().equals(foreign))
                        && HephaestusForge261Bridge.previewUpgradeTier(
                        level.registryAccess(), foreign, 5).isEmpty(),
                "PackagedFAA -1 world-forge transition entered our tier chain");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void upgradePreparationIsPureAndReturnsOnlyStateTarget(
            GameTestHelper helper) {
        var level = helper.getLevel();
        helper.setBlock(SENTINEL, Blocks.GOLD_BLOCK);
        BlockState beforeBlock = helper.getBlockState(SENTINEL);
        Set<UUID> beforeLightning = lightning(level);
        for (var recipe : HephaestusForge261Bridge.upgradeRecipes(
                level.getServer())) {
            Fixture fixture = fixture(recipe.view());
            ItemStack beforeMain = fixture.main().copy();
            List<ItemStack> beforeAuxiliary = copies(fixture.auxiliary());
            int[] bank = HephaestusForge261Bridge.capacities(
                    fixture.tier());
            int[] beforeBank = bank.clone();
            Selection selection = select(helper, fixture);
            var prepared = HephaestusForge261Bridge.prepareJob(level,
                    selection, fixture.main(), fixture.auxiliary(),
                    fixture.tier(), fixture.enhancers(), bank, false)
                    .orElseThrow();
            helper.assertTrue(prepared.upgradesForge()
                            && prepared.result().isEmpty()
                            && prepared.upgradeTier() == recipe.upgradeTier()
                            && prepared.duration()
                            == nativeRitual(helper, recipe.id()).duration()
                            && Arrays.equals(prepared.debit(),
                            selection.rawCosts())
                            && ItemStack.matches(fixture.main(), beforeMain)
                            && ItemStack.listMatches(fixture.auxiliary(),
                            beforeAuxiliary)
                            && Arrays.equals(bank, beforeBank)
                            && helper.getBlockState(SENTINEL).equals(beforeBlock)
                            && lightning(level).equals(beforeLightning),
                    "Preparation changed materials, essence, world, or emitted a core item");
            prepared.debit()[0] = -1;
            helper.assertTrue(prepared.debit()[0] >= 0,
                    "Prepared debit leaked a mutable internal array");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void wrongTierForgedTargetAndOptionalCodecSentinelStayRejected(
            GameTestHelper helper) {
        var level = helper.getLevel();
        for (var recipe : HephaestusForge261Bridge.upgradeRecipes(
                level.getServer())) {
            Fixture fixture = fixture(recipe.view());
            for (int tier = 0; tier <= 6; tier++) {
                if (tier == fixture.tier()) continue;
                helper.assertTrue(new HephaestusRecipeSelector()
                                .select(level, fixture.main(),
                                        fixture.auxiliary(), tier,
                                        fixture.enhancers(), true).isEmpty(),
                        "Wrong internal tier selected native upgrade "
                                + recipe.id());
            }
            Selection original = select(helper, fixture);
            int[] bank = HephaestusForge261Bridge.capacities(
                    fixture.tier());
            for (int forged : new int[]{-1, 0, 1, 6}) {
                Selection fake = new Selection(original.id(),
                        original.view(), original.inputs(),
                        original.resultPreview(), original.gateCosts(),
                        original.rawCosts(), original.contextSignature(),
                        original.signature(), forged);
                helper.assertTrue(HephaestusForge261Bridge.prepareJob(
                                level, fake, fixture.main(),
                                fixture.auxiliary(), fixture.tier(),
                                fixture.enhancers(), bank, false).isEmpty(),
                        "Forged absolute target was accepted: " + forged);
            }
        }
        for (int invalid : new int[]{6, Integer.MAX_VALUE}) {
            JsonObject data = new JsonObject();
            data.addProperty("result_tier", invalid);
            helper.assertTrue(UpgradeTierResult.CODEC.codec()
                            .parse(JsonOps.INSTANCE, data).result().isEmpty(),
                    "Native result codec accepted out-of-range tier " + invalid);
        }
        for (int foreignOrZero : new int[]{-1, 0}) {
            JsonObject data = new JsonObject();
            data.addProperty("result_tier", foreignOrZero);
            helper.assertTrue(UpgradeTierResult.CODEC.codec()
                            .parse(JsonOps.INSTANCE, data).result().isPresent()
                            == ModCompatibility.loaded("packaged_faa"),
                    "Unexpected optional native codec range for " + foreignOrZero);
        }
        helper.succeed();
    }

    private static Fixture fixture(RitualView view) {
        ItemStack main = view.mainIngredient().examples()
                .getFirst().copyWithCount(3);
        List<ItemStack> auxiliary = new ArrayList<>(
                Collections.nCopies(8, ItemStack.EMPTY));
        List<InputUse> expectedInputs = new ArrayList<>();
        expectedInputs.add(new InputUse(0, 1));
        for (int row = 0; row < view.inputs().size(); row++) {
            var input = view.inputs().get(row);
            int slot = 7 - row;
            auxiliary.set(slot, input.ingredient().examples()
                    .getFirst().copyWithCount(input.amount() + 3));
            expectedInputs.add(new InputUse(slot + 1, input.amount()));
        }
        expectedInputs.sort(java.util.Comparator.comparingInt(InputUse::slot));
        return new Fixture(main, auxiliary, view.tier(),
                new ArrayList<>(Collections.nCopies(4, ItemStack.EMPTY)),
                List.copyOf(expectedInputs));
    }

    private static Selection select(GameTestHelper helper,
                                    Fixture fixture) {
        return new HephaestusRecipeSelector().select(helper.getLevel(),
                fixture.main(), fixture.auxiliary(), fixture.tier(),
                fixture.enhancers(), false).orElseThrow();
    }

    private static Ritual nativeRitual(GameTestHelper helper,
                                       ResourceLocation id) {
        return helper.getLevel().registryAccess()
                .registryOrThrow(FARegistries.RITUAL).get(id);
    }

    private static Set<UUID> lightning(ServerLevel level) {
        Set<UUID> result = new HashSet<>();
        for (var entity : level.getAllEntities()) {
            if (LIGHTNING.equals(BuiltInRegistries.ENTITY_TYPE
                    .getKey(entity.getType()))) result.add(entity.getUUID());
        }
        return result;
    }

    private static List<ItemStack> copies(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "forbidden_arcanus", path);
    }

    private record Fixture(ItemStack main, List<ItemStack> auxiliary,
                           int tier, List<ItemStack> enhancers,
                           List<InputUse> expectedInputs) { }
}
