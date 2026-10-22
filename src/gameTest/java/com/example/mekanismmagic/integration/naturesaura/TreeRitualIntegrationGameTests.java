package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import de.ellpeck.naturesaura.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Internal tree processing, native costs, zero world events and durable output/migration. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TreeRitualIntegrationGameTests {
    private static final BlockPos MACHINE = new BlockPos(4, 2, 4);

    private TreeRitualIntegrationGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allSeventeenRecipesPreserveRepeatedIngredientsAndComponents(
            GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var recipes = TreeRitualRecipeBridge.scan(manager);
        helper.assertTrue(recipes.size() == 17, "Expected all 17 native tree rituals");
        for (var recipe : recipes) {
            List<ItemStack> materials = choices(recipe);
            Collections.reverse(materials);
            ItemStack sapling = choice(recipe.sapling());
            var uses = recipe.matchMaterials(materials, sapling).orElseThrow();
            helper.assertTrue(uses.stream().mapToInt(use -> use.count()).sum()
                            == recipe.ingredients().size(),
                    "Unordered matching lost repeated materials: " + recipe.id());
            helper.assertTrue(!recipe.matchMaterials(materials,
                            new ItemStack(Items.BIRCH_SAPLING)).isPresent(),
                    "Incorrect sapling accepted: " + recipe.id());
            var result = new TreeRitualRecipeBridge.Match(recipe, uses).machineRecipe();
            helper.assertTrue(result.duration() == recipe.time()
                            && result.resourceCosts().isEmpty()
                            && ItemStack.matches(result.output(), recipe.output()),
                    "Native duration, components or no-Aura semantics changed: " + recipe.id());
            ItemStack copy = recipe.output();
            copy.setCount(0);
            helper.assertTrue(!recipe.output().isEmpty(), "Catalog output was mutable");
            var changed = new TreeRitualRecipeBridge.TreeRitualRecipeView(recipe.id(),
                    recipe.sapling(), recipe.ingredients(), recipe.output(), recipe.time() + 1);
            helper.assertTrue(!changed.signature().equals(recipe.signature()),
                    "Same-id duration changes did not invalidate content signature");
        }
        var altar = recipes.stream().filter(recipe -> recipe.id().getPath()
                .endsWith("/nature_altar")).findFirst().orElseThrow();
        List<ItemStack> compressed = new ArrayList<>();
        compressed.add(new ItemStack(Items.STONE, 3));
        for (var ingredient : altar.ingredients()) {
            ItemStack stack = choice(ingredient);
            if (!stack.is(Items.STONE)) {
                compressed.add(stack);
            }
        }
        var uses = altar.matchMaterials(compressed, choice(altar.sapling())).orElseThrow();
        helper.assertTrue(uses.stream().filter(use -> use.slot() == 0)
                        .mapToInt(use -> use.count()).sum() == 3,
                "Three stone ingredients did not consume three units from the combined stack");
        compressed.getFirst().setCount(2);
        helper.assertTrue(altar.matchMaterials(compressed, choice(altar.sapling())).isEmpty(),
                "A two-stone stack paid for three native stone ingredients");
        long version = NatureAuraRecipeScanner.version(manager);
        NatureAuraRecipeScanner.refresh(manager);
        helper.assertTrue(NatureAuraRecipeScanner.version(manager) > version
                        && TreeRitualRecipeBridge.scan(manager).size() == 17
                        && TreeRitualRecipeBridge.scan(manager) != recipes,
                "Tree catalog was not rebuilt after data-pack refresh");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void allSeventeenProduceInsideSolidSurroundingsWithoutWorldEvents(GameTestHelper helper) {
        int[] gold = {0};
        Multiblocks.TREE_RITUAL.forEach(BlockPos.ZERO, 'G', (pos, matcher) -> { gold[0]++; return true; });
        helper.assertTrue(gold[0] == TreeRitualRecipeBridge.GOLD_POWDER_PER_OPERATION,
                "Internal gold cost differs from the original 16 powder positions");
        for (var recipe : TreeRitualRecipeBridge.scan(helper.getLevel().getRecipeManager())) {
            var tile = placeMachine(helper);
            loadRecipe(tile, recipe, 1);
            tile.getGoldPowderSlot().setStack(new ItemStack(ModBlocks.GOLD_POWDER, 16));
            // Deliberately unsuitable planting surface and occupied old ritual center.
            helper.getLevel().setBlockAndUpdate(tile.getRitualCenter(), Blocks.BEDROCK.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(tile.getRitualCenter().below(), Blocks.BEDROCK.defaultBlockState());
            var world = InternalNatureTestSupport.snapshot(helper.getLevel(), tile.getBlockPos());
            long spent = 0;
            try (var events = new InternalNatureTestSupport.NoWorldEvents(helper.getLevel(), tile.getBlockPos())) {
                for (int tick = 0; tick < recipe.time(); tick++) {
                    tile.getNativeEnergyContainer().setEnergy(2_000_000L);
                    long before = tile.getNativeEnergyContainer().getEnergy();
                    tile.onUpdateServer();
                    spent += before - tile.getNativeEnergyContainer().getEnergy();
                }
                events.assertUnused(helper);
            }
            helper.assertTrue(tile.hasPendingOutput() && tile.getMaterialSlots().stream().allMatch(slot -> slot.isEmpty())
                            && tile.getSaplingStack().isEmpty() && tile.getGoldPowderStack().isEmpty(),
                    "Internal ritual did not consume exactly one complete material/sapling/gold set: " + recipe.id());
            helper.assertTrue(spent == (long) recipe.time() * NatureAuraMachineConfig.TREE_RITUAL_BASE_ENERGY_PER_TICK,
                    "Internal ritual changed native duration or charged additional world-placement FE");
            tick(tile, 1);
            helper.assertTrue(ItemStack.matches(tile.getNativeOutputSlot().getStack(), recipe.output()) && !tile.hasPendingOutput(),
                    "Internal ritual lost native output count/components: " + recipe.id());
            InternalNatureTestSupport.assertUnchanged(helper, world);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void missingGoldPowerOutputSpaceAndRedstonePauseWithoutSpendingInputs(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = recipe(helper, "ancient_sapling");
        loadRecipe(tile, recipe, 1);
        tile.getGoldPowderSlot().setStack(new ItemStack(ModBlocks.GOLD_POWDER, 15));
        long energy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, 30);
        helper.assertTrue(tile.hasMissingGoldPowder() && tile.getProgress() == 0 && tile.getSaplingStack().getCount() == 1
                        && tile.getGoldPowderStack().getCount() == 15 && tile.getNativeEnergyContainer().getEnergy() == energy,
                "Insufficient internal gold powder spent inputs or FE");
        tile.getGoldPowderSlot().setStack(new ItemStack(ModBlocks.GOLD_POWDER, 16));
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        tick(tile, 30);
        helper.assertTrue(tile.hasOutputBlocked() && tile.getProgress() == 0 && tile.getNativeEnergyContainer().getEnergy() == energy,
                "A blocked output charged processing fees");
        tile.getNativeOutputSlot().setEmpty();
        tick(tile, 5);
        helper.assertTrue(tile.getProgress() == 5, "Ready internal ritual did not start");
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 20);
        helper.assertTrue(tile.getProgress() == 5 && tile.getSaplingStack().getCount() == 1
                        && tile.getGoldPowderStack().getCount() == 16, "Power loss consumed uncommitted materials");
        tile.getNativeEnergyContainer().setEnergy(2_000_000L);
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 20);
        helper.assertTrue(tile.getProgress() == 5 && tile.getNativeEnergyContainer().getEnergy() == 2_000_000L,
                "Redstone-disabled tree machine advanced");
        tile.setControlType(RedstoneControl.DISABLED);
        tick(tile, 1);
        helper.assertTrue(tile.getProgress() == 6, "Paused internal work did not resume");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void paidOutputSurvivesBlockedReloadAndOneStatefulMachineDrop(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = recipe(helper, "ancient_sapling");
        loadRecipe(tile, recipe, 2);
        tile.getGoldPowderSlot().setStack(new ItemStack(ModBlocks.GOLD_POWDER, 32));
        tick(tile, recipe.time());
        helper.assertTrue(tile.hasPendingOutput(), "Could not prepare a paid internal result");
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        long remainingEnergy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, 20);
        tile.loadAdditional(tile.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        var restored = InternalNatureTestSupport.breakAndReplace(helper, tile);
        helper.assertTrue(ItemStack.matches(restored.getPendingOutputs().getFirst(), recipe.output())
                        && restored.getSaplingStack().getCount() == 1 && restored.getGoldPowderStack().getCount() == 16,
                "Pending result or remaining materials were lost/duplicated in machine item data");
        restored.setControlType(RedstoneControl.HIGH);
        restored.getNativeOutputSlot().setEmpty();
        tick(restored, 6);
        helper.assertTrue(ItemStack.matches(restored.getNativeOutputSlot().getStack(), recipe.output())
                        && !restored.hasPendingOutput() && restored.getNativeEnergyContainer().getEnergy() == remainingEnergy
                        && restored.getSaplingStack().getCount() == 1 && restored.getGoldPowderStack().getCount() == 16,
                "Paid result delivery charged again or was lost while redstone-paused");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void legacyPlantedMetadataIsNotAnInternalPaymentAndWorldIsLeftAlone(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var pos = tile.getRitualCenter();
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, Blocks.OAK_SAPLING.defaultBlockState());
        var world = InternalNatureTestSupport.snapshot(helper.getLevel(), tile.getBlockPos());
        CompoundTag saved = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.put("tree_ritual_planted_sapling", new ItemStack(Items.OAK_SAPLING).save(helper.getLevel().registryAccess()));
        saved.putLong("tree_ritual_planted_center", pos.asLong());
        saved.putString("tree_ritual_planted_dimension", helper.getLevel().dimension().location().toString());
        saved.putInt("magic_progress", 20);
        saved.putString("magic_active_recipe", "legacy-external-ritual");
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
        try (var events = new InternalNatureTestSupport.NoWorldEvents(helper.getLevel(), tile.getBlockPos())) {
            tick(tile, 30);
            events.assertUnused(helper);
        }
        helper.assertTrue(tile.hasLegacyExternalSetup() && !tile.hasPendingOutput() && tile.getNativeOutputSlot().isEmpty()
                        && tile.getSaplingStack().isEmpty() && tile.getGoldPowderStack().isEmpty() && !tile.mekanismMagicIsBusy(),
                "Legacy planted metadata became a free internal input/refund or permanently blocked the machine");
        tile.loadAdditional(tile.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(tile.hasLegacyExternalSetup(), "Legacy setup information was discarded");
        var slots = tile.getInventorySlots(null);
        helper.assertTrue(slots.indexOf(tile.getSaplingSlot()) == 9 && slots.indexOf(tile.getGoldPowderSlot()) == 10
                        && slots.indexOf(tile.getNativeOutputSlot()) == 11, "Internalization renumbered released inventory slots");
        InternalNatureTestSupport.assertUnchanged(helper, world);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sameIdReloadResetsProgressAndKeepsCommittedResultSnapshot(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = recipe(helper, "ancient_sapling");
        loadRecipe(tile, recipe, 1);
        tile.getGoldPowderSlot().setStack(new ItemStack(ModBlocks.GOLD_POWDER, 16));
        tick(tile, 10);
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        ItemStack newOutput = recipe.output();
        newOutput.set(DataComponents.CUSTOM_NAME, Component.literal("Internal reload result"));
        try {
            List<RecipeHolder<?>> changed = new ArrayList<>(original);
            changed.removeIf(holder -> holder.id().equals(recipe.id()));
            changed.add(new RecipeHolder<>(recipe.id(), new TreeRitualRecipe(recipe.sapling(), newOutput, recipe.time() + 20, recipe.ingredients())));
            manager.replaceRecipes(changed);
            NatureAuraRecipeScanner.refresh(manager);
            tick(tile, 1);
            helper.assertTrue(tile.getProgress() == 1 && tile.getProgressRequired() == recipe.time() + 20,
                    "Same-id tree recipe change reused stale progress");
            tick(tile, recipe.time() + 19);
            helper.assertTrue(ItemStack.matches(tile.getPendingOutputs().getFirst(), newOutput), "Updated recipe lost output components");
        } finally {
            manager.replaceRecipes(original);
            NatureAuraRecipeScanner.refresh(manager);
        }
        tick(tile, 1);
        helper.assertTrue(ItemStack.matches(tile.getNativeOutputSlot().getStack(), newOutput), "Reload replaced an already paid output");
        helper.succeed();
    }

    private static TreeRitualProcessorBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.setBlock(MACHINE, NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_BLOCK.get());
        TreeRitualProcessorBlockEntity tile = helper.getBlockEntity(MACHINE);
        tile.setFacing(Direction.NORTH);
        tile.getNativeEnergyContainer().setEnergy(2_000_000L);
        return tile;
    }

    private static void loadRecipe(TreeRitualProcessorBlockEntity tile, TreeRitualRecipeBridge.TreeRitualRecipeView recipe, int saplings) {
        var inputs = choices(recipe);
        for (int i = 0; i < inputs.size(); i++) tile.getMaterialSlots().get(i).setStack(inputs.get(i));
        tile.getSaplingSlot().setStack(choice(recipe.sapling()).copyWithCount(saplings));
    }

    private static TreeRitualRecipeBridge.TreeRitualRecipeView recipe(GameTestHelper helper, String name) {
        return TreeRitualRecipeBridge.scan(helper.getLevel().getRecipeManager()).stream()
                .filter(recipe -> recipe.id().getPath().endsWith("/" + name)).findFirst().orElseThrow();
    }

    private static List<ItemStack> choices(TreeRitualRecipeBridge.TreeRitualRecipeView recipe) {
        return new ArrayList<>(recipe.ingredients().stream().map(TreeRitualIntegrationGameTests::choice).toList());
    }

    private static ItemStack choice(net.minecraft.world.item.crafting.Ingredient ingredient) {
        return NatureAuraRecipeBridge.representativeChoice(ingredient).orElseThrow();
    }

    private static void tick(TreeRitualProcessorBlockEntity tile, int ticks) {
        for (int tick = 0; tick < ticks; tick++) tile.onUpdateServer();
    }
}
