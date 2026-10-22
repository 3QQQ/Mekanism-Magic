package com.example.mekanismmagic.integration.arsnouveau;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.ae2.Ae2DirectOutputProvider;
import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Prepared and paid fluids across real Drygmy ticks and block-item restores. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrygmyFluidPersistenceGameTests {
    private static final BlockPos SOURCE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos RESTORED_POS = new BlockPos(4, 1, 1);
    private static final String PENDING_ITEMS = "drygmy_pending_outputs";
    private static final String PAID_FLUIDS = "drygmy_paid_fluid_outputs";

    private DrygmyFluidPersistenceGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void preparedFluidsAndCostsSurviveUpgradeRemovalAndPlacement(
            GameTestHelper helper) {
        NetworkFixture source = createFixture(helper, SOURCE_POS, true);
        AtomicReference<PreparedBatch> expected = new AtomicReference<>();
        AtomicReference<NetworkFixture> restored = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(() -> assertStorageReady(helper, source))
                .thenExecute(() -> {
                    Upgrade stackUpgrade = NativeMekanismRegistries
                            .dimensionMinerStackUpgrade();
                    helper.assertTrue(stackUpgrade != null,
                            "Drygmy stack upgrade is unavailable");
                    source.machine().getComponent()
                            .addUpgrades(stackUpgrade, 4);
                    source.machine().getComponent()
                            .addUpgrades(Upgrade.SPEED, 8);
                    PreparedBatch snapshot = prepareAndFreeze(helper,
                            source.machine());
                    helper.assertTrue(snapshot.multiplier() == 16,
                            "Four stack upgrades did not prepare 16 operations");
                    source.machine().getComponent()
                            .removeUpgrade(stackUpgrade, true);
                    source.machine().getComponent()
                            .removeUpgrade(Upgrade.SPEED, true);
                    assertPreparedContents(helper, source.machine(), snapshot,
                            "Removing upgrades");
                    // Speed changes deliberately rebase elapsed progress and
                    // energy per tick. Save their resulting values along
                    // with the unchanged harvest and Source-cost snapshot.
                    expected.set(preparedSnapshot(source.machine()));

                    ItemStack saved = machineItem(helper, source.machine());
                    NetworkFixture replacement = createFixture(helper,
                            RESTORED_POS, false);
                    restoreItem(helper, replacement, saved);
                    restored.set(replacement);
                }).thenIdle(2).thenExecute(() -> {
                    DrygmySimulatorBlockEntity machine =
                            restored.get().machine();
                    assertPrepared(helper, machine, expected.get(),
                            "Restoring the machine without AE power");
                    helper.assertTrue(machine.getMaxSource()
                                    >= expected.get().sourceCost(),
                            "Upgrade removal made saved Source cost impossible");
                    helper.assertTrue(machine.developmentPaidFluidOutputs()
                                    .isEmpty() && machine.getSource() == 0,
                            "An unpaid restored batch became a paid fluid output");
                    helper.assertTrue(storedMilk(source) == 0,
                            "Preparing or saving the batch inserted milk");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 280)
    public static void mixedBatchCompletedOfflineDoesNotReplayMilkOnReconnect(
            GameTestHelper helper) {
        NetworkFixture fixture = createFixture(helper, SOURCE_POS, true);
        AtomicReference<PreparedBatch> expected = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(() -> assertStorageReady(helper, fixture))
                .thenExecute(() -> {
                    expected.set(prepareAndFreeze(helper, fixture.machine()));
                    helper.setBlock(fixture.energyPos(), Blocks.AIR);
                }).thenWaitUntil(() -> helper.assertTrue(
                        !fixture.node().isActive(),
                        "Drygmy AE node is still powered after disconnect"))
                .thenExecute(() -> {
                    DrygmySimulatorBlockEntity machine = fixture.machine();
                    PreparedBatch snapshot = expected.get();
                    machine.setSource(snapshot.sourceCost());
                    machine.getNativeEnergyContainer()
                            .setEnergy(snapshot.energyPerTick());
                    machine.onUpdateServer();
                    helper.assertTrue(machine.getSource() == 0
                                    && machine.getNativeEnergyContainer()
                                    .getEnergy() == 0,
                            "Offline mixed harvest did not pay exactly once");
                    helper.assertTrue(machine.developmentPendingFluids()
                                    .isEmpty()
                                    && machine.developmentPaidFluidOutputs()
                                    .isEmpty(),
                            "Offline completion retained fluid for a later replay");
                    assertItemOutputs(helper, machine, snapshot.items());
                    helper.setBlock(fixture.energyPos(),
                            AEBlocks.CREATIVE_ENERGY_CELL.block());
                }).thenWaitUntil(() -> assertStorageReady(helper, fixture))
                .thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(storedMilk(fixture) == 0
                                    && fixture.machine().getSource() == 0
                                    && fixture.machine()
                                    .developmentPaidFluidOutputs().isEmpty(),
                            "Reconnecting AE replayed an offline fluid harvest");
                    assertItemOutputs(helper, fixture.machine(),
                            expected.get().items());
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void paidFluidQueueRestoresButIsDiscardedWhileOffline(
            GameTestHelper helper) {
        NetworkFixture source = createFixture(helper, SOURCE_POS, false);
        FluidStack milk = milk();
        helper.assertTrue(!milk.isEmpty(),
                "Milk fluid is unavailable for the persisted queue fixture");
        ItemStack saved = machineItem(helper, source.machine());
        CompoundTag data = blockEntityData(helper, saved);
        ListTag queue = new ListTag();
        queue.add(milk.save(helper.getLevel().registryAccess()));
        data.put(PAID_FLUIDS, queue);
        saved.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        NetworkFixture restored = createFixture(helper, RESTORED_POS, false);
        restoreItem(helper, restored, saved);

        helper.assertTrue(sameFluids(List.of(milk), restored.machine()
                        .developmentPaidFluidOutputs()),
                "Block-item restoration lost the paid fluid retry queue");
        CompoundTag persistedAgain = blockEntityData(helper,
                machineItem(helper, restored.machine()));
        ListTag persistedQueue = persistedAgain.getList(PAID_FLUIDS,
                Tag.TAG_COMPOUND);
        helper.assertTrue(persistedQueue.size() == 1,
                "Saving before the first tick lost the paid fluid retry queue");
        restored.machine().onUpdateServer();
        helper.assertTrue(restored.machine().developmentPaidFluidOutputs()
                        .isEmpty() && restored.machine().getSource() == 0
                        && restored.machine().developmentOutputCount() == 0,
                "An offline paid fluid retry survived or generated items");
        helper.assertTrue(blockEntityData(helper,
                        machineItem(helper, restored.machine()))
                        .getList(PAID_FLUIDS, Tag.TAG_COMPOUND).isEmpty(),
                "The discarded paid queue remained in the next save");
        helper.setBlock(restored.energyPos(),
                AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.startSequence()
                .thenWaitUntil(() -> assertStorageReady(helper, restored))
                .thenIdle(25).thenExecute(() -> helper.assertTrue(
                        storedMilk(restored) == 0 && restored.machine()
                                .developmentPaidFluidOutputs().isEmpty(),
                        "A discarded restored queue replayed after reconnect"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void fluidOnlyRestoredBatchWaitsWithoutSpendingOffline(
            GameTestHelper helper) {
        NetworkFixture source = createFixture(helper, SOURCE_POS, true);
        AtomicReference<PreparedBatch> expected = new AtomicReference<>();
        AtomicReference<NetworkFixture> restored = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(() -> assertStorageReady(helper, source))
                .thenExecute(() -> {
                    PreparedBatch snapshot = prepareAndFreeze(helper,
                            source.machine());
                    expected.set(snapshot);
                    ItemStack saved = machineItem(helper, source.machine());
                    CompoundTag data = blockEntityData(helper, saved);
                    // Preserve the prepared fluid plan, signature, progress
                    // and costs while constructing a fluid-only saved batch.
                    data.remove(PENDING_ITEMS);
                    saved.set(DataComponents.BLOCK_ENTITY_DATA,
                            CustomData.of(data));
                    NetworkFixture replacement = createFixture(helper,
                            RESTORED_POS, false);
                    restoreItem(helper, replacement, saved);
                    restored.set(replacement);
                    replacement.machine().setSource(snapshot.sourceCost());
                    replacement.machine().getNativeEnergyContainer()
                            .setEnergy(snapshot.energyPerTick());
                    replacement.machine().onUpdateServer();
                    assertFluidOnlyWaiting(helper, replacement.machine(),
                            snapshot);
                }).thenIdle(2).thenExecute(() -> {
                    assertFluidOnlyWaiting(helper, restored.get().machine(),
                            expected.get());
                    helper.assertTrue(storedMilk(source) == 0,
                            "Preparing a fluid-only restore generated milk");
                    helper.assertTrue(!restored.get().drive()
                                    .getInternalInventory()
                                    .extractItem(0, 1, false).isEmpty(),
                            "Could not remove the restored AE fluid cell");
                    helper.setBlock(restored.get().energyPos(),
                            AEBlocks.CREATIVE_ENERGY_CELL.block());
                }).thenWaitUntil(() -> {
                    NetworkFixture fixture = restored.get();
                    helper.assertTrue(fixture.node().isActive()
                                    && fixture.drive().isPowered(),
                            "Restored Drygmy did not join the empty powered grid");
                }).thenIdle(25).thenExecute(() -> {
                    assertFluidOnlyWaiting(helper, restored.get().machine(),
                            expected.get());
                }).thenSucceed();
    }

    private static PreparedBatch prepareAndFreeze(GameTestHelper helper,
                                                   DrygmySimulatorBlockEntity machine) {
        helper.assertTrue(machine.seedDevelopmentTest(helper.getLevel(),
                        ResourceLocation.withDefaultNamespace("cow")) > 0,
                "Cow did not prepare its ordinary item batch");
        PreparedBatch snapshot = preparedSnapshot(machine);
        machine.setSource(0);
        machine.getNativeEnergyContainer().setEnergy(0);
        helper.assertTrue(!snapshot.fluids().isEmpty()
                        && snapshot.fluids().stream().allMatch(fluid ->
                        FluidStack.isSameFluidSameComponents(fluid, milk()))
                        && snapshot.sourceCost() > 0,
                "Online cow batch has no real milk or Source cost snapshot");
        return snapshot;
    }

    private static PreparedBatch preparedSnapshot(
            DrygmySimulatorBlockEntity machine) {
        return new PreparedBatch(machine.developmentPendingOutputs(),
                machine.developmentPendingFluids(),
                machine.developmentPendingSignature(),
                machine.developmentPendingOperationMultiplier(),
                machine.developmentSourceCost(),
                machine.developmentPendingEnergyPerTick(),
                machine.getProgress());
    }

    private static void assertPrepared(GameTestHelper helper,
                                       DrygmySimulatorBlockEntity machine,
                                       PreparedBatch expected, String context) {
        assertPreparedContents(helper, machine, expected, context);
        helper.assertTrue(expected.energyPerTick()
                        == machine.developmentPendingEnergyPerTick()
                        && expected.progress() == machine.getProgress(),
                context + " changed the prepared progress or energy cost");
    }

    private static void assertPreparedContents(GameTestHelper helper,
                                                DrygmySimulatorBlockEntity machine,
                                                PreparedBatch expected,
                                                String context) {
        helper.assertTrue(itemAmounts(expected.items()).equals(itemAmounts(
                                machine.developmentPendingOutputs()))
                        && sameFluids(expected.fluids(),
                                machine.developmentPendingFluids())
                        && expected.signature().equals(
                                machine.developmentPendingSignature())
                        && expected.multiplier()
                        == machine.developmentPendingOperationMultiplier()
                        && expected.sourceCost() == machine.developmentSourceCost(),
                context + " changed the prepared items, fluids or Source cost");
    }

    private static void assertFluidOnlyWaiting(GameTestHelper helper,
                                                DrygmySimulatorBlockEntity machine,
                                                PreparedBatch expected) {
        helper.assertTrue(machine.developmentPendingOutputs().isEmpty()
                        && sameFluids(expected.fluids(),
                                machine.developmentPendingFluids())
                        && machine.getProgress() == expected.progress()
                        && machine.getSource() == expected.sourceCost()
                        && machine.getNativeEnergyContainer().getEnergy()
                        == expected.energyPerTick()
                        && machine.developmentPaidFluidOutputs().isEmpty()
                        && machine.developmentOutputCount() == 0,
                "Blocked fluid-only batch rerolled, progressed or spent resources");
    }

    private static void assertItemOutputs(GameTestHelper helper,
                                          DrygmySimulatorBlockEntity machine,
                                          List<ItemStack> expected) {
        List<ItemStack> visible = machine.mekanismMagicPatternOutputs()
                .stream().map(slot -> slot.getStack().copy()).toList();
        helper.assertTrue(itemAmounts(expected).equals(itemAmounts(visible)),
                "The mixed batch did not produce its exact ordinary items once");
    }

    private static Map<AEItemKey, Long> itemAmounts(List<ItemStack> stacks) {
        Map<AEItemKey, Long> amounts = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                amounts.merge(AEItemKey.of(stack), (long) stack.getCount(),
                        Long::sum);
            }
        }
        return amounts;
    }

    private static boolean sameFluids(List<FluidStack> expected,
                                       List<FluidStack> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (expected.get(index).getAmount() != actual.get(index).getAmount()
                    || !FluidStack.isSameFluidSameComponents(
                    expected.get(index), actual.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack machineItem(GameTestHelper helper,
                                          DrygmySimulatorBlockEntity machine) {
        ItemStack item = new ItemStack(machine.getBlockState().getBlock());
        machine.saveToItem(item, helper.getLevel().registryAccess());
        return item;
    }

    private static CompoundTag blockEntityData(GameTestHelper helper,
                                                ItemStack item) {
        CustomData custom = item.get(DataComponents.BLOCK_ENTITY_DATA);
        helper.assertTrue(custom != null,
                "Saved Drygmy item has no block entity data");
        return custom.copyTag();
    }

    private static void restoreItem(GameTestHelper helper,
                                     NetworkFixture fixture, ItemStack item) {
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(
                        helper.getLevel(), null,
                        helper.absolutePos(fixture.relativePos()), item),
                "Drygmy item did not restore its custom block entity data");
        fixture.machine().applyComponentsFromItemStack(item);
    }

    private static NetworkFixture createFixture(GameTestHelper helper,
                                                 BlockPos position,
                                                 boolean powered) {
        helper.setBlock(position,
                ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        DrygmySimulatorBlockEntity machine = helper.getBlockEntity(position);
        machine.setSource(0);
        machine.getNativeEnergyContainer().setEnergy(0);
        if (powered) {
            helper.setBlock(position.east(),
                    AEBlocks.CREATIVE_ENERGY_CELL.block());
        }
        helper.setBlock(position.east().above(), AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(position.east().above());
        helper.assertTrue(drive.getInternalInventory().insertItem(0,
                        AEItems.FLUID_CELL_1K.stack(), false).isEmpty(),
                "Could not install the real AE fluid storage cell");
        return new NetworkFixture(machine, drive, position);
    }

    private static void assertStorageReady(GameTestHelper helper,
                                            NetworkFixture fixture) {
        IGridNode node = fixture.node();
        helper.assertTrue(node != null && node.isActive()
                        && fixture.drive().isPowered(),
                "Drygmy did not join its real powered AE network");
        helper.assertTrue(node.getGrid().getStorageService().getInventory()
                        .insert(AEFluidKey.of(milk()), 1000,
                                Actionable.SIMULATE, IActionSource.empty())
                        == 1000,
                "AE fluid cell did not mount or accept a milk simulation");
    }

    private static FluidStack milk() {
        return FluidUtil.getFluidContained(new ItemStack(Items.MILK_BUCKET))
                .orElse(FluidStack.EMPTY);
    }

    private static long storedMilk(NetworkFixture fixture) {
        return fixture.node().getGrid().getStorageService().getInventory()
                .getAvailableStacks().get(AEFluidKey.of(milk()));
    }

    private record PreparedBatch(List<ItemStack> items, List<FluidStack> fluids,
                                 String signature, int multiplier,
                                 int sourceCost, long energyPerTick,
                                 int progress) {
    }

    private record NetworkFixture(DrygmySimulatorBlockEntity machine,
                                   DriveBlockEntity drive,
                                   BlockPos relativePos) {
        private IGridNode node() {
            return Ae2DirectOutputProvider.forTile(machine).getActionableNode();
        }

        private BlockPos energyPos() {
            return relativePos.east();
        }
    }
}
