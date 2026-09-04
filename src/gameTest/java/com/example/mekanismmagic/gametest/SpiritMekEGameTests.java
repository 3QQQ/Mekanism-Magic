package com.example.mekanismmagic.gametest;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.cells.CellState;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.blockentity.support
        .MeSmartPatternMultiplication;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.blockentity.NativeSpiritFactoryBlockEntity;
import com.example.mekanismmagic.blockentity.PersistentInputMutationGuard;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.OccultismSpiritPatternValidator;
import com.example.mekanismmagic.integration.occultism.SpiritFactoryRecipe;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime integration tests for the Spirit-machine Mek Energistics boundary.
 *
 * <p>This class belongs to the dedicated {@code gameTest} source set. It must
 * never be packaged in the release jar: its direct AE2/MekE references are
 * intentional and the corresponding GameTest run always supplies those mods.</p>
 */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritMekEGameTests {
    private static final BlockPos PROCESSOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 1);
    private static final BlockPos FACTORY_POS = new BlockPos(3, 1, 1);
    // A drive's front face is not grid-connectable. Put it above the creative
    // energy cell so its default horizontal orientation cannot isolate it.
    private static final BlockPos DRIVE_POS = new BlockPos(2, 2, 1);
    private static final BlockPos EXTRA_FACTORY_POS = new BlockPos(2, 1, 0);

    private SpiritMekEGameTests() {
    }

    // Use a bundled vanilla template so this isolated source set does not need
    // to ship a binary structure asset. Every block relevant to the test is
    // replaced explicitly below.
    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void standardPushRejectsChangedSpirit(
            GameTestHelper helper) {
        helper.setBlock(PROCESSOR_POS,
                NativeMekanismRegistries.SPIRIT_BLOCK.get());
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());

        MachineHandle machine = installPatternProvider(
                helper, PROCESSOR_POS);
        PatternCase recipe = deterministicRecipe(helper.getLevel());
        IInventorySlot spiritSlot = onlySlot(
                machine.host().mekanismMagicPersistentInputs(),
                "Spirit processor persistent source slot");
        IInventorySlot inputSlot = onlySlot(
                machine.host().mekanismMagicPatternInputs(),
                "Spirit processor pattern input slot");
        spiritSlot.setStack(recipe.validSpirit());

        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(generic(recipe.input(), 1)),
                List.of(generic(recipe.output(), recipe.output().getCount())));
        helper.assertFalse(encoded.isEmpty(),
                "AE2 refused to encode the deterministic Spirit pattern");
        machine.support().getPatternSlots().getFirst().setStack(encoded);
        machine.support().updatePatterns();
        IPatternDetails details = PatternDetailsHelper.decodePattern(
                encoded, helper.getLevel());
        helper.assertTrue(details != null,
                "AE2 could not decode its encoded Spirit pattern");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        machine.support().getMainNode().isActive(),
                        "Spirit processor ME node did not become active"))
                .thenExecute(() -> {
                    helper.assertTrue(machine.support()
                                    .getAvailablePatterns().stream()
                                    .anyMatch(pattern -> pattern.getDefinition()
                                            .equals(details.getDefinition())),
                            "Valid SpiritJob pattern was not advertised");
                    boolean pushed = machine.support()
                            .pushPatternWithAdapter(details,
                                    counters(recipe.input(), 1));
                    helper.assertTrue(pushed,
                            "Valid standard AE push was rejected");
                    helper.assertTrue(sameStackAndCount(
                                    inputSlot.getStack(), recipe.input(), 1),
                            "Valid standard AE push did not reach the input slot");

                    // Keep this synchronous with push: no recipe tick may
                    // consume the staged input before the assertion above.
                    inputSlot.setStack(ItemStack.EMPTY);
                    spiritSlot.setStack(recipe.invalidSpirit());
                    helper.assertFalse(machine.support()
                                    .getAvailablePatterns().stream()
                                    .anyMatch(pattern -> pattern.getDefinition()
                                            .equals(details.getDefinition())),
                            "Old pattern remained advertised after SpiritJob change");
                    boolean stalePush = machine.support()
                            .pushPatternWithAdapter(details,
                                    counters(recipe.input(), 1));
                    helper.assertFalse(stalePush,
                            "A stale pattern was accepted after SpiritJob change");
                    helper.assertTrue(inputSlot.getStack().isEmpty(),
                            "Rejected stale push mutated the machine input");
                })
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 300)
    public static void processorAndFactoryOutputsReachAeWithoutLoss(
            GameTestHelper helper) {
        helper.setBlock(PROCESSOR_POS,
                NativeMekanismRegistries.SPIRIT_BLOCK.get());
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(FACTORY_POS,
                NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.get());

        MachineHandle processor = installPatternProvider(
                helper, PROCESSOR_POS);
        MachineHandle factory = installPatternProvider(
                helper, FACTORY_POS);
        IInventorySlot processorOutput = onlySlot(
                processor.host().mekanismMagicPatternOutputs(),
                "Spirit processor output slot");
        IInventorySlot factoryOutput = firstSlot(
                factory.host().mekanismMagicPatternOutputs(),
                "Ultimate Spirit factory output slots");

        ItemStack processorStack = new ItemStack(Items.DIAMOND, 3);
        ItemStack factoryStack = new ItemStack(Items.EMERALD, 5);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            processor.support().getMainNode().isActive(),
                            "Spirit processor ME node did not become active");
                    helper.assertTrue(
                            factory.support().getMainNode().isActive(),
                            "Ultimate Spirit factory ME node did not become active");
                    helper.assertTrue(processor.support().getGrid()
                                    == factory.support().getGrid(),
                            "Spirit machines did not join the same AE grid");
                })
                .thenExecute(() -> {
                    processorOutput.setStack(processorStack.copy());
                    factoryOutput.setStack(factoryStack.copy());
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(sameStackAndCount(
                                    processorOutput.getStack(),
                                    processorStack, processorStack.getCount()),
                            "Processor output changed while AE had no storage");
                    helper.assertTrue(sameStackAndCount(
                                    factoryOutput.getStack(),
                                    factoryStack, factoryStack.getCount()),
                            "Factory output changed while AE had no storage");
                    attachDrive(helper);
                })
                .thenWaitUntil(() -> assertStorageReady(
                        helper, processor, processorStack))
                .thenWaitUntil(() -> {
                    helper.assertTrue(processorOutput.getStack().isEmpty(),
                            "Processor output did not enter AE storage");
                    helper.assertTrue(factoryOutput.getStack().isEmpty(),
                            "Factory output did not enter AE storage");
                    KeyCounter stored = processor.support().getGrid()
                            .getStorageService().getInventory()
                            .getAvailableStacks();
                    helper.assertTrue(stored.get(AEItemKey.of(
                                    processorStack))
                                    == processorStack.getCount(),
                            "AE stored the wrong processor output count");
                    helper.assertTrue(stored.get(AEItemKey.of(factoryStack))
                                    == factoryStack.getCount(),
                            "AE stored the wrong factory output count");
                })
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 320)
    public static void legacyPendingRefundSurvivesRecoveryAndReturnsToAe(
            GameTestHelper helper) {
        helper.setBlock(PROCESSOR_POS,
                NativeMekanismRegistries.SPIRIT_BLOCK.get());
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());

        MachineHandle machine = installPatternProvider(
                helper, PROCESSOR_POS);
        ItemStack input = new ItemStack(Items.GOLD_INGOT);
        AEItemKey inputKey = AEItemKey.of(input);
        int amountPerCraft = 2;
        int copies = 3;
        long expectedRefund = (long) amountPerCraft * copies;

        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(generic(input, amountPerCraft)),
                List.of(generic(new ItemStack(Items.DIAMOND), 1)));
        helper.assertFalse(encoded.isEmpty(),
                "AE2 refused to encode the legacy pending definition");
        IPatternDetails details = PatternDetailsHelper.decodePattern(
                encoded, helper.getLevel());
        helper.assertTrue(details != null,
                "AE2 could not decode the legacy pending definition");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        machine.support().getMainNode().isActive(),
                        "Legacy-pending machine ME node did not become active"))
                .thenExecute(() -> {
                    // Produce the persisted queue with MekE itself. This pins
                    // the real 3.0.6 save schema without duplicating its NBT
                    // writer in test code.
                    MeSmartPatternMultiplication legacy =
                            new MeSmartPatternMultiplication();
                    helper.assertTrue(legacy.enqueue(details,
                                    counters(input, expectedRefund)),
                            "MekE refused to enqueue the legacy smart batch");
                    CompoundTag pendingState = new CompoundTag();
                    legacy.savePending(pendingState,
                            helper.getLevel().registryAccess());
                    ListTag pending = pendingState.getList(
                            "SmartPatternMultiplicationPending",
                            Tag.TAG_COMPOUND);
                    helper.assertTrue(pending.size() == 1,
                            "MekE did not serialize exactly one pending entry");
                    helper.assertTrue(pending.getCompound(0)
                                    .getLong("Remaining") == copies,
                            "MekE serialized the wrong pending copy count");

                    machine.support().loadSlots(pendingState,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(machine.support().isPatternBusy(),
                            "Actual MekE loadSlots did not restore pending work");
                })
                .thenWaitUntil(() -> {
                    helper.assertFalse(machine.support().isPatternBusy(),
                            "Legacy pending copies were not retired by machine tick");
                    helper.assertTrue(machine.support().hasInterfaceRecovery(),
                            "Rejected legacy refund did not enter persistent recovery");
                })
                .thenExecute(() -> {
                    CompoundTag persisted = new CompoundTag();
                    machine.adapter().saveMeState(persisted,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(persisted.getList(
                                    "SmartPatternMultiplicationPending",
                                    Tag.TAG_COMPOUND).isEmpty(),
                            "Retired legacy pending work was serialized again");
                    helper.assertTrue(recoveryAmount(persisted,
                                    helper.getLevel().registryAccess(), inputKey)
                                    == expectedRefund,
                            "Persistent recovery did not conserve all legacy inputs");

                    // Exercise the corresponding real load path before the
                    // network becomes writable. The amount must survive the
                    // round trip, not merely remain in the live Java list.
                    machine.adapter().loadMeState(persisted,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(machine.support().hasInterfaceRecovery(),
                            "Recovery disappeared across actual MekE save/load");
                    CompoundTag roundTrip = new CompoundTag();
                    machine.adapter().saveMeState(roundTrip,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(recoveryAmount(roundTrip,
                                    helper.getLevel().registryAccess(), inputKey)
                                    == expectedRefund,
                            "Recovery amount changed across actual MekE save/load");
                    attachDrive(helper);
                })
                .thenWaitUntil(() -> assertStorageReady(
                        helper, machine, input))
                .thenWaitUntil(() -> {
                    helper.assertFalse(machine.support().hasInterfaceRecovery(),
                            "Persistent recovery did not drain after AE storage mounted");
                    long stored = machine.support().getGrid()
                            .getStorageService().getInventory()
                            .getAvailableStacks().get(inputKey);
                    helper.assertTrue(stored == expectedRefund,
                            "AE received the wrong legacy refund amount: "
                                    + stored + " instead of " + expectedRefund);
                })
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void directCapabilityCannotSwapBusySpirit(
            GameTestHelper helper) {
        helper.setBlock(PROCESSOR_POS,
                NativeMekanismRegistries.SPIRIT_BLOCK.get());
        helper.setBlock(FACTORY_POS,
                NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.get());
        PatternCase recipe = deterministicRecipe(helper.getLevel());

        BlockEntity processorEntity = helper.getBlockEntity(PROCESSOR_POS);
        helper.assertTrue(processorEntity
                        instanceof NativeMagicMachineBlockEntity,
                "Spirit processor block entity was not created");
        NativeMagicMachineBlockEntity processor =
                (NativeMagicMachineBlockEntity) processorEntity;
        assertDirectSourceGuard(helper, processor, processor,
                recipe.validSpirit(), recipe.invalidSpirit(), recipe.input(),
                "Spirit processor");

        BlockEntity factoryEntity = helper.getBlockEntity(FACTORY_POS);
        helper.assertTrue(factoryEntity
                        instanceof NativeSpiritFactoryBlockEntity,
                "Ultimate Spirit factory block entity was not created");
        NativeSpiritFactoryBlockEntity factory =
                (NativeSpiritFactoryBlockEntity) factoryEntity;
        assertDirectSourceGuard(helper, factory, factory,
                recipe.validSpirit(), recipe.invalidSpirit(), recipe.input(),
                "Ultimate Spirit factory");

        helper.setBlock(EXTRA_FACTORY_POS,
                requiredAddonBlock("absolute_spirit_factory"));
        BlockEntity extraFactoryEntity = helper.getBlockEntity(
                EXTRA_FACTORY_POS);
        helper.assertTrue(extraFactoryEntity instanceof TileEntityMekanism
                        && extraFactoryEntity
                        instanceof IMekanismMagicAutomation,
                "Absolute Spirit factory block entity was not created");
        assertDirectSourceGuard(helper,
                (TileEntityMekanism) extraFactoryEntity,
                (IMekanismMagicAutomation) extraFactoryEntity,
                recipe.validSpirit(), recipe.invalidSpirit(), recipe.input(),
                "Absolute Spirit factory");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void tierInstallerPreservesCompleteMeState(
            GameTestHelper helper) {
        helper.setBlock(FACTORY_POS,
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.get());
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());

        MachineHandle original = installPatternProvider(helper, FACTORY_POS);
        PatternCase recipe = deterministicRecipe(helper.getLevel());
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(generic(recipe.input(), 1)),
                List.of(generic(recipe.output(), recipe.output().getCount())));
        helper.assertFalse(encoded.isEmpty(),
                "AE2 refused to encode the tier-upgrade test pattern");
        AtomicReference<AbstractMeAeSupport<?>> upgradedSupport =
                new AtomicReference<>();
        AtomicReference<MeUpgradeRecipeMachineAdapter> upgradedAdapter =
                new AtomicReference<>();
        AtomicReference<CompoundTag> expectedNodeState =
                new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        original.support().getMainNode().isActive(),
                        "Basic Spirit factory ME node did not become active"))
                .thenExecute(() -> {
                    original.support().getPatternSlots().getFirst()
                            .setStack(encoded);
                    original.support()
                            .setVisibleInPatternAccessTerminal(false);

                    CompoundTag seeded = new CompoundTag();
                    original.adapter().saveMeState(seeded,
                            helper.getLevel().registryAccess());
                    seeded.putInt("PatternPriority", 73);
                    ListTag recovery = new ListTag();
                    AEItemKey recoveryKey = AEItemKey.of(Items.GOLD_INGOT);
                    helper.assertTrue(recoveryKey != null,
                            "Unable to construct AE recovery key");
                    recovery.add(GenericStack.writeTag(
                            helper.getLevel().registryAccess(),
                            new GenericStack(recoveryKey, 17)));
                    seeded.put("MeInterfaceRecovery", recovery);
                    original.adapter().loadMeState(seeded,
                            helper.getLevel().registryAccess());
                    original.adapter().onMeUpgradeStateChanged();

                    CompoundTag beforeUpgrade = new CompoundTag();
                    original.adapter().saveMeState(beforeUpgrade,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(original.support()
                                    .getPatternPriority() == 73,
                            "Unable to seed MekE priority before tier upgrade");
                    helper.assertTrue(recoveryAmount(beforeUpgrade,
                                    helper.getLevel().registryAccess(),
                                    recoveryKey) == 17,
                            "Unable to seed MekE recovery before tier upgrade");
                    helper.assertTrue(beforeUpgrade.contains(
                                    "node", Tag.TAG_COMPOUND),
                            "MekE did not serialize its managed node before "
                                    + "tier upgrade");
                    expectedNodeState.set(beforeUpgrade
                            .getCompound("node").copy());

                    ItemStack installer = MekanismItems
                            .ADVANCED_TIER_INSTALLER.asStack();
                    BlockPos absolute = helper.absolutePos(FACTORY_POS);
                    var player = helper.makeMockPlayer(GameType.SURVIVAL);
                    BlockHitResult hit = new BlockHitResult(
                            Vec3.atCenterOf(absolute), Direction.UP,
                            absolute, false);
                    InteractionResult result = MekanismItems
                            .ADVANCED_TIER_INSTALLER.asItem().useOn(
                                    new UseOnContext(helper.getLevel(),
                                            player, InteractionHand.MAIN_HAND,
                                            installer, hit));
                    helper.assertTrue(result == InteractionResult.CONSUME,
                            "Mekanism tier installer did not replace the "
                                    + "basic factory: " + result);
                    helper.assertTrue(installer.isEmpty(),
                            "Survival tier installer was not consumed");
                    helper.assertTrue(helper.getBlockState(FACTORY_POS).is(
                                    NativeMekanismRegistries
                                            .ADVANCED_SPIRIT_FACTORY_BLOCK
                                            .get()),
                            "Tier upgrade changed the Spirit factory into "
                                    + "the wrong block");

                    BlockEntity upgradedEntity = helper.getBlockEntity(
                            FACTORY_POS);
                    helper.assertTrue(upgradedEntity
                                    instanceof MeUpgradeRecipeMachineAdapter,
                            "Upgraded Spirit factory lost its MekE adapter");
                    MeUpgradeRecipeMachineAdapter upgraded =
                            (MeUpgradeRecipeMachineAdapter) upgradedEntity;
                    AbstractMeAeSupport<?> support = upgraded
                            .getOrCreateMeUpgradeRuntime().support();
                    CompoundTag afterUpgrade = new CompoundTag();
                    upgraded.saveMeState(afterUpgrade,
                            helper.getLevel().registryAccess());

                    helper.assertTrue(upgraded.getMeUpgradeContainer()
                                    .isInstalled(
                                            MeUpgradeType.PATTERN_PROVIDER),
                            "Pattern-provider upgrade disappeared during "
                                    + "tier replacement");
                    helper.assertTrue(support.getPatternPriority() == 73,
                            "Pattern priority disappeared during tier "
                                    + "replacement");
                    helper.assertFalse(
                            support.isVisibleInPatternAccessTerminal(),
                            "Pattern terminal visibility reset during tier "
                                    + "replacement");
                    helper.assertTrue(!support.getPatternSlots().isEmpty()
                                    && ItemStack.isSameItemSameComponents(
                                    support.getPatternSlots().getFirst()
                                            .getStack(), encoded),
                            "Encoded pattern disappeared during tier "
                                    + "replacement");
                    helper.assertTrue(recoveryAmount(afterUpgrade,
                                    helper.getLevel().registryAccess(),
                                    recoveryKey) == 17,
                            "Persistent recovery changed during tier "
                                    + "replacement");
                    upgradedAdapter.set(upgraded);
                    upgradedSupport.set(support);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        upgradedSupport.get() != null
                                && upgradedSupport.get().getMainNode()
                                .isActive(),
                        "Upgraded Spirit factory ME node did not reactivate"))
                .thenExecute(() -> {
                    CompoundTag reactivated = new CompoundTag();
                    upgradedAdapter.get().saveMeState(reactivated,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(reactivated.contains(
                                    "node", Tag.TAG_COMPOUND)
                                    && expectedNodeState.get().equals(
                                    reactivated.getCompound("node")),
                            "Managed ME node state changed during tier "
                                    + "replacement; before="
                                    + expectedNodeState.get() + ", after="
                                    + reactivated.getCompound("node"));
                })
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 360)
    public static void processingRevisionStopsInvalidFactoryWork(
            GameTestHelper helper) {
        helper.setBlock(FACTORY_POS,
                NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.get());
        BlockEntity blockEntity = helper.getBlockEntity(FACTORY_POS);
        helper.assertTrue(blockEntity
                        instanceof NativeSpiritFactoryBlockEntity,
                "Ultimate Spirit factory block entity was not created");
        NativeSpiritFactoryBlockEntity factory =
                (NativeSpiritFactoryBlockEntity) blockEntity;
        FactoryRecipeCase recipe = longRunningFactoryRecipe(
                helper.getLevel());
        IInventorySlot source = onlySlot(
                factory.mekanismMagicPersistentInputs(),
                "Ultimate Spirit factory persistent source slot");
        IInventorySlot input = firstSlot(
                factory.mekanismMagicPatternInputs(),
                "Ultimate Spirit factory input slots");
        IInventorySlot output = firstSlot(
                factory.mekanismMagicPatternOutputs(),
                "Ultimate Spirit factory output slots");
        AtomicReference<SpiritFactoryRecipe> stale =
                new AtomicReference<>();

        factory.mekanismMagicEnergyContainer().setEnergy(
                factory.mekanismMagicEnergyContainer().getMaxEnergy());
        source.setStack(recipe.spirit());
        input.setStack(recipe.input());

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(factory.mekanismMagicIsBusy(),
                            "Spirit factory never started its valid recipe");
                    helper.assertTrue(factory.getScaledProgress(100, 0) > 0,
                            "Spirit factory did not advance before reload");
                })
                .thenExecute(() -> {
                    SpiritFactoryRecipe cached = factory.getRecipe(0);
                    helper.assertTrue(cached != null,
                            "Running Spirit factory recipe disappeared early");
                    stale.set(cached);
                    long before = OccultismRecipeBridge
                            .spiritProcessingRevision();
                    OccultismRecipeBridge.invalidateRecipeCaches();
                    helper.assertTrue(OccultismRecipeBridge
                                    .spiritProcessingRevision() != before,
                            "Recipe invalidation did not advance the processing revision");
                    helper.assertFalse(cached.sameProcessingRevision(),
                            "Cached Spirit recipe survived its processing revision");
                    helper.assertFalse(cached.test(recipe.input()),
                            "Stale Spirit recipe still accepted its old input");

                    // Model a datapack/config change that removes the live
                    // match while the old cache is still active.
                    input.setStack(new ItemStack(Items.BARRIER));
                    helper.assertTrue(factory.getRecipe(0) == null,
                            "Replacement input unexpectedly has a Spirit recipe");
                })
                .thenWaitUntil(() -> {
                    helper.assertFalse(factory.mekanismMagicIsBusy(),
                            "Factory kept processing its stale cached recipe");
                    helper.assertTrue(factory.getScaledProgress(100, 0) == 0,
                            "Factory progress was not reset after revision change");
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(stale.get() != null
                                    && !stale.get().sameProcessingRevision(),
                            "Stale recipe unexpectedly became current again");
                    helper.assertTrue(output.getStack().isEmpty(),
                            "Stale cached recipe produced an output after reload");
                    helper.assertTrue(input.getStack().is(Items.BARRIER),
                            "Stale cached recipe consumed the replacement input");

                    input.setStack(ItemStack.EMPTY);
                    ItemStack manuallyRemoved = source.extractItem(
                            source.getCount(), mekanism.api.Action.EXECUTE,
                            AutomationType.MANUAL);
                    helper.assertTrue(!manuallyRemoved.isEmpty(),
                            "Player could not remove the idle Spirit source");
                    helper.assertTrue(source.getStack().isEmpty(),
                            "Spirit source remained locked after stale work reset");
                })
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 40)
    public static void persistentInputGuardHonorsMutationBoundaries(
            GameTestHelper helper) {
        AtomicBoolean allowExtract = new AtomicBoolean(false);
        AtomicBoolean allowInsert = new AtomicBoolean(false);
        BasicInventorySlot slot = BasicInventorySlot.at(
                (stack, automation) -> automation != AutomationType.EXTERNAL
                        || allowExtract.get(),
                (stack, automation) -> automation != AutomationType.EXTERNAL
                        || allowInsert.get(),
                stack -> stack.is(Items.DIAMOND)
                        || stack.is(Items.EMERALD),
                () -> {
                }, 0, 0);
        slot.setStack(new ItemStack(Items.DIAMOND, 4));

        assertPermit(helper, false, slot, ItemStack.EMPTY,
                "Clear bypassed denied external extraction");
        allowExtract.set(true);
        assertPermit(helper, true, slot, ItemStack.EMPTY,
                "Clear was rejected despite permitted extraction");

        allowExtract.set(false);
        assertPermit(helper, false, slot,
                new ItemStack(Items.DIAMOND, 2),
                "Shrink bypassed denied external extraction");
        allowExtract.set(true);
        assertPermit(helper, true, slot,
                new ItemStack(Items.DIAMOND, 2),
                "Shrink was rejected despite permitted extraction");

        allowInsert.set(false);
        assertPermit(helper, false, slot,
                new ItemStack(Items.DIAMOND, 6),
                "Same-stack growth bypassed denied external insertion");
        allowInsert.set(true);
        assertPermit(helper, true, slot,
                new ItemStack(Items.DIAMOND, 6),
                "Same-stack growth was rejected despite available capacity");
        assertPermit(helper, false, slot,
                new ItemStack(Items.DIAMOND, 65),
                "Same-stack growth exceeded the slot limit");

        allowExtract.set(false);
        assertPermit(helper, false, slot,
                new ItemStack(Items.EMERALD),
                "Replacement bypassed denied external extraction");
        allowExtract.set(true);
        allowInsert.set(false);
        assertPermit(helper, false, slot,
                new ItemStack(Items.EMERALD),
                "Replacement bypassed denied external insertion");
        allowInsert.set(true);
        assertPermit(helper, true, slot,
                new ItemStack(Items.EMERALD),
                "Valid replacement was rejected");
        assertPermit(helper, false, slot,
                new ItemStack(Items.DIRT),
                "Replacement bypassed the slot item validator");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 300)
    public static void extrasFactoryOutputReachesAeWithoutLoss(
            GameTestHelper helper) {
        helper.setBlock(EXTRA_FACTORY_POS,
                requiredAddonBlock("absolute_spirit_factory"));
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        MachineHandle factory = installPatternProvider(
                helper, EXTRA_FACTORY_POS);
        IInventorySlot output = firstSlot(
                factory.host().mekanismMagicPatternOutputs(),
                "Absolute Spirit factory output slots");
        ItemStack expected = new ItemStack(Items.GOLD_INGOT, 7);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        factory.support().getMainNode().isActive(),
                        "Absolute Spirit factory ME node did not become active"))
                .thenExecute(() -> output.setStack(expected.copy()))
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(sameStackAndCount(
                                    output.getStack(), expected,
                                    expected.getCount()),
                            "Extras output changed while AE had no storage");
                    attachDrive(helper);
                })
                .thenWaitUntil(() -> assertStorageReady(
                        helper, factory, expected))
                .thenWaitUntil(() -> {
                    helper.assertTrue(output.getStack().isEmpty(),
                            "Extras output did not enter AE storage");
                    KeyCounter stored = factory.support().getGrid()
                            .getStorageService().getInventory()
                            .getAvailableStacks();
                    helper.assertTrue(stored.get(AEItemKey.of(expected))
                                    == expected.getCount(),
                            "AE stored the wrong Extras output count");
                })
                .thenSucceed();
    }

    private static void assertDirectSourceGuard(
            GameTestHelper helper, TileEntityMekanism tile,
            IMekanismMagicAutomation host, ItemStack initialSource,
            ItemStack replacementSource, ItemStack input,
            String description) {
        IInventorySlot source = onlySlot(
                host.mekanismMagicPersistentInputs(),
                description + " persistent source slot");
        IInventorySlot processInput = firstSlot(
                host.mekanismMagicPatternInputs(),
                description + " pattern input slots");
        source.setStack(initialSource.copy());
        processInput.setStack(input.copyWithCount(1));
        int exposedIndex = exposedSlotIndex(tile, source);

        tile.setStackInSlot(exposedIndex,
                replacementSource.copy(), Direction.UP);
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        source.getStack(), initialSource),
                description + " allowed direct source replacement while busy");

        processInput.setStack(ItemStack.EMPTY);
        tile.setStackInSlot(exposedIndex,
                replacementSource.copy(), Direction.UP);
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        source.getStack(), initialSource),
                description + " allowed external direct replacement while idle");

        ItemStack externalExtraction = source.extractItem(1,
                mekanism.api.Action.SIMULATE, AutomationType.EXTERNAL);
        helper.assertTrue(externalExtraction.isEmpty(),
                description + " exposed its persistent spirit source to "
                        + "automatic ejection or external extraction");
        ItemStack manualExtraction = source.extractItem(1,
                mekanism.api.Action.SIMULATE, AutomationType.MANUAL);
        helper.assertTrue(!manualExtraction.isEmpty()
                        && ItemStack.isSameItemSameComponents(
                        manualExtraction, initialSource),
                description + " no longer allows the player to remove an "
                        + "idle spirit source manually");

        ItemStack removed = source.extractItem(source.getCount(),
                mekanism.api.Action.EXECUTE, AutomationType.MANUAL);
        helper.assertTrue(!removed.isEmpty() && source.getStack().isEmpty(),
                description + " failed to commit manual source removal");
        ItemStack remainder = source.insertItem(replacementSource.copy(),
                mekanism.api.Action.EXECUTE, AutomationType.EXTERNAL);
        helper.assertTrue(remainder.isEmpty()
                        && ItemStack.isSameItemSameComponents(
                        source.getStack(), replacementSource),
                description + " no longer accepts an automated source input "
                        + "after manual removal");
    }

    private static int exposedSlotIndex(TileEntityMekanism tile,
                                        IInventorySlot target) {
        List<IInventorySlot> exposed = tile.getInventorySlots(Direction.UP);
        for (int index = 0; index < exposed.size(); index++) {
            if (exposed.get(index) == target) {
                return index;
            }
        }
        throw new IllegalStateException(
                "Persistent source was not exposed on the configured top side");
    }

    private static Block requiredAddonBlock(String path) {
        Block block = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(
                        MekanismMagic.MOD_ID, path));
        if (block == Blocks.AIR) {
            throw new IllegalStateException(
                    "Required GameTest block is unavailable: " + path);
        }
        return block;
    }

    private static void assertStorageReady(
            GameTestHelper helper, MachineHandle machine,
            ItemStack probeStack) {
        BlockEntity blockEntity = helper.getBlockEntity(DRIVE_POS);
        helper.assertTrue(blockEntity instanceof DriveBlockEntity,
                "AE drive disappeared before storage mounted");
        DriveBlockEntity drive = (DriveBlockEntity) blockEntity;
        helper.assertTrue(drive.isPowered(),
                "AE drive did not join the powered Spirit-machine grid");
        helper.assertTrue(drive.getCellStatus(0) != CellState.ABSENT,
                "AE drive did not mount its test storage cell");
        helper.assertTrue(machine.support().getGrid() != null,
                "Spirit machine lost its AE grid");
        long accepted = machine.support().getGrid().getStorageService()
                .getInventory().insert(AEItemKey.of(probeStack), 1,
                        Actionable.SIMULATE, IActionSource.empty());
        helper.assertTrue(accepted == 1,
                "Mounted AE storage rejected a simulated item insert");
    }

    private static long recoveryAmount(
            CompoundTag state,
            net.minecraft.core.HolderLookup.Provider registries,
            AEItemKey expectedKey) {
        ListTag recovery = state.getList(
                "MeInterfaceRecovery", Tag.TAG_COMPOUND);
        long total = 0;
        for (int index = 0; index < recovery.size(); index++) {
            GenericStack entry = GenericStack.readTag(
                    registries, recovery.getCompound(index));
            if (entry == null || entry.what() == null
                    || entry.amount() <= 0
                    || !entry.what().equals(expectedKey)) {
                throw new IllegalStateException(
                        "Unexpected item in MekE recovery entry " + index);
            }
            total = Math.addExact(total, entry.amount());
        }
        return total;
    }

    private static MachineHandle installPatternProvider(
            GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        helper.assertTrue(blockEntity instanceof IMekanismMagicAutomation,
                "Placed block is not a Mekanism Magic automation host");
        helper.assertTrue(blockEntity
                        instanceof MeUpgradeRecipeMachineAdapter,
                "MekE adapter mixin was not applied to the placed machine");
        IMekanismMagicAutomation host =
                (IMekanismMagicAutomation) blockEntity;
        MeUpgradeRecipeMachineAdapter adapter =
                (MeUpgradeRecipeMachineAdapter) blockEntity;
        helper.assertTrue(adapter.isMeUpgradeTarget(),
                "Placed machine was not registered as a MekE upgrade target");
        var installed = adapter.getMeUpgradeContainer()
                .install(MeUpgradeType.PATTERN_PROVIDER);
        helper.assertTrue(installed.successful(),
                "Unable to install MekE pattern provider: " + installed);
        adapter.onMeUpgradeStateChanged();
        adapter.createMeNodeIfActive();
        AbstractMeAeSupport<?> support =
                adapter.getOrCreateMeUpgradeRuntime().support();
        return new MachineHandle(host, adapter, support);
    }

    private static void attachDrive(GameTestHelper helper) {
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        BlockEntity blockEntity = helper.getBlockEntity(DRIVE_POS);
        helper.assertTrue(blockEntity instanceof DriveBlockEntity,
                "AE drive block entity was not created");
        DriveBlockEntity drive = (DriveBlockEntity) blockEntity;
        ItemStack remainder = drive.getInternalInventory().insertItem(
                0, AEItems.ITEM_CELL_1K.stack(), false);
        helper.assertTrue(remainder.isEmpty(),
                "Unable to insert the test storage cell into the AE drive");
    }

    private static PatternCase deterministicRecipe(Level level) {
        List<OccultismRecipeBridge.SpiritJeiData> recipes =
                OccultismRecipeBridge.spiritJeiRecipes(level);
        for (OccultismRecipeBridge.SpiritJeiData candidate : recipes) {
            if (!"crushing".equals(candidate.recipeType())
                    || candidate.output().isEmpty()) {
                continue;
            }
            for (ItemStack possible : candidate.input().getItems()) {
                ItemStack input = possible.copyWithCount(1);
                ItemStack output = candidate.output().copy();
                List<PatternStack> inputs = List.of(
                        new PatternStack(input, 1));
                List<PatternStack> outputs = List.of(
                        new PatternStack(output, output.getCount()));
                if (!OccultismSpiritPatternValidator.matches(
                        level, candidate.spirit(), inputs, outputs)) {
                    continue;
                }
                for (OccultismRecipeBridge.SpiritJeiData other : recipes) {
                    ItemStack invalid = other.spirit();
                    if (!invalid.isEmpty()
                            && !ItemStack.isSameItemSameComponents(
                            invalid, candidate.spirit())
                            && !OccultismSpiritPatternValidator.matches(
                            level, invalid, inputs, outputs)) {
                        return new PatternCase(input,
                                output, candidate.spirit(), invalid);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "No deterministic Spirit crushing case and mismatched "
                        + "SpiritJob were available");
    }

    private static FactoryRecipeCase longRunningFactoryRecipe(Level level) {
        FactoryRecipeCase selected = null;
        for (OccultismRecipeBridge.SpiritJeiData candidate :
                OccultismRecipeBridge.spiritJeiRecipes(level)) {
            if (!"crushing".equals(candidate.recipeType())
                    || candidate.output().isEmpty()
                    || candidate.spirit().isEmpty()) {
                continue;
            }
            for (ItemStack possible : candidate.input().getItems()) {
                ItemStackHandler inventory = new ItemStackHandler(23);
                inventory.setStackInSlot(0,
                        possible.copyWithCount(
                                Math.max(1, possible.getMaxStackSize())));
                inventory.setStackInSlot(
                        NativeMagicMachineBlockEntity.CONTAINMENT_SLOT,
                        candidate.spirit());
                var found = OccultismRecipeBridge.findSpiritMachineRecipe(
                        level, inventory, candidate.spirit(), 0L);
                if (found.isEmpty() || found.get().randomTrade()
                        || found.get().recipe().inputs().isEmpty()) {
                    continue;
                }
                int required = found.get().recipe().inputs()
                        .getFirst().count();
                if (required <= 0
                        || required > possible.getMaxStackSize()) {
                    continue;
                }
                FactoryRecipeCase current = new FactoryRecipeCase(
                        possible.copyWithCount(required),
                        candidate.spirit(),
                        found.get().recipe().duration());
                if (selected == null
                        || current.duration() > selected.duration()) {
                    selected = current;
                }
            }
        }
        if (selected == null) {
            throw new IllegalStateException(
                    "No deterministic Spirit factory recipe was available");
        }
        return selected;
    }

    private static void assertPermit(
            GameTestHelper helper, boolean expected,
            BasicInventorySlot slot, ItemStack replacement,
            String message) {
        boolean actual = PersistentInputMutationGuard.permits(
                slot, replacement);
        helper.assertTrue(actual == expected,
                message + ": expected " + expected + " but got " + actual);
    }

    private static GenericStack generic(ItemStack stack, long amount) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || amount <= 0) {
            throw new IllegalArgumentException("Invalid AE item stack");
        }
        return new GenericStack(key, amount);
    }

    private static KeyCounter[] counters(ItemStack stack, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(AEItemKey.of(stack), amount);
        return new KeyCounter[]{counter};
    }

    private static IInventorySlot onlySlot(
            List<IInventorySlot> slots, String description) {
        if (slots == null || slots.size() != 1) {
            throw new IllegalStateException(description
                    + " expected exactly one slot, found "
                    + (slots == null ? "null" : slots.size()));
        }
        return slots.getFirst();
    }

    private static IInventorySlot firstSlot(
            List<IInventorySlot> slots, String description) {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalStateException(description + " were empty");
        }
        return slots.getFirst();
    }

    private static boolean sameStackAndCount(
            ItemStack actual, ItemStack expected, int count) {
        return ItemStack.isSameItemSameComponents(actual, expected)
                && actual.getCount() == count;
    }

    private record MachineHandle(
            IMekanismMagicAutomation host,
            MeUpgradeRecipeMachineAdapter adapter,
            AbstractMeAeSupport<?> support) {
    }

    private record PatternCase(
            ItemStack input,
            ItemStack output,
            ItemStack validSpirit,
            ItemStack invalidSpirit) {
        private PatternCase {
            input = input.copy();
            output = output.copy();
            validSpirit = validSpirit.copy();
            invalidSpirit = invalidSpirit.copy();
        }

        @Override
        public ItemStack input() {
            return input.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        @Override
        public ItemStack validSpirit() {
            return validSpirit.copy();
        }

        @Override
        public ItemStack invalidSpirit() {
            return invalidSpirit.copy();
        }
    }

    private record FactoryRecipeCase(ItemStack input, ItemStack spirit,
                                     int duration) {
        private FactoryRecipeCase {
            input = input.copy();
            spirit = spirit.copyWithCount(1);
            duration = Math.max(1, duration);
        }

        @Override
        public ItemStack input() {
            return input.copy();
        }

        @Override
        public ItemStack spirit() {
            return spirit.copy();
        }
    }
}
