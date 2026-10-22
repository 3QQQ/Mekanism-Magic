package com.example.mekanismmagic.gametest;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiniRitualCraftingGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private MiniRitualCraftingGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 500)
    public static void allNativePentaclesCraftWithRainbowVoidAndColoredChalk(GameTestHelper helper) {
        var recipes = recipes(helper);
        for (var recipe : recipes) {
            for (String chalk : List.of("rainbow", "void", "colors")) {
                var tile = place(helper);
                supply(tile, recipe, 2);
                if (chalk.equals("colors")) {
                    for (int index = 0; index < recipe.chalkColors().size(); index++) {
                        chalk(tile, index, chalk(helper, recipe.chalkColors().get(index)));
                    }
                } else {
                    chalk(tile, 7, chalk(helper, chalk));
                }
                var retained = tile.mekanismMagicManualOnlySlots().stream().map(slot -> slot.getStack().copy()).toList();
                // Miniatures are unstackable: take each completed output before
                // expecting the next operation, as an attached extractor would.
                untilOutput(helper, tile, recipe, 1);
                tile.getNativeOutputSlot().setStack(ItemStack.EMPTY);
                untilOutput(helper, tile, recipe, 1);
                helper.assertTrue(tile.mekanismMagicPatternInputs().stream().allMatch(slot -> slot.isEmpty()),
                        "Incomplete material consumption: " + recipe.pentacleId() + "/" + chalk);
                for (int index = 0; index < retained.size(); index++) {
                    helper.assertTrue(ItemStack.matches(retained.get(index), tile.mekanismMagicManualOnlySlots().get(index).getStack()),
                            "Chalk was consumed or changed: " + recipe.pentacleId() + "/" + chalk);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void universalChalkWorksInEveryModuleSlot(GameTestHelper helper) {
        var recipe = recipes(helper).stream().max(java.util.Comparator.comparingInt(value -> value.chalkColors().size())).orElseThrow();
        for (String color : List.of("rainbow", "void")) {
            for (int index = 0; index < NativeMagicMachineBlockEntity.CHALK_SLOT_COUNT; index++) {
                var tile = place(helper);
                supply(tile, recipe, 1);
                var chalk = chalk(helper, color);
                helper.assertTrue(tile.mekanismMagicManualOnlySlots().get(index)
                                .insertItem(chalk, Action.EXECUTE, AutomationType.MANUAL).isEmpty(),
                        "Manual chalk slot rejected " + color + " at " + index);
                untilOutput(helper, tile, recipe, 1);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void universalChalkStillMatchesEveryColorWithoutJeiAlternatives(GameTestHelper helper) {
        for (String color : OccultismRecipeBridge.ritualChalkColors()) {
            for (String variant : List.of(color, "rainbow", "void")) {
                var stack = chalk(helper, variant);
                helper.assertTrue(OccultismRecipeBridge.isAnyChalk(stack)
                                && OccultismRecipeBridge.isChalkForColor(stack, color),
                        "Removing JEI alternatives disabled usable chalk: " + variant + " for " + color);
            }
            helper.assertFalse(OccultismRecipeBridge.isChalkForColor(new ItemStack(Items.DIRT), color),
                    "Non-chalk item bypassed the color check");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void completeMaterialsStartAfterFiveServerTicks(GameTestHelper helper) {
        var tile = place(helper);
        supply(tile, recipes(helper).getFirst(), 1);
        chalk(tile, 0, chalk(helper, "rainbow"));
        tick(tile, 8);
        helper.assertTrue(tile.getProgress() > 0, "Complete rainbow-chalk recipe stayed idle beyond its five-tick settle period");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void steadyHopperTopUpsDoNotStarveCompleteRecipe(GameTestHelper helper) {
        var tile = place(helper);
        var recipe = recipes(helper).getFirst();
        supply(tile, recipe, 1);
        chalk(tile, 0, chalk(helper, "rainbow"));
        var marker = recipe.materials().getFirst().copyWithCount(1);
        for (int ticks = 0; ticks < 160 && tile.getNativeOutputSlot().isEmpty(); ticks++) {
            if (ticks % 8 == 0) {
                helper.assertTrue(tile.mekanismMagicPatternInputs().getFirst()
                                .insertItem(marker.copy(), Action.EXECUTE, AutomationType.EXTERNAL).isEmpty(),
                        "Hopper-style marker top-up was rejected");
            }
            tick(tile, 1);
        }
        helper.assertTrue(ItemStack.matches(recipe.output(), tile.getNativeOutputSlot().getStack()),
                "Regular hopper top-ups indefinitely postponed a complete rainbow-chalk recipe");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void insertingUniversalChalkWakesAnUnmatchedMachine(GameTestHelper helper) {
        var recipe = recipes(helper).stream().filter(value -> value.chalkColors().size() > 1).findFirst().orElseThrow();
        var tile = place(helper);
        supply(tile, recipe, 1);
        chalk(tile, 0, chalk(helper, recipe.chalkColors().getFirst()));
        tick(tile, 180);
        helper.assertTrue(tile.getNativeOutputSlot().isEmpty(), "One ordinary chalk bypassed the other required colors");
        chalk(tile, 15, chalk(helper, "rainbow"));
        untilOutput(helper, tile, recipe, 1);
        helper.succeed();
    }


    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void publishedYellowMarkerScreenshotMaterialAudit(GameTestHelper helper) {
        var recipe = yellowRecipe(helper);
        MekanismMagic.LOGGER.info("MINI_AUDIT yellow pentacle={} materials={} chalk={}",
                recipe.pentacleId(), recipe.materials(), recipe.chalkColors());
        for (String candle : List.of("minecraft:candle", "minecraft:white_candle",
                "occultism:large_candle", "occultism:large_candle_white")) {
            for (String bowl : List.of("occultism:sacrificial_bowl", "occultism:golden_sacrificial_bowl")) {
                var tile = place(helper);
                tile.mekanismMagicPatternInputs().get(0).setStack(new ItemStack(Items.YELLOW_DYE));
                tile.mekanismMagicPatternInputs().get(1).setStack(auditItem(candle, 8));
                tile.mekanismMagicPatternInputs().get(2).setStack(auditItem(bowl, 1));
                int slot = 0;
                for (String color : List.of("white", "purple", "gray", "gold")) {
                    chalk(tile, slot++, chalk(helper, color));
                }
                tick(tile, 140);
                MekanismMagic.LOGGER.info("MINI_AUDIT screenshot candle={} bowl={} output={} warning={}",
                        candle, bowl, tile.getNativeOutputSlot().getStack(),
                        tile.hasInputDoesntProduceOutputWarning());
            }
        }
        var exact = place(helper);
        supply(exact, recipe, 1);
        int slot = 0;
        for (String color : List.of("white", "purple", "gray", "gold")) {
            chalk(exact, slot++, chalk(helper, color));
        }
        untilOutput(helper, exact, recipe, 1);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void yellowPentacleNativeCandleTagAlternativesMustCraft(GameTestHelper helper) {
        var recipe = yellowRecipe(helper);
        var failures = new java.util.ArrayList<String>();
        for (String candle : List.of("minecraft:candle", "minecraft:white_candle",
                "occultism:large_candle", "occultism:large_candle_white")) {
            var tile = place(helper);
            supply(tile, recipe, 1);
            ItemStack alternate = auditItem(candle, 8);
            helper.assertTrue(alternate.getItem() instanceof net.minecraft.world.item.BlockItem block
                            && block.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.CANDLES),
                    "Diagnostic candle is not accepted by the native multiblock tag: " + candle);
            for (int slot = 0; slot < recipe.materials().size(); slot++) {
                var material = recipe.materials().get(slot);
                if (material.getItem() instanceof net.minecraft.world.item.BlockItem block
                        && block.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.CANDLES)) {
                    tile.mekanismMagicPatternInputs().get(slot)
                            .setStack(alternate.copyWithCount(material.getCount()));
                }
            }
            chalk(tile, 0, chalk(helper, "rainbow"));
            tick(tile, 140);
            if (!ItemStack.matches(recipe.output(), tile.getNativeOutputSlot().getStack())) {
                failures.add(candle);
            }
        }
        MekanismMagic.LOGGER.info("MINI_AUDIT native candle tag rejected alternatives={}", failures);
        helper.assertTrue(failures.isEmpty(), "Native candle tag alternatives fail: " + failures);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void yellowPentacleRecipeMustNotDependOnBookPreviewTime(GameTestHelper helper) {
        var recipe = yellowRecipe(helper);
        var projection = OccultismRecipeBridge.findProjectionByPentacle(
                helper.getLevel(), recipe.pentacleId()).orElseThrow();
        var multiblock = (com.klikli_dev.modonomicon.api.multiblock.Multiblock) projection.multiblock();
        long previousTime = helper.getLevel().getGameTime();
        var levelData = (net.minecraft.world.level.storage.ServerLevelData) helper.getLevel().getLevelData();
        List<ItemStack> first;
        List<ItemStack> second;
        List<List<ItemStack>> firstChoices;
        List<List<ItemStack>> secondChoices;
        try {
            multiblock.setLevel(helper.getLevel());
            levelData.setGameTime(0);
            OccultismRecipeBridge.invalidateRecipeCaches();
            first = yellowRecipe(helper).materials();
            firstChoices = yellowRecipe(helper).materialAlternatives();
            levelData.setGameTime(20);
            OccultismRecipeBridge.invalidateRecipeCaches();
            second = yellowRecipe(helper).materials();
            secondChoices = yellowRecipe(helper).materialAlternatives();
        } finally {
            multiblock.setLevel(null);
            levelData.setGameTime(previousTime);
            OccultismRecipeBridge.invalidateRecipeCaches();
        }
        MekanismMagic.LOGGER.info("MINI_AUDIT preview time 0={} time 20={}", first, second);
        helper.assertTrue(first.size() == second.size()
                        && java.util.stream.IntStream.range(0, first.size())
                        .allMatch(index -> ItemStack.matches(first.get(index), second.get(index))),
                "Opening the native multiblock preview changes the generated miniature materials: "
                        + first + " vs " + second);
        helper.assertTrue(firstChoices.size() == secondChoices.size()
                        && java.util.stream.IntStream.range(0, firstChoices.size()).allMatch(index ->
                        firstChoices.get(index).size() == secondChoices.get(index).size()
                                && java.util.stream.IntStream.range(0, firstChoices.get(index).size())
                                .allMatch(choice -> ItemStack.matches(firstChoices.get(index).get(choice),
                                        secondChoices.get(index).get(choice)))),
                "JEI alternative contents/counts/order changed with book preview time");
        helper.succeed();
    }


    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 500)
    public static void independentNativeCandleAndCenterBowlVariantsCraft(GameTestHelper helper) {
        var candles = BuiltInRegistries.BLOCK.getTagOrEmpty(net.minecraft.tags.BlockTags.CANDLES);
        var centerTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                ResourceLocation.parse("occultism:center_sacrificial_bowl"));
        int candleCases = 0;
        int bowlCases = 0;
        for (var holder : candles) {
            var item = holder.value().asItem();
            if (item == Items.AIR) continue;
            var tile = place(helper);
            literalYellowInputs(helper, tile, new ItemStack(item, 8),
                    auditItem("occultism:golden_sacrificial_bowl", 1));
            untilOutput(helper, tile, yellowRecipe(helper), 1);
            helper.assertTrue(tile.mekanismMagicPatternInputs().stream().allMatch(slot -> slot.isEmpty()),
                    "A native candle alternative was not fully consumed");
            candleCases++;
        }
        for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(centerTag)) {
            var item = holder.value().asItem();
            if (item == Items.AIR) continue;
            var tile = place(helper);
            literalYellowInputs(helper, tile, new ItemStack(Items.WHITE_CANDLE, 3), new ItemStack(item));
            tile.mekanismMagicPatternInputs().get(4).setStack(auditItem("occultism:large_candle", 2));
            tile.mekanismMagicPatternInputs().get(8).setStack(new ItemStack(Items.CANDLE, 3));
            untilOutput(helper, tile, yellowRecipe(helper), 1);
            helper.assertTrue(tile.mekanismMagicPatternInputs().stream().allMatch(slot -> slot.isEmpty()),
                    "Mixed stacks or native center bowl were not fully consumed");
            bowlCases++;
        }
        helper.assertTrue(candleCases >= 34 && bowlCases >= 4,
                "The independent native tag matrix is incomplete");
        MekanismMagic.LOGGER.info("MINI_FIX independent native candles={} center bowls={} all crafted",
                candleCases, bowlCases);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void alternativesStillRequireFullCountsMarkerAndChalk(GameTestHelper helper) {
        for (String failure : List.of("count", "marker", "bowl", "chalk")) {
            var tile = place(helper);
            literalYellowInputs(helper, tile, new ItemStack(Items.WHITE_CANDLE, failure.equals("count") ? 7 : 8),
                    auditItem(failure.equals("bowl") ? "occultism:sacrificial_bowl"
                            : "occultism:golden_sacrificial_bowl", 1));
            if (failure.equals("marker")) {
                tile.mekanismMagicPatternInputs().get(7).setStack(new ItemStack(Items.RED_DYE));
            }
            if (failure.equals("chalk")) {
                tile.mekanismMagicManualOnlySlots().get(1).setStack(ItemStack.EMPTY);
            }
            var before = tile.mekanismMagicPatternInputs().stream().map(slot -> slot.getStack().copy()).toList();
            tick(tile, 160);
            helper.assertTrue(tile.getNativeOutputSlot().isEmpty(),
                    "Invalid miniature inputs crafted: " + failure);
            for (int slot = 0; slot < before.size(); slot++) {
                helper.assertTrue(ItemStack.matches(before.get(slot),
                                tile.mekanismMagicPatternInputs().get(slot).getStack()),
                        "Rejected miniature inputs were consumed: " + failure);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void jeiAlternativesEqualNativeTagsAndCounts(GameTestHelper helper) {
        var recipe = yellowRecipe(helper);
        var candles = recipe.materialAlternatives().stream()
                .filter(choices -> choices.stream().anyMatch(stack -> stack.is(Items.CANDLE)))
                .findFirst().orElseThrow();
        var bowls = recipe.materialAlternatives().stream()
                .filter(choices -> choices.stream().anyMatch(stack -> stack.is(
                        auditItem("occultism:golden_sacrificial_bowl", 1).getItem())))
                .findFirst().orElseThrow();
        var centerTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                ResourceLocation.parse("occultism:center_sacrificial_bowl"));
        for (var pair : List.of(java.util.Map.entry(net.minecraft.tags.BlockTags.CANDLES, candles),
                java.util.Map.entry(centerTag, bowls))) {
            var expected = new java.util.HashSet<net.minecraft.world.item.Item>();
            BuiltInRegistries.BLOCK.getTagOrEmpty(pair.getKey()).forEach(holder -> {
                if (holder.value().asItem() != Items.AIR) expected.add(holder.value().asItem());
            });
            var actual = pair.getValue().stream().map(ItemStack::getItem)
                    .collect(java.util.stream.Collectors.toSet());
            helper.assertTrue(expected.equals(actual),
                    "JEI alternatives differ from the actual native tag: " + pair.getKey());
        }
        helper.assertTrue(candles.stream().allMatch(stack -> stack.getCount() == 8)
                        && bowls.stream().allMatch(stack -> stack.getCount() == 1),
                "Cycling a JEI material variant changed its required count");
        helper.succeed();
    }

    private static void literalYellowInputs(GameTestHelper helper, NativeMiniRitualAssemblerBlockEntity tile,
                                           ItemStack candles, ItemStack bowl) {
        // Inputs come from the supplied native JSON/tags, not our generated recipe.
        tile.mekanismMagicPatternInputs().get(0).setStack(new ItemStack(Items.YELLOW_DYE));
        tile.mekanismMagicPatternInputs().get(1).setStack(candles);
        tile.mekanismMagicPatternInputs().get(2).setStack(bowl);
        chalk(tile, 0, chalk(helper, "white"));
        chalk(tile, 1, chalk(helper, "purple"));
    }

    private static OccultismRecipeBridge.PentacleJeiData yellowRecipe(GameTestHelper helper) {
        return recipes(helper).stream().filter(recipe ->
                recipe.materials().getFirst().is(Items.YELLOW_DYE)).findFirst().orElseThrow();
    }

    private static ItemStack auditItem(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count);
    }

    private static List<OccultismRecipeBridge.PentacleJeiData> recipes(GameTestHelper helper) {
        var recipes = OccultismRecipeBridge.pentacleJeiRecipes(helper.getLevel());
        helper.assertTrue(recipes.size() == 18, "Expected all eighteen native miniature pentacles, got " + recipes.size());
        return recipes;
    }

    private static NativeMiniRitualAssemblerBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.get());
        return helper.getBlockEntity(POS);
    }

    private static void supply(NativeMiniRitualAssemblerBlockEntity tile, OccultismRecipeBridge.PentacleJeiData recipe, int copies) {
        for (int index = 0; index < recipe.materials().size(); index++) {
            var material = recipe.materials().get(index);
            tile.mekanismMagicPatternInputs().get(index).setStack(material.copyWithCount(material.getCount() * copies));
        }
    }

    private static ItemStack chalk(GameTestHelper helper, String color) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("occultism", "chalk_" + color));
        helper.assertTrue(item != Items.AIR, "Native chalk item is missing: " + color);
        return new ItemStack(item);
    }

    private static void chalk(NativeMiniRitualAssemblerBlockEntity tile, int slot, ItemStack stack) {
        tile.mekanismMagicManualOnlySlots().get(slot).setStack(stack);
    }

    private static void untilOutput(GameTestHelper helper, NativeMiniRitualAssemblerBlockEntity tile,
                                    OccultismRecipeBridge.PentacleJeiData recipe, int count) {
        for (int ticks = 0; ticks < 400 && tile.getNativeOutputSlot().getCount() < count; ticks++) tick(tile, 1);
        helper.assertTrue(ItemStack.matches(recipe.output().copyWithCount(count), tile.getNativeOutputSlot().getStack()),
                "Miniature did not craft: " + recipe.pentacleId() + ", output=" + tile.getNativeOutputSlot().getStack()
                        + ", progress=" + tile.getProgress());
    }

    private static void tick(NativeMiniRitualAssemblerBlockEntity tile, int ticks) {
        try {
            var update = NativeMagicMachineBlockEntity.class.getDeclaredMethod("onUpdateServer");
            update.setAccessible(true);
            for (int index = 0; index < ticks; index++) {
                tile.getNativeEnergyContainer().setEnergy(tile.getNativeEnergyContainer().getMaxEnergy());
                update.invoke(tile);
            }
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Real miniature assembler update failed", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to tick the real miniature assembler", exception);
        }
    }
}
