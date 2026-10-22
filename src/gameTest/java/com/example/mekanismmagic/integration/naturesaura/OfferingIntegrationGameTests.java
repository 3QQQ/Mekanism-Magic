package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import java.util.List;
import java.util.Set;

/** Internal offering batches, optional retained roses, no-world behavior and durable results. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OfferingIntegrationGameTests {
    private static final BlockPos MACHINE = new BlockPos(4, 2, 4);

    private OfferingIntegrationGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allSevenRecipesPreserveWholeStackSemantics(GameTestHelper helper) {
        var recipes = NatureAuraRecipeScanner.scanOfferings(
                helper.getLevel().getRecipeManager());
        helper.assertTrue(recipes.stream().filter(recipe ->
                        recipe.id().getNamespace().equals("naturesaura")).count() == 7,
                "Expected all seven Nature's Aura 41.9 offerings");
        var originalIds = Set.copyOf(recipes.stream()
                .map(OfferingRecipeBridge.OfferingRecipeView::id).toList());
        for (String path : List.of("depht_ingot_bee", "sky_ingot_bee")) {
            var id = ResourceLocation.fromNamespaceAndPath("productivebees",
                    "naturesaura/offering/" + path);
            helper.assertTrue(originalIds.contains(id)
                            == ModList.get().isLoaded("productivebees"),
                    "Productive Bees offering recipe has the wrong loading state: " + id);
        }
        for (var recipe : recipes) {
            ItemStack input = NatureAuraRecipeBridge.representativeChoice(
                    recipe.input()).orElseThrow().copyWithCount(64);
            ItemStack start = NatureAuraRecipeBridge.representativeChoice(
                    recipe.startItem()).orElseThrow().copyWithCount(2);
            helper.assertTrue(recipe.matches(input, start),
                    "Offering input/start pair did not match: " + recipe.id());
            var batch = OfferingRecipeBridge.toMachineRecipe(recipe,
                    input.getCount(), 0, 19).orElseThrow();
            helper.assertTrue(batch.inputs().size() == 1
                            && batch.inputs().getFirst().count() == 64
                            && batch.activationSlot() == 19,
                    "Whole-stack trigger did not consume exactly one start item");
            helper.assertTrue(batch.output().getCount()
                            == recipe.output().getCount() * 64,
                    "Offering batch output count was lost: " + recipe.id());
            helper.assertTrue(batch.resourceCosts().isEmpty(),
                    "Offering invented an Aura cost absent from the native recipe");
            ItemStack outputCopy = recipe.output();
            outputCopy.shrink(outputCopy.getCount());
            helper.assertTrue(!recipe.output().isEmpty(),
                    "Offering catalog output was mutable");
            helper.assertTrue(!recipe.matches(input, new ItemStack(Items.STICK)),
                    "Offering accepted an incorrect start item");
        }
        long previous = NatureAuraRecipeScanner.version(helper.getLevel().getRecipeManager());
        NatureAuraRecipeScanner.refresh(helper.getLevel().getRecipeManager());
        var refreshed = NatureAuraRecipeScanner.scanOfferings(
                helper.getLevel().getRecipeManager());
        helper.assertTrue(NatureAuraRecipeScanner.version(
                        helper.getLevel().getRecipeManager()) > previous
                        && refreshed.size() == recipes.size()
                        && Set.copyOf(refreshed.stream()
                        .map(OfferingRecipeBridge.OfferingRecipeView::id).toList())
                        .equals(originalIds),
                "Data-pack refresh did not preserve the complete offering catalog");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void fullStackUsesOneStartAndSurvivesBlockedRestart(GameTestHelper helper) {
        OfferingProcessorBlockEntity tile = placeMachine(helper);
        var recipe = NatureAuraRecipeScanner.scanOfferings(helper.getLevel().getRecipeManager()).stream()
                .filter(view -> view.output().getCount() == 2).findFirst().orElseThrow();
        loadInputs(tile, recipe, 64, 2);
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        helper.startSequence().thenExecuteAfter(24, () -> {
            helper.assertTrue(tile.getNativeInputSlot().isEmpty() && tile.getStartItemStack().getCount() == 1,
                    "One start item must process the full input stack without an external ring");
            helper.assertTrue(tile.getPendingOutputs().getFirst().getCount() == 128, "Blocked output lost a 128-item batch");
            tile.loadAdditional(tile.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        }).thenExecuteAfter(6, () -> {
            helper.assertTrue(tile.getPendingOutputs().getFirst().getCount() == 128 && tile.getStartItemStack().getCount() == 1,
                    "Restart lost output or repeated the trigger");
            tile.getNativeOutputSlot().setEmpty();
            tile.getWitherRoseSlot().setEmpty();
        }).thenExecuteAfter(6, () -> {
            int visible = tile.getNativeOutputSlot().getCount();
            helper.assertTrue(visible > 0 && visible + tile.getPendingOutputs().getFirst().getCount() == 128,
                    "Partial delivery broke offering output conservation");
            helper.assertTrue(tile.getStartItemStack().getCount() == 1, "Delivery spent another start item");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void outsideFlowersAreNeitherRequiredNorUsedForBonus(GameTestHelper helper) {
        var tile = placeMachine(helper);
        int[] count = {0};
        Multiblocks.OFFERING_TABLE.forEach(tile.getBlockPos(), 'R', (pos, matcher) -> {
            count[0]++;
            helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos, Blocks.WITHER_ROSE.defaultBlockState());
            return true;
        });
        helper.assertTrue(count[0] == OfferingProcessorBlockEntity.WITHER_ROSES_FOR_BONUS,
                "Internal rose threshold differs from the native ring's 36 roses");
        var snapshot = InternalNatureTestSupport.snapshot(helper.getLevel(), tile.getBlockPos());
        var recipe = NatureAuraRecipeScanner.scanOfferings(helper.getLevel().getRecipeManager()).getFirst();
        loadInputs(tile, recipe, 1, 2);
        try (var events = new InternalNatureTestSupport.NoWorldEvents(helper.getLevel(), tile.getBlockPos())) {
            tick(tile, 20);
            events.assertUnused(helper);
        }
        helper.assertTrue(tile.getNativeInputSlot().isEmpty() && tile.getStartItemStack().getCount() == 1
                        && !tile.hasMissingFlowerRing() && tile.getPendingOutputs().get(1).isEmpty(),
                "External flowers affected internal production or gave a free wither bonus");
        InternalNatureTestSupport.assertUnchanged(helper, snapshot);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void onlyThirtySixRetainedRosesEnableOneDurableNativeBonus(GameTestHelper helper) {
        for (int roses : new int[]{35, 36}) {
            var tile = placeMachine(helper);
            var recipe = NatureAuraRecipeScanner.scanOfferings(helper.getLevel().getRecipeManager()).getFirst();
            loadInputs(tile, recipe, 1, 2);
            tile.getWitherRoseSlot().setStack(new ItemStack(Items.WITHER_ROSE, roses));
            tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
            tile.getBonusOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
            tick(tile, 20);
            int bonus = tile.getPendingOutputs().get(1).getCount();
            helper.assertTrue(roses == 35 ? bonus == 0 : bonus >= 4 && bonus <= 8, "Incorrect optional wither bonus threshold/range");
            helper.assertTrue(tile.getWitherRoseSlot().getCount() == roses && tile.getStartItemStack().getCount() == 1,
                    "Internal roses were consumed or the start item was spent twice");
            helper.assertTrue(tile.getWitherRoseSlot().extractItem(1, Action.EXECUTE, AutomationType.EXTERNAL).isEmpty(),
                    "Automation extracted the retained roses");
            var restored = InternalNatureTestSupport.breakAndReplace(helper, tile);
            helper.assertTrue(restored.getWitherRoseSlot().getCount() == roses
                            && restored.getPendingOutputs().get(1).getCount() == bonus,
                    "Machine item lost roses or rerolled an already committed bonus");
            restored.getWitherRoseSlot().setEmpty();
            tick(restored, 30);
            helper.assertTrue(restored.getPendingOutputs().get(1).getCount() == bonus,
                    "Removing the optional catalyst changed a paid bonus");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void redstoneAndNoPowerDoNotSpendInternalInputs(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = NatureAuraRecipeScanner.scanOfferings(helper.getLevel().getRecipeManager()).getFirst();
        loadInputs(tile, recipe, 1, 1);
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 40);
        helper.assertTrue(tile.getProgress() == 0 && tile.getNativeInputSlot().getCount() == 1
                        && tile.getStartItemStack().getCount() == 1 && !tile.hasPendingOutput(),
                "Unpowered internal offering spent inputs");
        tile.getNativeEnergyContainer().setEnergy(10_000);
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 40);
        helper.assertTrue(tile.getProgress() == 0 && tile.getNativeEnergyContainer().getEnergy() == 10_000,
                "Redstone-disabled offering advanced or spent FE");
        tile.setControlType(RedstoneControl.DISABLED);
        tick(tile, 20);
        helper.assertTrue(tile.hasPendingOutput() && tile.getNativeInputSlot().isEmpty() && tile.getStartItemStack().isEmpty()
                        && tile.getNativeEnergyContainer().getEnergy() == 10_000 - 20L * NatureAuraMachineConfig.OFFERING_BASE_ENERGY_PER_TICK,
                "Internal offering did not recover with one exact trigger charge");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void appendedCatalystKeepsLegacyInventoryIndicesAndPortPermissions(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var slots = tile.getInventorySlots(null);
        helper.assertTrue(slots.indexOf(tile.getNativeInputSlot()) == 1 && slots.indexOf(tile.getNativeOutputSlot()) == 3
                        && slots.indexOf(tile.getBonusOutputSlot()) == 4 && slots.indexOf(tile.getWitherRoseSlot()) == 5,
                "Adding the retained slot renumbered released energy/input/output slots");
        int roseIndex = slots.indexOf(tile.getWitherRoseSlot());
        helper.assertTrue(tile.insertItem(roseIndex, new ItemStack(Items.WITHER_ROSE, 36), null, Action.EXECUTE).isEmpty()
                        && tile.extractItem(roseIndex, 36, null, Action.EXECUTE).isEmpty(),
                "Unsided inventory API cannot fill the rose slot or can extract its retained contents");
        tile.loadAdditional(tile.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(tile.getWitherRoseSlot().getCount() == 36 && tile.hasWitherRoseBonus(),
                "Rose state did not survive native inventory serialization");
        helper.succeed();
    }

    private static OfferingProcessorBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.setBlock(MACHINE, NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK.get());
        return helper.getBlockEntity(MACHINE);
    }

    private static void loadInputs(OfferingProcessorBlockEntity tile, OfferingRecipeBridge.OfferingRecipeView recipe, int inputs, int starts) {
        tile.getNativeInputSlot().setStack(NatureAuraRecipeBridge.representativeChoice(recipe.input()).orElseThrow().copyWithCount(inputs));
        tile.mekanismMagicPatternInputs().get(1).setStack(NatureAuraRecipeBridge.representativeChoice(recipe.startItem()).orElseThrow().copyWithCount(starts));
        tile.getNativeEnergyContainer().setEnergy(2_000_000L);
    }

    private static void tick(OfferingProcessorBlockEntity tile, int ticks) {
        for (int tick = 0; tick < ticks; tick++) tile.onUpdateServer();
    }
}
