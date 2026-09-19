package com.example.mekanismmagic.gametest;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusJeiData;
import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusRecipeViews;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.EnchantingApparatusProcessorBlockEntity;
import com.example.mekanismmagic.integration.mekenergistics.SpiritPatternMeRouter;
import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ArmorUpgradeRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import com.hollingsworth.arsnouveau.common.items.data.ArmorPerkHolder;
import com.hollingsworth.arsnouveau.common.items.data.WarpScrollData;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Exact selected inputs must survive JEI, AE admission and real apparatus execution. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArsApparatusDynamicGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 1);
    private static final BlockPos DRIVE_POS = new BlockPos(2, 2, 1);
    private static final BlockPos CPU_POS = new BlockPos(2, 1, 2);

    private ArsApparatusDynamicGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void boundWarpScrollCopyKeepsDestinationAndRejectsWrongAeOutput(
            GameTestHelper helper) {
        RecipeHolder<? extends IEnchantingRecipe> holder = recipe(helper, "warp_scroll_copy");
        ItemStack scroll = item("warp_scroll");
        WarpScrollData destination = new WarpScrollData(Optional.of(new BlockPos(91, 72, -37)),
                "minecraft:overworld", new Vec2(12, 45), false);
        scroll.set(DataComponentRegistry.WARP_SCROLL, destination);
        scroll.set(DataComponents.CUSTOM_NAME, Component.literal("Home destination"));
        List<ItemStack> inputs = inputs(holder, scroll);
        ItemStack output = exact(helper, holder, inputs);
        helper.assertTrue(output.getCount() == 2
                        && destination.equals(output.get(DataComponentRegistry.WARP_SCROLL))
                        && scroll.get(DataComponents.CUSTOM_NAME)
                        .equals(output.get(DataComponents.CUSTOM_NAME)),
                "Copied warp-scroll preview lost its bound destination or name");
        helper.assertTrue(scroll.getCount() == 1
                        && destination.equals(scroll.get(DataComponentRegistry.WARP_SCROLL)),
                "Warp-scroll preview changed its source item");
        ItemStack wrong = output.copy();
        wrong.set(DataComponentRegistry.WARP_SCROLL, new WarpScrollData(
                Optional.of(new BlockPos(92, 72, -37)), "minecraft:overworld",
                new Vec2(12, 45), false));
        helper.assertTrue(ArsApparatusRecipeViews.resolveExact(helper.getLevel(), holder,
                        List.of(item("warp_scroll"), scroll.copy())).isEmpty(),
                "A blank-reagent view advertised a different result from the machine's bound scroll");
        List<ItemStack> conflicting = List.of(scroll.copy(), wrong.copyWithCount(1));
        helper.assertTrue(ArsApparatusRecipeViews.resolveExact(
                        helper.getLevel(), holder, conflicting).isEmpty(),
                "Two differently bound scrolls advertised an arbitrary destination");
        ItemStackHandler ambiguous = new ItemStackHandler(9);
        ambiguous.setStackInSlot(0, conflicting.getFirst().copy());
        ambiguous.setStackInSlot(1, conflicting.get(1).copy());
        helper.assertTrue(ArsNouveauRecipeBridge.findApparatusRecipe(
                        helper.getLevel(), ambiguous, 0, 9).isEmpty(),
                "The real apparatus selected an arbitrary destination from two bound scrolls");
        EnchantingApparatusProcessorBlockEntity tile = machine(helper);
        assertAdmission(helper, tile, inputs, output, wrong);
        assertBridgeOutput(helper, inputs, output);
        runMachine(helper, tile, inputs, output);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 600)
    public static void enchantedNamedSwordCompletesRealAeCpuJob(
            GameTestHelper helper) {
        RecipeHolder<? extends IEnchantingRecipe> holder = recipe(helper, "enchanters_sword");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(139);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Keep the sharp blade"));
        sword.enchant(helper.getLevel().registryAccess().holderOrThrow(Enchantments.SHARPNESS), 3);
        CustomData.update(DataComponents.CUSTOM_DATA, sword,
                tag -> tag.putString("apparatus_owner", "retained"));
        List<ItemStack> inputs = inputs(holder, sword);
        ItemStack output = exact(helper, holder, inputs);
        helper.assertTrue(output.is(item("enchanters_sword").getItem())
                        && output.getDamageValue() == 0
                        && sword.get(DataComponents.CUSTOM_NAME)
                        .equals(output.get(DataComponents.CUSTOM_NAME))
                        && sword.get(DataComponents.ENCHANTMENTS)
                        .equals(output.get(DataComponents.ENCHANTMENTS))
                        && sword.get(DataComponents.CUSTOM_DATA)
                        .equals(output.get(DataComponents.CUSTOM_DATA)),
                "Enchanter sword preview lost functional input components");
        helper.assertTrue(sword.getDamageValue() == 139,
                "Enchanter sword preview repaired the actual source item");
        EnchantingApparatusProcessorBlockEntity tile = machine(helper);
        ItemStack wrong = output.copy();
        wrong.remove(DataComponents.ENCHANTMENTS);
        assertAdmission(helper, tile, inputs, output, wrong);
        assertBridgeOutput(helper, inputs, output);
        runCpu(helper, tile, inputs, output);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void enchantedArmorConversionKeepsExactSelectedComponents(
            GameTestHelper helper) {
        RecipeHolder<? extends IEnchantingRecipe> holder = recipe(helper, "battlemage_boots");
        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        boots.setDamageValue(87);
        boots.set(DataComponents.CUSTOM_NAME, Component.literal("Named enchanted boots"));
        boots.enchant(helper.getLevel().registryAccess().holderOrThrow(Enchantments.PROTECTION), 4);
        List<ItemStack> inputs = inputs(holder, boots);
        ItemStack output = exact(helper, holder, inputs);
        helper.assertTrue(output.is(item("battlemage_boots").getItem())
                        && output.getDamageValue() == 0
                        && boots.get(DataComponents.CUSTOM_NAME)
                        .equals(output.get(DataComponents.CUSTOM_NAME))
                        && boots.get(DataComponents.ENCHANTMENTS)
                        .equals(output.get(DataComponents.ENCHANTMENTS)),
                "Armor conversion preview lost the selected armor components");
        EnchantingApparatusProcessorBlockEntity tile = machine(helper);
        ItemStack wrong = output.copy();
        wrong.set(DataComponents.CUSTOM_NAME, Component.literal("Wrong armor"));
        assertAdmission(helper, tile, inputs, output, wrong);
        assertBridgeOutput(helper, inputs, output);
        runMachine(helper, tile, inputs, output);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void armorTierPreviewPreservesColorAndDoesNotMutateInput(
            GameTestHelper helper) {
        RecipeHolder<? extends IEnchantingRecipe> holder = ArsNouveauAPI.getInstance()
                .getEnchantingApparatusRecipes(helper.getLevel()).stream()
                .filter(candidate -> candidate.value() instanceof ArmorUpgradeRecipe armor
                        && armor.tier() == 2).findFirst().orElseThrow();
        ItemStack boots = item("battlemage_boots");
        boots.set(DataComponentRegistry.ARMOR_PERKS,
                new ArmorPerkHolder().setTier(1).setColor("red"));
        boots.set(DataComponents.CUSTOM_NAME, Component.literal("Tiered red boots"));
        boots.enchant(helper.getLevel().registryAccess().holderOrThrow(Enchantments.UNBREAKING), 2);
        ItemStack before = boots.copy();
        List<ItemStack> inputs = inputs(holder, boots);
        ItemStack output = exact(helper, holder, inputs);
        helper.assertTrue(output.get(DataComponentRegistry.ARMOR_PERKS).getTier() == 2
                        && output.get(DataComponentRegistry.ARMOR_PERKS).getColor().equals("red")
                        && boots.get(DataComponents.ENCHANTMENTS)
                        .equals(output.get(DataComponents.ENCHANTMENTS)),
                "Armor tier preview lost its color or preexisting enchantment");
        same(helper, before, boots, "Armor tier preview mutated the selected input");
        same(helper, output, exact(helper, holder, inputs),
                "Repeated armor tier preview changes its output components");
        ArsApparatusJeiData unavailable = new ArsApparatusJeiData(
                ResourceLocation.parse("mekanism_magic:missing_dynamic_test_recipe"), holder.value());
        helper.assertTrue(ArsApparatusRecipeViews.resolveExact(
                        helper.getLevel(), unavailable, inputs).isEmpty(),
                "An unregistered recipe view used a stale recipe object");
        EnchantingApparatusProcessorBlockEntity tile = machine(helper);
        ItemStack wrong = output.copy();
        wrong.set(DataComponentRegistry.ARMOR_PERKS,
                output.get(DataComponentRegistry.ARMOR_PERKS).setColor("blue"));
        assertAdmission(helper, tile, inputs, output, wrong);
        assertBridgeOutput(helper, inputs, output);
        runMachine(helper, tile, inputs, output);
    }

    private static RecipeHolder<? extends IEnchantingRecipe> recipe(GameTestHelper helper,
                                                                   String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ars_nouveau", path);
        return ArsNouveauAPI.getInstance().getEnchantingApparatusRecipes(helper.getLevel())
                .stream().filter(holder -> holder.id().equals(id)).findFirst().orElseThrow(() ->
                        new IllegalStateException("Missing real Ars recipe: " + id));
    }

    private static ItemStack item(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("ars_nouveau", path)));
    }

    private static List<ItemStack> inputs(RecipeHolder<? extends IEnchantingRecipe> holder,
                                           ItemStack reagent) {
        List<ItemStack> result = new ArrayList<>();
        result.add(reagent);
        ArsApparatusRecipeViews.pedestalIngredients(holder.value()).forEach(ingredient ->
                result.add(ArsNouveauRecipeBridge.representativeChoice(ingredient)));
        return result;
    }

    private static ItemStack exact(GameTestHelper helper,
                                    RecipeHolder<? extends IEnchantingRecipe> holder,
                                    List<ItemStack> inputs) {
        return ArsApparatusRecipeViews.resolveExact(helper.getLevel(), holder, inputs)
                .orElseThrow(() -> new IllegalStateException(
                        "Exact Ars preview did not resolve: " + holder.id()));
    }

    private static EnchantingApparatusProcessorBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK.get());
        EnchantingApparatusProcessorBlockEntity tile = helper.getBlockEntity(MACHINE_POS);
        tile.getComponent().addUpgrades(Upgrade.SPEED, 8);
        tile.getComponent().addUpgrades(Upgrade.ENERGY, 8);
        charge(tile);
        return tile;
    }

    private static void charge(EnchantingApparatusProcessorBlockEntity tile) {
        tile.setSource(tile.getMaxSource());
        tile.mekanismMagicEnergyContainer().setEnergy(
                tile.mekanismMagicEnergyContainer().getMaxEnergy());
    }

    private static void assertBridgeOutput(GameTestHelper helper, List<ItemStack> inputs,
                                            ItemStack expected) {
        ItemStackHandler inventory = new ItemStackHandler(9);
        for (int index = 0; index < inputs.size(); index++) {
            inventory.setStackInSlot(index, inputs.get(index).copy());
        }
        ItemStack actual = ArsNouveauRecipeBridge.findApparatusRecipe(
                helper.getLevel(), inventory, 0, 9).orElseThrow().output();
        same(helper, expected, actual, "Real apparatus recipe lookup differs from JEI preview");
    }

    private static void runMachine(GameTestHelper helper,
                                     EnchantingApparatusProcessorBlockEntity tile,
                                     List<ItemStack> inputs, ItemStack expected) {
        for (int index = 0; index < inputs.size(); index++) {
            tile.mekanismMagicPatternInputs().get(index).setStack(inputs.get(index).copy());
        }
        helper.onEachTick(() -> charge(tile));
        helper.startSequence().thenWaitUntil(() -> {
            ItemStack actual = tile.mekanismMagicPatternOutputs().getFirst().getStack();
            helper.assertFalse(actual.isEmpty(), "Real apparatus has not produced its output");
            same(helper, expected, actual, "Completed apparatus output differs from the exact preview");
            helper.assertTrue(actual.getCount() == expected.getCount(),
                    "Completed apparatus output has the wrong count");
        }).thenSucceed();
    }

    private static void assertAdmission(GameTestHelper helper,
                                         EnchantingApparatusProcessorBlockEntity tile,
                                         List<ItemStack> inputs, ItemStack output, ItemStack wrong) {
        IPatternDetails valid = PatternDetailsHelper.decodePattern(encode(inputs, output), helper.getLevel());
        helper.assertTrue(valid != null && SpiritPatternMeRouter.matchesPattern(tile, valid, counters(inputs)),
                "AE rejected the exact Ars input/output component contract");
        IPatternDetails invalid = PatternDetailsHelper.decodePattern(encode(inputs, wrong), helper.getLevel());
        helper.assertTrue(invalid != null, "AE did not decode the deliberately wrong test pattern");
        helper.assertFalse(SpiritPatternMeRouter.matchesPattern(tile, invalid, counters(inputs)),
                "AE accepted an Ars output with wrong components");
        List<ItemStack> substituted = new ArrayList<>(inputs);
        ItemStack changed = inputs.getFirst().copy();
        changed.set(DataComponents.CUSTOM_NAME, Component.literal("Undeclared input variant"));
        substituted.set(0, changed);
        helper.assertFalse(SpiritPatternMeRouter.matchesPattern(tile, valid, counters(substituted)),
                "AE accepted a dispatched input with undeclared components");
    }

    private static Map<AEItemKey, Long> amounts(List<ItemStack> inputs) {
        Map<AEItemKey, Long> amounts = new LinkedHashMap<>();
        inputs.forEach(stack -> amounts.merge(AEItemKey.of(stack), (long) stack.getCount(), Math::addExact));
        return amounts;
    }

    private static ItemStack encode(List<ItemStack> inputs, ItemStack output) {
        return PatternDetailsHelper.encodeProcessingPattern(amounts(inputs).entrySet().stream()
                        .map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList(),
                List.of(new GenericStack(AEItemKey.of(output), output.getCount())));
    }

    private static KeyCounter[] counters(List<ItemStack> inputs) {
        // AE expects one counter for each distinct declared ingredient key.
        return amounts(inputs).entrySet().stream().map(entry -> {
            KeyCounter counter = new KeyCounter();
            counter.add(entry.getKey(), entry.getValue());
            return counter;
        }).toArray(KeyCounter[]::new);
    }

    private static void runCpu(GameTestHelper helper,
                                 EnchantingApparatusProcessorBlockEntity tile,
                                 List<ItemStack> inputs, ItemStack expectedStack) {
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CPU_POS, AEBlocks.CRAFTING_STORAGE_4K.block());
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS);
        helper.assertTrue(drive.getInternalInventory().insertItem(
                        0, AEItems.ITEM_CELL_16K.stack(), false).isEmpty(),
                "Could not install the Ars crafting test cell");
        MeUpgradeRecipeMachineAdapter adapter = (MeUpgradeRecipeMachineAdapter) (Object) tile;
        helper.assertTrue(adapter.getMeUpgradeContainer().install(MeUpgradeType.PATTERN_PROVIDER)
                        .successful(), "Could not install the real apparatus ME provider");
        adapter.onMeUpgradeStateChanged();
        adapter.createMeNodeIfActive();
        AbstractMeAeSupport<?> support = adapter.getOrCreateMeUpgradeRuntime().support();
        support.getPatternSlots().getFirst().setStack(encode(inputs, expectedStack));
        support.updatePatterns();
        helper.onEachTick(() -> charge(tile));
        AEItemKey expected = AEItemKey.of(expectedStack);
        AtomicReference<Future<ICraftingPlan>> calculation = new AtomicReference<>();
        AtomicReference<ICraftingSubmitResult> submission = new AtomicReference<>();
        AtomicReference<CraftingCPUCluster> submittedCpu = new AtomicReference<>();
        AtomicReference<ICraftingLink> link = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(support.getMainNode().isActive() && drive.isPowered(),
                    "Ars crafting network did not become active");
            helper.assertFalse(support.getGrid().getCraftingService().getCpus().isEmpty(),
                    "Ars test has no real crafting CPU");
            helper.assertTrue(support.getGrid().getCraftingService().isCraftable(expected),
                    "Exact Ars pattern was not advertised");
            helper.assertTrue(support.getGrid().getStorageService().getInventory()
                            .insert(expected, 1, Actionable.SIMULATE, IActionSource.empty()) == 1,
                    "Ars crafting cell is not mounted");
        }).thenExecute(() -> amounts(inputs).forEach((key, count) ->
                helper.assertTrue(support.getGrid().getStorageService().getInventory()
                                .insert(key, count, Actionable.MODULATE, IActionSource.empty()) == count,
                        "Ars crafting cell rejected an input")))
                .thenWaitUntil(() -> amounts(inputs).forEach((key, count) ->
                        helper.assertTrue(support.getGrid().getStorageService().getCachedInventory()
                                        .get(key) == count, "Ars crafting cache has not observed its inputs")))
                .thenExecute(() -> calculation.set(support.getGrid().getCraftingService()
                        .beginCraftingCalculation(helper.getLevel(),
                                () -> actionSource(support), expected, expectedStack.getCount(),
                                CalculationStrategy.REPORT_MISSING_ITEMS)))
                .thenWaitUntil(() -> {
                    helper.assertTrue(calculation.get() != null && calculation.get().isDone(),
                            "AE is still calculating the exact Ars craft");
                    if (submission.get() == null) {
                        ICraftingPlan plan;
                        try {
                            plan = calculation.get().get();
                        } catch (Exception failure) {
                            helper.fail(brief("Ars calculation failed: " + failure));
                            return;
                        }
                        helper.assertTrue(plan != null && !plan.simulation() && plan.missingItems().isEmpty(),
                                "AE could not plan the exact Ars ingredients");
                        CraftingCPUCluster cpu = support.getGrid().getCraftingService().getCpus().stream()
                                .filter(CraftingCPUCluster.class::isInstance).map(CraftingCPUCluster.class::cast)
                                .filter(candidate -> !candidate.isBusy()
                                        && candidate.getAvailableStorage() >= plan.bytes())
                                .findFirst().orElse(null);
                        helper.assertTrue(cpu != null, "No idle real CPU can hold the Ars craft");
                        submittedCpu.set(cpu);
                        submission.set(support.getGrid().getCraftingService().submitJob(
                                plan, null, cpu, false, actionSource(support)));
                    }
                    helper.assertTrue(submission.get().successful(), brief("AE rejected the Ars craft: "
                            + submission.get().errorCode() + " / " + submission.get().errorDetail()));
                    ICraftingLink cpuLink = submittedCpu.get().craftingLogic.getLastLink();
                    helper.assertTrue(cpuLink != null, "Successful Ars task has no CPU-side crafting link");
                    link.set(cpuLink);
                }).thenWaitUntil(() -> {
                    CraftingCPUCluster cpu = submittedCpu.get();
                    helper.assertTrue(link.get() != null && !link.get().isCanceled(),
                            "The real Ars CPU task was not submitted or was canceled");
                    helper.assertTrue(!cpu.isBusy() && cpu.getJobStatus() == null,
                            "The real Ars CPU is still waiting for its exact output");
                    helper.assertTrue(support.getGrid().getCraftingService().getRequestedAmount(expected) == 0,
                            "The real Ars CPU still requests the encoded output key");
                    for (var entry : cpu.craftingLogic.getInventory().list) {
                        helper.assertTrue(entry.getLongValue() == 0,
                                "The real Ars CPU retained an internal inventory item");
                    }
                    if (!link.get().isStandalone()) {
                        helper.assertTrue(link.get().isDone(), "Ars requester link did not complete");
                    }
                    helper.assertTrue(support.getGrid().getStorageService().getInventory().getAvailableStacks()
                                    .get(expected) == expectedStack.getCount(),
                            "The completed Ars CPU task did not return the exact expected component key");
                    helper.assertTrue(tile.mekanismMagicPatternInputs().stream()
                                    .allMatch(slot -> slot.getStack().isEmpty()),
                            "The completed Ars craft retained an input");
                }).thenSucceed();
    }

    private static IActionSource actionSource(AbstractMeAeSupport<?> support) {
        return IActionSource.ofMachine(() -> support.getMainNode().getNode());
    }

    private static void same(GameTestHelper helper, ItemStack expected, ItemStack actual, String message) {
        helper.assertTrue(expected != null && actual != null && !expected.isEmpty() && !actual.isEmpty(),
                message + "; expected or actual stack is missing");
        helper.assertTrue(ItemStack.isSameItemSameComponents(expected, actual)
                        && AEItemKey.of(expected).equals(AEItemKey.of(actual)), brief(message));
    }

    private static String brief(String value) {
        return value.length() <= 980 ? value : value.substring(0, 977) + "...";
    }
}
