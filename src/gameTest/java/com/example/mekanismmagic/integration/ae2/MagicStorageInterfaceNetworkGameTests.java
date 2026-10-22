package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.menu.slot.FakeSlot;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceMarkerItem;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCellInventory;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCellItem;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCells;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceNetworkAccess;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Real AE ticks move magic between capabilities, a drive cell and consumers. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagicStorageInterfaceNetworkGameTests {
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 2);
    private static final BlockPos DRIVE_POS = new BlockPos(2, 2, 2);
    private static final BlockPos INTERFACE_POS = new BlockPos(3, 1, 2);
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 2);

    private MagicStorageInterfaceNetworkGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void sharedPipeKeepsTypedEssenceSharesWhenProviderIsTemporarilyAbsent(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS,
                com.example.mekanismmagic.integration.common.network.MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK.get());
        var pipe = ((com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity)
                helper.getBlockEntity(MACHINE_POS)).getTransmitter();
        var data = new net.minecraft.nbt.CompoundTag();
        var shares = new net.minecraft.nbt.CompoundTag();
        for (int channel = 0; channel < 4; channel++) {
            shares.putLong(com.example.mekanismmagic.integration.common.resource.EssenceResources.IDS.get(channel)
                    .toString(), 11L + channel);
        }
        data.put("magic_pipe_essence_shares", shares);
        data.putInt("magic_pipe_essence_cursor", 2);
        pipe.read(helper.getLevel().registryAccess(), data);
        helper.startSequence().thenExecuteAfter(6, () -> {
            var saved = pipe.write(helper.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag());
            for (int channel = 0; channel < 4; channel++) {
                String id = com.example.mekanismmagic.integration.common.resource.EssenceResources.IDS.get(channel).toString();
                helper.assertTrue(saved.getCompound("magic_pipe_essence_shares").getLong(id) == 11L + channel,
                        "Shared pipe discarded cached essence during world save without its provider");
            }
            // A live network owns the save shares until the real upgrade
            // lifecycle withdraws them into this physical transmitter.
            pipe.startUpgrading();
            var upgrade = pipe.getUpgradeData().resources;
            for (int channel = 0; channel < 4; channel++) {
                String id = com.example.mekanismmagic.integration.common.resource.EssenceResources.IDS.get(channel).toString();
                helper.assertTrue(upgrade.getCompound("magic_pipe_essence_shares").getLong(id) == 11L + channel,
                        "Shared pipe discarded cached essence during its real upgrade without its provider");
            }
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 400)
    public static void sourceAndAuraRoundTripThroughRealInterfaceAndMagicDisk(
            GameTestHelper helper) {
        DriveBlockEntity drive = createDrive(helper);
        helper.setBlock(INTERFACE_POS, AEBlocks.INTERFACE.block());
        InterfaceBlockEntity terminal = helper.getBlockEntity(INTERFACE_POS);
        AEKey sourceKey = sourceKey(helper);
        ResourceLocation auraId = IAuraType.forLevel(helper.getLevel()).getName();
        AEKey auraKey = MagicResourceKeys.key(auraId);
        helper.assertTrue(auraKey != null, "Local Aura key is unavailable");
        ISourceCap source = helper.getLevel().getCapability(
                CapabilityRegistry.SOURCE_CAPABILITY,
                helper.absolutePos(INTERFACE_POS), Direction.UP);
        IAuraContainer aura = helper.getLevel().getCapability(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                helper.absolutePos(INTERFACE_POS), Direction.UP);
        helper.assertTrue(source != null && aura != null,
                "Real AE interface is missing its Source or Aura capability");

        helper.startSequence().thenWaitUntil(() -> {
            assertDriveReady(helper, drive, sourceKey, auraKey);
            helper.assertTrue(terminal.getMainNode().isActive()
                            && terminal.getMainNode().getGrid()
                            == drive.getMainNode().getGrid(),
                    "Waiting for the interface to join the drive's powered grid");
        }).thenExecute(() -> {
            helper.assertTrue(source.receiveSource(12_345, true) == 12_345
                            && aura.storeAura(23_456, true) == 23_456
                            && source.getSource() == 0 && aura.getStoredAura() == 0
                            && cell(drive).getAvailableStacks().isEmpty(),
                    "Interface simulation created magic in its buffer or disk");
            helper.assertTrue(source.receiveSource(12_345, false) == 12_345
                            && aura.storeAura(23_456, false) == 23_456,
                    "Interface did not accept both independent magic resources");
            assertConserved(helper, drive, source, aura, auraId, 0, 0);
        }).thenWaitUntil(() -> helper.assertTrue(
                source.getSource() == 0 && aura.getStoredAura() == 0
                        && cell(drive).storedAmount(MagicResourceKeys.SOURCE_ID) == 12_345
                        && cell(drive).storedAmount(auraId) == 23_456,
                "AE interface did not automatically export its magic into the new disk"))
                .thenExecute(() -> {
                    assertConserved(helper, drive, source, aura, auraId, 0, 0);
                    configureStock(helper, terminal, 0, sourceKey, 4_000);
                    configureStock(helper, terminal, 1, auraKey, 8_000);
                }).thenWaitUntil(() -> helper.assertTrue(
                        source.getSource() == 4_000 && aura.getStoredAura() == 8_000
                                && cell(drive).storedAmount(MagicResourceKeys.SOURCE_ID) == 8_345
                                && cell(drive).storedAmount(auraId) == 15_456,
                        "Configured interface did not stock Source and Aura from the magic disk"))
                .thenExecute(() -> {
                    assertConserved(helper, drive, source, aura, auraId, 0, 0);
                    helper.assertTrue(source.extractSource(4_000, true) == 4_000
                                    && aura.drainAura(8_000, true) == 8_000,
                            "Interface did not simulate its stocked magic extraction");
                    assertConserved(helper, drive, source, aura, auraId, 0, 0);
                    // Stop replenishment before this same-tick final withdrawal.
                    terminal.getInterfaceLogic().getConfig().clear();
                    helper.assertTrue(source.extractSource(Integer.MAX_VALUE, false) == 4_000
                                    && aura.drainAura(Integer.MAX_VALUE, false) == 8_000,
                            "Interface capability withdrawal lost or duplicated stocked magic");
                    assertConserved(helper, drive, source, aura, auraId, 4_000, 8_000);
                }).thenIdle(5).thenExecute(() -> {
                    helper.assertTrue(source.getSource() == 0 && aura.getStoredAura() == 0,
                            "Cleared interface configuration refilled without a stock request");
                    assertConserved(helper, drive, source, aura, auraId, 4_000, 8_000);
                    ItemStack removed = drive.getInternalInventory().extractItem(0, 1, false);
                    helper.assertTrue(!removed.isEmpty()
                                    && MagicStorageCellItem.contents(removed).amounts().equals(
                                    Map.of(MagicResourceKeys.SOURCE_ID, 8_345L, auraId, 15_456L)),
                            "Final disk removal did not persist the conserved resource balances");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 320)
    public static void existingMachineRefillsSourceFromTheNewMagicDisk(
            GameTestHelper helper) {
        DriveBlockEntity drive = createDrive(helper);
        helper.setBlock(MACHINE_POS, ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        DrygmySimulatorBlockEntity machine = helper.getBlockEntity(MACHINE_POS);
        // No entity inputs are installed, so this is a pure refill fixture.
        machine.getNativeEnergyContainer().setEnergy(0);
        machine.setSource(0);
        AEKey key = sourceKey(helper);
        helper.startSequence().thenWaitUntil(() -> {
            assertDriveReady(helper, drive, key);
            var node = Ae2DirectOutputProvider.forTile(machine).getActionableNode();
            helper.assertTrue(node != null && node.isActive()
                            && node.getGrid() == drive.getMainNode().getGrid(),
                    "Existing Drygmy AE node did not join the drive's powered grid");
        }).thenExecute(() -> {
            // Native high-frequency refill may already have attempted this
            // empty disk earlier in the same tick. Pause it across a tick so
            // the manual probe does not hit the intentional double-pull guard.
            helper.assertTrue(MachineAeResourceHooks.isEnabled(machine)
                            && MachineAeResourceHooks.toggleSupply(machine)
                            && !MachineAeResourceHooks.isEnabled(machine),
                    "Could not pause native supply for the isolated manual refill probe");
            helper.assertTrue(network(drive).insert(key, 6_000, Actionable.MODULATE,
                            IActionSource.empty()) == 6_000,
                    "New magic disk did not accept canonical Source for machine refill");
            helper.assertTrue(machine.getSource() == 0
                            && cell(drive).storedAmount(MagicResourceKeys.SOURCE_ID) == 6_000,
                    "Seeding network Source changed the consumer's local tank");
        }).thenExecuteAfter(1, () -> {
            helper.assertTrue(MachineAeResourceHooks.toggleSupply(machine)
                            && MachineAeResourceHooks.isEnabled(machine),
                    "Could not resume native supply for the manual refill probe");
            int moved = ArsSourceNetworkAccess.pullInto(machine, machine, 2_500);
            helper.assertTrue(moved == 2_500 && machine.getSource() == 2_500
                            && cell(drive).storedAmount(MagicResourceKeys.SOURCE_ID) == 3_500,
                    "Existing Ars network hook did not refill from the new disk exactly once");
        }).thenWaitUntil(() -> helper.assertTrue(machine.getSource() == 6_000
                        && cell(drive).storedAmount(MagicResourceKeys.SOURCE_ID) == 0,
                "The existing automatic Source refill path did not take the remaining disk contents"))
                .thenIdle(20).thenExecute(() -> {
                    helper.assertTrue(machine.getSource() == 6_000
                                    && cell(drive).getAvailableStacks().isEmpty(),
                            "Repeated automatic refill created Source or changed another resource");
                    ItemStack removed = drive.getInternalInventory().extractItem(0, 1, false);
                    helper.assertTrue(!removed.isEmpty()
                                    && MagicStorageCellItem.contents(removed).amounts().isEmpty(),
                            "Machine refill left persisted ghost Source in the removed magic disk");
                }).thenSucceed();
    }

    private static DriveBlockEntity createDrive(GameTestHelper helper) {
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS);
        ItemStack disk = new ItemStack(MagicStorageCells.CELLS.get(1).get());
        helper.assertTrue(drive.getInternalInventory().insertItem(0, disk, false).isEmpty(),
                "Real ME drive rejected the registered 1K magic disk");
        return drive;
    }

    private static void assertDriveReady(GameTestHelper helper, DriveBlockEntity drive,
                                         AEKey... keys) {
        helper.assertTrue(drive.getMainNode().isActive() && drive.isPowered()
                        && drive.getOriginalCellInventory(0) instanceof MagicStorageCellInventory,
                "Waiting for the powered ME drive to mount its magic disk");
        for (AEKey key : keys) {
            helper.assertTrue(network(drive).insert(key, 1, Actionable.SIMULATE,
                            IActionSource.empty()) == 1,
                    "Mounted ME network does not accept its canonical magic resource");
        }
    }

    private static void configureStock(GameTestHelper helper,
                                        InterfaceBlockEntity terminal, int slotIndex,
                                        AEKey key, int requested) {
        ItemStack marker = new ItemStack(MagicStorageCells.MARKER.get());
        MagicResourceMarkerItem.select(marker, MagicResourceKeys.resourceId(key));
        GenericStack ghost = GenericStack.unwrapItemStack(marker);
        helper.assertTrue(ghost != null && key.equals(ghost.what()) && ghost.amount() == 0,
                "Resource marker did not resolve to an empty canonical magic ghost");
        var config = terminal.getInterfaceLogic().getConfig();
        FakeSlot slot = new FakeSlot(config.createMenuWrapper(), slotIndex);
        helper.assertTrue(slot.canSetFilterTo(marker),
                "Native interface configuration rejected the magic resource marker");
        slot.set(marker);
        helper.assertTrue(key.equals(config.getKey(slotIndex))
                        && config.getAmount(slotIndex) == 1,
                "Empty magic marker did not create the native initial stock request");
        slot.set(GenericStack.wrapInItemStack(key, requested));
        helper.assertTrue(key.equals(config.getKey(slotIndex))
                        && config.getAmount(slotIndex) == requested,
                "Native interface stock request did not retain its selected magic quantity");
    }

    private static void assertConserved(GameTestHelper helper, DriveBlockEntity drive,
                                        ISourceCap source, IAuraContainer aura,
                                        ResourceLocation auraId, int removedSource,
                                        int removedAura) {
        helper.assertTrue(cell(drive).storedAmount(MagicResourceKeys.SOURCE_ID)
                                + source.getSource() + removedSource == 12_345
                        && cell(drive).storedAmount(auraId)
                                + aura.getStoredAura() + removedAura == 23_456,
                "Disk, interface and withdrawn balances lost or duplicated Source/Aura");
    }

    private static AEKey sourceKey(GameTestHelper helper) {
        AEKey key = MagicResourceKeys.source();
        helper.assertTrue(key != null
                        && MagicResourceKeys.SOURCE_ID.equals(MagicResourceKeys.resourceId(key)),
                "Source canonical key is unavailable");
        if (ModCompatibility.loaded(ModCompatibility.ARS_ENERGISTIQUE)) {
            try {
                helper.assertTrue(key == Class.forName("gripe._90.arseng.me.key.SourceKey")
                                .getField("KEY").get(null),
                        "Interface and machine refill do not use the native ArsEng Source key");
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Installed ArsEng Source key is unavailable", failure);
            }
        } else {
            helper.assertTrue(key.getType() == MagicResourceKeys.TYPE,
                    "Source did not use the addon's fallback key without ArsEng");
        }
        return key;
    }

    private static MagicStorageCellInventory cell(DriveBlockEntity drive) {
        return (MagicStorageCellInventory) drive.getOriginalCellInventory(0);
    }

    private static MEStorage network(DriveBlockEntity drive) {
        return drive.getMainNode().getGrid().getStorageService().getInventory();
    }
}
