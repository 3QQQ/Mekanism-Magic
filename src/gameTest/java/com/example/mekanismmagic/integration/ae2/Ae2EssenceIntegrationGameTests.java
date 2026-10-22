package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.util.ConfigInventory;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCellInventory;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCells;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceCapabilities;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceHost;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForgeProcessorBlockEntity;
import mekanism.api.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/** Typed F&A Essence across a real AE interface, disk and native Forge node. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2EssenceIntegrationGameTests {
    private static final BlockPos ENERGY = new BlockPos(2, 1, 2);
    private static final BlockPos DRIVE = new BlockPos(2, 2, 2);
    private static final BlockPos MACHINE = new BlockPos(3, 1, 2);

    private Ae2EssenceIntegrationGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void adapterSeparatesFourKeysAndBatchesWithoutSecondTank(
            GameTestHelper helper) {
        AEKey[] keys = keys(helper);
        AtomicInteger changes = new AtomicInteger();
        ConfigInventory slots = ConfigInventory.storage(5)
                .supportedTypes(MagicResourceKeys.TYPE)
                .changeListener(changes::incrementAndGet).build();
        slots.setCapacity(MagicResourceKeys.TYPE, 100L);
        slots.setStack(0, new GenericStack(keys[1], 40));
        slots.setStack(1, new GenericStack(keys[3], 10));
        changes.set(0);
        EssenceResourceHost view = Ae2EssenceInterfaceStorage.create(slots);
        helper.assertTrue(view != null
                        && view.getStoredEssence(0) == 0
                        && view.getStoredEssence(1) == 40
                        && view.getStoredEssence(2) == 0
                        && view.getStoredEssence(3) == 10,
                "AE Essence view mixed four typed keys or kept a second tank");
        helper.assertTrue(view.insertEssence(0, 175,
                        Action.SIMULATE) == 175
                        && view.getStoredEssence(0) == 0
                        && changes.get() == 0,
                "Simulated typed insertion changed AE slots");
        helper.assertTrue(view.insertEssence(0, 175,
                        Action.EXECUTE) == 175
                        && view.getStoredEssence(0) == 175
                        && changes.get() == 1,
                "Multi-slot Essence insertion did not batch exactly once");
        helper.assertTrue(view.extractEssence(0, 125,
                        Action.SIMULATE) == 125
                        && view.getStoredEssence(0) == 175
                        && changes.get() == 1,
                "Simulated Essence extraction changed AE slots");
        helper.assertTrue(view.extractEssence(0, 125,
                        Action.EXECUTE) == 125
                        && view.getStoredEssence(0) == 50
                        && view.getStoredEssence(1) == 40
                        && view.getStoredEssence(3) == 10
                        && changes.get() == 2,
                "Typed Essence extraction lost units or touched another key");
        helper.assertTrue(view.insertEssence(-1, 1,
                        Action.EXECUTE) == 0
                        && view.extractEssence(4, 1,
                        Action.EXECUTE) == 0,
                "Invalid Essence channel aliased a live key");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 200)
    public static void physicalInterfaceExportsAndStocksTypedEssence(
            GameTestHelper helper) {
        if (!ModCompatibility.forbiddenArcanusLoaded()) {
            helper.succeed();
            return;
        }
        DriveBlockEntity drive = createDrive(helper);
        helper.setBlock(MACHINE, AEBlocks.INTERFACE.block());
        InterfaceBlockEntity tile = helper.getBlockEntity(MACHINE);
        AEKey[] keys = keys(helper);
        helper.startSequence().thenWaitUntil(() -> {
            assertDriveReady(helper, drive, keys);
            helper.assertTrue(tile.getMainNode().isActive()
                            && tile.getMainNode().getGrid()
                            == drive.getMainNode().getGrid(),
                    "AE interface did not join the powered magic disk");
        }).thenExecute(() -> {
            EssenceResourceHost adapter = helper.getLevel()
                    .getCapability(EssenceResourceCapabilities.ESSENCE,
                            helper.absolutePos(MACHINE), Direction.UP);
            helper.assertTrue(adapter != null,
                    "AE interface lacks the shared four-Essence capability");
            for (int channel = 0; channel < 4; channel++) {
                int amount = 20 * (channel + 1);
                helper.assertTrue(adapter.insertEssence(channel, amount,
                                Action.SIMULATE) == amount
                                && adapter.insertEssence(channel, amount,
                                Action.EXECUTE) == amount,
                        "AE interface rejected canonical Essence " + channel);
            }
        }).thenWaitUntil(() -> {
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(cell(drive).storedAmount(
                                EssenceResources.IDS.get(channel))
                                == 20L * (channel + 1),
                        "AE interface did not export Essence to disk "
                                + channel);
            }
        }).thenExecute(() -> {
            EssenceResourceHost adapter = helper.getLevel()
                    .getCapability(EssenceResourceCapabilities.ESSENCE,
                            helper.absolutePos(MACHINE), Direction.UP);
            tile.getInterfaceLogic().getConfig().setStack(0,
                    new GenericStack(keys[0], 10));
            helper.assertTrue(adapter != null,
                    "AE interface Essence adapter vanished after export");
        }).thenWaitUntil(() -> {
            EssenceResourceHost adapter = helper.getLevel()
                    .getCapability(EssenceResourceCapabilities.ESSENCE,
                            helper.absolutePos(MACHINE), Direction.UP);
            helper.assertTrue(adapter != null
                            && adapter.getStoredEssence(0) == 10
                            && cell(drive).storedAmount(
                            EssenceResources.IDS.get(0)) == 10,
                    "AE interface did not stock a selected typed Essence key");
        }).thenExecute(() -> {
            EssenceResourceHost adapter = helper.getLevel()
                    .getCapability(EssenceResourceCapabilities.ESSENCE,
                            helper.absolutePos(MACHINE), Direction.UP);
            helper.assertTrue(adapter.extractEssence(0, 5,
                            Action.EXECUTE) == 5
                            && adapter.getStoredEssence(0) == 5,
                    "AE interface could not return stocked Essence to a neighbor");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void forgeDirectNodeWithdrawsPoweredTypedEssenceAndSupplyOffStops(
            GameTestHelper helper) {
        if (!ModCompatibility.forbiddenArcanusLoaded()) {
            helper.succeed();
            return;
        }
        DriveBlockEntity drive = createDrive(helper);
        helper.setBlock(MACHINE,
                ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity forge =
                helper.getBlockEntity(MACHINE);
        AEKey[] keys = keys(helper);
        long[] seeded = {1_000L, 100L, 2_000L, 300L};
        int[] atSupplyOff = new int[4];
        helper.startSequence().thenWaitUntil(() -> {
            assertDriveReady(helper, drive, keys);
            var node = Ae2MachineResourceProvider.forTile(forge)
                    .getActionableNode();
            helper.assertTrue(node != null && node.isActive()
                            && node.getGrid()
                            == drive.getMainNode().getGrid(),
                    "Forge did not use its one active native AE resource node");
            helper.assertTrue((MachineAeResourceHooks.snapshot(forge)
                            .resourceMask()
                            & MachineAeResourceHooks.ESSENCE_MASK)
                            == MachineAeResourceHooks.ESSENCE_MASK,
                    "Forge AE node does not advertise four Essence resources");
        }).thenExecute(() -> {
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(network(drive).insert(keys[channel],
                                seeded[channel], Actionable.MODULATE,
                                IActionSource.empty())
                                == seeded[channel],
                        "Magic disk rejected typed Essence " + channel);
            }
        }).thenWaitUntil(() -> {
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(forge.getStoredEssence(channel) > 0,
                        "Forge did not refill typed Essence from AE "
                                + channel);
            }
        }).thenExecute(() -> {
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(cell(drive).storedAmount(
                                EssenceResources.IDS.get(channel))
                                + forge.getStoredEssence(channel)
                                == seeded[channel],
                        "AE direct refill lost or duplicated Essence "
                                + channel);
                atSupplyOff[channel] = forge.getStoredEssence(channel);
            }
            helper.assertTrue(MachineAeResourceHooks.isEnabled(forge)
                            && MachineAeResourceHooks.toggleSupply(forge)
                            && !MachineAeResourceHooks.isEnabled(forge),
                    "Could not switch Forge AE resource supply off");
        }).thenIdle(15).thenExecute(() -> {
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(cell(drive).storedAmount(
                                EssenceResources.IDS.get(channel))
                                + forge.getStoredEssence(channel)
                                == seeded[channel]
                                && forge.getStoredEssence(channel)
                                == atSupplyOff[channel],
                        "Supply-off continued withdrawing typed Essence "
                                + channel);
            }
        }).thenSucceed();
    }

    private static AEKey[] keys(GameTestHelper helper) {
        AEKey[] keys = new AEKey[EssenceResources.CHANNELS];
        for (int channel = 0; channel < keys.length; channel++) {
            keys[channel] = MagicResourceKeys.essence(channel);
            helper.assertTrue(keys[channel] != null
                            && EssenceResources.IDS.get(channel).equals(
                            MagicResourceKeys.resourceId(keys[channel])),
                    "Missing canonical AE Essence key " + channel);
        }
        return keys;
    }

    private static DriveBlockEntity createDrive(GameTestHelper helper) {
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE);
        helper.assertTrue(drive.getInternalInventory().insertItem(0,
                        new ItemStack(MagicStorageCells.CELLS.get(1).get()),
                        false).isEmpty(),
                "Magic disk did not mount in the physical drive");
        return drive;
    }

    private static void assertDriveReady(GameTestHelper helper,
                                         DriveBlockEntity drive,
                                         AEKey[] keys) {
        helper.assertTrue(drive.getMainNode().isActive()
                        && drive.getOriginalCellInventory(0)
                        instanceof MagicStorageCellInventory,
                "Waiting for a powered mounted magic disk");
        for (AEKey key : keys) {
            helper.assertTrue(network(drive).insert(key, 1,
                            Actionable.SIMULATE,
                            IActionSource.empty()) == 1,
                    "Mounted disk rejected a canonical Essence key");
        }
    }

    private static MagicStorageCellInventory cell(DriveBlockEntity drive) {
        return (MagicStorageCellInventory) drive
                .getOriginalCellInventory(0);
    }

    private static appeng.api.storage.MEStorage network(
            DriveBlockEntity drive) {
        return drive.getMainNode().getGrid()
                .getStorageService().getInventory();
    }
}
