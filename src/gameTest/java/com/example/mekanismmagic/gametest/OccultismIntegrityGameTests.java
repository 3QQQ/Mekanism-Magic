package com.example.mekanismmagic.gametest;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Runtime checks for Occultism container remainder semantics. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OccultismIntegrityGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos OUTPUT_TARGET_POS = new BlockPos(1, 2, 1);

    private OccultismIntegrityGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 80)
    public static void fragileSoulGemBreaksButSoulGemReturns(
            GameTestHelper helper) {
        Item normalGem = occultismItem("soul_gem");
        Item fragileGem = occultismItem("fragile_soul_gem");
        helper.assertTrue(normalGem != Items.AIR && fragileGem != Items.AIR,
                "Occultism soul-gem items were not registered");

        ItemStackHandler normal = sacrificeInventory(
                filledContainer(normalGem));
        helper.assertTrue(OccultismRecipeBridge.consumeSacrifice(
                        normal, NativeMagicMachineBlockEntity.SACRIFICE_SLOT),
                "Normal soul gem sacrifice was rejected");
        ItemStack normalRemainder = normal.getStackInSlot(
                NativeMagicMachineBlockEntity.RITUAL_REMAINDER_SLOT);
        helper.assertTrue(normalRemainder.is(normalGem)
                        && normalRemainder.get(
                        DataComponents.ENTITY_DATA) == null,
                "Reusable soul gem did not return empty");

        ItemStackHandler fragile = sacrificeInventory(
                filledContainer(fragileGem));
        helper.assertTrue(OccultismRecipeBridge.consumeSacrifice(
                        fragile,
                        NativeMagicMachineBlockEntity.SACRIFICE_SLOT),
                "Fragile soul gem sacrifice was rejected");
        helper.assertTrue(fragile.getStackInSlot(
                        NativeMagicMachineBlockEntity.SACRIFICE_SLOT)
                        .isEmpty()
                        && fragile.getStackInSlot(
                        NativeMagicMachineBlockEntity.RITUAL_REMAINDER_SLOT)
                        .isEmpty(),
                "Fragile soul gem was incorrectly returned for reuse");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void indexedRitualSelectorsPreserveAmbiguity(
            GameTestHelper helper) {
        List<OccultismRecipeBridge.RitualJeiData> rituals =
                OccultismRecipeBridge.ritualJeiRecipes(helper.getLevel());
        helper.assertFalse(rituals.isEmpty(),
                "Occultism ritual catalog was empty");

        OccultismRecipeBridge.RitualJeiData selected = null;
        List<OccultismRecipeBridge.RitualAutomationInput> inputs =
                List.of();
        for (OccultismRecipeBridge.RitualJeiData ritual : rituals) {
            List<OccultismRecipeBridge.RitualAutomationInput> candidate =
                    automationInputs(ritual);
            if (!candidate.isEmpty()
                    && OccultismRecipeBridge.planRitualAutomation(
                    helper.getLevel(), ritual.selector(), candidate,
                    false).isPresent()) {
                selected = ritual;
                inputs = candidate;
                break;
            }
        }
        helper.assertTrue(selected != null,
                "No executable ritual was available for selector testing");

        Optional<OccultismRecipeBridge.RitualAutomationPlan> exact =
                OccultismRecipeBridge.planRitualAutomation(
                        helper.getLevel(), selected.selector(), inputs,
                        false);
        helper.assertTrue(exact.isPresent()
                        && exact.get().recipeId().equals(
                        selected.recipeId()),
                "Recipe-bound miniature did not resolve its exact recipe");

        Set<ResourceLocation> pentacleMatches = matchingPlans(
                helper, rituals, inputs, selected.pentacleId());
        ItemStack pentacleSelector = OccultismRecipeBridge
                .createPentacleMiniRitual(selected.pentacleId());
        assertScopedPlan(helper, pentacleSelector, inputs,
                pentacleMatches, "pentacle-only");

        Set<ResourceLocation> ultimateMatches = matchingPlans(
                helper, rituals, inputs, null);
        ItemStack ultimateSelector = new ItemStack(
                MekanismMagic.ULTIMATE_MINI_RITUAL.get());
        assertScopedPlan(helper, ultimateSelector, inputs,
                ultimateMatches, "ultimate");

        OccultismRecipeBridge.invalidateRecipeCaches();
        Optional<OccultismRecipeBridge.RitualAutomationPlan> rebuilt =
                OccultismRecipeBridge.planRitualAutomation(
                        helper.getLevel(), selected.selector(), inputs,
                        false);
        helper.assertTrue(rebuilt.isPresent()
                        && rebuilt.get().recipeId().equals(
                        selected.recipeId()),
                "Recipe-revision invalidation did not rebuild the ritual "
                        + "selector index");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void miniaturePentaclesUseUniqueAutomaticDyeSignatures(
            GameTestHelper helper) {
        List<OccultismRecipeBridge.PentacleJeiData> recipes =
                OccultismRecipeBridge.pentacleJeiRecipes(helper.getLevel());
        helper.assertFalse(recipes.isEmpty(),
                "Occultism exposed no miniature pentacle recipes");
        Set<String> signatures = new LinkedHashSet<>();
        OccultismRecipeBridge.PentacleJeiData extraDyeCase = null;

        for (OccultismRecipeBridge.PentacleJeiData recipe : recipes) {
            String signature = dyeSignature(recipe.materials());
            helper.assertFalse(signature.isEmpty(),
                    "Miniature pentacle has no dye signature: "
                            + recipe.pentacleId());
            helper.assertTrue(signatures.add(signature),
                    "Duplicate miniature pentacle dye signature: "
                            + signature);
            helper.assertTrue(recipe.materials().size()
                            <= NativeMagicMachineBlockEntity.INPUT_SLOTS,
                    "Miniature pentacle exceeds the sixteen material slots: "
                            + recipe.pentacleId());

            ItemStackHandler inventory = pentacleInventory(recipe, true);
            List<com.example.mekanismmagic.integration.common.recipe
                    .MachineRecipeResult> matches = OccultismRecipeBridge
                    .findMiniRitualCandidates(helper.getLevel(), inventory);
            helper.assertTrue(matches.size() == 1,
                    "Bulk dye input did not uniquely identify "
                            + recipe.pentacleId() + ": " + matches.size());
            helper.assertTrue(OccultismRecipeBridge.miniRitualPentacle(
                            matches.getFirst().output())
                            .filter(recipe.pentacleId()::equals).isPresent(),
                    "Automatic pentacle recognition selected the wrong output");
            if (extraDyeCase == null && recipe.materials().size()
                    < NativeMagicMachineBlockEntity.INPUT_SLOTS) {
                extraDyeCase = recipe;
            }
        }

        helper.assertTrue(extraDyeCase != null,
                "No pentacle recipe left a slot for ambiguity testing");
        ItemStackHandler malformed = pentacleInventory(extraDyeCase, false);
        Set<Item> expectedDyes = dyeItems(extraDyeCase.materials());
        Item extraDye = List.of(Items.WHITE_DYE, Items.ORANGE_DYE,
                        Items.MAGENTA_DYE, Items.LIGHT_BLUE_DYE,
                        Items.YELLOW_DYE, Items.LIME_DYE, Items.PINK_DYE,
                        Items.GRAY_DYE, Items.LIGHT_GRAY_DYE, Items.CYAN_DYE,
                        Items.PURPLE_DYE, Items.BLUE_DYE, Items.BROWN_DYE,
                        Items.GREEN_DYE, Items.RED_DYE, Items.BLACK_DYE)
                .stream().filter(item -> !expectedDyes.contains(item))
                .findFirst().orElseThrow();
        malformed.setStackInSlot(extraDyeCase.materials().size(),
                new ItemStack(extraDye, 64));
        helper.assertTrue(OccultismRecipeBridge.findMiniRitualCandidates(
                        helper.getLevel(), malformed).isEmpty(),
                "Malformed multi-marker input silently selected a pentacle");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 100)
    public static void ritualInputsCannotBeDuplicatedByAutomaticEjection(
            GameTestHelper helper) {
        helper.setBlock(MACHINE_POS,
                NativeMekanismRegistries.RITUAL_BLOCK.get());
        helper.setBlock(OUTPUT_TARGET_POS, Blocks.CHEST);
        BlockEntity machineEntity = helper.getBlockEntity(MACHINE_POS);
        BlockEntity targetEntity = helper.getBlockEntity(OUTPUT_TARGET_POS);
        helper.assertTrue(machineEntity instanceof NativeRitualEngineBlockEntity,
                "Ritual engine block entity was not created");
        helper.assertTrue(targetEntity instanceof ChestBlockEntity,
                "Ritual output chest was not created");
        NativeRitualEngineBlockEntity machine =
                (NativeRitualEngineBlockEntity) machineEntity;
        ChestBlockEntity target = (ChestBlockEntity) targetEntity;
        List<IInventorySlot> patternInputs =
                machine.mekanismMagicPatternInputs();
        helper.assertTrue(patternInputs.size() >= 2,
                "Ritual engine did not expose its constrained inputs");
        IInventorySlot activation = patternInputs.get(0);
        IInventorySlot sacrifice = patternInputs.get(1);
        ItemStack activationStack = new ItemStack(Items.DIAMOND, 3);
        ItemStack sacrificeStack = new ItemStack(Items.COW_SPAWN_EGG);

        helper.assertTrue(activation.insertItem(activationStack.copy(),
                        Action.EXECUTE, AutomationType.EXTERNAL).isEmpty(),
                "Ritual activation slot rejected automated insertion");
        helper.assertTrue(sacrifice.insertItem(sacrificeStack.copy(),
                        Action.EXECUTE, AutomationType.EXTERNAL).isEmpty(),
                "Ritual sacrifice slot rejected automated insertion");
        assertInputOnlySlot(helper, machine, activation, activationStack,
                "activation");
        assertInputOnlySlot(helper, machine, sacrifice, sacrificeStack,
                "sacrifice");

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(sameStackAndCount(
                                    activation.getStack(), activationStack),
                            "Automatic ejection changed the ritual activation input");
                    helper.assertTrue(sameStackAndCount(
                                    sacrifice.getStack(), sacrificeStack),
                            "Automatic ejection changed the ritual sacrifice input");
                    helper.assertTrue(containerItemCount(target) == 0,
                            "Automatic ejection copied a protected ritual input");

                    List<IInventorySlot> outputs =
                            machine.mekanismMagicPatternOutputs();
                    helper.assertTrue(outputs.size() >= 2,
                            "Ritual engine did not expose both real outputs");
                    outputs.get(0).setStack(new ItemStack(Items.GOLD_INGOT, 2));
                    outputs.get(1).setStack(new ItemStack(Items.GLASS_BOTTLE));
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(containerItemCount(target) == 3,
                            "Real ritual outputs were not transferred exactly once");
                    helper.assertTrue(machine.mekanismMagicPatternOutputs()
                                    .stream().allMatch(slot ->
                                            slot.getStack().isEmpty()),
                            "A real ritual output remained after automatic ejection");
                    helper.assertTrue(sameStackAndCount(
                                    activation.getStack(), activationStack)
                                    && sameStackAndCount(
                                    sacrifice.getStack(), sacrificeStack),
                            "Protected ritual inputs changed while ejecting outputs");
                })
                .thenSucceed();
    }

    private static void assertInputOnlySlot(
            GameTestHelper helper, NativeRitualEngineBlockEntity machine,
            IInventorySlot slot, ItemStack expected, String name) {
        helper.assertTrue(slot.extractItem(expected.getCount(),
                        Action.SIMULATE, AutomationType.EXTERNAL).isEmpty(),
                "Ritual " + name + " slot allows direct external extraction");
        ItemStack manual = slot.extractItem(expected.getCount(),
                Action.SIMULATE, AutomationType.MANUAL);
        helper.assertTrue(sameStackAndCount(manual, expected),
                "Ritual " + name + " slot no longer allows manual removal");
        List<IInventorySlot> topSlots = machine.getInventorySlots(Direction.UP);
        int sidedIndex = topSlots.indexOf(slot);
        helper.assertTrue(sidedIndex >= 0,
                "Ritual " + name + " slot is missing from the top input port");
        helper.assertTrue(machine.extractItem(sidedIndex, expected.getCount(),
                        Direction.UP, Action.SIMULATE).isEmpty(),
                "Ritual " + name + " slot is exposed as a top-side output");
    }

    private static boolean sameStackAndCount(ItemStack actual,
                                             ItemStack expected) {
        return actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static int containerItemCount(ChestBlockEntity target) {
        int count = 0;
        for (int slot = 0; slot < target.getContainerSize(); slot++) {
            count += target.getItem(slot).getCount();
        }
        return count;
    }

    private static ItemStackHandler pentacleInventory(
            OccultismRecipeBridge.PentacleJeiData recipe,
            boolean bulkDyes) {
        ItemStackHandler inventory = new ItemStackHandler(
                NativeMagicMachineBlockEntity.MACHINE_INVENTORY_SIZE);
        for (int index = 0; index < recipe.materials().size(); index++) {
            ItemStack stack = recipe.materials().get(index).copy();
            if (bulkDyes && stack.getItem() instanceof DyeItem) {
                stack.setCount(stack.getMaxStackSize());
            }
            inventory.setStackInSlot(index, stack);
        }
        inventory.setStackInSlot(
                NativeMagicMachineBlockEntity.CHALK_SLOT_START,
                new ItemStack(occultismItem("chalk_void")));
        return inventory;
    }

    private static String dyeSignature(List<ItemStack> materials) {
        return dyeItems(materials).stream()
                .map(BuiltInRegistries.ITEM::getKey)
                .map(ResourceLocation::toString)
                .sorted()
                .reduce((left, right) -> left + "+" + right)
                .orElse("");
    }

    private static Set<Item> dyeItems(List<ItemStack> materials) {
        Set<Item> result = new LinkedHashSet<>();
        for (ItemStack stack : materials) {
            if (stack.getItem() instanceof DyeItem) {
                result.add(stack.getItem());
            }
        }
        return Set.copyOf(result);
    }

    private static List<OccultismRecipeBridge.RitualAutomationInput>
    automationInputs(OccultismRecipeBridge.RitualJeiData ritual) {
        List<OccultismRecipeBridge.RitualAutomationInput> result =
                new ArrayList<>();
        Ingredient activation = ritual.activation();
        if (activation != null && !activation.isEmpty()) {
            ItemStack stack = firstIngredient(activation);
            if (stack.isEmpty()) {
                return List.of();
            }
            result.add(new OccultismRecipeBridge.RitualAutomationInput(
                    stack, 1));
        }
        if (!ritual.sacrifices().isEmpty()) {
            result.add(new OccultismRecipeBridge.RitualAutomationInput(
                    ritual.sacrifices().getFirst(), 1));
        }
        for (Ingredient ingredient : ritual.ingredients()) {
            ItemStack stack = firstIngredient(ingredient);
            if (stack.isEmpty()) {
                return List.of();
            }
            result.add(new OccultismRecipeBridge.RitualAutomationInput(
                    stack, 1));
        }
        return List.copyOf(result);
    }

    private static ItemStack firstIngredient(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? ItemStack.EMPTY
                : items[0].copyWithCount(1);
    }

    private static Set<ResourceLocation> matchingPlans(
            GameTestHelper helper,
            List<OccultismRecipeBridge.RitualJeiData> rituals,
            List<OccultismRecipeBridge.RitualAutomationInput> inputs,
            ResourceLocation pentacleFilter) {
        Set<ResourceLocation> matches = new LinkedHashSet<>();
        for (OccultismRecipeBridge.RitualJeiData ritual : rituals) {
            if (pentacleFilter != null
                    && !pentacleFilter.equals(ritual.pentacleId())) {
                continue;
            }
            OccultismRecipeBridge.planRitualAutomation(
                    helper.getLevel(), ritual.selector(), inputs, false)
                    .map(OccultismRecipeBridge.RitualAutomationPlan::recipeId)
                    .ifPresent(matches::add);
        }
        return Set.copyOf(matches);
    }

    private static void assertScopedPlan(
            GameTestHelper helper, ItemStack selector,
            List<OccultismRecipeBridge.RitualAutomationInput> inputs,
            Set<ResourceLocation> matching, String scope) {
        Optional<OccultismRecipeBridge.RitualAutomationPlan> actual =
                OccultismRecipeBridge.planRitualAutomation(
                        helper.getLevel(), selector, inputs, false);
        if (matching.size() == 1) {
            helper.assertTrue(actual.isPresent()
                            && matching.contains(actual.get().recipeId()),
                    scope + " selector did not resolve its sole candidate");
        } else {
            helper.assertTrue(actual.isEmpty(),
                    scope + " selector silently chose among "
                            + matching.size() + " candidates");
        }
    }

    private static Item occultismItem(String path) {
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("occultism", path));
    }

    private static ItemStack filledContainer(Item item) {
        ItemStack stack = new ItemStack(item);
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:cow");
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(entity));
        return stack;
    }

    private static ItemStackHandler sacrificeInventory(ItemStack sacrifice) {
        ItemStackHandler inventory = new ItemStackHandler(
                NativeMagicMachineBlockEntity.MACHINE_INVENTORY_SIZE);
        inventory.setStackInSlot(
                NativeMagicMachineBlockEntity.SACRIFICE_SLOT, sacrifice);
        return inventory;
    }
}
