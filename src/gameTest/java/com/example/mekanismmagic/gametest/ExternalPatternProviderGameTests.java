package com.example.mekanismmagic.gametest;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import com.example.mekanismmagic.blockentity.NativeSpiritFactoryBlockEntity;
import com.example.mekanismmagic.integration.ae2.Ae2IdentifierImbuementPattern;
import com.example.mekanismmagic.integration.ae2.Ae2ImbuementProvider;
import com.example.mekanismmagic.integration.ae2.Ae2NatureAltarPattern;
import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusRecipeViews;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierItem;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierAssemblerBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.EnchantingApparatusProcessorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraRecipeScanner;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.OccultismSpiritPatternValidator;
import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import mekanism.api.Upgrade;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Actual stock AE2 provider, with no integrated MekE provider upgrade. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExternalPatternProviderGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos PROVIDER_POS = new BlockPos(1, 2, 1);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 2, 1);
    private static final BlockPos DRIVE_POS = new BlockPos(2, 2, 2);
    private static final BlockPos CPU_POS = new BlockPos(1, 2, 2);

    private ExternalPatternProviderGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 1200)
    public static void ordinaryProviderFeedsApparatusFromOneFaceAndReceivesExactOutput(
            GameTestHelper helper) {
        ApparatusFixture fixture = apparatusFixture(helper);
        List<ItemStack> ingredients = fixture.inputs();
        ItemStack result = fixture.output();

        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK.get());
        EnchantingApparatusProcessorBlockEntity machine =
                helper.getBlockEntity(MACHINE_POS);
        machine.getComponent().addUpgrades(Upgrade.SPEED, 8);
        machine.getComponent().addUpgrades(Upgrade.ENERGY, 8);
        helper.onEachTick(() -> {
            machine.setSource(machine.getMaxSource());
            charge(machine);
        });
        runRealCpu(helper, machine, ingredients, result);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void directCraftingCapabilityRejectsWrongFaceDisabledFaceAndMalformedBatch(
            GameTestHelper helper) {
        ApparatusFixture fixture = apparatusFixture(helper);
        EnchantingApparatusProcessorBlockEntity machine = placeApparatus(helper);
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(
                encode(fixture.inputs(), fixture.output()), helper.getLevel());
        helper.assertTrue(pattern != null,
                "AE could not decode a valid apparatus processing pattern");
        KeyCounter[] offered = counters(fixture.inputs());
        ICraftingMachine top = craftingMachine(helper, Direction.UP);
        helper.assertTrue(top != null && top.acceptsPlans(),
                "Top port does not expose active AE crafting-machine capability");

        ICraftingMachine wrong = craftingMachine(helper, Direction.NORTH);
        helper.assertTrue(wrong == null
                        || !wrong.pushPattern(pattern, offered,
                        Direction.NORTH),
                "AE crafting-machine capability accepted a disabled side");
        helper.assertTrue(allInputsEmpty(machine),
                "Wrong-face AE push changed apparatus inventory");

        KeyCounter[] malformed = counters(fixture.inputs());
        malformed[0] = new KeyCounter();
        malformed[0].add(AEItemKey.of(Items.DIRT), 1);
        helper.assertFalse(top.pushPattern(pattern, malformed, Direction.UP),
                "AE accepted an offered batch different from the pattern");
        helper.assertTrue(allInputsEmpty(machine),
                "Malformed AE batch partially inserted apparatus inputs");

        machine.getConfig().getConfig(TransmissionType.ITEM)
                .setDataType(DataType.NONE, RelativeSide.TOP);
        machine.getConfig().sideChanged(TransmissionType.ITEM,
                RelativeSide.TOP);
        ICraftingMachine disabled = craftingMachine(helper, Direction.UP);
        helper.assertTrue(disabled == null
                        || !disabled.pushPattern(pattern, offered,
                        Direction.UP),
                "AE crafting-machine capability ignored a disabled top side");
        helper.assertFalse(top.pushPattern(pattern, offered, Direction.UP),
                "Cached AE capability ignored live side reconfiguration");
        helper.assertTrue(allInputsEmpty(machine),
                "Disabled-face AE push changed apparatus inventory");

        RelativeSide north = RelativeSide.fromDirections(
                machine.getDirection(), Direction.NORTH);
        machine.getConfig().getConfig(TransmissionType.ITEM)
                .setDataType(DataType.INPUT_OUTPUT, north);
        machine.getConfig().sideChanged(TransmissionType.ITEM, north);
        ICraftingMachine selectedFace = craftingMachine(helper,
                Direction.NORTH);
        helper.assertTrue(selectedFace != null
                        && selectedFace.acceptsPlans()
                        && selectedFace.pushPattern(pattern, offered,
                        Direction.NORTH),
                "Ordinary provider could not use a configured non-top item face");
        List<ItemStack> staged = machine.mekanismMagicPatternInputs()
                .stream().map(slot -> slot.getStack().copy())
                .filter(stack -> !stack.isEmpty()).toList();
        helper.assertTrue(distinct(staged).equals(distinct(fixture.inputs()))
                        && machine.getNativeOutputSlot().isEmpty(),
                "Configured non-top face lost, duplicated or prematurely consumed batch items");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void directCraftingCapabilityRejectsBlockedOutputAtomically(
            GameTestHelper helper) {
        ApparatusFixture fixture = apparatusFixture(helper);
        EnchantingApparatusProcessorBlockEntity machine = placeApparatus(helper);
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(
                encode(fixture.inputs(), fixture.output()), helper.getLevel());
        helper.assertTrue(pattern != null,
                "AE could not decode a valid apparatus processing pattern");
        ItemStack obstruction = new ItemStack(Items.DIRT, 64);
        machine.getNativeOutputSlot().setStack(obstruction.copy());
        ICraftingMachine top = craftingMachine(helper, Direction.UP);
        helper.assertTrue(top != null && top.acceptsPlans(),
                "Top port does not expose active AE crafting-machine capability");
        helper.assertFalse(top.pushPattern(pattern, counters(fixture.inputs()),
                        Direction.UP),
                "AE committed a batch with no space for its exact output");
        helper.assertTrue(allInputsEmpty(machine)
                        && ItemStack.matches(obstruction,
                        machine.getNativeOutputSlot().getStack()),
                "Rejected full-output AE batch mutated machine inventory");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void ordinaryProviderCapabilityPreservesImbuementVirtualRecipeContext(
            GameTestHelper helper) {
        RecipeHolder<ImbuementRecipe> recipe =
                ArsNouveauRecipeScanner.scan(helper.getLevel().getRecipeManager())
                        .stream().filter(holder ->
                                holder.value().getPedestalItems().isEmpty()
                                        && holder.value().getInput().test(
                                        new ItemStack(Items.AMETHYST_SHARD)))
                        .findFirst().orElseThrow();
        ItemStack reagent = ArsNouveauRecipeBridge.representativeChoice(
                recipe.value().getInput());
        CustomData.update(DataComponents.CUSTOM_DATA, reagent,
                tag -> tag.putString("external_provider_payload", "kept"));
        ItemStack result = recipe.value().getResultItem(
                helper.getLevel().registryAccess());
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity machine =
                helper.getBlockEntity(MACHINE_POS);
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(
                encode(List.of(reagent), result), helper.getLevel());
        helper.assertTrue(pattern instanceof Ae2IdentifierImbuementPattern,
                "Plain imbuement pattern lost its exact-recipe AE decoder");
        ICraftingMachine top = craftingMachine(helper, Direction.UP);
        helper.assertTrue(top != null && top.acceptsPlans()
                        && top.pushPattern(pattern,
                        counters(List.of(reagent)), Direction.UP),
                "Ordinary AE capability rejected valid imbuement reagent");
        ItemStack received = machine.getNativeInputSlot().getStack();
        helper.assertTrue(recipe.id().equals(
                        ArsNouveauRecipeBridge.patternRecipe(received))
                        && ArsNouveauRecipeScanner.semanticSignature(
                        helper.getLevel().getRecipeManager(), recipe.id())
                        .equals(ArsNouveauRecipeBridge
                                .patternRecipeSignature(received))
                        && ItemStack.isSameItemSameComponents(reagent,
                        ArsNouveauRecipeBridge.recipeInputView(received)),
                "One-face AE route stripped the virtual recipe receipt or real reagent components");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 1200)
    public static void ordinaryProviderCapabilityPreservesNatureVirtualRecipeContext(
            GameTestHelper helper) {
        NatureAuraRecipeBridge.AltarRecipeView recipe =
                NatureAuraRecipeScanner.scan(helper.getLevel().getRecipeManager())
                        .stream().filter(candidate ->
                                !candidate.requiresCatalyst()
                                        && !candidate.output().isEmpty()
                                        && NatureAuraRecipeBridge
                                        .representativeChoice(candidate.input())
                                        .isPresent())
                        .findFirst().orElseThrow();
        ItemStack reagent = NatureAuraRecipeBridge
                .representativeChoice(recipe.input()).orElseThrow();
        CustomData.update(DataComponents.CUSTOM_DATA, reagent,
                tag -> tag.putString("external_provider_payload", "kept"));
        ItemStack context = NatureAuraRecipeBridge.createPatternContext(
                helper.getLevel(), recipe.id());
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(GenericStack.fromItemStack(reagent),
                        GenericStack.fromItemStack(context)),
                List.of(GenericStack.fromItemStack(recipe.output())));
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(
                encoded, helper.getLevel());
        helper.assertTrue(pattern instanceof Ae2NatureAltarPattern,
                "Nature altar pattern lost its virtual-context AE decoder");
        helper.setBlock(MACHINE_POS,
                NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get());
        NatureAltarProcessorBlockEntity machine =
                helper.getBlockEntity(MACHINE_POS);
        ICraftingMachine top = craftingMachine(helper, Direction.UP);
        helper.assertTrue(top != null && top.acceptsPlans()
                        && top.pushPattern(pattern,
                        counters(List.of(reagent)), Direction.UP),
                "Ordinary AE capability rejected exact Nature altar work");
        ItemStack received = machine.getNativeInputSlot().getStack();
        var savedContext = machine.getUpgradeData(
                helper.getLevel().registryAccess());
        helper.assertTrue(ItemStack.matches(reagent, received)
                        && !NatureAuraRecipeBridge
                        .hasPatternRecipeMarker(received)
                        && recipe.id().equals(machine.getLockedRecipe())
                        && savedContext.contextRemaining(0) == 1
                        && NatureAuraRecipeBridge.recipeSignature(recipe)
                        .equals(savedContext.lockedRecipeSignature(0)),
                "One-face AE route lost the clean reagent or exact locked recipe transaction");
        var aura = machine.getAuraContainer(Direction.UP);
        helper.assertTrue(aura != null,
                "Nature altar has no input Aura capability");
        helper.onEachTick(() -> {
            charge(machine);
            // The sided container enforces a per-tick transfer ceiling, so
            // a complete recipe budget is funded across several world ticks.
            aura.storeAura(recipe.auraCost(), false);
        });
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(ItemStack.matches(recipe.output(),
                            machine.getNativeOutputSlot().getStack())
                            && machine.getNativeInputSlot().isEmpty(),
                    "Ordinary AE-delivered Nature altar work did not finish with exact output");
            helper.assertTrue(machine.getLockedRecipe() == null
                            && machine.getUpgradeData(
                            helper.getLevel().registryAccess())
                            .contextRemaining(0) == 0,
                    "Nature altar retained a finished virtual recipe context");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void ordinaryProviderCannotSwitchBusyImbuementFactoryCatalyst(
            GameTestHelper helper) {
        List<CatalystCase> cases = catalystCases(helper, 2);
        CatalystCase first = cases.get(0);
        CatalystCase second = cases.get(1);
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get());
        ImbuementFactoryBlockEntity factory =
                helper.getBlockEntity(MACHINE_POS);
        helper.assertTrue(((MeUpgradeRecipeMachineAdapter) (Object) factory)
                        .getMeUpgradeContainer().isEmpty(),
                "Imbuement factory unexpectedly has integrated MekE provider");
        helper.assertTrue(factory.mekanismMagicPersistentInputs().size() >= 2,
                "Factory cannot retain both physical catalyst identifiers");
        factory.mekanismMagicPersistentInputs().get(0).setStack(
                ArsNouveauRecipeBridge.createIdentifierForRecipe(
                        helper.getLevel(), first.id()));
        factory.mekanismMagicPersistentInputs().get(1).setStack(
                ArsNouveauRecipeBridge.createIdentifierForRecipe(
                        helper.getLevel(), second.id()));
        ICraftingMachine top = craftingMachine(helper, Direction.UP);
        helper.assertTrue(top != null && top.acceptsPlans(),
                "Imbuement factory has no ordinary-provider top capability");
        IPatternDetails firstPattern = catalystPattern(helper, first);
        IPatternDetails secondPattern = catalystPattern(helper, second);
        helper.assertTrue(top.pushPattern(firstPattern,
                        counters(List.of(first.input())), Direction.UP),
                "First catalyst-context pattern could not enter idle factory");
        ItemStack selected = factory.selectedCatalystIdentifier().copy();
        helper.assertTrue(CatalystIdentifierItem.catalystId(selected)
                        .equals(first.catalystId())
                        && factory.mekanismMagicPatternInputs().stream()
                        .anyMatch(slot -> first.id().equals(
                                ArsNouveauRecipeBridge.patternRecipe(
                                        slot.getStack()))),
                "Successful first batch did not commit its exact catalyst context");
        List<ItemStack> before = factory.mekanismMagicPatternInputs()
                .stream().map(slot -> slot.getStack().copy()).toList();
        helper.assertFalse(top.pushPattern(secondPattern,
                        counters(List.of(second.input())), Direction.UP),
                "Second catalyst recipe displaced an occupied factory lane");
        List<ItemStack> after = factory.mekanismMagicPatternInputs()
                .stream().map(slot -> slot.getStack().copy()).toList();
        helper.assertTrue(before.size() == after.size()
                        && java.util.stream.IntStream.range(0, before.size())
                        .allMatch(index -> ItemStack.matches(
                                before.get(index), after.get(index)))
                        && ItemStack.matches(selected,
                        factory.selectedCatalystIdentifier()),
                "Rejected cross-catalyst AE batch changed inputs or shared selection");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 150)
    public static void requiredImbuementCatalystCannotBeVirtualizedByAeOrManualReceipt(
            GameTestHelper helper) {
        CatalystCase recipe = catalystCases(helper, 1).getFirst();
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity processor =
                helper.getBlockEntity(MACHINE_POS);
        ICraftingMachine top = craftingMachine(helper, Direction.UP);
        IPatternDetails pattern = catalystPattern(helper, recipe);
        Ae2ImbuementProvider nativeProvider =
                Ae2ImbuementProvider.forTile(processor);
        helper.assertFalse(nativeProvider.getAvailablePatterns().stream()
                        .anyMatch(candidate -> recipe.id().equals(
                        ArsNouveauRecipeBridge.patternRecipe(
                                candidate.getDefinition().toStack()))),
                "Native AE provider advertised a catalyst-required recipe without its physical identifier");
        helper.assertTrue(top != null && top.acceptsPlans(),
                "Imbuement processor has no ordinary-provider capability");
        helper.assertFalse(top.pushPattern(pattern,
                        counters(List.of(recipe.input())), Direction.UP),
                "AE input fabricated a pedestal catalyst from pattern metadata");
        helper.assertTrue(processor.getNativeInputSlot().isEmpty(),
                "Rejected catalyst-less AE batch entered the reagent slot");

        ItemStack marked = ArsNouveauRecipeBridge.markPatternInput(
                recipe.input(), recipe.id(),
                ArsNouveauRecipeScanner.semanticSignature(
                        helper.getLevel().getRecipeManager(), recipe.id()));
        processor.getNativeInputSlot().setStack(marked.copy());
        processor.setSource(processor.getMaxSource());
        charge(processor);
        for (int tick = 0; tick < 110; tick++) {
            TileEntityMekanism.tickServer(helper.getLevel(),
                    processor.getBlockPos(), processor.getBlockState(),
                    processor);
        }
        helper.assertTrue(ItemStack.matches(marked,
                        processor.getNativeInputSlot().getStack())
                        && processor.getNativeOutputSlot().isEmpty(),
                "A manually inserted recipe receipt consumed input without a physical catalyst");
        processor.getNativeInputSlot().setStack(ItemStack.EMPTY);

        ItemStack physical = ArsNouveauRecipeBridge
                .createIdentifierForRecipe(helper.getLevel(), recipe.id());
        helper.assertTrue(!physical.isEmpty()
                        && !processor.mekanismMagicPersistentInputs()
                        .isEmpty(),
                "No craftable physical catalyst identifier or library slot");
        processor.mekanismMagicPersistentInputs().getFirst()
                .setStack(physical.copy());
        helper.assertTrue(nativeProvider.getAvailablePatterns().stream()
                        .anyMatch(candidate -> recipe.id().equals(
                        ArsNouveauRecipeBridge.patternRecipe(
                                candidate.getDefinition().toStack()))),
                "Native AE provider did not advertise a physically enabled catalyst recipe");
        helper.assertTrue(top.pushPattern(pattern,
                        counters(List.of(recipe.input())), Direction.UP),
                "AE rejected a required recipe after its real identifier was installed");
        helper.assertTrue(!processor.getNativeInputSlot().isEmpty()
                        && ItemStack.matches(physical,
                        processor.selectedCatalystIdentifier())
                        && ItemStack.matches(physical,
                        processor.mekanismMagicPersistentInputs()
                                .getFirst().getStack()),
                "Successful AE batch lost or consumed its physical catalyst identifier");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void ordinaryProviderSplitsDuplicateCatalystSamplesIntoSeparateCells(
            GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID,
                "external_provider_duplicate_catalyst_fixture");
        ItemStack sample = new ItemStack(Items.BLAZE_POWDER);
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        try {
            List<RecipeHolder<?>> changed = new ArrayList<>(original);
            changed.add(new RecipeHolder<>(id, new ImbuementRecipe(
                    Ingredient.of(Items.AMETHYST_SHARD),
                    new ItemStack(Items.DIAMOND), 100,
                    List.of(Ingredient.of(Items.BLAZE_POWDER),
                            Ingredient.of(Items.BLAZE_POWDER)))));
            manager.replaceRecipes(changed);
            ArsNouveauRecipeScanner.refresh(manager);

            ItemStackHandler separated = new ItemStackHandler(9);
            separated.setStackInSlot(0, sample.copy());
            separated.setStackInSlot(1, sample.copy());
            var selected = ArsNouveauRecipeBridge.findCatalystIdentifier(
                    helper.getLevel(), separated, 0, 9);
            helper.assertTrue(selected.isPresent()
                            && !selected.get().output().isEmpty(),
                    "Two separate duplicate samples did not select exact identifier recipe");
            ItemStack expected = selected.get().output().copy();
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(sample), 2)),
                    List.of(GenericStack.fromItemStack(expected)));
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(
                    encoded, helper.getLevel());
            helper.assertTrue(pattern != null
                            && pattern.getInputs().length == 1,
                    "AE grouped duplicate samples into an invalid pattern");

            helper.setBlock(MACHINE_POS,
                    ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_BLOCK
                            .get());
            CatalystIdentifierAssemblerBlockEntity machine =
                    helper.getBlockEntity(MACHINE_POS);
            ICraftingMachine top = craftingMachine(helper, Direction.UP);
            helper.assertTrue(top != null && top.acceptsPlans()
                            && top.pushPattern(pattern,
                            counters(List.of(sample.copyWithCount(2))),
                            Direction.UP),
                    "Ordinary provider rejected grouped duplicate catalyst samples");

            List<ItemStack> slots = machine.mekanismMagicPatternInputs()
                    .stream().map(slot -> slot.getStack().copy()).toList();
            helper.assertTrue(slots.size() == 9
                            && slots.get(0).getCount() == 1
                            && slots.get(1).getCount() == 1
                            && ItemStack.isSameItemSameComponents(
                            sample, slots.get(0))
                            && ItemStack.isSameItemSameComponents(
                            sample, slots.get(1))
                            && slots.subList(2, 9).stream()
                            .allMatch(ItemStack::isEmpty),
                    "Grouped AE key was packed into one cell instead of two physical samples");
            ItemStackHandler actual = new ItemStackHandler(9);
            for (int index = 0; index < 9; index++) {
                actual.setStackInSlot(index, slots.get(index));
            }
            var resolved = ArsNouveauRecipeBridge.findCatalystIdentifier(
                    helper.getLevel(), actual, 0, 9);
            helper.assertTrue(resolved.isPresent()
                            && ItemStack.matches(expected,
                            resolved.get().output())
                            && machine.getNativeOutputSlot().isEmpty(),
                    "Separated duplicate samples changed exact identifier output contract");
        } finally {
            manager.replaceRecipes(original);
            ArsNouveauRecipeScanner.refresh(manager);
        }
        helper.succeed();
    }

    private static List<CatalystCase> catalystCases(
            GameTestHelper helper, int required) {
        List<CatalystCase> cases = new ArrayList<>();
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(
                        helper.getLevel().getRecipeManager())) {
            if (holder.value().getPedestalItems().isEmpty()) continue;
            ItemStack input = ArsNouveauRecipeBridge.representativeChoice(
                    holder.value().getInput());
            ItemStack output = holder.value().getResultItem(
                    helper.getLevel().registryAccess());
            ItemStack identifier = ArsNouveauRecipeBridge
                    .createPatternIdentifierForRecipe(helper.getLevel(),
                            holder.id());
            if (input.isEmpty() || output.isEmpty()
                    || identifier.isEmpty()) continue;
            ResourceLocation catalystId = CatalystIdentifierItem
                    .catalystId(identifier);
            if (cases.stream().anyMatch(existing ->
                    existing.catalystId().equals(catalystId))) continue;
            cases.add(new CatalystCase(holder.id(), input.copyWithCount(1),
                    output.copy(), identifier.copyWithCount(1),
                    catalystId));
            if (cases.size() >= required) return List.copyOf(cases);
        }
        throw new IllegalStateException(
                "Not enough distinct real imbuement catalyst recipes");
    }

    private static IPatternDetails catalystPattern(GameTestHelper helper,
                                                    CatalystCase recipe) {
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(GenericStack.fromItemStack(recipe.input()),
                        GenericStack.fromItemStack(recipe.identifier())),
                List.of(GenericStack.fromItemStack(recipe.output())));
        IPatternDetails decoded = PatternDetailsHelper.decodePattern(
                encoded, helper.getLevel());
        helper.assertTrue(decoded instanceof Ae2IdentifierImbuementPattern
                        && decoded.getInputs().length == 1,
                "Catalyst-context pattern did not decode to one physical input");
        return decoded;
    }

    private record CatalystCase(ResourceLocation id, ItemStack input,
                                ItemStack output, ItemStack identifier,
                                ResourceLocation catalystId) {
    }

    private static ApparatusFixture apparatusFixture(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "ars_nouveau", "enchanters_sword");
        RecipeHolder<? extends IEnchantingRecipe> recipe =
                ArsNouveauAPI.getInstance()
                        .getEnchantingApparatusRecipes(helper.getLevel())
                        .stream().filter(holder -> holder.id().equals(id))
                        .findFirst().orElseThrow(() -> new IllegalStateException(
                                "Missing real Ars recipe: " + id));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(139);
        sword.set(DataComponents.CUSTOM_NAME,
                Component.literal("External provider audit"));
        List<ItemStack> ingredients = new ArrayList<>();
        ingredients.add(sword);
        ArsApparatusRecipeViews.pedestalIngredients(recipe.value())
                .forEach(ingredient -> ingredients.add(
                        ArsNouveauRecipeBridge.representativeChoice(ingredient)));
        ItemStack result = ArsApparatusRecipeViews.resolveExact(
                helper.getLevel(), recipe, ingredients).orElseThrow();
        helper.assertTrue(ingredients.size() <= 9 && !result.isEmpty()
                        && ingredients.stream().noneMatch(ItemStack::isEmpty),
                "Ars fixture cannot fit a normal processing pattern");
        return new ApparatusFixture(List.copyOf(ingredients), result);
    }

    private static EnchantingApparatusProcessorBlockEntity placeApparatus(
            GameTestHelper helper) {
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK.get());
        return helper.getBlockEntity(MACHINE_POS);
    }

    private static ICraftingMachine craftingMachine(
            GameTestHelper helper, Direction side) {
        BlockEntity tile = helper.getBlockEntity(MACHINE_POS);
        return ICraftingMachine.of(tile, side);
    }

    private static boolean allInputsEmpty(
            NativeMagicMachineBlockEntity machine) {
        return machine.mekanismMagicPatternInputs().stream().allMatch(
                slot -> slot.getStack().isEmpty());
    }

    private static KeyCounter[] counters(List<ItemStack> inputs) {
        return distinct(inputs).entrySet().stream().map(entry -> {
            KeyCounter counter = new KeyCounter();
            counter.add(entry.getKey(), entry.getValue());
            return counter;
        }).toArray(KeyCounter[]::new);
    }

    private record ApparatusFixture(List<ItemStack> inputs,
                                    ItemStack output) {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 1200)
    public static void ordinaryProviderRoutesRitualMaterialsAndContextFromOneFace(
            GameTestHelper helper) {
        OccultismRecipeBridge.RitualJeiData selected = null;
        List<ItemStack> ingredients = List.of();
        ItemStack expectedOutput = ItemStack.EMPTY;
        for (OccultismRecipeBridge.RitualJeiData candidate :
                OccultismRecipeBridge.ritualJeiRecipes(helper.getLevel())) {
            if (candidate.output().isEmpty() || candidate.selector().isEmpty()
                    || candidate.activation() == null
                    || candidate.activation().isEmpty()) {
                continue;
            }
            List<ItemStack> offered = ritualInputs(candidate);
            if (offered.isEmpty() || distinct(offered).size() > 9) {
                continue;
            }
            List<OccultismRecipeBridge.RitualAutomationInput> planInputs =
                    offered.stream().map(stack ->
                            new OccultismRecipeBridge.RitualAutomationInput(
                                    stack, stack.getCount())).toList();
            var plan = OccultismRecipeBridge.planRitualAutomation(
                    helper.getLevel(), candidate.selector(), planInputs,
                    false);
            if (plan.isPresent() && !plan.get().output().isEmpty()
                    && !distinct(offered).containsKey(
                            AEItemKey.of(plan.get().output()))) {
                selected = candidate;
                ingredients = offered;
                expectedOutput = plan.get().output();
                break;
            }
        }
        helper.assertTrue(selected != null,
                "No exact item-producing Occultism ritual fits an AE processing pattern");
        helper.setBlock(MACHINE_POS,
                NativeMekanismRegistries.RITUAL_BLOCK.get());
        NativeRitualEngineBlockEntity machine = helper.getBlockEntity(MACHINE_POS);
        machine.mekanismMagicManualOnlySlots().getFirst()
                .setStack(selected.selector().copy());
        machine.getComponent().addUpgrades(Upgrade.SPEED, 8);
        machine.getComponent().addUpgrades(Upgrade.ENERGY, 8);
        helper.onEachTick(() -> charge(machine));
        runRealCpu(helper, machine, ingredients, expectedOutput);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 1200)
    public static void ordinaryProviderRunsSpiritFactoryWithoutMachineMeUpgrade(
            GameTestHelper helper) {
        SpiritFixture fixture = spiritFixture(helper);
        helper.setBlock(MACHINE_POS,
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.get());
        NativeSpiritFactoryBlockEntity factory =
                helper.getBlockEntity(MACHINE_POS);
        helper.assertTrue(factory.mekanismMagicPersistentInputs().size() == 1,
                "Spirit factory has no unique persistent spirit slot");
        factory.mekanismMagicPersistentInputs().getFirst()
                .setStack(fixture.spirit().copy());
        helper.onEachTick(() -> charge(factory));
        runRealCpu(helper, factory, List.of(fixture.input()),
                fixture.output());
    }

    private static SpiritFixture spiritFixture(GameTestHelper helper) {
        for (OccultismRecipeBridge.SpiritJeiData candidate :
                OccultismRecipeBridge.spiritJeiRecipes(helper.getLevel())) {
            if (!"crushing".equals(candidate.recipeType())
                    || candidate.output().isEmpty()
                    || candidate.spirit().isEmpty()) continue;
            for (ItemStack choice : candidate.input().getItems()) {
                if (choice.isEmpty()) continue;
                ItemStack input = choice.copyWithCount(1);
                ItemStack output = candidate.output().copy();
                if (!OccultismSpiritPatternValidator.matches(
                        helper.getLevel(), candidate.spirit(),
                        List.of(new PatternStack(input, 1)),
                        List.of(new PatternStack(output,
                                output.getCount())))) continue;
                return new SpiritFixture(input, output,
                        candidate.spirit().copy());
            }
        }
        throw new IllegalStateException(
                "No deterministic item-producing Spirit crushing recipe");
    }

    private record SpiritFixture(ItemStack input, ItemStack output,
                                 ItemStack spirit) {
    }

    private static List<ItemStack> ritualInputs(
            OccultismRecipeBridge.RitualJeiData ritual) {
        List<ItemStack> offered = new ArrayList<>();
        if (ritual.activation() != null && !ritual.activation().isEmpty()) {
            ItemStack activation = firstChoice(ritual.activation());
            if (activation.isEmpty()) return List.of();
            offered.add(activation);
        }
        if (!ritual.sacrifices().isEmpty()) {
            ItemStack sacrifice = ritual.sacrifices().getFirst();
            if (sacrifice.isEmpty()) return List.of();
            offered.add(sacrifice.copyWithCount(1));
        }
        for (Ingredient ingredient : ritual.ingredients()) {
            ItemStack material = firstChoice(ingredient);
            if (material.isEmpty()) return List.of();
            offered.add(material);
        }
        return List.copyOf(offered);
    }

    private static ItemStack firstChoice(Ingredient ingredient) {
        ItemStack[] choices = ingredient.getItems();
        return choices.length == 0 ? ItemStack.EMPTY : choices[0].copyWithCount(1);
    }

    private static Map<AEItemKey, Long> distinct(List<ItemStack> inputs) {
        Map<AEItemKey, Long> amounts = new LinkedHashMap<>();
        inputs.forEach(stack -> amounts.merge(
                AEItemKey.of(stack), (long) stack.getCount(), Math::addExact));
        return amounts;
    }

    private static ItemStack encode(List<ItemStack> inputs, ItemStack output) {
        return PatternDetailsHelper.encodeProcessingPattern(
                distinct(inputs).entrySet().stream().map(entry ->
                        new GenericStack(entry.getKey(), entry.getValue())).toList(),
                List.of(new GenericStack(AEItemKey.of(output), output.getCount())));
    }

    private static void charge(IMekanismMagicAutomation machine) {
        if (machine.mekanismMagicEnergyContainer() != null) {
            machine.mekanismMagicEnergyContainer().setEnergy(
                    machine.mekanismMagicEnergyContainer().getMaxEnergy());
        }
    }

    private static void runRealCpu(GameTestHelper helper,
                                   IMekanismMagicAutomation machine,
                                   List<ItemStack> inputs,
                                   ItemStack output) {
        helper.assertTrue(((MeUpgradeRecipeMachineAdapter) (Object) machine)
                        .getMeUpgradeContainer().isEmpty(),
                "Machine unexpectedly has an integrated MekE upgrade");
        helper.setBlock(PROVIDER_POS, AEBlocks.PATTERN_PROVIDER.block()
                .defaultBlockState().setValue(
                        PatternProviderBlock.PUSH_DIRECTION,
                        PushDirection.DOWN));
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROVIDER_POS);
        helper.assertTrue(provider.getTargets().contains(
                        net.minecraft.core.Direction.DOWN)
                        && provider.getTargets().size() == 1,
                "Ordinary AE2 provider is not aimed at the one machine face");
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        helper.setBlock(CPU_POS, AEBlocks.CRAFTING_STORAGE_4K.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS);
        helper.assertTrue(drive.getInternalInventory().insertItem(
                        0, AEItems.ITEM_CELL_16K.stack(), false).isEmpty(),
                "Could not install ordinary-provider test cell");
        provider.getLogic().getPatternInv().setItemDirect(
                0, encode(inputs, output));
        provider.getLogic().updatePatterns();
        AEItemKey expected = AEItemKey.of(output);
        AtomicReference<Future<ICraftingPlan>> calculation =
                new AtomicReference<>();
        AtomicReference<ICraftingSubmitResult> submission =
                new AtomicReference<>();
        AtomicReference<CraftingCPUCluster> submittedCpu =
                new AtomicReference<>();
        IActionSource source = IActionSource.ofMachine(
                () -> provider.getMainNode().getNode());

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(provider.getMainNode().isActive()
                            && drive.isPowered()
                            && provider.getLogic().getGrid() != null,
                    "Ordinary-provider AE network is not active");
            helper.assertTrue(provider.getLogic().getGrid()
                            .getCraftingService().isCraftable(expected),
                    "External provider did not advertise the processing pattern");
            helper.assertTrue(!provider.getLogic().getGrid()
                            .getCraftingService().getCpus().isEmpty(),
                    "External provider has no real crafting CPU");
        }).thenExecute(() -> distinct(inputs).forEach((key, amount) ->
                helper.assertTrue(provider.getLogic().getGrid()
                                .getStorageService().getInventory().insert(
                                        key, amount, Actionable.MODULATE,
                                        source) == amount,
                        "AE cell rejected an external-provider ingredient")))
                .thenWaitUntil(() -> distinct(inputs).forEach((key, amount) ->
                        helper.assertTrue(provider.getLogic().getGrid()
                                        .getStorageService().getCachedInventory()
                                        .get(key) == amount,
                                "AE cache has not observed an ingredient")))
                .thenExecute(() -> calculation.set(provider.getLogic().getGrid()
                        .getCraftingService().beginCraftingCalculation(
                                helper.getLevel(), () -> source, expected,
                                output.getCount(),
                                CalculationStrategy.REPORT_MISSING_ITEMS)))
                .thenWaitUntil(() -> {
                    helper.assertTrue(calculation.get() != null
                                    && calculation.get().isDone(),
                            "External-provider AE craft is still calculating");
                    if (submission.get() == null) {
                        ICraftingPlan plan;
                        try {
                            plan = calculation.get().get();
                        } catch (Exception failure) {
                            helper.fail("External-provider plan failed: " + failure);
                            return;
                        }
                        helper.assertTrue(plan != null && !plan.simulation()
                                        && plan.missingItems().isEmpty(),
                                "AE could not plan the exact external-provider inputs");
                        CraftingCPUCluster cpu = provider.getLogic().getGrid()
                                .getCraftingService().getCpus().stream()
                                .filter(CraftingCPUCluster.class::isInstance)
                                .map(CraftingCPUCluster.class::cast)
                                .filter(candidate -> !candidate.isBusy()
                                        && candidate.getAvailableStorage()
                                        >= plan.bytes())
                                .findFirst().orElse(null);
                        helper.assertTrue(cpu != null,
                                "No idle real CPU can hold external-provider craft");
                        submittedCpu.set(cpu);
                        submission.set(provider.getLogic().getGrid()
                                .getCraftingService().submitJob(plan, null,
                                        cpu, false, source));
                    }
                    helper.assertTrue(submission.get().successful(),
                            "AE rejected external-provider craft: "
                                    + submission.get().errorCode());
                }).thenWaitUntil(() -> {
                    helper.assertTrue(!submittedCpu.get().isBusy()
                                    && submittedCpu.get().getJobStatus() == null,
                            "Real AE CPU still waits for machine output");
                    helper.assertTrue(provider.getLogic().getGrid()
                                    .getCraftingService()
                                    .getRequestedAmount(expected) == 0,
                            "AE CPU still requests machine output");
                    helper.assertTrue(provider.getLogic().getGrid()
                                    .getStorageService().getInventory()
                                    .getAvailableStacks().get(expected)
                                    == output.getCount(),
                            "Exact machine output was not returned through stock provider");
                    helper.assertTrue(machine.mekanismMagicPatternInputs()
                                    .stream().allMatch(slot ->
                                            slot.getStack().isEmpty()),
                            "External provider left processing inputs behind");
                    helper.assertTrue(machine.mekanismMagicPatternOutputs()
                                    .stream().allMatch(slot ->
                                            slot.getStack().isEmpty()),
                            "Machine output was not extracted back into AE");
                }).thenSucceed();
    }
}
