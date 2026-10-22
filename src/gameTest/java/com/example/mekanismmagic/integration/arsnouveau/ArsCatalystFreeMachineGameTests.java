package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** Machine-only receipt regressions. Source and FE remain required; no AE/provider mocks are used. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArsCatalystFreeMachineGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final BlockPos RESTORED = new BlockPos(4, 2, 1);
    private static final List<ResourceLocation> NATIVE_IDS = List.of(ars("imbuement_amethyst"),
            ars("imbuement_lapis"), ars("imbuement_amethyst_block"));

    private ArsCatalystFreeMachineGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void allThreeNativeNoCatalystRecipesKeepEveryRemainingReceiptAcrossMachineKinds(GameTestHelper helper) {
        for (Kind kind : Kind.values()) {
            for (ResourceLocation id : NATIVE_IDS) {
                var recipe = recipe(helper, id);
                helper.assertTrue(recipe.value().getPedestalItems().isEmpty(), "Expected a native zero-pedestal recipe: " + id);
                for (boolean signed : new boolean[]{false, true}) {
                    var tile = place(helper, POS, kind);
                    select(helper, tile, ars("imbuement_fire_essence"));
                    ItemStack selected = selected(tile).copy();
                    ItemStack input = ArsNouveauRecipeBridge.representativeChoice(recipe.value().getInput()).copyWithCount(3);
                    ItemStack marked = signed ? marked(helper, input, id) : ArsNouveauRecipeBridge.markPatternInput(input, id);
                    input(tile, 0).setStack(marked.copy());
                    setSource(tile, 3 * recipe.value().getSource());
                    untilOutput(helper, tile, 1);
                    helper.assertTrue(input(tile, 0).getCount() == 2 && ItemStack.matches(marked.copyWithCount(2), input(tile, 0).getStack())
                                    && ItemStack.matches(selected, selected(tile))
                                    && getSource(tile) == 2 * recipe.value().getSource(),
                            "First operation lost the remaining signed/legacy receipt, Source cost or selected catalyst: " + kind + "/" + id);
                    untilOutput(helper, tile, 3);
                    helper.assertTrue(input(tile, 0).isEmpty() && getSource(tile) == 0
                                    && ItemStack.matches(recipe.value().getResultItem(helper.getLevel().registryAccess()).copyWithCount(3), output(tile, 0).getStack())
                                    && !ArsNouveauRecipeBridge.hasPatternRecipeMarker(output(tile, 0).getStack())
                                    && ItemStack.matches(selected, selected(tile)),
                            "Remaining no-catalyst units selected the wrong recipe, copied metadata to output or changed shared context: " + kind + "/" + id);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void noCatalystReceiptNeverBypassesNativeSourceCost(GameTestHelper helper) {
        for (Kind kind : Kind.values()) {
            for (ResourceLocation id : NATIVE_IDS) {
                var recipe = recipe(helper, id);
                var tile = place(helper, POS, kind);
                select(helper, tile, ars("imbuement_fire_essence"));
                ItemStack beforeSelection = selected(tile).copy();
                ItemStack marked = marked(helper, ArsNouveauRecipeBridge.representativeChoice(recipe.value().getInput()), id);
                input(tile, 0).setStack(marked.copy());
                setSource(tile, 0);
                long energy = automation(tile).mekanismMagicEnergyContainer().getEnergy();
                tick(tile, 20);
                helper.assertTrue(outputCount(tile) == 0 && progress(tile, 0) == 0 && getSource(tile) == 0
                                && automation(tile).mekanismMagicEnergyContainer().getEnergy() == energy
                                && ItemStack.matches(marked, input(tile, 0).getStack()) && ItemStack.matches(beforeSelection, selected(tile)),
                        "No-catalyst was treated as zero-Source processing or changed selection while blocked: " + kind + "/" + id);
                setSource(tile, recipe.value().getSource());
                untilOutput(helper, tile, 1);
                helper.assertTrue(getSource(tile) == 0 && input(tile, 0).isEmpty(), "Resumption did not pay exactly the native Source cost");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void exactNoCatalystBatchWinsStrictComponentConflictWithoutChangingSelectedCatalyst(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        ItemStack reagent = strictReagent();
        Ingredient strict = DataComponentIngredient.of(true, reagent);
        ResourceLocation target = testId("strict_no_catalyst");
        ResourceLocation conflict = testId("strict_with_catalyst");
        var noCatalyst = new RecipeHolder<>(target, new ImbuementRecipe(strict, new ItemStack(Items.EMERALD), 250, List.of()));
        var withCatalyst = new RecipeHolder<>(conflict, new ImbuementRecipe(strict, new ItemStack(Items.DIAMOND), 250,
                List.of(Ingredient.of(Items.STICK))));
        try {
            replace(helper, original, noCatalyst, withCatalyst);
            for (Kind kind : Kind.values()) {
                var tile = place(helper, POS, kind);
                select(helper, tile, conflict);
                ItemStack selection = selected(tile).copy();
                ItemStack marked = marked(helper, reagent.copyWithCount(3), target);
                helper.assertTrue(strict.test(reagent) && !strict.test(marked),
                        "Strict ingredient fixture does not distinguish a physical receipt from the clean input");
                input(tile, 0).setStack(marked.copy());
                setSource(tile, 1_000);
                untilOutput(helper, tile, 1);
                helper.assertTrue(ItemStack.matches(marked.copyWithCount(2), input(tile, 0).getStack())
                                && ItemStack.matches(reagent.copyWithCount(2), ArsNouveauRecipeBridge.recipeInputView(input(tile, 0).getStack()))
                                && output(tile, 0).getStack().is(Items.EMERALD),
                        "Strict component matching consumed a cleaned copy or erased metadata on the physical remainder: " + kind);
                untilOutput(helper, tile, 3);
                helper.assertTrue(output(tile, 0).getStack().is(Items.EMERALD) && getSource(tile) == 250
                                && ItemStack.matches(selection, selected(tile)) && input(tile, 0).isEmpty(),
                        "The selected competing catalyst captured a later unit of the exact marked batch: " + kind);
                output(tile, 0).setEmpty();
                input(tile, 0).setStack(reagent.copy());
                untilOutput(helper, tile, 1);
                helper.assertTrue(output(tile, 0).getStack().is(Items.DIAMOND) && getSource(tile) == 0
                                && !ArsNouveauRecipeBridge.hasPatternRecipeMarker(output(tile, 0).getStack()),
                        "An exhausted exact-receipt cache captured the following unmarked catalyst recipe: " + kind);
            }
        } finally {
            manager.replaceRecipes(original);
            ArsNouveauRecipeScanner.refresh(manager);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void noCatalystLaneRunsBesideExistingCatalystWorkWithoutSwitchingItsContext(GameTestHelper helper) {
        var ordinary = recipe(helper, ars("imbuement_fire_essence"));
        var noCatalyst = recipe(helper, ars("imbuement_amethyst"));
        for (Kind kind : List.of(Kind.FACTORY, Kind.EXTRA)) {
            var tile = place(helper, POS, kind);
            select(helper, tile, ordinary.id());
            ItemStack selection = selected(tile).copy();
            input(tile, 1).setStack(ArsNouveauRecipeBridge.representativeChoice(ordinary.value().getInput()));
            setSource(tile, ordinary.value().getSource() + noCatalyst.value().getSource());
            tick(tile, 30);
            helper.assertTrue(progress(tile, 1) > 0 && outputCount(tile) == 0, "The catalyst lane did not own running work");
            input(tile, 0).setStack(marked(helper, new ItemStack(Items.AMETHYST_SHARD), noCatalyst.id()));
            untilOutput(helper, tile, 2);
            helper.assertTrue(ItemStack.matches(noCatalyst.value().getResultItem(helper.getLevel().registryAccess()), output(tile, 0).getStack())
                            && ItemStack.matches(ordinary.value().getResultItem(helper.getLevel().registryAccess()), output(tile, 1).getStack())
                            && ItemStack.matches(selection, selected(tile)) && getSource(tile) == 0,
                    "No-catalyst lane blocked/displaced other catalyst work or reused its Source payment: " + kind);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void savedMachineItemRestoresTheRemainingMarkedBatchAndCatalystSelection(GameTestHelper helper) {
        ResourceLocation id = ars("imbuement_amethyst_block");
        var recipe = recipe(helper, id);
        for (Kind kind : Kind.values()) {
            var tile = place(helper, POS, kind);
            select(helper, tile, ars("imbuement_fire_essence"));
            ItemStack selected = selected(tile).copy();
            ItemStack marked = marked(helper, new ItemStack(Items.AMETHYST_BLOCK, 3), id);
            input(tile, 0).setStack(marked.copy());
            setSource(tile, recipe.value().getSource() * 3);
            untilOutput(helper, tile, 1);
            ItemStack saved = new ItemStack(tile.getBlockState().getBlock());
            tile.saveToItem(saved, helper.getLevel().registryAccess());
            ItemStack beforeRestore = saved.copy();
            helper.setBlock(POS, Blocks.AIR);
            var restored = place(helper, RESTORED, kind);
            helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, helper.absolutePos(RESTORED), saved),
                    "Machine item omitted its serialized no-catalyst batch");
            restored.applyComponentsFromItemStack(saved);
            helper.assertTrue(ItemStack.matches(marked.copyWithCount(2), input(restored, 0).getStack())
                            && ItemStack.matches(selected, selected(restored)) && getSource(restored) == recipe.value().getSource() * 2,
                    "Loading lost the remaining receipt, Source or unrelated catalyst context: " + kind);
            untilOutput(helper, restored, 3);
            helper.assertTrue(input(restored, 0).isEmpty() && getSource(restored) == 0
                            && !ArsNouveauRecipeBridge.hasPatternRecipeMarker(output(restored, 0).getStack())
                            && ItemStack.matches(beforeRestore, saved),
                    "Reloaded batch changed its output or consumed the saved item while restoring: " + kind);
            helper.setBlock(RESTORED, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void malformedAndMismatchedMarkersNeverFallBackToAnOrdinaryRecipe(GameTestHelper helper) {
        ResourceLocation id = ars("imbuement_amethyst");
        ItemStack unrelatedData = strictReagent();
        helper.assertTrue(!ArsNouveauRecipeBridge.hasPatternRecipeMarker(unrelatedData)
                        && ArsNouveauRecipeBridge.patternRecipe(unrelatedData) == null,
                "Unrelated CUSTOM_DATA was parsed as an empty minecraft: recipe marker");
        List<ItemStack> invalid = new ArrayList<>();
        invalid.add(ArsNouveauRecipeBridge.markPatternInput(new ItemStack(Items.AMETHYST_SHARD), testId("missing_receipt_recipe")));
        invalid.add(ArsNouveauRecipeBridge.markPatternInput(new ItemStack(Items.AMETHYST_SHARD), id, ""));
        invalid.add(ArsNouveauRecipeBridge.markPatternInput(new ItemStack(Items.AMETHYST_SHARD), id, "wrong-signature"));
        for (boolean signatureOnly : new boolean[]{false, true}) {
            ItemStack stack = new ItemStack(Items.AMETHYST_SHARD);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putString(
                    signatureOnly ? "pattern_recipe_signature" : "pattern_recipe", "not a valid receipt"));
            invalid.add(stack);
        }
        for (String blank : new String[]{"", "   "}) {
            ItemStack stack = new ItemStack(Items.AMETHYST_SHARD);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putString("pattern_recipe", blank));
            helper.assertTrue(ArsNouveauRecipeBridge.hasPatternRecipeMarker(stack)
                            && ArsNouveauRecipeBridge.patternRecipe(stack) == null,
                    "A present but blank recipe marker was accepted as a valid ID");
            invalid.add(stack);
        }
        for (Kind kind : Kind.values()) {
            for (ItemStack marked : invalid) {
                var tile = place(helper, POS, kind);
                select(helper, tile, ars("imbuement_fire_essence"));
                ItemStack selection = selected(tile).copy();
                input(tile, 0).setStack(marked.copy());
                setSource(tile, 5_000);
                long energy = automation(tile).mekanismMagicEnergyContainer().getEnergy();
                tick(tile, 20);
                helper.assertTrue(outputCount(tile) == 0 && progress(tile, 0) == 0 && getSource(tile) == 5_000
                                && automation(tile).mekanismMagicEnergyContainer().getEnergy() == energy
                                && ItemStack.matches(marked, input(tile, 0).getStack()) && ItemStack.matches(selection, selected(tile)),
                        "Malformed metadata silently ran an ordinary no-catalyst recipe or switched context: " + kind);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void outputCountOnlyReloadInvalidatesTheOldSignedBatchUntilExplicitlyRebound(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        ResourceLocation id = testId("count_sensitive_no_catalyst");
        var initial = new RecipeHolder<>(id, new ImbuementRecipe(Ingredient.of(Items.PAPER), new ItemStack(Items.EMERALD), 250, List.of()));
        var doubled = new RecipeHolder<>(id, new ImbuementRecipe(Ingredient.of(Items.PAPER), new ItemStack(Items.EMERALD, 2), 250, List.of()));
        try {
            for (Kind kind : Kind.values()) {
                replace(helper, original, initial);
                var tile = place(helper, POS, kind);
                ItemStack oldReceipt = marked(helper, new ItemStack(Items.PAPER, 2), id);
                input(tile, 0).setStack(oldReceipt.copy());
                setSource(tile, 500);
                tick(tile, 37);
                helper.assertTrue(progress(tile, 0) > 0 && outputCount(tile) == 0, "Signed reload fixture never began");
                long energy = automation(tile).mekanismMagicEnergyContainer().getEnergy();
                replace(helper, original, doubled);
                tick(tile, 20);
                helper.assertTrue(progress(tile, 0) == 0 && outputCount(tile) == 0 && getSource(tile) == 500
                                && automation(tile).mekanismMagicEnergyContainer().getEnergy() == energy
                                && ItemStack.matches(oldReceipt, input(tile, 0).getStack()),
                        "A count-only recipe reload reused an old signed batch or silently unmarked it: " + kind);
                input(tile, 0).setStack(marked(helper, new ItemStack(Items.PAPER, 2), id));
                untilOutput(helper, tile, 4);
                helper.assertTrue(output(tile, 0).getStack().is(Items.EMERALD) && input(tile, 0).isEmpty() && getSource(tile) == 0,
                        "Explicitly rebound recipe did not use the new output count and exact operation cost: " + kind);
            }
        } finally {
            manager.replaceRecipes(original);
            ArsNouveauRecipeScanner.refresh(manager);
        }
        helper.succeed();
    }

    private enum Kind { SINGLE, FACTORY, EXTRA }

    private static TileEntityMekanism place(GameTestHelper helper, BlockPos pos, Kind kind) {
        helper.setBlock(pos, Blocks.AIR);
        Block block = switch (kind) {
            case SINGLE -> ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get();
            case FACTORY -> ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get();
            case EXTRA -> MekanismExtrasImbuementFactories.INFINITE_BLOCK.get();
        };
        helper.setBlock(pos, block);
        TileEntityMekanism tile = helper.getBlockEntity(pos);
        if (tile instanceof ImbuementFactoryBlockEntity factory && factory.isSorting()) factory.toggleSorting();
        if (tile instanceof ExtraImbuementFactoryBlockEntity factory && factory.isSorting()) factory.toggleSorting();
        var energy = automation(tile).mekanismMagicEnergyContainer();
        energy.setEnergy(energy.getMaxEnergy());
        setSource(tile, 0);
        return tile;
    }

    private static void select(GameTestHelper helper, TileEntityMekanism tile, ResourceLocation id) {
        ItemStack identifier = ArsNouveauRecipeBridge.createIdentifierForRecipe(helper.getLevel(), id);
        helper.assertTrue(ArsNouveauRecipeBridge.identifierMatchesRecipe(
                        helper.getLevel(), identifier, id),
                "The unrelated catalyst fixture lacks a current recipe signature");
        List<IInventorySlot> library = automation(tile)
                .mekanismMagicPersistentInputs();
        helper.assertTrue(!library.isEmpty(),
                "The test machine has no retained catalyst library slot");
        library.getFirst().setStack(identifier.copy());
        helper.assertTrue(((CatalystIdentifierSelectionHost) tile).selectCatalystIdentifierId(
                        CatalystIdentifierItem.catalystId(identifier).toString()), "Unable to select the test's unrelated catalyst");
    }

    private static ItemStack selected(TileEntityMekanism tile) { return ((CatalystIdentifierSelectionHost) tile).selectedCatalystIdentifier(); }
    private static IMekanismMagicAutomation automation(TileEntityMekanism tile) { return (IMekanismMagicAutomation) tile; }
    private static IInventorySlot input(TileEntityMekanism tile, int lane) { return automation(tile).mekanismMagicPatternInputs().get(lane); }
    private static IInventorySlot output(TileEntityMekanism tile, int lane) { return automation(tile).mekanismMagicPatternOutputs().get(lane); }
    private static int outputCount(TileEntityMekanism tile) { return automation(tile).mekanismMagicPatternOutputs().stream().mapToInt(IInventorySlot::getCount).sum(); }

    private static void setSource(TileEntityMekanism tile, int amount) {
        if (tile instanceof ImbuementProcessorBlockEntity single) single.setSource(amount);
        else if (tile instanceof ImbuementFactoryBlockEntity factory) factory.getSourceStorage().setSource(amount);
        else ((ExtraImbuementFactoryBlockEntity) tile).getSourceStorage().setSource(amount);
    }

    private static int getSource(TileEntityMekanism tile) {
        if (tile instanceof ImbuementProcessorBlockEntity single) return single.getSource();
        if (tile instanceof ImbuementFactoryBlockEntity factory) return factory.getSource();
        return ((ExtraImbuementFactoryBlockEntity) tile).getSource();
    }

    private static int progress(TileEntityMekanism tile, int lane) {
        if (tile instanceof NativeMagicMachineBlockEntity single) return single.getProgress();
        if (tile instanceof ImbuementFactoryBlockEntity factory) return factory.getProgress(lane);
        return ((ExtraImbuementFactoryBlockEntity) tile).getProgress(lane);
    }

    private static void tick(TileEntityMekanism tile, int ticks) {
        try {
            var update = tile.getClass().getDeclaredMethod("onUpdateServer");
            update.setAccessible(true);
            for (int index = 0; index < ticks; index++) update.invoke(tile);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Real imbuement update failed", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to reach the real machine's protected update hook", exception);
        }
    }

    private static void untilOutput(GameTestHelper helper, TileEntityMekanism tile, int count) {
        for (int ticks = 0; ticks < 600 && outputCount(tile) < count; ticks++) tick(tile, 1);
        helper.assertTrue(outputCount(tile) == count, "The expected bounded imbuement operations did not complete: "
                + tile.getClass().getSimpleName() + ", expected=" + count + ", actual=" + outputCount(tile)
                + ", progress=" + progress(tile, 0) + ", Source=" + getSource(tile)
                + ", input=" + input(tile, 0).getStack() + ", marked="
                + ArsNouveauRecipeBridge.hasPatternRecipeMarker(input(tile, 0).getStack()));
    }

    private static RecipeHolder<ImbuementRecipe> recipe(GameTestHelper helper, ResourceLocation id) {
        var recipe = ArsNouveauRecipeScanner.find(helper.getLevel().getRecipeManager(), id);
        helper.assertTrue(recipe != null, "Missing native imbuement fixture: " + id);
        return recipe;
    }

    private static ItemStack marked(GameTestHelper helper, ItemStack input, ResourceLocation id) {
        return ArsNouveauRecipeBridge.markPatternInput(input, id,
                ArsNouveauRecipeScanner.semanticSignature(helper.getLevel().getRecipeManager(), id));
    }

    private static ItemStack strictReagent() {
        ItemStack input = new ItemStack(Items.PAPER);
        CompoundTag data = new CompoundTag();
        data.putString("strict_payload", "preserve this exact component");
        input.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return input;
    }

    private static void replace(GameTestHelper helper, List<RecipeHolder<?>> original, RecipeHolder<?>... additions) {
        List<RecipeHolder<?>> recipes = new ArrayList<>(original);
        for (RecipeHolder<?> addition : additions) {
            recipes.removeIf(existing -> existing.id().equals(addition.id()));
            recipes.add(addition);
        }
        helper.getLevel().getRecipeManager().replaceRecipes(recipes);
        ArsNouveauRecipeScanner.refresh(helper.getLevel().getRecipeManager());
    }

    private static ResourceLocation ars(String path) { return ResourceLocation.fromNamespaceAndPath("ars_nouveau", path); }
    private static ResourceLocation testId(String path) { return ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, path); }
}
