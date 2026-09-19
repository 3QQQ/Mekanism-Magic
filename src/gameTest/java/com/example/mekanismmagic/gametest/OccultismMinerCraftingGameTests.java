package com.example.mekanismmagic.gametest;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
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
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import com.example.mekanismmagic.integration.mekenergistics.OccultismRitualMeRouter;
import com.example.mekanismmagic.integration.occultism.OccultismMachineItemIdentity;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge.RitualJeiData;
import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Real AE crafting jobs: the CPU must receive exactly the key encoded by JEI. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OccultismMinerCraftingGameTests {
    private static final BlockPos LOWER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 1);
    private static final BlockPos UPPER_POS = new BlockPos(3, 1, 1);
    private static final BlockPos DRIVE_POS = new BlockPos(2, 2, 1);
    private static final BlockPos CPU_POS = new BlockPos(2, 1, 2);
    private static final Set<Future<ICraftingPlan>> LOGGED_CALCULATIONS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Future<ICraftingPlan>, SubmissionAttempt> SUBMISSION_ATTEMPTS =
            new WeakHashMap<>();
    private static final Map<ICraftingLink, Long> LAST_PENDING_LOG = new WeakHashMap<>();
    private static final Map<ICraftingLink, CraftingCPUCluster> SUBMITTED_CPUS = new WeakHashMap<>();

    private OccultismMinerCraftingGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 600)
    public static void dynamicRepairCompletesExactNamedEnchantedAeJob(
            GameTestHelper helper) {
        RitualCase original = ritual(helper, "repair_tools");
        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(73);
        damaged.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this pickaxe"));
        damaged.enchant(helper.getLevel().registryAccess()
                .holderOrThrow(Enchantments.EFFICIENCY), 3);
        CustomData.update(DataComponents.CUSTOM_DATA, damaged,
                tag -> tag.putString("repair_test_owner", "persistent"));
        List<ItemStack> inputs = new ArrayList<>(original.inputs());
        inputs.set(0, damaged);
        RitualCase exact = exactRitual(helper, original.recipe(), inputs);
        ItemStack expected = damaged.copy();
        expected.setDamageValue(0);
        assertSameComponents(helper, expected, exact.recipe().output(),
                "Repair preview lost the selected tool's components");
        helper.assertTrue(damaged.getDamageValue() == 73,
                "Resolving the repair preview mutated its input");
        assertSameComponents(helper, expected, resolvedOutput(helper,
                        original.recipe(), damaged, true),
                "Repair runtime and selected JEI output differ");
        var uniformBatch = OccultismRecipeBridge.planRitualAutomation(
                helper.getLevel(), original.recipe().selector(), inputs.stream()
                        .map(stack -> new OccultismRecipeBridge.RitualAutomationInput(stack, 2))
                        .toList(), true);
        helper.assertTrue(uniformBatch.isPresent() && uniformBatch.orElseThrow().copies() == 2,
                "A uniform two-copy dynamic repair batch was rejected");
        List<OccultismRecipeBridge.RitualAutomationInput> mixedBatch = new ArrayList<>();
        mixedBatch.add(new OccultismRecipeBridge.RitualAutomationInput(damaged, 1));
        ItemStack differentlyNamed = damaged.copy();
        differentlyNamed.set(DataComponents.CUSTOM_NAME, Component.literal("Another pickaxe"));
        mixedBatch.add(new OccultismRecipeBridge.RitualAutomationInput(differentlyNamed, 1));
        inputs.stream().skip(1).forEach(stack -> mixedBatch.add(
                new OccultismRecipeBridge.RitualAutomationInput(stack, 2)));
        helper.assertTrue(OccultismRecipeBridge.planRitualAutomation(helper.getLevel(),
                        original.recipe().selector(), mixedBatch, true).isEmpty(),
                "A repair batch promising one output key accepted two different named outputs");
        runDynamicRitualCpu(helper, exact);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 600)
    public static void dynamicResurrectionCompletesExactEntityAeJob(
            GameTestHelper helper) {
        RitualCase original = ritual(helper, "resurrect_mob");
        helper.assertTrue(original.recipe().output().isEmpty(),
                "A soul without entity data advertised a resurrection result");
        ItemStack soul = original.inputs().getFirst().copy();
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:wolf");
        entity.putString("CustomName", "{\"text\":\"Returned companion\"}");
        entity.putInt("CollarColor", 11);
        entity.putFloat("Health", 7.0F);
        net.minecraft.nbt.ListTag position = new net.minecraft.nbt.ListTag();
        position.add(net.minecraft.nbt.DoubleTag.valueOf(100));
        position.add(net.minecraft.nbt.DoubleTag.valueOf(70));
        position.add(net.minecraft.nbt.DoubleTag.valueOf(-50));
        entity.put("Pos", position);
        soul.set(DataComponents.ENTITY_DATA, CustomData.of(entity));
        List<ItemStack> inputs = new ArrayList<>(original.inputs());
        inputs.set(0, soul);
        RitualCase exact = exactRitual(helper, original.recipe(), inputs);
        ItemStack output = exact.recipe().output();
        helper.assertTrue(output.is(MekanismMagic.RITUAL_SPAWN_EGG.get()),
                "Resurrection preview still exposes a placeholder icon");
        CustomData saved = output.get(DataComponents.ENTITY_DATA);
        helper.assertTrue(saved != null && saved.getUnsafe().getString("id")
                        .equals("minecraft:wolf")
                        && saved.getUnsafe().getInt("CollarColor") == 11
                        && saved.getUnsafe().getFloat("Health") == 7.0F
                        && saved.getUnsafe().getString("CustomName")
                        .contains("Returned companion")
                        && !saved.getUnsafe().contains("Pos"),
                "Resurrection preview lost saved entity data or retained its old position");
        helper.assertTrue(soul.get(DataComponents.ENTITY_DATA).getUnsafe().contains("Pos"),
                "Resurrection preview mutated the soul's entity data");
        assertSameComponents(helper, output, resolvedOutput(helper,
                        original.recipe(), soul, true),
                "Resurrection runtime and selected JEI output differ");
        runDynamicRitualCpu(helper, exact);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 100)
    public static void dynamicUpgradePreservesSatchelContentsAndBinding(
            GameTestHelper helper) {
        RitualCase original = ritual(helper, "craft_upgrade_ritual_satchel");
        List<ItemStack> inputs = new ArrayList<>(original.inputs());
        ItemStack activation = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("occultism:book_of_binding_bound_afrit")));
        setBoundSpiritName(activation, "Satchel upgrade binding");
        inputs.set(0, activation);
        ItemStack primary = inputs.get(1).copy();
        ItemContainerContents contents = ItemContainerContents.fromItems(
                List.of(new ItemStack(Items.DIAMOND, 7), new ItemStack(Items.GOLD_INGOT, 11)));
        primary.set(DataComponents.CONTAINER, contents);
        primary.set(DataComponents.CUSTOM_NAME, Component.literal("Filled ritual satchel"));
        setTestComponent(primary, "satchel_uuid",
                java.util.UUID.fromString("812e7200-6b85-4c46-b18b-c029b9c6ef53"));
        inputs.set(1, primary);
        RitualCase exact = exactRitual(helper, original.recipe(), inputs);
        ItemStack output = exact.recipe().output();
        helper.assertTrue(output.is(BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse("occultism:ritual_satchel_t2"))),
                "Upgrade preview kept the lower-tier item");
        helper.assertTrue(contents.equals(output.get(DataComponents.CONTAINER))
                        && primary.get(DataComponents.CUSTOM_NAME)
                        .equals(output.get(DataComponents.CUSTOM_NAME))
                        && testComponent(primary, "satchel_uuid")
                        .equals(testComponent(output, "satchel_uuid"))
                        && "Satchel upgrade binding".equals(testComponent(output, "spirit_name")),
                "Upgrade preview lost contents, custom name, satchel UUID or spirit binding");
        assertExactRitualRuntime(helper, exact);
        assertDynamicPatternRejectsWrongComponents(helper, exact);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 100)
    public static void dynamicSummonUsesExactBoundBookFocusAndCallingBook(
            GameTestHelper helper) {
        RitualCase original = ritual(helper, "summon_foliot_transport_items");
        ItemStack defaultActivation = original.inputs().getFirst();
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(defaultActivation.getItem())
                        .getPath().equals("book_of_binding_foliot"),
                "Summoning JEI still exposes an unnamed bound-book default");
        RitualCase defaults = exactRitual(helper, original.recipe(), original.inputs());
        assertSameComponents(helper, original.recipe().output(), defaults.recipe().output(),
                "Default summoning preview differs from its encoded default inputs");
        ItemStack bound = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("occultism:book_of_binding_bound_foliot")));
        setBoundSpiritName(bound, "Exact focused worker");
        helper.assertTrue(OccultismRecipeBridge.matchesRitualJeiActivation(
                        helper.getLevel(), original.recipe(), bound),
                "The summoning view rejected a valid bound-book focus");
        List<ItemStack> inputs = new ArrayList<>(original.inputs());
        inputs.set(0, bound);
        RitualCase exact = exactRitual(helper, original.recipe(), inputs);
        helper.assertFalse(ItemStack.isSameItemSameComponents(
                        exact.recipe().output(), defaults.recipe().output()),
                "Bound-book focus was collapsed into the unnamed summon output");
        CompoundTag entity = exact.recipe().output().get(DataComponents.ENTITY_DATA).copyTag();
        ItemStack callingBook = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                entity.getCompound("ritual_calling_book"));
        helper.assertFalse(callingBook.isEmpty(), "Focused worker lost its calling book");
        helper.assertTrue("Exact focused worker".equals(testComponent(callingBook, "spirit_name")),
                "Focused worker's calling book lost the chosen spirit name");
        assertExactRitualRuntime(helper, exact);
        assertDynamicPatternRejectsWrongComponents(helper, exact);
        helper.succeed();
    }

    private static RitualCase exactRitual(GameTestHelper helper, RitualJeiData recipe,
                                          List<ItemStack> inputs) {
        ItemStack output = OccultismRecipeBridge.resolveRitualJeiOutput(
                helper.getLevel(), recipe, inputs).orElseThrow(() ->
                new IllegalStateException("Dynamic JEI output was not resolved: " + recipe.recipeId()));
        return new RitualCase(new RitualJeiData(recipe.recipeId(), recipe.pentacleId(),
                recipe.ingredients(), recipe.activation(), recipe.sacrifices(),
                recipe.selector(), output), List.copyOf(inputs));
    }

    private static void assertExactRitualRuntime(GameTestHelper helper, RitualCase exact) {
        ItemStackHandler inventory = new ItemStackHandler(
                NativeMagicMachineBlockEntity.MACHINE_INVENTORY_SIZE);
        ItemStack activation = exact.inputs().getFirst();
        inventory.setStackInSlot(NativeMagicMachineBlockEntity.ACTIVATION_SLOT, activation.copy());
        for (int material = 0; material < exact.recipe().ingredients().size(); material++) {
            inventory.setStackInSlot(material, exact.inputs().get(material + 1).copy());
        }
        ItemStack actual = OccultismRecipeBridge.findRitualRecipe(helper.getLevel(), inventory,
                exact.recipe().selector(), activation, ItemStack.EMPTY, true).orElseThrow().output();
        assertSameComponents(helper, exact.recipe().output(), actual,
                "Dynamic JEI preview and runtime output differ");
    }

    private static void assertDynamicPatternRejectsWrongComponents(
            GameTestHelper helper, RitualCase exact) {
        helper.setBlock(LOWER_POS, NativeMekanismRegistries.RITUAL_BLOCK.get());
        NativeRitualEngineBlockEntity tile = helper.getBlockEntity(LOWER_POS);
        configureRitual(tile, exact);
        helper.assertTrue(OccultismRitualMeRouter.matchesPattern(tile,
                        decode(helper, exact, exact.recipe().output()), counters(exact.inputs())),
                "AE rejected the exact dynamic ritual output");
        ItemStack wrong = exact.recipe().output().copy();
        CustomData.update(DataComponents.CUSTOM_DATA, wrong,
                tag -> tag.putString("wrong_dynamic_output", "must reject"));
        helper.assertFalse(OccultismRitualMeRouter.matchesPattern(tile,
                        decode(helper, exact, wrong), counters(exact.inputs())),
                "AE accepted the right item with wrong output components");
    }

    private static void runDynamicRitualCpu(GameTestHelper helper, RitualCase exact) {
        assertDynamicPatternRejectsWrongComponents(helper, exact);
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CPU_POS, AEBlocks.CRAFTING_STORAGE_4K.block());
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS);
        helper.assertTrue(drive.getInternalInventory().insertItem(
                        0, AEItems.ITEM_CELL_16K.stack(), false).isEmpty(),
                "Could not install the dynamic recipe test storage cell");
        Machine machine = installMachine(helper, LOWER_POS, exact);
        helper.onEachTick(() -> charge(machine.tile()));
        AEItemKey expected = AEItemKey.of(exact.recipe().output());
        AtomicReference<Future<ICraftingPlan>> calculation = new AtomicReference<>();
        AtomicReference<ICraftingLink> link = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(machine.support().getMainNode().isActive() && drive.isPowered(),
                            "Dynamic ritual AE network did not become active");
                    helper.assertFalse(crafting(machine).getCpus().isEmpty(),
                            "Dynamic ritual network has no real crafting CPU");
                    helper.assertTrue(crafting(machine).isCraftable(expected),
                            "Exact dynamic ritual pattern was not advertised");
                    helper.assertTrue(storage(machine).insert(expected, 1, Actionable.SIMULATE,
                                    IActionSource.empty()) == 1,
                            "Dynamic ritual storage cell is not mounted");
                })
                .thenExecute(() -> seedIngredients(helper, machine, exact, 1, null))
                .thenWaitUntil(() -> assertCachedInventoryCurrent(helper, machine))
                .thenExecute(() -> calculation.set(calculate(helper, machine, expected)))
                .thenWaitUntil(() -> submit(helper, machine, calculation.get(), link,
                        null, false, "exact dynamic ritual"))
                .thenWaitUntil(() -> completed(helper, machine, link.get(), expected,
                        exact.recipe().output().getCount()))
                .thenExecute(() -> helper.assertTrue(machine.tile().mekanismMagicPatternInputs()
                                .stream().allMatch(slot -> slot.getStack().isEmpty()),
                        "Completed dynamic ritual retained its consumed inputs"))
                .thenSucceed();
    }

    @SuppressWarnings("unchecked")
    private static <T> void setTestComponent(ItemStack stack, String path, T value) {
        DataComponentType<T> component = (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(ResourceLocation.fromNamespaceAndPath("occultism", path));
        if (component == null) {
            throw new IllegalStateException("Missing native Occultism component: " + path);
        }
        stack.set(component, value);
    }

    private static Object testComponent(ItemStack stack, String path) {
        DataComponentType<?> component = BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(ResourceLocation.fromNamespaceAndPath("occultism", path));
        return component == null ? null : stack.get(component);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 1600)
    public static void minerChainCompletesRepeatedAndRecursiveAeJobs(
            GameTestHelper helper) {
        RitualCase lower = ritual(helper, "craft_miner_foliot_unspecialized");
        RitualCase upper = ritual(helper, "craft_miner_djinni_ores");
        AEItemKey lowerKey = AEItemKey.of(lower.recipe().output());
        AEItemKey upperKey = AEItemKey.of(upper.recipe().output());
        helper.assertTrue(upper.inputs().stream().anyMatch(input ->
                        lowerKey.equals(AEItemKey.of(input))),
                "JEI's second miner tier does not request the first tier's key");

        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CPU_POS, AEBlocks.CRAFTING_STORAGE_4K.block());
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS);
        helper.assertTrue(drive.getInternalInventory().insertItem(
                        0, AEItems.ITEM_CELL_16K.stack(), false).isEmpty(),
                "Could not install the crafting test storage cell");
        Machine lowerMachine = installMachine(helper, LOWER_POS, lower);
        Machine upperMachine = installMachine(helper, UPPER_POS, upper);

        // Only supply energy and speed upgrades. All inputs, recipe execution,
        // output insertion and completion below are driven by the real CPU.
        helper.onEachTick(() -> {
            charge(lowerMachine.tile());
            charge(upperMachine.tile());
        });
        AtomicReference<Future<ICraftingPlan>> calculation =
                new AtomicReference<>();
        AtomicReference<ICraftingLink> link = new AtomicReference<>();
        AtomicReference<ItemStack> firstLower = new AtomicReference<>();
        AtomicReference<ItemStack> firstUpper = new AtomicReference<>();

        var sequence = helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(lowerMachine.support()
                                    .getMainNode().isActive()
                                    && upperMachine.support()
                                    .getMainNode().isActive(),
                            "Miner ritual providers did not become active");
                    helper.assertTrue(lowerMachine.support().getGrid()
                                    == upperMachine.support().getGrid(),
                            "Miner ritual engines did not share an AE grid");
                    helper.assertTrue(drive.isPowered(),
                            "Miner crafting drive did not become powered");
                    ICraftingService crafting = crafting(lowerMachine);
                    helper.assertFalse(crafting.getCpus().isEmpty(),
                            "A real crafting CPU did not form on the miner grid");
                    helper.assertTrue(crafting.isCraftable(lowerKey)
                                    && crafting.isCraftable(upperKey),
                            "JEI-derived miner patterns were not advertised");
                    helper.assertTrue(storage(lowerMachine).insert(lowerKey,
                                    1, Actionable.SIMULATE,
                                    IActionSource.empty()) == 1,
                            "Miner crafting drive did not mount its cell");
                })
                .thenExecute(() -> {
                    seedIngredients(helper, lowerMachine, lower, 3, null);
                    seedIngredients(helper, lowerMachine, upper, 3, lowerKey);
                })
                .thenWaitUntil(() -> assertCachedInventoryCurrent(helper, lowerMachine))
                .thenExecute(() -> calculation.set(calculate(helper, lowerMachine, lowerKey)))
                .thenWaitUntil(() -> submit(helper, lowerMachine,
                        calculation.get(), link, null, false, "1/5 first lower"))
                .thenWaitUntil(() -> completed(helper, lowerMachine,
                        link.get(), lowerKey, 1))
                .thenExecute(() -> {
                    firstLower.set(actualStoredMiner(helper, lowerMachine,
                            lower.recipe().output(), 1));
                })
                .thenWaitUntil(() -> assertCachedInventoryCurrent(helper, lowerMachine))
                .thenExecute(() -> calculation.set(calculate(helper, lowerMachine, lowerKey)))
                .thenWaitUntil(() -> submit(helper, lowerMachine,
                        calculation.get(), link, null, false, "2/5 repeated lower"))
                .thenWaitUntil(() -> completed(helper, lowerMachine,
                        link.get(), lowerKey, 2))
                .thenExecute(() -> {
                    helper.assertTrue(firstLower.get() != null,
                            "First lower-tier result was not recorded");
                    ItemStack repeated = actualStoredMiner(helper,
                            lowerMachine, lower.recipe().output(), 2);
                    assertSameComponents(helper, firstLower.get(), repeated,
                            "Repeated lower-tier machine results diverged");
                    helper.assertTrue(upper.inputs().stream().anyMatch(input ->
                                    ItemStack.isSameItemSameComponents(
                                            input, repeated)),
                            "Next-tier JEI input does not match the actual miner");
                })
                .thenWaitUntil(() -> assertCachedInventoryCurrent(helper, lowerMachine))
                .thenExecute(() -> calculation.set(calculate(helper, lowerMachine, upperKey)))
                .thenWaitUntil(() -> submit(helper, lowerMachine,
                        calculation.get(), link, lowerKey, false, "3/5 first upper"))
                .thenWaitUntil(() -> completed(helper, lowerMachine,
                        link.get(), upperKey, 1))
                .thenExecute(() -> {
                    helper.assertTrue(amount(lowerMachine, lowerKey) == 1,
                            "Upper-tier task did not consume one actual lower miner");
                    firstUpper.set(actualStoredMiner(helper, lowerMachine,
                            upper.recipe().output(), 1));
                })
                .thenWaitUntil(() -> assertCachedInventoryCurrent(helper, lowerMachine))
                .thenExecute(() -> calculation.set(calculate(helper, lowerMachine, upperKey)))
                .thenWaitUntil(() -> submit(helper, lowerMachine,
                        calculation.get(), link, lowerKey, false, "4/5 repeated upper"))
                .thenWaitUntil(() -> completed(helper, lowerMachine,
                        link.get(), upperKey, 2))
                .thenExecute(() -> {
                    helper.assertTrue(firstUpper.get() != null,
                            "First upper-tier result was not recorded");
                    helper.assertTrue(amount(lowerMachine, lowerKey) == 0,
                            "Second upper-tier task left an unconsumed lower miner");
                    assertSameComponents(helper, firstUpper.get(),
                            actualStoredMiner(helper, lowerMachine,
                                    upper.recipe().output(), 2),
                            "Repeated upper-tier machine results diverged");
                    helper.assertTrue(storage(lowerMachine).extract(upperKey,
                                    2, Actionable.MODULATE,
                                    IActionSource.empty()) == 2,
                            "Could not remove finished miners before recursive craft");
                })
                .thenWaitUntil(() -> assertCachedInventoryCurrent(helper, lowerMachine))
                .thenExecute(() -> calculation.set(calculate(helper, lowerMachine, upperKey)))
                .thenWaitUntil(() -> submit(helper, lowerMachine,
                        calculation.get(), link, null, true, "5/5 recursive upper"))
                .thenWaitUntil(() -> completed(helper, lowerMachine,
                        link.get(), upperKey, 1))
                .thenExecute(() -> {
                    helper.assertTrue(firstUpper.get() != null,
                            "First upper-tier result was not recorded before recursion");
                    helper.assertTrue(amount(lowerMachine, lowerKey) == 0,
                            "Recursive chain left an unmatched lower-tier output");
                    assertSameComponents(helper, firstUpper.get(),
                            actualStoredMiner(helper, lowerMachine,
                                    upper.recipe().output(), 1),
                            "Recursive machine output differs from direct crafting");
                    helper.assertTrue(lowerMachine.tile()
                                    .mekanismMagicPatternInputs().stream()
                                    .allMatch(slot -> slot.getStack().isEmpty())
                                    && upperMachine.tile()
                                    .mekanismMagicPatternInputs().stream()
                                    .allMatch(slot -> slot.getStack().isEmpty()),
                            "Completed miner chain retained consumed input items");
                });
        sequence.thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 100)
    public static void minerPatternsKeepDamageEnchantmentsAndCustomDataDistinct(
            GameTestHelper helper) {
        RitualCase recipe = ritual(helper, "craft_miner_foliot_unspecialized");
        helper.setBlock(LOWER_POS, NativeMekanismRegistries.RITUAL_BLOCK.get());
        NativeRitualEngineBlockEntity tile = helper.getBlockEntity(LOWER_POS);
        configureRitual(tile, recipe);
        KeyCounter[] inputs = counters(recipe.inputs());
        IPatternDetails unmodified = decode(helper, recipe, recipe.recipe().output());
        helper.assertTrue(OccultismRitualMeRouter.matchesPattern(tile,
                        unmodified, inputs),
                brief("Unmodified JEI miner pattern was rejected; "
                        + patternDiagnostics(tile, recipe, unmodified, inputs), 980));
        KeyCounter malformed = new KeyCounter();
        recipe.inputs().forEach(stack -> malformed.add(AEItemKey.of(stack), 1));
        helper.assertFalse(OccultismRitualMeRouter.matchesPattern(tile,
                        unmodified, new KeyCounter[]{malformed}),
                "Ritual router accepted multiple item keys in one AE input counter");

        List<ItemStack> distinct = new ArrayList<>();
        ItemStack damaged = recipe.recipe().output().copy();
        helper.assertTrue(damaged.isDamageableItem(),
                "Miner regression item is not damageable");
        damaged.setDamageValue(1);
        distinct.add(damaged);
        ItemStack enchanted = recipe.recipe().output().copy();
        enchanted.enchant(helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING), 1);
        distinct.add(enchanted);
        ItemStack custom = recipe.recipe().output().copy();
        CompoundTag functionalData = new CompoundTag();
        functionalData.putInt("mekanism_magic_test_capacity", 37);
        custom.set(DataComponents.CUSTOM_DATA, CustomData.of(functionalData));
        distinct.add(custom);
        ItemStack renamed = recipe.recipe().output().copy();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Saved miner"));
        distinct.add(renamed);
        for (String component : List.of("max_mining_time", "rolls_per_operation")) {
            @SuppressWarnings("unchecked")
            DataComponentType<Integer> type = (DataComponentType<Integer>)
                    BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                            ResourceLocation.fromNamespaceAndPath(
                                    "occultism", component));
            helper.assertTrue(type != null,
                    "Missing functional miner component: " + component);
            ItemStack configured = recipe.recipe().output().copy();
            configured.set(type, configured.getOrDefault(type, 0) + 7);
            distinct.add(configured);
        }

        for (ItemStack changed : distinct) {
            ItemStack snapshot = changed.copy();
            assertSameComponents(helper, changed,
                    OccultismMachineItemIdentity.normalizeMiner(changed),
                    "Miner normalization erased a functional component");
            assertSameComponents(helper, snapshot, changed,
                    "Miner normalization mutated its input stack");
            helper.assertFalse(AEItemKey.of(recipe.recipe().output())
                            .equals(AEItemKey.of(changed)),
                    "AE merged a miner with distinct functional components");
            helper.assertFalse(OccultismRitualMeRouter.matchesPattern(tile,
                            decode(helper, recipe, changed), inputs),
                    "Ritual pattern validation ignored changed output components");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void differentBoundBooksProduceIdenticalActualMiners(
            GameTestHelper helper) {
        RitualCase recipe = ritual(helper, "craft_miner_foliot_unspecialized");
        helper.setBlock(LOWER_POS, NativeMekanismRegistries.RITUAL_BLOCK.get());
        helper.setBlock(UPPER_POS, NativeMekanismRegistries.RITUAL_BLOCK.get());
        NativeRitualEngineBlockEntity first = helper.getBlockEntity(LOWER_POS);
        NativeRitualEngineBlockEntity second = helper.getBlockEntity(UPPER_POS);
        seedNamedRitual(first, recipe, "Miner test A");
        seedNamedRitual(second, recipe, "Miner test B");
        helper.onEachTick(() -> {
            charge(first);
            charge(second);
        });
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertFalse(first.mekanismMagicPatternOutputs()
                                    .getFirst().getStack().isEmpty(),
                            "First named-book ritual never produced a miner");
                    helper.assertFalse(second.mekanismMagicPatternOutputs()
                                    .getFirst().getStack().isEmpty(),
                            "Second named-book ritual never produced a miner");
                })
                .thenExecute(() -> {
                    ItemStack left = first.mekanismMagicPatternOutputs()
                            .getFirst().getStack();
                    ItemStack right = second.mekanismMagicPatternOutputs()
                            .getFirst().getStack();
                    assertSameComponents(helper, recipe.recipe().output(), left,
                            "Named-book actual output differs from JEI");
                    assertSameComponents(helper, left, right,
                            "Different binding names produced different miner keys");
                    // Native tooltip/accessor initialization must be idempotent.
                    readBoundSpiritName(left);
                    left.getMaxStackSize();
                    assertSameComponents(helper, recipe.recipe().output(), left,
                            "Native miner getters changed a completed output key");
                })
                .thenSucceed();
    }

    private static void seedNamedRitual(NativeRitualEngineBlockEntity tile,
                                        RitualCase recipe, String spiritName) {
        configureRitual(tile, recipe);
        tile.getComponent().addUpgrades(Upgrade.SPEED, 8);
        tile.getComponent().addUpgrades(Upgrade.ENERGY, 8);
        ItemStack binding = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("occultism",
                        "book_of_binding_bound_foliot")));
        setBoundSpiritName(binding, spiritName);
        var inputs = tile.mekanismMagicPatternInputs();
        inputs.getFirst().setStack(binding);
        for (int material = 0; material < recipe.recipe().ingredients().size();
             material++) {
            inputs.get(material + 2).setStack(
                    first(recipe.recipe().ingredients().get(material)));
        }
        charge(tile);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void nativeNamedRitualsMatchJeiAndKeepDeclaredComponents(
            GameTestHelper helper) {
        List<String> namedOutputs = List.of("knowledge_tablet", "ender_satchel",
                "infused_pickaxe", "dimensional_matrix", "satchel",
                "ritual_satchel_t1", "ritual_satchel_t2", "storage_remote",
                "iesnium_butcher_knife");
        for (String outputPath : namedOutputs) {
            RitualCase testCase = ritual(helper, "craft_" + outputPath);
            RitualJeiData recipe = testCase.recipe();
            helper.assertTrue(OccultismMachineItemIdentity.isStableNamedOutput(
                            recipe.output()),
                    "Native named output is outside the audited set: " + outputPath);
            ItemStack declaredOutput = helper.getLevel().getRecipeManager()
                    .byKey(recipe.recipeId()).orElseThrow().value()
                    .getResultItem(helper.getLevel().registryAccess());
            assertSameComponents(helper, declaredOutput, recipe.output(),
                    "Named recipe's declared components changed in JEI: " + outputPath);

            ItemStack unbound = first(recipe.activation());
            String unboundPath = BuiltInRegistries.ITEM.getKey(
                    unbound.getItem()).getPath();
            helper.assertTrue(unboundPath.startsWith("book_of_binding_")
                            && !unboundPath.startsWith("book_of_binding_bound_"),
                    "Named ritual JEI still requires a random named binding book");
            ItemStack bound = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("occultism",
                            unboundPath.replace("book_of_binding_",
                                    "book_of_binding_bound_"))));
            for (String spiritName : List.of("Named item test A", "Named item test B")) {
                setBoundSpiritName(bound, spiritName);
                ItemStack actual = resolvedOutput(helper, recipe, bound, false);
                assertSameComponents(helper, recipe.output(), actual,
                        "Bound spirit name changed the native output: " + outputPath);
                assertSameComponents(helper, declaredOutput, actual,
                        "Named ritual lost declared components: " + outputPath);
            }
            assertSameComponents(helper, recipe.output(),
                    resolvedOutput(helper, recipe, unbound, true),
                    "Dictionary automation output differs from JEI: " + outputPath);
            var plan = OccultismRecipeBridge.planRitualAutomation(
                    helper.getLevel(), recipe.selector(), testCase.inputs().stream()
                            .map(stack -> new OccultismRecipeBridge
                                    .RitualAutomationInput(stack, 1)).toList(), true);
            helper.assertTrue(plan.isPresent(),
                    "Dictionary automation rejected native named recipe: " + outputPath);
            assertSameComponents(helper, recipe.output(), plan.orElseThrow().output(),
                    "Named item automation plan differs from JEI: " + outputPath);
        }
        helper.succeed();
    }

    private static ItemStack resolvedOutput(GameTestHelper helper,
                                            RitualJeiData recipe,
                                            ItemStack activation,
                                            boolean dictionaryEnabled) {
        ItemStackHandler inventory = new ItemStackHandler(
                NativeMagicMachineBlockEntity.MACHINE_INVENTORY_SIZE);
        inventory.setStackInSlot(NativeMagicMachineBlockEntity.ACTIVATION_SLOT,
                activation.copy());
        for (int material = 0; material < recipe.ingredients().size(); material++) {
            inventory.setStackInSlot(material, first(recipe.ingredients().get(material)));
        }
        return OccultismRecipeBridge.findRitualRecipe(helper.getLevel(),
                        inventory, recipe.selector(), activation, ItemStack.EMPTY,
                        dictionaryEnabled)
                .orElseThrow(() -> new IllegalStateException(
                        "Machine cannot resolve native named recipe " + recipe.recipeId()))
                .output();
    }

    private static void setBoundSpiritName(ItemStack stack, String name) {
        try {
            Class.forName("com.klikli_dev.occultism.util.ItemNBTUtil")
                    .getMethod("setBoundSpiritName", ItemStack.class, String.class)
                    .invoke(null, stack, name);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native binding-name setter unavailable", failure);
        }
    }

    private static void readBoundSpiritName(ItemStack stack) {
        try {
            Class.forName("com.klikli_dev.occultism.util.ItemNBTUtil")
                    .getMethod("getBoundSpiritName", ItemStack.class)
                    .invoke(null, stack);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native binding-name getter unavailable", failure);
        }
    }

    private static RitualCase ritual(GameTestHelper helper, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "occultism", "ritual/" + path);
        RitualJeiData recipe = OccultismRecipeBridge.ritualJeiRecipes(
                        helper.getLevel()).stream()
                .filter(candidate -> candidate.recipeId().equals(id))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Missing public miner recipe: " + id));
        helper.assertTrue(recipe.sacrifices().isEmpty(),
                "The two-tier regression requires no entity sacrifice");
        List<ItemStack> inputs = new ArrayList<>();
        if (recipe.activation() != null && !recipe.activation().isEmpty()) {
            inputs.add(first(recipe.activation()));
        }
        recipe.ingredients().forEach(ingredient -> inputs.add(first(ingredient)));
        return new RitualCase(recipe, List.copyOf(inputs));
    }

    private static ItemStack first(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            throw new IllegalStateException("Unresolved miner recipe ingredient");
        }
        return items[0].copyWithCount(1);
    }

    private static Machine installMachine(GameTestHelper helper, BlockPos pos,
                                          RitualCase recipe) {
        helper.setBlock(pos, NativeMekanismRegistries.RITUAL_BLOCK.get());
        NativeRitualEngineBlockEntity tile = helper.getBlockEntity(pos);
        configureRitual(tile, recipe);
        tile.getComponent().addUpgrades(Upgrade.SPEED, 8);
        tile.getComponent().addUpgrades(Upgrade.ENERGY, 8);
        charge(tile);
        MeUpgradeRecipeMachineAdapter adapter =
                (MeUpgradeRecipeMachineAdapter) (Object) tile;
        helper.assertTrue(adapter.getMeUpgradeContainer()
                        .install(MeUpgradeType.PATTERN_PROVIDER).successful(),
                "Could not install the ritual engine's real MekE provider");
        adapter.onMeUpgradeStateChanged();
        adapter.createMeNodeIfActive();
        AbstractMeAeSupport<?> support = adapter
                .getOrCreateMeUpgradeRuntime().support();
        support.getPatternSlots().getFirst().setStack(encode(
                recipe, recipe.recipe().output()));
        support.updatePatterns();
        return new Machine(tile, support);
    }

    private static void charge(NativeRitualEngineBlockEntity tile) {
        tile.mekanismMagicEnergyContainer().setEnergy(
                tile.mekanismMagicEnergyContainer().getMaxEnergy());
    }

    private static void configureRitual(NativeRitualEngineBlockEntity tile,
                                        RitualCase recipe) {
        var manualSlots = tile.mekanismMagicManualOnlySlots();
        manualSlots.getFirst().setStack(recipe.recipe().selector());
        manualSlots.get(1).setStack(new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("occultism",
                        "dictionary_of_spirits"))));
    }

    private static ItemStack encode(RitualCase recipe, ItemStack output) {
        Map<AEItemKey, Long> inputs = new LinkedHashMap<>();
        recipe.inputs().forEach(stack -> inputs.merge(
                AEItemKey.of(stack), 1L, Math::addExact));
        return PatternDetailsHelper.encodeProcessingPattern(
                inputs.entrySet().stream().map(entry ->
                        new GenericStack(entry.getKey(), entry.getValue()))
                        .toList(),
                List.of(new GenericStack(AEItemKey.of(output),
                        output.getCount())));
    }

    private static IPatternDetails decode(GameTestHelper helper,
                                          RitualCase recipe, ItemStack output) {
        String before = describeKey(AEItemKey.of(output));
        String recipeBefore = describeKey(AEItemKey.of(recipe.recipe().output()));
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(
                encode(recipe, output), helper.getLevel());
        helper.assertTrue(pattern != null, "AE could not decode a miner pattern");
        logTest("Miner decode {}: before={}, after={}",
                recipe.recipe().recipeId(), before, describeKey(AEItemKey.of(output)));
        logTest("Miner decode recipe identity before={}, after={}",
                recipeBefore, describeKey(AEItemKey.of(recipe.recipe().output())));
        for (GenericStack decoded : pattern.getOutputs()) {
            logTest("Miner decoded output: {} x {}",
                    describeKey(decoded.what()), decoded.amount());
        }
        return pattern;
    }

    private static KeyCounter[] counters(List<ItemStack> inputs) {
        Map<AEItemKey, Long> amounts = new LinkedHashMap<>();
        inputs.forEach(stack -> amounts.merge(AEItemKey.of(stack), 1L, Math::addExact));
        return amounts.entrySet().stream().map(entry -> {
            KeyCounter counter = new KeyCounter();
            counter.add(entry.getKey(), entry.getValue());
            return counter;
        }).toArray(KeyCounter[]::new);
    }

    private static void seedIngredients(GameTestHelper helper, Machine machine,
                                        RitualCase recipe, int copies,
                                        AEItemKey excluded) {
        for (ItemStack input : recipe.inputs()) {
            AEItemKey key = AEItemKey.of(input);
            if (key.equals(excluded)) {
                continue;
            }
            helper.assertTrue(storage(machine).insert(key, copies,
                            Actionable.MODULATE, IActionSource.empty()) == copies,
                    "Storage rejected miner test ingredient: " + describeKey(key));
        }
        logTest("Miner CPU ingredient seed for {}: copies={}, excluded={}, dictionary={}",
                recipe.recipe().recipeId(), copies, describeKey(excluded),
                dictionaryDiagnostics(machine.tile()));
        logCounter("seeded stored input", storage(machine).getAvailableStacks());
    }

    private static Future<ICraftingPlan> calculate(GameTestHelper helper,
                                                   Machine machine,
                                                   AEItemKey output) {
        logTest("Miner CPU calculating requested output={}", describeKey(output));
        for (var craftable : crafting(machine).getCraftables(AEItemKey.filter())) {
            logTest("Miner CPU available craftable={} exactRequested={}",
                    describeKey(craftable), output.equals(craftable));
        }
        for (IPatternDetails pattern : crafting(machine).getCraftingFor(output)) {
            for (GenericStack advertised : pattern.getOutputs()) {
                logTest("Miner CPU matching advertised output={}",
                        describeKey(advertised.what()));
            }
        }
        return crafting(machine).beginCraftingCalculation(helper.getLevel(),
                () -> machineActionSource(machine), output, 1,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    private static IActionSource machineActionSource(Machine machine) {
        return IActionSource.ofMachine(() -> machine.support().getMainNode().getNode());
    }

    private static void assertCachedInventoryCurrent(GameTestHelper helper,
                                                     Machine machine) {
        helper.assertTrue(machine.support().getGrid() != null,
                "Miner grid disappeared before its storage cache refreshed");
        KeyCounter actual = storage(machine).getAvailableStacks();
        KeyCounter cached = machine.support().getGrid().getStorageService()
                .getCachedInventory();
        for (var entry : actual) {
            helper.assertTrue(cached.get(entry.getKey()) == entry.getLongValue(),
                    brief("AE calculation cache has not observed stored inventory: "
                            + describeKey(entry.getKey()) + ", actual=" + entry.getLongValue()
                            + ", cached=" + cached.get(entry.getKey()), 980));
        }
        for (var entry : cached) {
            helper.assertTrue(actual.get(entry.getKey()) == entry.getLongValue(),
                    brief("AE calculation cache retains an extracted inventory entry: "
                            + describeKey(entry.getKey()) + ", cached=" + entry.getLongValue()
                            + ", actual=" + actual.get(entry.getKey()), 980));
        }
    }

    private static void submit(GameTestHelper helper, Machine machine,
                               Future<ICraftingPlan> future,
                               AtomicReference<ICraftingLink> link,
                               AEItemKey consumedLower, boolean recursive,
                               String job) {
        helper.assertTrue(future != null,
                "Miner crafting calculation was not started");
        helper.assertTrue(future.isDone(), "AE is still calculating miner craft");
        ICraftingPlan plan;
        try {
            plan = future.get();
        } catch (Exception exception) {
            helper.fail(brief("Miner calculation failed: " + exception, 980));
            return;
        }
        helper.assertTrue(plan != null, "AE returned no miner crafting plan");
        String diagnostic = "job=" + job + ", missing="
                + describeCounter(plan.missingItems()) + ", final="
                + describeKey(plan.finalOutput().what());
        if (plan.simulation() && LOGGED_CALCULATIONS.add(future)) {
            logTest("Miner calculation failed {}: final={}",
                    job, describeKey(plan.finalOutput().what()));
            logCounter("missing", plan.missingItems());
            logCounter("used", plan.usedItems());
            logCounter("stored", storage(machine).getAvailableStacks());
            logTest("Miner calculation dictionary={}",
                    dictionaryDiagnostics(machine.tile()));
            for (var entry : plan.patternTimes().entrySet()) {
                for (GenericStack planned : entry.getKey().getOutputs()) {
                    logTest("Miner planned output={} x {}, crafts={}",
                            describeKey(planned.what()), planned.amount(), entry.getValue());
                }
            }
        }
        helper.assertFalse(plan.simulation(),
                brief("AE reported missing miner ingredients: " + diagnostic, 980));
        helper.assertTrue(plan.missingItems().isEmpty(),
                brief("Miner crafting plan contains missing inputs: " + diagnostic, 980));
        if (consumedLower != null) {
            helper.assertTrue(plan.usedItems().get(consumedLower) == 1,
                    "Next tier does not consume the actual stored lower miner key");
            helper.assertTrue(plan.patternTimes().size() == 1,
                    "Next tier unnecessarily recrafted a lower miner already stored");
        }
        if (recursive) {
            helper.assertTrue(plan.patternTimes().size() == 2,
                    "The final task did not plan both consecutive miner tiers");
        }
        SubmissionAttempt attempt = SUBMISSION_ATTEMPTS.get(future);
        if (attempt == null) {
            CraftingCPUCluster cpu = crafting(machine).getCpus().stream()
                    .filter(CraftingCPUCluster.class::isInstance)
                    .map(CraftingCPUCluster.class::cast)
                    .filter(candidate -> !candidate.isBusy()
                            && candidate.getAvailableStorage() >= plan.bytes())
                    .findFirst().orElse(null);
            helper.assertTrue(cpu != null,
                    "No idle real crafting CPU can hold miner job " + job);
            logSubmissionSnapshot(helper, machine, plan, job, "before attempt 1");
            ICraftingSubmitResult result = crafting(machine).submitJob(plan, null, cpu, false,
                    machineActionSource(machine));
            attempt = new SubmissionAttempt(result, cpu);
            SUBMISSION_ATTEMPTS.put(future, attempt);
            logTest("Miner submit {} first result: successful={}, "
                            + "error={}, detail={}, hasLink={}; attempts=1, retry disabled",
                    job, result.successful(), result.errorCode(),
                    diagnosticValue(result.errorDetail()), result.link() != null);
            logSubmissionSnapshot(helper, machine, plan, job, "after attempt 1");
        }
        ICraftingSubmitResult result = attempt.result();
        helper.assertTrue(result.successful(),
                brief("AE rejected miner job " + job + " on first and only submission: "
                         + result.errorCode() + " / " + diagnosticValue(result.errorDetail()), 980));
        // A standalone request deliberately has no requester-side link in the
        // submit result. Track the real CPU-side link while output returns to ME.
        ICraftingLink cpuLink = attempt.cpu().craftingLogic.getLastLink();
        helper.assertTrue(cpuLink != null,
                "Successful standalone miner task has no CPU-side crafting link");
        SUBMITTED_CPUS.put(cpuLink, attempt.cpu());
        link.set(cpuLink);
    }

    private static void logSubmissionSnapshot(GameTestHelper helper, Machine machine,
                                               ICraftingPlan plan, String job,
                                               String phase) {
        logTest("Miner submit snapshot {} {}", job, phase);
        KeyCounter actual = storage(machine).getAvailableStacks();
        KeyCounter cached = machine.support().getGrid().getStorageService().getCachedInventory();
        logCounter("submit " + phase + " stored", actual);
        logCounter("submit " + phase + " used", plan.usedItems());
        for (var entry : plan.usedItems()) {
            long machineExtract = storage(machine).extract(entry.getKey(), entry.getLongValue(),
                    Actionable.SIMULATE, machineActionSource(machine));
            long emptyExtract = storage(machine).extract(entry.getKey(), entry.getLongValue(),
                    Actionable.SIMULATE, IActionSource.empty());
            logTest("Miner submit {} extract {} requested={}, actual={}, "
                            + "cached={}, machineSource={}, emptySource={}", phase,
                    describeKey(entry.getKey()), entry.getLongValue(), actual.get(entry.getKey()),
                    cached.get(entry.getKey()), machineExtract, emptyExtract);
        }
        logMachineSlots(helper, phase);
    }

    private static void logMachineSlots(GameTestHelper helper, String phase) {
        for (BlockPos pos : List.of(LOWER_POS, UPPER_POS)) {
            // getBlockEntity on GameTestHelper asserts when no tile exists.
            // A single-machine fixture intentionally has no upper engine;
            // diagnostics must not prevent submitJob from ever being called.
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                    instanceof NativeRitualEngineBlockEntity tile)) {
                logTest("Miner submit {} engine at {} absent", phase, pos);
                continue;
            }
            logTest("Miner submit {} engine={} progress={} busy={}",
                    phase, pos, tile.getProgress(), tile.mekanismMagicIsBusy());
            var inputs = tile.mekanismMagicPatternInputs();
            for (int index = 0; index < inputs.size(); index++) {
                ItemStack stack = inputs.get(index).getStack();
                if (!stack.isEmpty()) {
                    logTest("Miner submit {} engine={} input[{}]={} x {}",
                            phase, pos, index, describeKey(AEItemKey.of(stack)), stack.getCount());
                }
            }
            var outputs = tile.mekanismMagicPatternOutputs();
            for (int index = 0; index < outputs.size(); index++) {
                ItemStack stack = outputs.get(index).getStack();
                if (!stack.isEmpty()) {
                    logTest("Miner submit {} engine={} output[{}]={} x {}",
                            phase, pos, index, describeKey(AEItemKey.of(stack)), stack.getCount());
                }
            }
        }
    }

    private static String patternDiagnostics(NativeRitualEngineBlockEntity tile,
                                             RitualCase recipe,
                                             IPatternDetails pattern,
                                             KeyCounter[] counters) {
        var rawInputs = recipe.inputs().stream().map(stack ->
                new OccultismRecipeBridge.RitualAutomationInput(stack, 1)).toList();
        var rawPlan = tile.mekanismMagicPlanRitualAutomation(rawInputs);
        List<OccultismRecipeBridge.RitualAutomationInput> keyedInputs = new ArrayList<>();
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                if (entry.getKey() instanceof AEItemKey key) {
                    keyedInputs.add(new OccultismRecipeBridge.RitualAutomationInput(
                            key.toStack(), Math.toIntExact(entry.getLongValue())));
                }
            }
        }
        var keyedPlan = tile.mekanismMagicPlanRitualAutomation(keyedInputs);
        logTest("Miner pattern dictionary={}", dictionaryDiagnostics(tile));
        for (ItemStack input : recipe.inputs()) {
            logTest("Miner pattern raw input={} x {}",
                    describeKey(AEItemKey.of(input)), input.getCount());
        }
        for (KeyCounter counter : counters) {
            logCounter("pattern input", counter);
        }
        String diagnostic = "recipe=" + recipe.recipe().recipeId()
                + ", rawPlan=" + rawPlan.map(plan -> "copies=" + plan.copies()
                        + " output=" + describeKey(AEItemKey.of(plan.output())))
                .orElse("absent") + ", keyedPlan="
                + keyedPlan.map(plan -> "copies=" + plan.copies()
                        + " output=" + describeKey(AEItemKey.of(plan.output())))
                .orElse("absent") + ", decodedOutputs=" + pattern.getOutputs().stream()
                .map(output -> describeKey(output.what()) + " x " + output.amount()).toList()
                + ", exactOutputMatch=" + keyedPlan.map(plan ->
                        pattern.getOutputs().size() == 1
                                && AEItemKey.of(plan.output()).equals(
                                pattern.getOutputs().getFirst().what())).orElse(false);
        logTest("Miner pattern result={}", brief(diagnostic, 850));
        return brief(diagnostic, 900);
    }

    private static String dictionaryDiagnostics(NativeRitualEngineBlockEntity tile) {
        var slots = tile.mekanismMagicManualOnlySlots();
        ItemStack dictionary = slots.size() > 1 ? slots.get(1).getStack() : ItemStack.EMPTY;
        return "manualSlotCount=" + slots.size() + ", slot1="
                + describeKey(AEItemKey.of(dictionary)) + ", count=" + dictionary.getCount()
                + ", isDictionaryOfSpirits="
                + OccultismRecipeBridge.isDictionaryOfSpirits(dictionary);
    }

    private static String describeCounter(KeyCounter counter) {
        List<String> entries = new ArrayList<>();
        for (var entry : counter) {
            entries.add(describeKey(entry.getKey()) + " x " + entry.getLongValue());
        }
        return brief(entries.toString(), 850);
    }

    private static String describeKey(appeng.api.stacks.AEKey key) {
        if (key instanceof AEItemKey item) {
            ItemStack stack = item.getReadOnlyStack();
            return brief(item.getId() + " name=" + componentValue(stack, "spirit_name")
                    + " miningTime=" + componentValue(stack, "max_mining_time")
                    + " rolls=" + componentValue(stack, "rolls_per_operation")
                    + " damage=" + stack.getDamageValue()
                    + " patchHash=" + Integer.toHexString(stack.getComponentsPatch().hashCode()), 300);
        }
        return key == null ? "none" : key.getClass().getSimpleName();
    }

    private static String componentValue(ItemStack stack, String path) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("occultism", path));
        return type == null ? "unregistered"
                : brief(String.valueOf(stack.get(type)).replace("\u00a7", "\\u00a7"), 80);
    }

    private static void logCounter(String label, KeyCounter counter) {
        for (var entry : counter) {
            logTest("Miner {}: {} x {}", label,
                    describeKey(entry.getKey()), entry.getLongValue());
        }
    }

    private static String brief(String text, int maximumLength) {
        return text.length() <= maximumLength ? text
                : text.substring(0, maximumLength - 3) + "...";
    }

    /** Keep each test log bounded and never stringify a CPU, recipe or component map. */
    private static void logTest(String format, Object... arguments) {
        StringBuilder message = new StringBuilder();
        int offset = 0;
        for (Object argument : arguments) {
            int marker = format.indexOf("{}", offset);
            if (marker < 0) {
                break;
            }
            message.append(format, offset, marker).append(diagnosticValue(argument));
            offset = marker + 2;
            if (message.length() >= 850) {
                break;
            }
        }
        if (message.length() < 850) {
            message.append(format.substring(offset));
        }
        MekanismMagic.LOGGER.info("{}", brief(message.toString()
                .replace('\n', ' ').replace('\r', ' '), 850));
    }

    private static String diagnosticValue(Object value) {
        if (value == null) {
            return "none";
        }
        if (value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?>
                || value instanceof java.util.UUID || value instanceof ResourceLocation
                || value instanceof BlockPos) {
            return brief(value.toString(), 800);
        }
        if (value instanceof appeng.api.stacks.AEKey key) {
            return describeKey(key);
        }
        if (value instanceof ItemStack stack) {
            return describeKey(AEItemKey.of(stack));
        }
        return value.getClass().getSimpleName();
    }

    private static void completed(GameTestHelper helper, Machine machine,
                                  ICraftingLink link, AEItemKey expected,
                                  long expectedAmount) {
        helper.assertTrue(link != null, "Miner CPU task was not submitted");
        CraftingCPUCluster submittedCpu = SUBMITTED_CPUS.get(link);
        helper.assertTrue(submittedCpu != null,
                "Miner task has no recorded submitted CPU");
        boolean cpuInventoryEmpty = true;
        for (var entry : submittedCpu.craftingLogic.getInventory().list) {
            if (entry.getLongValue() != 0) {
                cpuInventoryEmpty = false;
                break;
            }
        }
        boolean standaloneFinished = !link.isCanceled() && !submittedCpu.isBusy()
                && submittedCpu.getJobStatus() == null
                && crafting(machine).getRequestedAmount(expected) == 0
                && amount(machine, expected) == expectedAmount && cpuInventoryEmpty;
        boolean finished = link.isStandalone() ? standaloneFinished : link.isDone();
        long tick = helper.getTick();
        Long lastLogged = LAST_PENDING_LOG.get(link);
        if (!finished && (lastLogged == null || tick - lastLogged >= 200)) {
            LAST_PENDING_LOG.put(link, tick);
            logPendingJob(helper, machine, link, expected, expectedAmount);
        }
        helper.assertFalse(link.isCanceled(), "Miner CPU task was canceled");
        // AE2 19.2.17 standalone links have no nexus and never flip isDone.
        // Their completion is observable through the uncanceled CPU becoming
        // idle, draining its inventory and returning the exact requested key.
        if (!link.isStandalone()) {
            helper.assertTrue(link.isDone(),
                    "Miner CPU is waiting for an output key; still requested="
                            + crafting(machine).getRequestedAmount(expected));
        }
        helper.assertTrue(!submittedCpu.isBusy() && submittedCpu.getJobStatus() == null,
                "Miner CPU remained busy after returning its result");
        helper.assertTrue(crafting(machine).getRequestedAmount(expected) == 0,
                "Miner CPU still requests the finished output key");
        helper.assertTrue(cpuInventoryEmpty,
                "Miner CPU retained internal inventory after completing its task");
        helper.assertTrue(amount(machine, expected) == expectedAmount,
                "Finished miner task has the wrong exact-key output count");
    }

    private static void logPendingJob(GameTestHelper helper, Machine machine,
                                       ICraftingLink link, AEItemKey expected,
                                       long expectedAmount) {
        logTest("Miner pending at tick {}: id={}, expected={} x {}, "
                        + "standalone={}, done={}, canceled={}, requested={}, stored={}",
                helper.getTick(), link.getCraftingID(), describeKey(expected), expectedAmount,
                link.isStandalone(), link.isDone(), link.isCanceled(),
                crafting(machine).getRequestedAmount(expected), amount(machine, expected));
        for (var entry : storage(machine).getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey item && item.is(expected.getItem())) {
                logTest("Miner pending actual item={} x {}, exactExpected={}",
                        describeKey(item), entry.getLongValue(), expected.equals(item));
            }
        }
        for (var cpu : crafting(machine).getCpus()) {
            var status = cpu.getJobStatus();
            logTest("Miner pending CPU: busy={}, hasStatus={}, "
                            + "statusOutput={}, progress={}/{}", cpu.isBusy(), status != null,
                    status == null || status.crafting() == null
                            ? "none" : describeKey(status.crafting().what()),
                    status == null ? 0 : status.progress(), status == null ? 0 : status.totalItems());
            if (cpu instanceof CraftingCPUCluster cluster) {
                var logic = cluster.craftingLogic;
                GenericStack finalOutput = logic.getFinalJobOutput();
                ICraftingLink currentLink = logic.getLastLink();
                logTest("Miner pending CPU logic: hasJob={}, final={}, "
                                + "waiting={}, pendingOutputs={}, cpuStored={}, suspended={}, "
                                + "cantStore={}, currentLinkSame={}, currentLinkDone={}",
                        logic.hasJob(), finalOutput == null ? "none" : describeKey(finalOutput.what()),
                        logic.getWaitingFor(expected), logic.getPendingOutputs(expected),
                        logic.getStored(expected), logic.isJobSuspended(), logic.isCantStoreItems(),
                        currentLink == link, currentLink != null && currentLink.isDone());
                KeyCounter cpuItems = new KeyCounter();
                logic.getAllItems(cpuItems);
                logCounter("pending CPU item", cpuItems);
            }
        }
        logMachineSlots(helper, "pending tick " + helper.getTick());
    }

    private static ItemStack actualStoredMiner(GameTestHelper helper,
                                               Machine machine,
                                               ItemStack expected, long count) {
        List<AEItemKey> keys = new ArrayList<>();
        long actualCount = 0;
        for (var entry : storage(machine).getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey item
                    && item.is(expected.getItem()) && entry.getLongValue() > 0) {
                keys.add(item);
                actualCount += entry.getLongValue();
            }
        }
        helper.assertTrue(keys.size() == 1 && actualCount == count,
                "Actual miner outputs split into different AE component keys");
        ItemStack actual = keys.getFirst().toStack();
        assertSameComponents(helper, expected, actual,
                "JEI preview differs from the actual machine output");
        return actual;
    }

    private static void assertSameComponents(GameTestHelper helper,
                                             ItemStack expected,
                                             ItemStack actual,
                                             String message) {
        helper.assertTrue(expected != null && actual != null,
                message + "; a prerequisite item stack was not recorded");
        helper.assertTrue(ItemStack.isSameItemSameComponents(expected, actual)
                        && AEItemKey.of(expected).equals(AEItemKey.of(actual)),
                brief(message + "; expected=" + describeKey(AEItemKey.of(expected))
                        + "; actual=" + describeKey(AEItemKey.of(actual)), 980));
    }

    private static long amount(Machine machine, AEItemKey key) {
        return storage(machine).getAvailableStacks().get(key);
    }

    private static appeng.api.storage.MEStorage storage(Machine machine) {
        return machine.support().getGrid().getStorageService().getInventory();
    }

    private static ICraftingService crafting(Machine machine) {
        return machine.support().getGrid().getCraftingService();
    }

    private record RitualCase(RitualJeiData recipe, List<ItemStack> inputs) {
    }

    private record Machine(NativeRitualEngineBlockEntity tile,
                           AbstractMeAeSupport<?> support) {
    }

    private record SubmissionAttempt(ICraftingSubmitResult result,
                                     CraftingCPUCluster cpu) {
    }
}
