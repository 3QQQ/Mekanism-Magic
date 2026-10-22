package com.example.mekanismmagic.integration.arsnouveau;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.ae2.Ae2IdentifierImbuementPattern;
import com.example.mekanismmagic.integration.ae2.Ae2ImbuementProvider;
import com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/** Exercises real cached processing with unmarked manual/automation inputs. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArsImbuementContinuityGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private enum Kind { SINGLE, FACTORY, EXTRA }

    private ArsImbuementContinuityGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void singleSwitchesRecipeWithUnchangedReagent(GameTestHelper helper) {
        switchDuringProcessing(helper, Kind.SINGLE);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void factorySwitchesRecipeWithUnchangedReagent(GameTestHelper helper) {
        switchDuringProcessing(helper, Kind.FACTORY);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void extrasSwitchesRecipeWithUnchangedReagent(GameTestHelper helper) {
        switchDuringProcessing(helper, Kind.EXTRA);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void singleNativeAePushesKeepProcessingAcrossRecipes(GameTestHelper helper) {
        var tile = (ImbuementProcessorBlockEntity) place(helper, Kind.SINGLE);
        installIdentifiers(helper, tile,
                recipe(helper, "imbuement_fire_essence"),
                recipe(helper, "imbuement_water_essence"));
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var provider = Ae2ImbuementProvider.forTile(tile);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(provider.getGridNode(Direction.EAST) != null
                        && provider.getGridNode(Direction.EAST).isActive(), "Native imbuement AE node is not active"))
                .thenExecute(() -> {
                    for (String id : List.of("imbuement_fire_essence", "imbuement_water_essence", "imbuement_fire_essence")) {
                        var recipe = recipe(helper, id);
                        var reagent = ArsNouveauRecipeBridge.representativeChoice(recipe.value().getInput()).copyWithCount(1);
                        var pattern = provider.getAvailablePatterns().stream().filter(candidate ->
                                candidate.getOutputs().getFirst().what().equals(AEItemKey.of(recipe.value().getOutput())))
                                .findFirst().orElseThrow();
                        refill(tile, recipe.value().getSource() * 3);
                        for (int operation = 0; operation < 3; operation++) {
                            var counter = new KeyCounter();
                            counter.add(AEItemKey.of(reagent), 1);
                            helper.assertTrue(provider.pushPattern(pattern, new KeyCounter[]{counter}),
                                    "Native provider rejected consecutive " + id + ", operation=" + operation);
                            untilOutput(helper, tile, recipe, 1);
                            helper.assertTrue(input(tile).isEmpty(), "Native provider did not finish its accepted input");
                            output(tile).setStack(ItemStack.EMPTY);
                        }
                        helper.assertTrue(source(tile) == 0, "Native provider did not pay exactly for three operations");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void factoryMekEPushesKeepProcessingAcrossRecipes(GameTestHelper helper) {
        mekEBatches(helper, Kind.FACTORY);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void extrasMekEPushesKeepProcessingAcrossRecipes(GameTestHelper helper) {
        mekEBatches(helper, Kind.EXTRA);
    }

    private static void mekEBatches(GameTestHelper helper, Kind kind) {
        var tile = place(helper, kind);
        installIdentifiers(helper, tile,
                recipe(helper, "imbuement_fire_essence"),
                recipe(helper, "imbuement_water_essence"));
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var adapter = (MeUpgradeRecipeMachineAdapter) tile;
        helper.assertTrue(adapter.isMeUpgradeTarget()
                        && adapter.getMeUpgradeContainer().install(MeUpgradeType.PATTERN_PROVIDER).successful(),
                kind + " could not install the real MekE pattern provider");
        adapter.onMeUpgradeStateChanged();
        adapter.createMeNodeIfActive();
        var support = adapter.getOrCreateMeUpgradeRuntime().support();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(support.getMainNode().isActive(), "MekE node is not active"))
                .thenExecute(() -> {
                    for (String id : List.of("imbuement_fire_essence", "imbuement_water_essence", "imbuement_fire_essence")) {
                        var recipe = recipe(helper, id);
                        var reagent = ArsNouveauRecipeBridge.representativeChoice(recipe.value().getInput()).copyWithCount(1);
                        var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(
                                        GenericStack.fromItemStack(reagent), GenericStack.fromItemStack(
                                                ArsNouveauRecipeBridge.createIdentifierForRecipe(helper.getLevel(), recipe.id()))),
                                List.of(GenericStack.fromItemStack(recipe.value().getOutput())));
                        support.getPatternSlots().getFirst().setStack(encoded);
                        support.updatePatterns();
                        var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
                        helper.assertTrue(pattern instanceof Ae2IdentifierImbuementPattern, "Missing exact imbuement decoder");
                        refill(tile, recipe.value().getSource() * 3);
                        for (int operation = 0; operation < 3; operation++) {
                            var counter = new KeyCounter();
                            counter.add(AEItemKey.of(reagent), 1);
                            helper.assertTrue(support.pushPatternWithAdapter(pattern, new KeyCounter[]{counter}),
                                    kind + " rejected a consecutive push for " + id + ", operation=" + operation);
                            untilOutput(helper, tile, recipe, 1);
                            helper.assertTrue(input(tile).isEmpty(), kind + " did not finish the accepted MekE input");
                            output(tile).setStack(ItemStack.EMPTY);
                        }
                        helper.assertTrue(source(tile) == 0, kind + " did not pay exactly for three MekE operations");
                    }
                })
                .thenSucceed();
    }

    private static void switchDuringProcessing(GameTestHelper helper, Kind kind) {
        switchDuringProcessing(helper, kind, false);
        switchDuringProcessing(helper, kind, true);
    }

    private static void switchDuringProcessing(GameTestHelper helper, Kind kind, boolean physical) {
        var tile = place(helper, kind);
        var first = recipe(helper, "imbuement_fire_essence");
        var second = recipe(helper, "imbuement_water_essence");
        ItemStack reagent = ArsNouveauRecipeBridge.representativeChoice(first.value().getInput()).copyWithCount(3);
        helper.assertTrue(second.value().getInput().test(reagent), "Fixtures must use the same reagent");
        installIdentifiers(helper, tile, first, second);
        if (physical) {
            selectPhysical(tile, 0);
        } else {
            select(helper, tile, first);
        }
        refill(tile, second.value().getSource() * 3);
        input(tile).setStack(reagent);
        tick(tile, 5);
        helper.assertTrue(progress(tile) > 0 && output(tile).isEmpty(), kind + " did not start the first recipe");
        if (physical) selectPhysical(tile, 1);
        else select(helper, tile, second);
        untilOutput(helper, tile, second, 3);
        helper.assertTrue(source(tile) == 0, kind + " did not charge exactly the new recipe's Source cost");
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void consecutiveBatchesSwitchWithoutAnIdleTick(GameTestHelper helper) {
        for (Kind kind : Kind.values()) {
            var tile = place(helper, kind);
            installIdentifiers(helper, tile,
                    recipe(helper, "imbuement_fire_essence"),
                    recipe(helper, "imbuement_water_essence"));
            for (String id : List.of("imbuement_fire_essence", "imbuement_water_essence", "imbuement_fire_essence")) {
                var recipe = recipe(helper, id);
                String catalyst = CatalystIdentifierItem.catalystId(
                        ArsNouveauRecipeBridge.createIdentifierForRecipe(helper.getLevel(), recipe.id())).toString();
                helper.assertTrue(selection(tile).canSelectCatalystIdentifierId(catalyst),
                        kind + " stayed busy after completing a batch: " + id);
                select(helper, tile, recipe);
                refill(tile, recipe.value().getSource() * 3);
                input(tile).setStack(ArsNouveauRecipeBridge.representativeChoice(recipe.value().getInput()).copyWithCount(3));
                untilOutput(helper, tile, recipe, 3);
                helper.assertTrue(input(tile).isEmpty() && source(tile) == 0,
                        kind + " did not consume the complete batch exactly once");
                // Refill immediately: automation need not leave an empty slot for one server tick.
                output(tile).setStack(ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void clearingSelectionInvalidatesInFlightCatalystWork(GameTestHelper helper) {
        for (Kind kind : Kind.values()) {
            for (boolean starved : new boolean[]{false, true}) {
                var tile = place(helper, kind);
                var recipe = recipe(helper, "imbuement_fire_essence");
                installIdentifiers(helper, tile, recipe);
                select(helper, tile, recipe);
                refill(tile, recipe.value().getSource());
                input(tile).setStack(ArsNouveauRecipeBridge.representativeChoice(recipe.value().getInput()).copyWithCount(1));
                tick(tile, 5);
                if (starved) refill(tile, 0);
                if (tile instanceof ImbuementProcessorBlockEntity single) single.clearCatalystIdentifierSelection();
                else if (tile instanceof ImbuementFactoryBlockEntity factory) factory.clearCatalystIdentifierSelection();
                else ((ExtraImbuementFactoryBlockEntity) tile).clearCatalystIdentifierSelection();
                tick(tile, 250);
                helper.assertTrue(output(tile).isEmpty() && input(tile).getCount() == 1
                                && source(tile) == (starved ? 0 : recipe.value().getSource()) && progress(tile) == 0,
                        kind + " kept stale catalyst work after clearing its selection");
                refill(tile, recipe.value().getSource());
                select(helper, tile, recipe);
                untilOutput(helper, tile, recipe, 1);
            }
        }
        helper.succeed();
    }

    private static TileEntityMekanism place(GameTestHelper helper, Kind kind) {
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, switch (kind) {
            case SINGLE -> ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get();
            case FACTORY -> ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get();
            case EXTRA -> MekanismExtrasImbuementFactories.INFINITE_BLOCK.get();
        });
        TileEntityMekanism tile = helper.getBlockEntity(POS);
        if (tile instanceof ImbuementFactoryBlockEntity factory && factory.isSorting()) factory.toggleSorting();
        if (tile instanceof ExtraImbuementFactoryBlockEntity factory && factory.isSorting()) factory.toggleSorting();
        return tile;
    }

    private static RecipeHolder<ImbuementRecipe> recipe(GameTestHelper helper, String path) {
        var recipe = ArsNouveauRecipeScanner.find(helper.getLevel().getRecipeManager(),
                ResourceLocation.fromNamespaceAndPath("ars_nouveau", path));
        helper.assertTrue(recipe != null && !recipe.value().getPedestalItems().isEmpty(), "Missing catalyst fixture: " + path);
        return recipe;
    }

    private static CatalystIdentifierSelectionHost selection(TileEntityMekanism tile) {
        return (CatalystIdentifierSelectionHost) tile;
    }

    @SafeVarargs
    private static void installIdentifiers(GameTestHelper helper,
                                           TileEntityMekanism tile,
                                           RecipeHolder<ImbuementRecipe>... recipes) {
        List<IInventorySlot> slots = ((IMekanismMagicAutomation) tile)
                .mekanismMagicPersistentInputs();
        helper.assertTrue(slots.size() >= recipes.length,
                "Imbuement fixture has too few catalyst library slots");
        for (int index = 0; index < recipes.length; index++) {
            ItemStack identifier = ArsNouveauRecipeBridge
                    .createIdentifierForRecipe(helper.getLevel(),
                            recipes[index].id());
            helper.assertTrue(ArsNouveauRecipeBridge.identifierMatchesRecipe(
                            helper.getLevel(), identifier,
                            recipes[index].id()),
                    "Fixture catalyst identifier is not signed for "
                            + recipes[index].id());
            slots.get(index).setStack(identifier);
        }
    }

    private static void select(GameTestHelper helper, TileEntityMekanism tile, RecipeHolder<ImbuementRecipe> recipe) {
        helper.assertTrue(selection(tile).selectCatalystIdentifierForRecipe(recipe.id()), "Unable to select " + recipe.id());
    }

    private static void selectPhysical(TileEntityMekanism tile, int index) {
        if (tile instanceof ImbuementProcessorBlockEntity single) single.selectCatalystIdentifier(index);
        else if (tile instanceof ImbuementFactoryBlockEntity factory) factory.selectCatalystIdentifier(index);
        else ((ExtraImbuementFactoryBlockEntity) tile).selectCatalystIdentifier(index);
    }

    private static IInventorySlot input(TileEntityMekanism tile) {
        return ((IMekanismMagicAutomation) tile).mekanismMagicPatternInputs().getFirst();
    }

    private static IInventorySlot output(TileEntityMekanism tile) {
        return ((IMekanismMagicAutomation) tile).mekanismMagicPatternOutputs().getFirst();
    }

    private static void refill(TileEntityMekanism tile, int source) {
        var energy = ((IMekanismMagicAutomation) tile).mekanismMagicEnergyContainer();
        energy.setEnergy(energy.getMaxEnergy());
        if (tile instanceof ImbuementProcessorBlockEntity single) single.setSource(source);
        else if (tile instanceof ImbuementFactoryBlockEntity factory) factory.getSourceStorage().setSource(source);
        else ((ExtraImbuementFactoryBlockEntity) tile).getSourceStorage().setSource(source);
    }

    private static int source(TileEntityMekanism tile) {
        if (tile instanceof ImbuementProcessorBlockEntity single) return single.getSource();
        if (tile instanceof ImbuementFactoryBlockEntity factory) return factory.getSource();
        return ((ExtraImbuementFactoryBlockEntity) tile).getSource();
    }

    private static int progress(TileEntityMekanism tile) {
        if (tile instanceof NativeMagicMachineBlockEntity single) return single.getProgress();
        if (tile instanceof ImbuementFactoryBlockEntity factory) return factory.getProgress(0);
        return ((ExtraImbuementFactoryBlockEntity) tile).getProgress(0);
    }

    private static void untilOutput(GameTestHelper helper, TileEntityMekanism tile, RecipeHolder<ImbuementRecipe> recipe, int count) {
        for (int ticks = 0; ticks < 1_200 && output(tile).getCount() < count; ticks++) tick(tile, 1);
        helper.assertTrue(output(tile).getCount() == count
                        && ItemStack.isSameItemSameComponents(output(tile).getStack(), recipe.value().getOutput()),
                tile.getClass().getSimpleName() + " did not continuously produce " + recipe.id()
                        + ": output=" + output(tile).getStack() + ", input=" + input(tile).getStack()
                        + ", progress=" + progress(tile) + ", Source=" + source(tile));
    }

    private static void tick(TileEntityMekanism tile, int ticks) {
        try {
            var update = tile.getClass().getDeclaredMethod("onUpdateServer");
            update.setAccessible(true);
            for (int index = 0; index < ticks; index++) update.invoke(tile);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Real imbuement processing failed", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to tick the real imbuement machine", exception);
        }
    }
}
