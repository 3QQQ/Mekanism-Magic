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
