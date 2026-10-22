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
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import mekanism.api.Upgrade;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Paid delivery remains available while redstone stops loot preparation and resource payment. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrygmyRedstoneGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final String PAID_FLUIDS = "drygmy_paid_fluid_outputs";

    private DrygmyRedstoneGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void stoppedHighAndLowNeverPrepareFreshPoweredOrSourceOnlyLoot(GameTestHelper helper) {
        for (RedstoneControl mode : List.of(RedstoneControl.HIGH, RedstoneControl.LOW)) {
            for (boolean powered : List.of(false, true)) {
                var tile = place(helper);
                tile.mekanismMagicPersistentInputs().getFirst().setStack(cowJar());
                int source = Math.max(1, tile.developmentSourceCost());
                tile.setSource(source);
                tile.getNativeEnergyContainer().setEnergy(powered ? 100_000 : 0);
                signal(helper, tile, mode == RedstoneControl.LOW);
                tile.setControlType(mode);
                helper.assertTrue(!tile.canFunction(), "Drygmy fixture did not enter its stopped native mode");
                tick(tile, 8);
                helper.assertTrue(tile.developmentPendingOutputs().isEmpty() && tile.developmentPendingFluids().isEmpty()
                                && tile.developmentPendingSignature().isEmpty() && tile.getProgress() == 0
                                && tile.getSource() == source && tile.getNativeEnergyContainer().getEnergy() == (powered ? 100_000 : 0)
                                && !tile.getActive() && !tile.isVisuallyProcessing(),
                        "Stopped Drygmy prepared loot, paid Source/energy or advanced in " + mode + " powered=" + powered);
                helper.assertTrue(!tile.supportsMode(RedstoneControl.PULSE), "Unexpected Drygmy pulse support needs dedicated semantics");
                tile.setControlType(RedstoneControl.PULSE);
                helper.assertTrue(tile.getControlType() == RedstoneControl.DISABLED,
                        "Unsupported pulse selection no longer follows Mekanism's existing fallback");
                signal(helper, tile, false);
                tile.setControlType(RedstoneControl.HIGH);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void poweredPreparedLootAndPaidFluidsSurviveStoppedSaveAndResumeWithoutRepayment(GameTestHelper helper) {
        NetworkFixture fixture = network(helper);
        helper.startSequence().thenWaitUntil(() -> assertStorageReady(helper, fixture)).thenExecute(() -> {
            for (RedstoneControl mode : List.of(RedstoneControl.HIGH, RedstoneControl.LOW)) {
                var tile = fixture.machine();
                Map<AEItemKey, Long> expectedItems = accountedItems(helper, fixture);
                prepare(helper, tile, mode, false);
                Snapshot expected = snapshot(tile);
                addPaidWater(helper, tile, 750);
                Map<AEFluidKey, Long> expectedFluids = accountedFluids(fixture);
                int source = tile.getSource();
                long energy = tile.getNativeEnergyContainer().getEnergy();
                // Restoration may drain paid output to ME, but must not
                // reroll or advance the still-unpaid prepared harvest.
                assertStorageReady(helper, fixture);
                tick(tile, 8);
                assertFrozen(helper, tile, expected, source, energy);
                assertFluidsConserved(helper, fixture, expectedFluids, "Stopped " + mode);
                helper.assertTrue(accountedItems(helper, fixture).equals(expectedItems),
                        "Stopped " + mode + " produced unpaid prepared items");
                tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
                tile.updatePower();
                assertStorageReady(helper, fixture);
                assertFluidsConserved(helper, fixture, expectedFluids, "Immediate reload " + mode);
                tick(tile, 8);
                helper.assertTrue(tile.getControlType() == mode && !tile.canFunction(), "Stopped Drygmy mode was lost during load");
                assertFrozen(helper, tile, expected, source, energy);
                assertFluidsConserved(helper, fixture, expectedFluids, "Stopped reloaded " + mode);
                signal(helper, tile, mode == RedstoneControl.HIGH);
                helper.assertTrue(tile.canFunction(), "Restoring the required signal did not enable Drygmy");
                tick(tile, 1);
                helper.assertTrue(tile.getSource() == source - expected.sourceCost(),
                        "Resuming " + mode + " charged Source incorrectly: before=" + source + ", after=" + tile.getSource()
                                + ", cost=" + expected.sourceCost());
                helper.assertTrue(tile.getNativeEnergyContainer().getEnergy() == energy - expected.energyPerTick(),
                        "Resuming " + mode + " did not pay exactly its one remaining FE tick");
                helper.assertTrue(tile.developmentPendingOutputs().isEmpty() && tile.developmentPendingFluids().isEmpty(),
                        "Resuming " + mode + " did not retire its prepared batch");
                for (ItemStack output : expected.outputs()) {
                    expectedItems.merge(AEItemKey.of(output), (long) output.getCount(), Long::sum);
                }
                for (FluidStack fluid : expected.fluids()) {
                    expectedFluids.merge(AEFluidKey.of(fluid), (long) fluid.getAmount(), Long::sum);
                }
                helper.assertTrue(accountedItems(helper, fixture).equals(expectedItems),
                        "Resuming " + mode + " changed its fixed item harvest: expected=" + expectedItems
                                + ", actual=" + accountedItems(helper, fixture));
                assertFluidsConserved(helper, fixture, expectedFluids, "Resumed " + mode);
                signal(helper, tile, false);
                tile.setControlType(RedstoneControl.HIGH);
            }
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void sourceOnlyHighAndLowPauseAcrossActualFiveTickCadenceAndPayOnceOnResume(GameTestHelper helper) {
        AtomicReference<DrygmySimulatorBlockEntity> machine = new AtomicReference<>(prepared(helper, RedstoneControl.HIGH, true));
        AtomicReference<Snapshot> expected = new AtomicReference<>(snapshot(machine.get()));
        AtomicReference<Integer> source = new AtomicReference<>(machine.get().getSource());
        helper.startSequence().thenIdle(6).thenExecute(() -> {
            assertFrozen(helper, machine.get(), expected.get(), source.get(), 0);
            machine.get().loadAdditional(save(helper, machine.get()), helper.getLevel().registryAccess());
            machine.get().updatePower();
        }).thenIdle(6).thenExecute(() -> {
            assertFrozen(helper, machine.get(), expected.get(), source.get(), 0);
            signal(helper, machine.get(), true);
        }).thenWaitUntil(() -> helper.assertTrue(machine.get().getSource() == source.get() - expected.get().sourceCost(),
                "Source-only HIGH harvest has not reached its next native five-tick work interval"))
                .thenExecute(() -> {
                    helper.assertTrue(machine.get().getNativeEnergyContainer().getEnergy() == 0
                                    && machine.get().developmentOutputCount() == expected.get().outputs().stream().mapToInt(ItemStack::getCount).sum(),
                            "Source-only HIGH resumption used FE or repeated its fixed output");
                    machine.set(prepared(helper, RedstoneControl.LOW, true));
                    expected.set(snapshot(machine.get()));
                    source.set(machine.get().getSource());
                }).thenIdle(6).thenExecute(() -> {
                    assertFrozen(helper, machine.get(), expected.get(), source.get(), 0);
                    signal(helper, machine.get(), false);
                }).thenWaitUntil(() -> helper.assertTrue(machine.get().getSource() == source.get() - expected.get().sourceCost(),
                        "Source-only LOW harvest has not reached its next native five-tick work interval"))
                .thenExecute(() -> {
                    helper.assertTrue(machine.get().getNativeEnergyContainer().getEnergy() == 0
                                    && machine.get().developmentOutputCount() == expected.get().outputs().stream().mapToInt(ItemStack::getCount).sum(),
                            "Source-only LOW resumption used FE or repeated its fixed output");
                    signal(helper, machine.get(), false);
                    machine.get().setControlType(RedstoneControl.HIGH);
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void stoppedDrygmyDeliversExistingItemBufferAndKeepsPaidFluidWithoutPreparingMoreLoot(GameTestHelper helper) {
        NetworkFixture fixture = network(helper);
        var tile = fixture.machine();
        tile.mekanismMagicPersistentInputs().getFirst().setStack(cowJar());
        tile.setSource(Math.max(1, tile.developmentSourceCost()));
        tile.getNativeEnergyContainer().setEnergy(100_000);
        tile.setControlType(RedstoneControl.HIGH);
        helper.startSequence().thenWaitUntil(() -> assertStorageReady(helper, fixture)).thenExecute(() -> {
            CompoundTag data = save(helper, tile);
            CompoundTag item = new CompoundTag();
            item.put("stack", new ItemStack(Items.DIAMOND).save(helper.getLevel().registryAccess()));
            item.putLong("count", 23);
            ListTag items = new ListTag();
            items.add(item);
            data.put("drygmy_long_buffer", items);
            ListTag fluids = new ListTag();
            fluids.add(new FluidStack(Fluids.WATER, 750).save(helper.getLevel().registryAccess()));
            data.put(PAID_FLUIDS, fluids);
            tile.loadAdditional(data, helper.getLevel().registryAccess());
            tile.updatePower();
            helper.assertTrue(paidWater(tile) == 750, "Immediate load failed to decode the paid water queue");
            Map<AEItemKey, Long> expectedItems = Map.of(AEItemKey.of(Items.DIAMOND), 23L);
            helper.assertTrue(accountedItems(helper, fixture).equals(expectedItems),
                    "Immediate load failed to restore the 23 paid buffered diamonds");
            Map<AEFluidKey, Long> expectedFluids = accountedFluids(fixture);
            int source = tile.getSource();
            assertStorageReady(helper, fixture);
            tick(tile, 3);
            helper.assertTrue(accountedItems(helper, fixture).equals(expectedItems),
                    "Stopped paid-item conservation failed: " + accountedItems(helper, fixture));
            helper.assertTrue(fixture.node().getGrid().getStorageService().getInventory().getAvailableStacks()
                            .get(AEItemKey.of(Items.DIAMOND)) > 0 || tile.developmentOutputCount() > 0,
                    "Stopped Drygmy did not deliver any of its already-paid item buffer");
            assertFluidsConserved(helper, fixture, expectedFluids, "Stopped paid-only delivery");
            helper.assertTrue(tile.developmentPendingOutputs().isEmpty() && tile.developmentPendingFluids().isEmpty()
                            && tile.developmentPendingSignature().isEmpty() && tile.getProgress() == 0,
                    "Stopped paid-only delivery prepared or advanced a new harvest");
            helper.assertTrue(tile.getSource() == source, "Stopped paid-only delivery charged new Source");
            helper.assertTrue(tile.getNativeEnergyContainer().getEnergy() == 100_000,
                    "Stopped paid-only delivery charged new FE");
            helper.assertTrue(!tile.canFunction() && !tile.getActive() && !tile.isVisuallyProcessing(),
                    "Stopped paid-only delivery enabled processing or animation");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void legacyPendingFeeRepricesWithoutChangingLootSourceOrElapsedWork(GameTestHelper helper) {
        var tile = place(helper);
        Upgrade stack = NativeMekanismRegistries.dimensionMinerStackUpgrade();
        helper.assertTrue(stack != null, "Pending-tariff migration requires the real stack upgrade");
        tile.getComponent().addUpgrades(stack, 2);
        tile.getComponent().addUpgrades(Upgrade.SPEED, 2);
        tile.getComponent().addUpgrades(Upgrade.ENERGY, 1);
        helper.assertTrue(tile.seedDevelopmentTest(helper.getLevel(), ResourceLocation.withDefaultNamespace("cow")) > 0,
                "Could not seed a legacy-priced Drygmy harvest");
        helper.assertTrue(tile.developmentPendingOperationMultiplier() == 4,
                "Migration fixture did not prepare four harvests");
        tile.getComponent().removeUpgrade(stack, true);
        Snapshot expected = snapshot(tile);
        int source = expected.sourceCost();
        long usage = Math.min(tile.getNativeEnergyContainer().getMaxEnergy(),
                MekanismUtils.getEnergyPerTick(tile, MagicMachineEnergyProfiles.RESOURCE_GATHERING) * expected.multiplier());
        long oldUsage = MekanismUtils.getEnergyPerTick(tile, 800) * expected.multiplier();
        helper.assertTrue(usage == expected.energyPerTick() && usage != oldUsage,
                "Legacy fixture does not distinguish old and new tariffs");
        tile.setSource(source);
        tile.getNativeEnergyContainer().setEnergy(100_000);
        signal(helper, tile, false);
        tile.setControlType(RedstoneControl.HIGH);
        CompoundTag legacy = save(helper, tile);
        legacy.putLong("drygmy_pending_energy_per_tick", oldUsage);
        tile.loadAdditional(legacy, helper.getLevel().registryAccess());
        tile.updatePower();
        // Signature-checked restoration runs before the redstone guard but
        // must only replace the obsolete tariff, never roll or pay again.
        tick(tile, 3);
        assertFrozen(helper, tile, expected, source, 100_000);
        helper.assertTrue(tile.getComponent().getUpgrades(stack) == 0,
                "Repricing restored removed stack upgrades instead of retaining the prepared batch size");
        CompoundTag repriced = save(helper, tile);
        helper.assertTrue(repriced.getLong("drygmy_pending_energy_per_tick") == usage,
                "Saving the migrated harvest retained its old tariff");
        tile.loadAdditional(repriced, helper.getLevel().registryAccess());
        tile.updatePower();
        tick(tile, 2);
        assertFrozen(helper, tile, expected, source, 100_000);
        signal(helper, tile, true);
        tick(tile, 1);
        helper.assertTrue(tile.developmentPendingOutputs().isEmpty() && tile.developmentPendingFluids().isEmpty(),
                "Resuming the migrated harvest did not retire its prepared result");
        helper.assertTrue(tile.getSource() == 0 && tile.getNativeEnergyContainer().getEnergy() == 100_000 - usage,
                "Migrated Drygmy did not pay its one unchanged Source charge and one new-priced FE tick");
        Map<AEItemKey, Long> visible = new HashMap<>();
        for (var slot : tile.mekanismMagicPatternOutputs()) {
            ItemStack output = slot.getStack();
            if (!output.isEmpty()) visible.merge(AEItemKey.of(output), (long) output.getCount(), Long::sum);
        }
        Map<AEItemKey, Long> fixed = new HashMap<>();
        for (ItemStack output : expected.outputs()) fixed.merge(AEItemKey.of(output), (long) output.getCount(), Long::sum);
        helper.assertTrue(visible.equals(fixed), "Tariff migration changed or duplicated the fixed Drygmy item harvest");
        signal(helper, tile, false);
        helper.succeed();
    }

    private static DrygmySimulatorBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.AIR);
        for (Direction side : Direction.values()) helper.setBlock(POS.relative(side), Blocks.AIR);
        helper.setBlock(POS, ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        DrygmySimulatorBlockEntity tile = helper.getBlockEntity(POS);
        tile.updatePower();
        return tile;
    }

    private static DrygmySimulatorBlockEntity prepared(GameTestHelper helper, RedstoneControl mode, boolean sourceOnly) {
        var tile = place(helper);
        prepare(helper, tile, mode, sourceOnly);
        return tile;
    }

    private static void prepare(GameTestHelper helper, DrygmySimulatorBlockEntity tile,
                                RedstoneControl mode, boolean sourceOnly) {
        helper.assertTrue(tile.seedDevelopmentTest(helper.getLevel(), ResourceLocation.withDefaultNamespace("cow")) > 0,
                "Could not seed an actual finite cow harvest");
        helper.assertTrue(tile.developmentSourceCost() > 0, "Source-only coverage needs the original positive Drygmy Source cost");
        tile.setSource(tile.developmentSourceCost());
        tile.getNativeEnergyContainer().setEnergy(sourceOnly ? 0 : 100_000);
        signal(helper, tile, mode == RedstoneControl.LOW);
        tile.setControlType(mode);
        helper.assertTrue(!tile.canFunction(), "Prepared harvest fixture did not stop under native redstone conditions");
    }

    private static ItemStack cowJar() {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:cow");
        ItemStack jar = new ItemStack(BlockRegistry.MOB_JAR.get());
        jar.set(DataComponentRegistry.MOB_JAR.get(), new MobJarData(entity, null));
        return jar;
    }

    private static void signal(GameTestHelper helper, DrygmySimulatorBlockEntity tile, boolean powered) {
        helper.setBlock(POS.below(), powered ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
        tile.updatePower();
        helper.assertTrue(tile.isPowered() == powered, "Physical redstone block did not update native Drygmy power");
    }

    private static void addPaidWater(GameTestHelper helper, DrygmySimulatorBlockEntity tile, int amount) {
        int previousWater = paidWater(tile);
        CompoundTag data = save(helper, tile);
        ListTag paid = data.getList(PAID_FLUIDS, Tag.TAG_COMPOUND);
        paid.add(new FluidStack(Fluids.WATER, amount).save(helper.getLevel().registryAccess()));
        data.put(PAID_FLUIDS, paid);
        tile.loadAdditional(data, helper.getLevel().registryAccess());
        tile.updatePower();
        helper.assertTrue(paidWater(tile) == previousWater + amount,
                "Immediate load failed to decode paid water: before=" + previousWater + ", added=" + amount
                        + ", loaded=" + paidWater(tile));
    }

    private static int paidWater(DrygmySimulatorBlockEntity tile) {
        return tile.developmentPaidFluidOutputs().stream().filter(fluid -> fluid.getFluid() == Fluids.WATER)
                .mapToInt(FluidStack::getAmount).sum();
    }

    private static NetworkFixture network(GameTestHelper helper) {
        var tile = place(helper);
        tile.setSource(0);
        tile.getNativeEnergyContainer().setEnergy(0);
        tile.setControlType(RedstoneControl.HIGH);
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS.east().above(), AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(POS.east().above());
        helper.assertTrue(drive.getInternalInventory().insertItem(0, AEItems.FLUID_CELL_1K.stack(), false).isEmpty(),
                "Could not install the real AE fluid cell");
        helper.assertTrue(drive.getInternalInventory().insertItem(1, AEItems.ITEM_CELL_1K.stack(), false).isEmpty(),
                "Could not install the real AE item cell");
        return new NetworkFixture(tile, drive);
    }

    private static void assertStorageReady(GameTestHelper helper, NetworkFixture fixture) {
        IGridNode node = fixture.node();
        helper.assertTrue(node != null && node.isActive() && fixture.drive().isPowered(),
                "Paid-fluid conservation fixture has not joined its real powered AE network");
        var storage = node.getGrid().getStorageService().getInventory();
        helper.assertTrue(storage.insert(AEFluidKey.of(new FluidStack(Fluids.WATER, 1000)), 1000,
                        Actionable.SIMULATE, IActionSource.empty()) == 1000,
                "Paid-fluid fixture's mounted cell cannot accept water");
        helper.assertTrue(storage.insert(AEItemKey.of(Items.DIAMOND), 23,
                        Actionable.SIMULATE, IActionSource.empty()) == 23,
                "Paid-item fixture's mounted cell cannot accept diamonds");
    }

    private static Map<AEItemKey, Long> accountedItems(GameTestHelper helper, NetworkFixture fixture) {
        Map<AEItemKey, Long> result = new HashMap<>();
        for (var slot : fixture.machine().mekanismMagicPatternOutputs()) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) result.merge(AEItemKey.of(stack), (long) stack.getCount(), Long::sum);
        }
        for (Tag entry : save(helper, fixture.machine()).getList("drygmy_long_buffer", Tag.TAG_COMPOUND)) {
            CompoundTag buffered = (CompoundTag) entry;
            ItemStack stack = ItemStack.parseOptional(helper.getLevel().registryAccess(), buffered.getCompound("stack"));
            if (!stack.isEmpty()) result.merge(AEItemKey.of(stack), buffered.getLong("count"), Long::sum);
        }
        for (var entry : fixture.node().getGrid().getStorageService().getInventory().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey item && entry.getLongValue() > 0) {
                result.merge(item, entry.getLongValue(), Long::sum);
            }
        }
        return result;
    }

    private static Map<AEFluidKey, Long> accountedFluids(NetworkFixture fixture) {
        Map<AEFluidKey, Long> result = new HashMap<>();
        for (FluidStack fluid : fixture.machine().developmentPaidFluidOutputs()) {
            result.merge(AEFluidKey.of(fluid), (long) fluid.getAmount(), Long::sum);
        }
        for (var entry : fixture.node().getGrid().getStorageService().getInventory().getAvailableStacks()) {
            if (entry.getKey() instanceof AEFluidKey fluid && entry.getLongValue() > 0) {
                result.merge(fluid, entry.getLongValue(), Long::sum);
            }
        }
        return result;
    }

    private static void assertFluidsConserved(GameTestHelper helper, NetworkFixture fixture,
                                              Map<AEFluidKey, Long> expected, String context) {
        Map<AEFluidKey, Long> actual = accountedFluids(fixture);
        helper.assertTrue(actual.equals(expected), context + " changed paid fluid conservation: expected=" + expected
                + ", actual=" + actual + ", waterQueued=" + paidWater(fixture.machine())
                + ", waterInME=" + fixture.node().getGrid().getStorageService().getInventory().getAvailableStacks()
                .get(AEFluidKey.of(new FluidStack(Fluids.WATER, 1))));
    }

    private static Snapshot snapshot(DrygmySimulatorBlockEntity tile) {
        return new Snapshot(tile.developmentPendingOutputs(), tile.developmentPendingFluids(), tile.developmentPendingSignature(),
                tile.developmentPendingOperationMultiplier(), tile.developmentSourceCost(), tile.developmentPendingEnergyPerTick(),
                tile.getProgress(), tile.getProgressRequired());
    }

    private static void assertFrozen(GameTestHelper helper, DrygmySimulatorBlockEntity tile, Snapshot expected, int source, long energy) {
        helper.assertTrue(ItemStack.listMatches(expected.outputs(), tile.developmentPendingOutputs())
                        && sameFluids(expected.fluids(), tile.developmentPendingFluids())
                        && expected.signature().equals(tile.developmentPendingSignature())
                        && tile.developmentPendingOperationMultiplier() == expected.multiplier()
                        && tile.developmentSourceCost() == expected.sourceCost() && tile.developmentPendingEnergyPerTick() == expected.energyPerTick()
                        && tile.getProgress() == expected.progress() && tile.getProgressRequired() == expected.required()
                        && tile.getSource() == source && tile.getNativeEnergyContainer().getEnergy() == energy
                        && !tile.getActive() && !tile.isVisuallyProcessing(),
                "Stopped Drygmy changed its prepared random items/fluids, context, progress or Source/energy payment");
    }

    private static boolean sameFluids(List<FluidStack> first, List<FluidStack> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            if (first.get(index).getAmount() != second.get(index).getAmount()
                    || !FluidStack.isSameFluidSameComponents(first.get(index), second.get(index))) return false;
        }
        return true;
    }

    private static CompoundTag save(GameTestHelper helper, DrygmySimulatorBlockEntity tile) {
        return tile.saveWithoutMetadata(helper.getLevel().registryAccess());
    }

    private static void tick(DrygmySimulatorBlockEntity tile, int ticks) {
        for (int tick = 0; tick < ticks; tick++) tile.onUpdateServer();
    }

    private record Snapshot(List<ItemStack> outputs, List<FluidStack> fluids, String signature,
                            int multiplier, int sourceCost, long energyPerTick, int progress, int required) {
    }

    private record NetworkFixture(DrygmySimulatorBlockEntity machine, DriveBlockEntity drive) {
        private IGridNode node() {
            return Ae2DirectOutputProvider.forTile(machine).getActionableNode();
        }
    }
}
