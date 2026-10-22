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
import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantmentRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ReactiveEnchantmentRecipe;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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


    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 600)
    public static void reactiveBookKeepsWrittenSpellAndCompletesRealAeCpuJob(
            GameTestHelper helper) {
        var holder = recipe(helper, "reactive_1");
        List<ItemStack> selected = reactiveInputs(holder, new ItemStack(Items.BOOK), "Reactive audit");
        ItemStack parchmentBefore = selected.get(1).copy();
        ItemStack nativeOutput = nativeOutput(helper, holder, selected);
        ItemStack output = exact(helper, holder, selected);
        same(helper, nativeOutput, output, "Reactive preview differs from native Ars assembly");
        helper.assertTrue(output.is(Items.ENCHANTED_BOOK)
                        && output.has(DataComponentRegistry.REACTIVE_CASTER.get())
                        && output.get(DataComponentRegistry.REACTIVE_CASTER).getSpell()
                        .equals(SpellCasterRegistry.from(selected.get(1)).getSpell()),
                "Reactive book lost its written parchment spell");
        same(helper, parchmentBefore, selected.get(1), "Reactive assembly mutated the parchment");
        var tile = machine(helper);
        ItemStack wrong = output.copy();
        wrong.remove(DataComponentRegistry.REACTIVE_CASTER.get());
        assertAdmission(helper, tile, selected, output, wrong);
        assertBridgeOutput(helper, selected, output);
        runCpu(helper, tile, selected, output);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 600)
    public static void reactiveBooksCraftAllFourLevelsInOneMachine(GameTestHelper helper) {
        var tile = machine(helper);
        helper.onEachTick(() -> charge(tile));
        var sequence = helper.startSequence();
        AtomicReference<ItemStack> reagent = new AtomicReference<>(new ItemStack(Items.BOOK));
        AtomicReference<ItemStack> expected = new AtomicReference<>();
        for (int target = 1; target <= 4; target++) {
            final int level = target;
            sequence.thenExecute(() -> {
                var holder = recipe(helper, "reactive_" + level);
                List<ItemStack> selected = level == 1
                        ? reactiveInputs(holder, reagent.get(), "Retained spell")
                        : inputs(holder, reagent.get());
                ItemStack result = exact(helper, holder, selected);
                same(helper, nativeOutput(helper, holder, selected), result,
                        "Reactive upgrade differs from native output at level " + level);
                var enchantment = ((EnchantmentRecipe) holder.value()).holderFor(helper.getLevel());
                helper.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(result)
                                .getLevel(enchantment) == level,
                        "Wrong reactive stored enchantment level");
                if (level > 1) {
                    helper.assertTrue(result.get(DataComponentRegistry.REACTIVE_CASTER)
                                    .equals(reagent.get().get(DataComponentRegistry.REACTIVE_CASTER)),
                            "Upgrading reactive book lost the spell");
                }
                ItemStack wrong = result.copy();
                wrong.remove(DataComponentRegistry.REACTIVE_CASTER.get());
                assertAdmission(helper, tile, selected, result, wrong);
                expected.set(result);
                for (int slot = 0; slot < selected.size(); slot++) {
                    tile.mekanismMagicPatternInputs().get(slot).setStack(selected.get(slot).copy());
                }
            }).thenWaitUntil(() -> {
                ItemStack actual = tile.mekanismMagicPatternOutputs().getFirst().getStack();
                helper.assertFalse(actual.isEmpty(), "Waiting for reactive level " + level);
                same(helper, expected.get(), actual, "Machine changed the reactive book");
                helper.assertTrue(actual.getCount() == 1
                                && tile.mekanismMagicPatternInputs().stream()
                                .allMatch(slot -> slot.getStack().isEmpty()),
                        "Reactive machine did not consume exactly one batch");
            }).thenExecute(() -> {
                reagent.set(tile.mekanismMagicPatternOutputs().getFirst().getStack().copy());
                reagent.get().set(DataComponents.CUSTOM_NAME, Component.literal("Reactive audit book"));
                tile.mekanismMagicPatternOutputs().getFirst().setStack(ItemStack.EMPTY);
            });
        }
        sequence.thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 4000)
    public static void allNativeEnchantmentBookRecipesCraftAndRejectWrongLevels(
            GameTestHelper helper) {
        var recipes = ArsNouveauAPI.getInstance().getEnchantingApparatusRecipes(helper.getLevel())
                .stream().filter(holder -> holder.value() instanceof EnchantmentRecipe).toList();
        helper.assertTrue(recipes.size() > 4, "Expected all native enchantment recipes, not only Reactive");
        var tile = machine(helper);
        helper.onEachTick(() -> charge(tile));
        var sequence = helper.startSequence();
        AtomicReference<ItemStack> expected = new AtomicReference<>();
        List<String> missingDefaultPreviews = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();
        for (var holder : recipes) {
            var enchantment = (EnchantmentRecipe) holder.value();
            ItemStack reagent = new ItemStack(enchantment.enchantLevel() == 1
                    ? Items.BOOK : Items.ENCHANTED_BOOK);
            if (enchantment.enchantLevel() > 1) {
                reagent.enchant(enchantment.holderFor(helper.getLevel()), enchantment.enchantLevel() - 1);
                reagent.set(DataComponents.CUSTOM_NAME, Component.literal("Enchantment audit book"));
            }
            List<ItemStack> selected = enchantment instanceof ReactiveEnchantmentRecipe
                    ? reactiveInputs(holder, reagent, "Native matrix") : inputs(holder, reagent);
            ItemStack result = nativeOutput(helper, holder, selected);
            boolean hasDefault = ArsApparatusRecipeViews.defaultReagents(helper.getLevel(), enchantment)
                    .stream().anyMatch(candidate -> ArsApparatusRecipeViews.resolveExact(
                            helper.getLevel(), holder, inputs(holder, candidate)).isPresent());
            if (!hasDefault) {
                missingDefaultPreviews.add(holder.id().toString());
            }
            var resolved = ArsApparatusRecipeViews.resolveExact(helper.getLevel(), holder, selected);
            if (resolved.isEmpty()) {
                ItemStackHandler inventory = new ItemStackHandler(9);
                for (int slot = 0; slot < selected.size(); slot++) {
                    inventory.setStackInSlot(slot, selected.get(slot).copy());
                }
                var chosen = ArsNouveauRecipeBridge.findApparatusRecipe(helper.getLevel(), inventory, 0, 9);
                String detail = holder.id() + " -> " + chosen.map(value -> value.id()
                        + " uses=" + value.inputs() + " output=" + value.output().getComponentsPatch())
                        .orElse("no machine match");
                mismatches.add(holder.id().toString());
                MekanismMagic.LOGGER.error("APPARATUS_AUDIT native-valid machine mismatch {}", detail);
                continue;
            }
            same(helper, result, resolved.orElseThrow(),
                    "Native enchantment assembly mismatch: " + holder.id());
            helper.assertTrue(result.is(Items.ENCHANTED_BOOK)
                            && EnchantmentHelper.getEnchantmentsForCrafting(result)
                            .getLevel(enchantment.holderFor(helper.getLevel())) == enchantment.enchantLevel(),
                    "Book does not carry its stored enchantment: " + holder.id());
            assertBridgeOutput(helper, selected, result);
            ItemStack wrongOutput = result.copy();
            wrongOutput.remove(DataComponents.STORED_ENCHANTMENTS);
            assertAdmission(helper, tile, selected, result, wrongOutput);
            List<ItemStack> alreadyAtTarget = new ArrayList<>(selected);
            alreadyAtTarget.set(0, result.copy());
            helper.assertTrue(ArsApparatusRecipeViews.resolveExact(
                            helper.getLevel(), holder, alreadyAtTarget).isEmpty(),
                    "Accepted a book already at the target level: " + holder.id());
            if (enchantment.enchantLevel() > 1) {
                List<ItemStack> skippedLevel = new ArrayList<>(selected);
                skippedLevel.set(0, new ItemStack(Items.BOOK));
                helper.assertTrue(ArsApparatusRecipeViews.resolveExact(
                                helper.getLevel(), holder, skippedLevel).isEmpty(),
                        "Accepted skipping enchantment levels: " + holder.id());
            }
            sequence.thenExecute(() -> {
                expected.set(result);
                for (int slot = 0; slot < selected.size(); slot++) {
                    tile.mekanismMagicPatternInputs().get(slot).setStack(selected.get(slot).copy());
                }
            }).thenWaitUntil(() -> {
                ItemStack actual = tile.mekanismMagicPatternOutputs().getFirst().getStack();
                helper.assertFalse(actual.isEmpty(), "Waiting for " + holder.id());
                same(helper, expected.get(), actual, "Wrong real machine output: " + holder.id());
                helper.assertTrue(actual.getCount() == 1
                                && tile.mekanismMagicPatternInputs().stream()
                                .allMatch(slot -> slot.getStack().isEmpty()),
                        "Incorrect input consumption: " + holder.id());
            }).thenExecute(() ->
                    tile.mekanismMagicPatternOutputs().getFirst().setStack(ItemStack.EMPTY));
        }
        MekanismMagic.LOGGER.info("APPARATUS_AUDIT native enchantment recipes={}, missing default JEI previews={} {}",
                recipes.size(), missingDefaultPreviews.size(), missingDefaultPreviews);
        sequence.thenExecute(() -> {
            MekanismMagic.LOGGER.info("APPARATUS_AUDIT completed real machine recipes={}, mismatches={} {}",
                    recipes.size() - mismatches.size(), mismatches.size(), mismatches);
            helper.assertTrue(mismatches.isEmpty(), brief("Native-valid recipes fail machine lookup: " + mismatches));
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void protectionFourBookMustNotBeHijackedByPiercingOne(GameTestHelper helper) {
        var holder = recipe(helper, "protection_4");
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(helper.getLevel().registryAccess().holderOrThrow(Enchantments.PROTECTION), 3);
        List<ItemStack> selected = inputs(holder, book);
        ItemStack expected = nativeOutput(helper, holder, selected);
        var tile = machine(helper);
        for (int slot = 0; slot < selected.size(); slot++) {
            tile.mekanismMagicPatternInputs().get(slot).setStack(selected.get(slot).copy());
        }
        helper.onEachTick(() -> charge(tile));
        helper.startSequence().thenWaitUntil(() -> {
            ItemStack actual = tile.mekanismMagicPatternOutputs().getFirst().getStack();
            helper.assertFalse(actual.isEmpty(), "Waiting for a real protection IV output");
        }).thenExecute(() -> {
            ItemStack actual = tile.mekanismMagicPatternOutputs().getFirst().getStack();
            MekanismMagic.LOGGER.info("APPARATUS_AUDIT protection IV real output={}, remaining inputs={}",
                    actual.getComponentsPatch(), tile.mekanismMagicPatternInputs().stream()
                            .map(slot -> slot.getStack().toString()).toList());
            same(helper, expected, actual, "Protection IV ingredients produced the wrong real book");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void reactiveBlankAndWrittenPedestalSelectionAudit(GameTestHelper helper) {
        var holder = recipe(helper, "reactive_1");
        List<ItemStack> blank = inputs(holder, new ItemStack(Items.BOOK));
        helper.assertTrue(ArsApparatusRecipeViews.assembleExact(
                        helper.getLevel(), holder.value(), blank).isEmpty(),
                "Native Reactive I must reject an unwritten parchment");
        List<ItemStack> written = reactiveInputs(holder, new ItemStack(Items.BOOK), "Written spell A");
        ItemStack expected = exact(helper, holder, written);
        ItemStackHandler inventory = new ItemStackHandler(9);
        for (int slot = 0; slot < blank.size(); slot++) {
            inventory.setStackInSlot(slot, blank.get(slot).copy());
        }
        inventory.setStackInSlot(4, written.get(1).copy());
        var blankFirst = ArsNouveauRecipeBridge.findApparatusRecipe(helper.getLevel(), inventory, 0, 9);
        inventory.setStackInSlot(1, written.get(1).copy());
        inventory.setStackInSlot(4, blank.get(1).copy());
        var writtenFirst = ArsNouveauRecipeBridge.findApparatusRecipe(helper.getLevel(), inventory, 0, 9);
        // Reactive applies to every item, including another parchment. With
        // surplus reagents an unordered inventory can deliberately reject an
        // ambiguous result; record it separately from an exact recipe failure.
        writtenFirst.ifPresent(value -> same(helper, expected, value.output(),
                "Wrong written parchment output"));

        ItemStack other = writtenParchment("Written spell B");
        inventory.setStackInSlot(4, other);
        var firstVariant = ArsNouveauRecipeBridge.findApparatusRecipe(helper.getLevel(), inventory, 0, 9);
        inventory.setStackInSlot(1, other);
        inventory.setStackInSlot(4, written.get(1).copy());
        var secondVariant = ArsNouveauRecipeBridge.findApparatusRecipe(helper.getLevel(), inventory, 0, 9);
        boolean ambiguousSlotDependent = firstVariant.isPresent() && secondVariant.isPresent()
                && !ItemStack.isSameItemSameComponents(
                        firstVariant.orElseThrow().output(), secondVariant.orElseThrow().output());
        MekanismMagic.LOGGER.info("APPARATUS_AUDIT reactive blank-first={}, written-first={}, different spells slot-dependent={}",
                blankFirst.isPresent(), writtenFirst.isPresent(), ambiguousSlotDependent);
        MekanismMagic.LOGGER.info("APPARATUS_AUDIT reactive blank-first output={}",
                blankFirst.map(value -> value.output() + " " + value.output().getComponentsPatch())
                        .orElse("none"));
        helper.assertTrue(blankFirst.isEmpty() && writtenFirst.isEmpty()
                        && firstVariant.isEmpty() && secondVariant.isEmpty(),
                "Reactive crafting changes when blank and written parchments swap unordered input slots");
        helper.succeed();
    }


    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 600)
    public static void writtenParchmentJeiFocusCompletesRealAeJob(GameTestHelper helper) {
        var holder = recipe(helper, "reactive_1");
        ItemStack parchment = writtenParchment("JEI selected actual spell");
        ItemStack before = parchment.copy();
        var selected = ArsApparatusRecipeViews.selectInputs(helper.getLevel(), holder.value(), List.of(parchment));
        helper.assertTrue(selected.valid(), "JEI rejected a real parchment focus");
        List<ItemStack> exactInputs = new ArrayList<>();
        exactInputs.add(selected.reagents().stream().filter(stack -> stack.is(Items.BOOK)).findFirst().orElseThrow());
        exactInputs.addAll(selected.pedestals());
        helper.assertTrue(selected.pedestals().stream().anyMatch(stack ->
                        ItemStack.isSameItemSameComponents(stack, parchment)),
                "JEI replaced the actual spell with a blank pedestal");
        ItemStack expected = nativeOutput(helper, holder, exactInputs);
        same(helper, expected, exact(helper, holder, exactInputs), "Focused JEI differs from native output");
        same(helper, before, parchment, "JEI focus selection mutated the player's parchment");
        var tile = machine(helper);
        ItemStack wrong = expected.copy();
        wrong.remove(DataComponentRegistry.REACTIVE_CASTER.get());
        assertAdmission(helper, tile, exactInputs, expected, wrong);
        runCpu(helper, tile, exactInputs, expected);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void jeiBrowseHidesOnlyUnresolvedApparatusPreviews(GameTestHelper helper) {
        var level = helper.getLevel();
        var reactive = recipe(helper, "reactive_1");
        var reactiveView = new ArsApparatusJeiData(reactive.id(), reactive.value());
        helper.assertFalse(reactiveView.equals(new ArsApparatusJeiData(
                        reactive.id(), reactive.value(), true)),
                "JEI must not hide a focused lookup with an old browse-page identity");
        helper.assertFalse(ArsApparatusRecipeViews.hasResolvedPreview(level, reactiveView, List.of()),
                "Unfocused Reactive I must not show an outputless JEI page");
        helper.assertFalse(ArsApparatusRecipeViews.hasResolvedPreview(level, reactiveView,
                        List.of(item("spell_parchment"))),
                "An unwritten parchment must not make the Reactive I page visible");
        ItemStack written = writtenParchment("Focused JEI usage");
        helper.assertTrue(ArsApparatusRecipeViews.hasResolvedPreview(level, reactiveView,
                        List.of(written)),
                "A real written parchment must retain its Reactive I usage page");

        var sharpnessTwo = recipe(helper, "sharpness_2");
        var sharpnessTwoView = new ArsApparatusJeiData(sharpnessTwo.id(), sharpnessTwo.value());
        helper.assertFalse(ArsApparatusRecipeViews.hasResolvedPreview(level, sharpnessTwoView,
                        List.of()),
                "Sharpness II without the previous level must not show a blank JEI page");
        ItemStack previousLevel = new ItemStack(Items.ENCHANTED_BOOK);
        previousLevel.enchant(((EnchantmentRecipe) sharpnessTwo.value()).holderFor(level), 1);
        helper.assertTrue(ArsApparatusRecipeViews.hasResolvedPreview(level, sharpnessTwoView,
                        List.of(previousLevel)),
                "A real Sharpness I book must retain the Sharpness II usage page");
        helper.assertFalse(ArsApparatusRecipeViews.hasResolvedPreview(level, sharpnessTwoView,
                        List.of(new ItemStack(Items.BOOK))),
                "A plain book must not advertise a skipped Sharpness II upgrade");

        var sharpnessOne = recipe(helper, "sharpness_1");
        var sharpnessOneView = new ArsApparatusJeiData(sharpnessOne.id(), sharpnessOne.value());
        helper.assertTrue(ArsApparatusRecipeViews.hasResolvedPreview(level, sharpnessOneView,
                        List.of()),
                "Sharpness I has a real default output and must remain browsable");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void jeiFocusDoesNotInventSpellsOrReplaceInvalidReagents(GameTestHelper helper) {
        var reactive = recipe(helper, "reactive_1");
        for (List<ItemStack> focus : List.of(List.<ItemStack>of(), List.of(item("spell_parchment")))) {
            var selected = ArsApparatusRecipeViews.selectInputs(helper.getLevel(), reactive.value(), focus);
            for (ItemStack reagent : selected.reagents()) {
                List<ItemStack> exact = new ArrayList<>();
                exact.add(reagent);
                exact.addAll(selected.pedestals());
                helper.assertTrue(ArsApparatusRecipeViews.resolveExact(
                        helper.getLevel(), reactive, exact).isEmpty(), "JEI invented a spell");
            }
        }
        var conflict = ArsApparatusRecipeViews.selectInputs(helper.getLevel(), reactive.value(),
                List.of(writtenParchment("A"), writtenParchment("B")));
        helper.assertFalse(conflict.valid(), "JEI silently chose one of two conflicting spells");
        var upgrade = recipe(helper, "reactive_2");
        ItemStack wrongBook = new ItemStack(Items.BOOK);
        wrongBook.set(DataComponents.CUSTOM_NAME, Component.literal("Wrong input must remain selected"));
        var selected = ArsApparatusRecipeViews.selectInputs(helper.getLevel(), upgrade.value(), List.of(wrongBook));
        helper.assertTrue(selected.reagents().size() == 1
                && ItemStack.matches(wrongBook, selected.reagents().getFirst()), "JEI replaced an invalid input");
        List<ItemStack> exact = new ArrayList<>(selected.reagents());
        exact.addAll(selected.pedestals());
        helper.assertTrue(ArsApparatusRecipeViews.resolveExact(helper.getLevel(), upgrade, exact).isEmpty(),
                "JEI allowed skipping an enchantment level");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void conflictingRecipeSelectionIsIndependentOfSlotOrder(GameTestHelper helper) {
        for (String id : List.of("protection_2", "protection_3", "protection_4", "sweeping_edge_2",
                "sweeping_edge_3", "depth_strider_1", "depth_strider_2", "depth_strider_3", "knockback_2")) {
            var holder = recipe(helper, id);
            var enchantment = (EnchantmentRecipe) holder.value();
            ItemStack reagent = new ItemStack(enchantment.enchantLevel() == 1 ? Items.BOOK : Items.ENCHANTED_BOOK);
            if (enchantment.enchantLevel() > 1) reagent.enchant(
                    enchantment.holderFor(helper.getLevel()), enchantment.enchantLevel() - 1);
            List<ItemStack> selected = inputs(holder, reagent);
            ItemStack expected = nativeOutput(helper, holder, selected);
            for (int shift = 0; shift < 9; shift++) {
                ItemStackHandler inventory = new ItemStackHandler(9);
                for (int slot = 0; slot < selected.size(); slot++)
                    inventory.setStackInSlot((slot + shift) % 9, selected.get(slot).copy());
                var found = ArsNouveauRecipeBridge.findApparatusRecipe(helper.getLevel(), inventory, 0, 9).orElseThrow();
                helper.assertTrue(found.id().equals(holder.id()), "Slot rotation selected a different recipe: " + id);
                same(helper, expected, found.output(), "Slot rotation changed output");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 600)
    public static void protectionFourCraftsTwoBatchesWithoutDowngrading(GameTestHelper helper) {
        var holder = recipe(helper, "protection_4");
        ItemStack reagent = new ItemStack(Items.ENCHANTED_BOOK);
        reagent.enchant(helper.getLevel().registryAccess().holderOrThrow(Enchantments.PROTECTION), 3);
        List<ItemStack> selected = inputs(holder, reagent);
        ItemStack expected = nativeOutput(helper, holder, selected);
        var tile = machine(helper);
        for (int slot = 1; slot < selected.size(); slot++)
            tile.mekanismMagicPatternInputs().get(slot).setStack(selected.get(slot).copyWithCount(2));
        helper.onEachTick(() -> charge(tile));
        var sequence = helper.startSequence();
        for (int operation = 0; operation < 2; operation++) {
            sequence.thenExecute(() -> tile.mekanismMagicPatternInputs().getFirst().setStack(reagent.copy()))
                    .thenWaitUntil(() -> {
                        var output = tile.mekanismMagicPatternOutputs().getFirst().getStack();
                        helper.assertFalse(output.isEmpty(), "Waiting for the protection IV batch");
                        same(helper, expected, output, "A repeated batch selected a material subset");
                    }).thenExecute(() -> tile.mekanismMagicPatternOutputs().getFirst().setStack(ItemStack.EMPTY));
        }
        sequence.thenExecute(() -> helper.assertTrue(tile.mekanismMagicPatternInputs().stream()
                .allMatch(slot -> slot.getStack().isEmpty()), "Two operations did not consume exactly two batches"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void mixedParchmentsNeverConsumeAnUnrelatedBook(GameTestHelper helper) {
        var tile = machine(helper);
        var selected = reactiveInputs(recipe(helper, "reactive_1"), new ItemStack(Items.BOOK), "Ambiguous input");
        selected.add(item("spell_parchment"));
        for (int slot = 0; slot < selected.size(); slot++)
            tile.mekanismMagicPatternInputs().get(slot).setStack(selected.get(slot).copy());
        helper.onEachTick(() -> charge(tile));
        helper.startSequence().thenExecuteAfter(120, () -> {
            helper.assertTrue(tile.mekanismMagicPatternOutputs().getFirst().getStack().isEmpty(),
                    "A mixed ambiguous batch produced an unintended item");
            for (int slot = 0; slot < selected.size(); slot++)
                helper.assertTrue(ItemStack.matches(selected.get(slot),
                        tile.mekanismMagicPatternInputs().get(slot).getStack()), "Ambiguous batch consumed input");
        }).thenSucceed();
    }


    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void higherBookUsageIndexCarriesTheRequiredPreviousLevel(GameTestHelper helper) {
        com.example.mekanismmagic.integration.arsnouveau.client.ArsApparatusJeiReagents.clear();
        int checked = 0;
        for (var holder : ArsNouveauAPI.getInstance().getEnchantingApparatusRecipes(helper.getLevel())) {
            if (!(holder.value() instanceof EnchantmentRecipe enchantment) || enchantment.enchantLevel() < 2) continue;
            ItemStack previous = new ItemStack(Items.ENCHANTED_BOOK);
            previous.enchant(enchantment.holderFor(helper.getLevel()), enchantment.enchantLevel() - 1);
            var indexed = com.example.mekanismmagic.integration.arsnouveau.client.ArsApparatusJeiReagents
                    .indexReagents(helper.getLevel(), enchantment);
            // JEI's book subtype is the complete stored-enchantment value,
            // so an unenchanted book does not index this actual usage.
            helper.assertTrue(indexed.stream().anyMatch(stack -> stack.is(Items.ENCHANTED_BOOK)
                            && EnchantmentHelper.getEnchantmentsForCrafting(stack)
                            .equals(EnchantmentHelper.getEnchantmentsForCrafting(previous))),
                    "JEI cannot find the required previous-level book: " + holder.id());
            helper.assertTrue(ArsApparatusRecipeViews.defaultReagents(helper.getLevel(), enchantment)
                            .stream().filter(stack -> stack.is(Items.ENCHANTED_BOOK))
                            .allMatch(stack -> EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty()),
                    "A lookup example leaked into an unselected concrete JEI input");
            checked++;
        }
        helper.assertTrue(checked > 50, "Expected the full higher-level book lookup matrix");
        MekanismMagic.LOGGER.info("APPARATUS_FIX indexed previous-level books={}", checked);
        helper.succeed();
    }

    private static ItemStack writtenParchment(String name) {
        ItemStack parchment = item("spell_parchment");
        SpellCasterRegistry.from(parchment).setSpell(
                new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE).withName(name)).saveToStack(parchment);
        return parchment;
    }

    private static List<ItemStack> reactiveInputs(
            RecipeHolder<? extends IEnchantingRecipe> holder, ItemStack reagent, String spellName) {
        List<ItemStack> selected = inputs(holder, reagent);
        for (int slot = 1; slot < selected.size(); slot++) {
            if (selected.get(slot).is(item("spell_parchment").getItem())) {
                selected.set(slot, writtenParchment(spellName));
            }
        }
        return selected;
    }

    private static ItemStack nativeOutput(GameTestHelper helper,
            RecipeHolder<? extends IEnchantingRecipe> holder, List<ItemStack> selected) {
        var nativeInput = new ApparatusRecipeInput(selected.getFirst().copy(),
                selected.subList(1, selected.size()).stream().map(ItemStack::copy).toList(), null);
        helper.assertTrue(holder.value().matches(nativeInput, helper.getLevel()),
                "Native Ars rejects the diagnostic inputs: " + holder.id());
        return holder.value().assemble(nativeInput, helper.getLevel().registryAccess());
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
