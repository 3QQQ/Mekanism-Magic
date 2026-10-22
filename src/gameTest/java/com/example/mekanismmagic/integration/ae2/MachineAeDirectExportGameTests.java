package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCellItem;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCells;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauMachineConfig;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.FeSourceConverterBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssenceCondenserBlockEntity;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasNatureAltarFactories;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasSpiritFactories;
import com.example.mekanismmagic.integration.naturesaura.AuraFuelGeneratorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.FeAuraConverterBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import mekanism.api.Action;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/** Passive machine ticks export physical output into a real ME drive; no interface is placed. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineAeDirectExportGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 2);
    private static final BlockPos ENERGY = new BlockPos(2, 1, 2);
    private static final BlockPos DRIVE = new BlockPos(2, 2, 2);

    private MachineAeDirectExportGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void condenserExportsFourTypedBanksWithSupplyOffAndPersistsDisk(GameTestHelper helper) {
        if (skipForbidden(helper)) return;
        Network fixture = place(helper, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get(), true, magicDisk());
        EssenceCondenserBlockEntity machine = (EssenceCondenserBlockEntity) fixture.machine();
        int[] paid = {12_345, 67, 23_456, 34_567};
        supplyOff(helper, machine);
        machine.getNativeEnergyContainer().setEnergy(0);
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, essenceKeys(helper)))
                .thenExecute(() -> setBanks(helper, machine, paid))
                .thenWaitUntil(() -> assertExported(helper, fixture, machine, paid))
                .thenIdle(25).thenExecute(() -> {
                    assertExported(helper, fixture, machine, paid);
                    helper.assertTrue(!MachineAeResourceHooks.isEnabled(machine)
                                    && machine.getNativeEnergyContainer().getEnergy() == 0,
                            "Output export consumed machine FE or reenabled input supply");
                    ItemStack disk = removeDisk(helper, fixture);
                    ItemStack restored = roundTrip(helper, disk);
                    for (int channel = 0; channel < 4; channel++) {
                        helper.assertTrue(MagicStorageCellItem.contents(restored).amounts()
                                        .getOrDefault(EssenceResources.IDS.get(channel), 0L) == paid[channel],
                                "Removed disk lost or retyped condenser bank " + channel);
                    }
                    install(helper, fixture.drive(), restored);
                }).thenWaitUntil(() -> ready(helper, fixture, essenceKeys(helper)))
                .thenExecute(() -> assertExported(helper, fixture, machine, paid)).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void offlineCondenserKeepsEveryBankUntilDirectNetworkPowersUp(GameTestHelper helper) {
        if (skipForbidden(helper)) return;
        Network fixture = place(helper, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get(), false, magicDisk());
        EssenceCondenserBlockEntity machine = (EssenceCondenserBlockEntity) fixture.machine();
        int[] paid = {101, 2, 303, 404};
        supplyOff(helper, machine);
        machine.getNativeEnergyContainer().setEnergy(0);
        setBanks(helper, machine, paid);
        helper.startSequence().thenIdle(25).thenExecute(() -> {
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(machine.getStoredEssence(channel) == paid[channel],
                        "Unpowered network debited condenser bank " + channel);
            }
            machine.loadWithComponents(machine.saveWithoutMetadata(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess());
            helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        }).thenWaitUntil(() -> ready(helper, fixture, essenceKeys(helper)))
                .thenWaitUntil(() -> assertExported(helper, fixture, machine, paid)).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void missingItemOnlyAndPartitionedCellsPreserveEssenceForLaterExport(GameTestHelper helper) {
        if (skipForbidden(helper)) return;
        Network fixture = place(helper, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get(), true);
        EssenceCondenserBlockEntity machine = (EssenceCondenserBlockEntity) fixture.machine();
        int[] paid = {1_101, 22, 3_303, 4_404};
        supplyOff(helper, machine);
        machine.getNativeEnergyContainer().setEnergy(0);
        setBanks(helper, machine, paid);
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture))
                .thenIdle(25).thenExecute(() -> {
                    assertBanks(helper, machine, paid);
                    install(helper, fixture.drive(), AEItems.ITEM_CELL_1K.stack());
                }).thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenIdle(25).thenExecute(() -> {
                    assertBanks(helper, machine, paid);
                    helper.assertTrue(fixture.storage().getAvailableStacks().isEmpty(),
                            "Item-only cell received fake essence items");
                    removeDisk(helper, fixture);
                    ItemStack filtered = magicDisk();
                    ((MagicStorageCellItem) filtered.getItem()).getConfigInventory(filtered)
                            .setStack(0, new GenericStack(MagicResourceKeys.source(), 1));
                    install(helper, fixture.drive(), filtered);
                }).thenWaitUntil(() -> ready(helper, fixture, MagicResourceKeys.source()))
                .thenIdle(25).thenExecute(() -> {
                    assertBanks(helper, machine, paid);
                    helper.assertTrue(fixture.storage().getAvailableStacks().isEmpty(),
                            "Source-partitioned disk accepted an excluded essence type");
                    removeDisk(helper, fixture);
                    install(helper, fixture.drive(), magicDisk());
                }).thenWaitUntil(() -> ready(helper, fixture, essenceKeys(helper)))
                .thenWaitUntil(() -> assertExported(helper, fixture, machine, paid)).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 360)
    public static void fullAndPartialDiskExportsConserveAllBanksAcrossReload(GameTestHelper helper) {
        if (skipForbidden(helper)) return;
        Network fixture = place(helper, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get(), true, magicDisk());
        EssenceCondenserBlockEntity machine = (EssenceCondenserBlockEntity) fixture.machine();
        int[] paid = {3_123, 2_045, 6_789, 10_111};
        AEKey source = MagicResourceKeys.source();
        AtomicLong prefill = new AtomicLong();
        supplyOff(helper, machine);
        machine.getNativeEnergyContainer().setEnergy(0);
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, essenceKeys(helper)))
                .thenExecute(() -> {
                    long capacity = fixture.storage().insert(source, Long.MAX_VALUE,
                            Actionable.SIMULATE, IActionSource.empty());
                    helper.assertTrue(capacity > 1000 && fixture.storage().insert(source, capacity,
                                    Actionable.MODULATE, IActionSource.empty()) == capacity,
                            "Could not completely fill the mounted magic disk");
                    prefill.set(capacity);
                    setBanks(helper, machine, paid);
                }).thenIdle(25).thenExecute(() -> {
                    assertBanks(helper, machine, paid);
                    assertConserved(helper, fixture, machine, paid);
                    helper.assertTrue(fixture.storage().extract(source, 1000,
                                    Actionable.MODULATE, IActionSource.empty()) == 1000,
                            "Could not free one physical storage byte");
                }).thenWaitUntil(() -> {
                    assertConserved(helper, fixture, machine, paid);
                    long exported = 0;
                    for (int channel = 0; channel < 4; channel++) exported += storedEssence(fixture, channel);
                    helper.assertTrue(exported == 1000,
                            "Waiting for exactly one byte of partial typed essence output");
                }).thenExecute(() -> {
                    assertConserved(helper, fixture, machine, paid);
                    CompoundTag saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
                    machine.loadWithComponents(saved, helper.getLevel().registryAccess());
                    ItemStack disk = roundTrip(helper, removeDisk(helper, fixture));
                    install(helper, fixture.drive(), disk);
                }).thenWaitUntil(() -> ready(helper, fixture))
                .thenIdle(25).thenExecute(() -> {
                    assertConserved(helper, fixture, machine, paid);
                    helper.assertTrue(fixture.storage().extract(source, Long.MAX_VALUE,
                                    Actionable.MODULATE, IActionSource.empty()) == prefill.get() - 1000,
                            "Partial export changed the preexisting Source balance");
                }).thenWaitUntil(() -> assertExported(helper, fixture, machine, paid)).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void ordinaryMachineExportsItemsButRetainsReagentAndManualCatalyst(GameTestHelper helper) {
        Network fixture = place(helper, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get(), true,
                AEItems.ITEM_CELL_1K.stack(), magicDisk());
        ImbuementProcessorBlockEntity machine = (ImbuementProcessorBlockEntity) fixture.machine();
        supplyOff(helper, machine);
        machine.getNativeEnergyContainer().setEnergy(0);
        machine.setSource(5000);
        ItemStack reagent = new ItemStack(Items.DIRT, 11);
        ItemStack catalyst = new ItemStack(ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
        machine.getNativeInputSlot().setStack(reagent.copy());
        IInventorySlot manual = machine.mekanismMagicManualOnlySlots().getFirst();
        manual.setStack(catalyst.copy());
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> machine.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, 37)))
                .thenWaitUntil(() -> helper.assertTrue(machine.getNativeOutputSlot().isEmpty()
                                && stored(fixture, AEItemKey.of(Items.STONE)) == 37,
                        "Ordinary imbuement machine did not export its completed item output"))
                .thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(ItemStack.matches(machine.getNativeInputSlot().getStack(), reagent)
                                    && ItemStack.matches(manual.getStack(), catalyst)
                                    && stored(fixture, AEItemKey.of(Items.DIRT)) == 0
                                    && stored(fixture, AEItemKey.of(catalyst)) == 0
                                    && stored(fixture, AEItemKey.of(Items.STONE)) == 37
                                    && machine.getSource() == 5000
                                    && stored(fixture, MagicResourceKeys.source()) == 0,
                            "Item export removed a reagent/catalyst/consumer Source or duplicated output");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void fullVesselExportsButPartialWorkingVesselAndEssenceSamplesStay(GameTestHelper helper) {
        if (skipForbidden(helper)) return;
        Network fixture = place(helper, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get(), false,
                AEItems.ITEM_CELL_1K.stack());
        EssenceCondenserBlockEntity machine = (EssenceCondenserBlockEntity) fixture.machine();
        supplyOff(helper, machine);
        machine.getNativeEnergyContainer().setEnergy(0);
        ItemStack aurealSample = forbiddenItem("aureal_bottle");
        ItemStack soulSample = forbiddenItem("soul");
        machine.getSampleSlot(0).setStack(aurealSample.copy());
        machine.getSampleSlot(1).setStack(soulSample.copy());
        machine.getVesselInputSlot(0).setStack(forbiddenItem("utrem_jar"));
        machine.getVesselInputSlot(1).setStack(forbiddenItem("utrem_jar"));
        setBanks(helper, machine, new int[]{10_000, 1, 0, 0});
        ItemStack[] vessels = new ItemStack[2];
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                        !machine.getVesselOutputSlot(0).isEmpty() && !machine.getVesselOutputSlot(1).isEmpty()
                                && machine.getStoredEssence(0) == 0 && machine.getStoredEssence(1) == 0,
                        "Unpowered fixture did not fill its full and partial physical vessels"))
                .thenExecute(() -> {
                    vessels[0] = machine.getVesselOutputSlot(0).getStack().copy();
                    vessels[1] = machine.getVesselOutputSlot(1).getStack().copy();
                    helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
                }).thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(vessels[0])))
                .thenWaitUntil(() -> helper.assertTrue(machine.getVesselOutputSlot(0).isEmpty()
                                && stored(fixture, AEItemKey.of(vessels[0])) == 1,
                        "Completed native vessel was not returned through the machine's direct AE node"))
                .thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(ItemStack.matches(machine.getVesselOutputSlot(1).getStack(), vessels[1])
                                    && ItemStack.matches(machine.getSampleSlot(0).getStack(), aurealSample)
                                    && ItemStack.matches(machine.getSampleSlot(1).getStack(), soulSample)
                                    && stored(fixture, AEItemKey.of(vessels[1])) == 0
                                    && stored(fixture, AEItemKey.of(aurealSample)) == 0
                                    && stored(fixture, AEItemKey.of(soulSample)) == 0
                                    && stored(fixture, AEItemKey.of(vessels[0])) == 1,
                            "Direct item export removed a partial working vessel/sample or duplicated a full vessel");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 360)
    public static void realFeConversionExportsOnePaidSourceBatchDirectly(GameTestHelper helper) {
        Network fixture = place(helper, ArsNouveauRegistries.SOURCE_CONVERTER_BLOCK.get(), true, magicDisk());
        FeSourceConverterBlockEntity machine = (FeSourceConverterBlockEntity) fixture.machine();
        supplyOff(helper, machine);
        machine.getNativeEnergyContainer().setEnergy(0);
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, MagicResourceKeys.source()))
                .thenExecute(() -> machine.getNativeEnergyContainer().setEnergy(
                        (long) ArsNouveauMachineConfig.SOURCE_CONVERTER_DURATION
                                * ArsNouveauMachineConfig.SOURCE_CONVERTER_JOULES_PER_TICK))
                .thenWaitUntil(() -> helper.assertTrue(machine.getNativeEnergyContainer().getEnergy() == 0
                                && machine.getSource() == 0
                                && stored(fixture, MagicResourceKeys.source())
                                == ArsNouveauMachineConfig.SOURCE_CONVERTER_SOURCE_PER_OPERATION,
                        "Direct AE output did not receive exactly one finite-energy Source operation"))
                .thenIdle(25).thenExecute(() -> helper.assertTrue(machine.getSource() == 0
                                && stored(fixture, MagicResourceKeys.source())
                                == ArsNouveauMachineConfig.SOURCE_CONVERTER_SOURCE_PER_OPERATION,
                        "Source output was repulled into its generator or produced without payment"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void realFuelGeneratorExportsExactlyOnePaidAuraQuotaDirectly(GameTestHelper helper) {
        Network fixture = place(helper, NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK.get(), true, magicDisk());
        AuraFuelGeneratorBlockEntity machine = (AuraFuelGeneratorBlockEntity) fixture.machine();
        supplyOff(helper, machine);
        AEKey aura = MagicResourceKeys.key(machine.getGenerationAuraType().getName());
        helper.assertTrue(aura != null, "Generator Aura type has no canonical key");
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, aura))
                .thenExecute(() -> machine.getNativeInputSlot().setStack(new ItemStack(Items.EGG)))
                .thenWaitUntil(() -> helper.assertTrue(machine.getNativeInputSlot().isEmpty()
                                && machine.getRemainingFuelAura() == 0 && machine.getStoredAura() == 0
                                && stored(fixture, aura) == 10_000,
                        "Direct AE output lost, duplicated or retyped the egg's 10,000 Aura quota"))
                .thenIdle(25).thenExecute(() -> helper.assertTrue(machine.getStoredAura() == 0
                                && stored(fixture, aura) == 10_000 && !MachineAeResourceHooks.isEnabled(machine),
                        "Aura export refilled its generator or ignored the supply state"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sourceOutputRefundDoesNotOpenInputOrRenewThisTicksOutputBudget(GameTestHelper helper) {
        helper.setBlock(MACHINE, ArsNouveauRegistries.SOURCE_CONVERTER_BLOCK.get());
        FeSourceConverterBlockEntity machine = helper.getBlockEntity(MACHINE);
        int rate = machine.getTransferRate();
        machine.setSource(rate + 500);
        for (int attempt = 0; attempt < 3; attempt++) {
            helper.assertTrue(machine.extractProducedSource(rate + 1, true) == rate
                            && machine.getSource() == rate + 500,
                    "Source output simulation debited the tank or spent the tick's output budget");
        }
        helper.assertTrue(machine.extractProducedSource(rate + 1, false) == rate
                        && machine.getSource() == 500
                        && machine.getSourceStorage().receiveSource(1000, false) == 0,
                "Source reservation did not debit its exact bounded output or opened generator input");
        helper.assertTrue(machine.refundProducedSource(1000) == 1000
                        && machine.getSource() == 1500
                        && machine.extractProducedSource(1000, false) == 0,
                "Output refund lost Source or renewed a spent per-tick transfer budget");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void essenceReservationRefundIsTypedAndNeverOpensConsumerInsertion(GameTestHelper helper) {
        if (skipForbidden(helper)) return;
        helper.setBlock(MACHINE, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get());
        EssenceCondenserBlockEntity machine = helper.getBlockEntity(MACHINE);
        setBanks(helper, machine, new int[]{100, 2, 300, 400});
        helper.assertTrue(machine.extractEssenceFluid(0, 90, Action.SIMULATE) == 90
                        && machine.getStoredEssence(0) == 100,
                "Essence reservation simulation debited the real bank");
        helper.assertTrue(machine.extractEssenceFluid(0, 90, Action.EXECUTE) == 90
                        && machine.refundExtractedEssence(0, 55, Action.SIMULATE) == 55
                        && machine.getStoredEssence(0) == 10
                        && machine.refundExtractedEssence(0, 55, Action.EXECUTE) == 55
                        && machine.getStoredEssence(0) == 65
                        && machine.getStoredEssence(1) == 2
                        && machine.getStoredEssence(2) == 300
                        && machine.getStoredEssence(3) == 400
                        && machine.insertEssenceFluid(0, 10, Action.EXECUTE) == 0
                        && machine.refundExtractedEssence(-1, 100, Action.EXECUTE) == 0
                        && machine.refundExtractedEssence(4, 100, Action.EXECUTE) == 0,
                "Essence refund changed another bank, opened input, or lost the exact unaccepted remainder");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void auraRefundRequiresTheOriginalTypeAndAnActiveOutputTransaction(GameTestHelper helper) {
        helper.setBlock(MACHINE, NaturesAuraRegistries.FE_AURA_CONVERTER_BLOCK.get());
        FeAuraConverterBlockEntity machine = helper.getBlockEntity(MACHINE);
        var type = machine.getGenerationAuraType();
        var other = type.isSimilar(NaturesAuraAPI.TYPE_NETHER)
                ? NaturesAuraAPI.TYPE_OVERWORLD : NaturesAuraAPI.TYPE_NETHER;
        helper.assertTrue(machine.getAuraContainer(Direction.UP).storeAura(100, false) == 0
                        && machine.refundGeneratedAura(type, 100) == 0
                        && machine.getStoredAura() == 0,
                "Aura output port or an unlocked refund opened ordinary generator input");
        helper.assertTrue(machine.beginAuraOutputTransaction(), "Could not begin the Aura output transaction");
        try {
            helper.assertTrue(!machine.canCycleAuraTypeMode() && !machine.cycleAuraTypeMode()
                            && machine.refundGeneratedAura(other, 100) == 0
                            && machine.refundGeneratedAura(type, 60) == 60
                            && machine.getStoredAura() == 60,
                    "Aura output callback retyped a pending reservation or lost its bounded same-type refund");
        } finally {
            machine.endAuraOutputTransaction();
        }
        helper.assertTrue(machine.refundGeneratedAura(type, 40) == 0 && machine.getStoredAura() == 60,
                "Aura refund remained open after the output transaction ended");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void nativeFactoryExportsAllLanesThroughOneNode(GameTestHelper helper) {
        runFactory(helper, ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get(), 3);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void extrasFactoryExportsAllLanesThroughOneNode(GameTestHelper helper) {
        runFactory(helper, MekanismExtrasImbuementFactories.INFINITE_BLOCK.get(), 17);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void nativeNatureFactoryExportsAllLanesThroughOneNode(GameTestHelper helper) {
        runFactory(helper, NaturesAuraRegistries.BASIC_NATURE_ALTAR_FACTORY_BLOCK.get(), 3);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void extrasNatureFactoryExportsAllLanesThroughOneNode(GameTestHelper helper) {
        runFactory(helper, MekanismExtrasNatureAltarFactories.INFINITE_BLOCK.get(), 17);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void extrasSpiritFactoryExportsAllLanesThroughOneNode(GameTestHelper helper) {
        runFactory(helper, MekanismExtrasSpiritFactories.INFINITE_BLOCK.get(), 17);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void continuouslyRefilledFirstFactoryLaneCannotStarveMatchingLaterOutputs(GameTestHelper helper) {
        Network fixture = place(helper, ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get(), true,
                AEItems.ITEM_CELL_1K.stack());
        IMekanismMagicAutomation automation = (IMekanismMagicAutomation) fixture.machine();
        supplyOff(helper, fixture.machine());
        automation.mekanismMagicEnergyContainer().setEnergy(0);
        var outputs = automation.mekanismMagicPatternOutputs();
        helper.assertTrue(outputs.size() == 3, "Factory fixture is missing a physical output lane");
        AtomicBoolean replenish = new AtomicBoolean();
        AtomicLong supplied = new AtomicLong();
        helper.onEachTick(() -> {
            if (replenish.get() && outputs.getFirst().isEmpty()) {
                ((BasicInventorySlot) outputs.getFirst()).setStackUnchecked(new ItemStack(Items.STONE));
                supplied.incrementAndGet();
            }
        });
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    seedOutputs(automation);
                    supplied.set(laneTotal(automation));
                    replenish.set(true);
                }).thenWaitUntil(() -> helper.assertTrue(outputs.get(1).isEmpty() && outputs.get(2).isEmpty(),
                        "The continuously replenished first output starved matching later factory lanes"))
                .thenExecute(() -> replenish.set(false))
                .thenWaitUntil(() -> assertFactoryExported(helper, fixture, automation, supplied.get()))
                .thenIdle(25).thenExecute(() -> assertFactoryExported(helper, fixture, automation, supplied.get()))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 500)
    public static void mekEFactoryExportsExactlyOnceWhileItsSingleNodeChangesOwner(GameTestHelper helper) {
        Network fixture = place(helper, NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.get(), true,
                AEItems.ITEM_CELL_1K.stack());
        IMekanismMagicAutomation automation = (IMekanismMagicAutomation) fixture.machine();
        MeUpgradeRecipeMachineAdapter adapter = (MeUpgradeRecipeMachineAdapter) fixture.machine();
        supplyOff(helper, fixture.machine());
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    assertSingleNode(helper, fixture);
                    seedOutputs(automation);
                }).thenWaitUntil(() -> assertFactoryExported(helper, fixture, automation, laneTotal(automation)))
                .thenExecute(() -> {
                    helper.assertTrue(adapter.getMeUpgradeContainer().install(MeUpgradeType.PATTERN_PROVIDER)
                                    .successful(), "Could not install the real factory MekE upgrade");
                    adapter.onMeUpgradeStateChanged();
                    adapter.createMeNodeIfActive();
                    helper.assertTrue(fixture.drive().getMainNode().getGrid().size() <= 3,
                            "Installing MekE temporarily created a second machine node");
                }).thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    assertSingleNode(helper, fixture);
                    seedOutputs(automation);
                }).thenWaitUntil(() -> assertFactoryExported(helper, fixture, automation, 2 * laneTotal(automation)))
                .thenExecute(() -> {
                    helper.assertTrue(adapter.getMeUpgradeContainer().uninstall(MeUpgradeType.PATTERN_PROVIDER),
                            "Could not uninstall the empty factory MekE upgrade");
                    adapter.onMeUpgradeStateChanged();
                    helper.assertTrue(fixture.drive().getMainNode().getGrid().size() <= 3,
                            "Removing MekE temporarily created a second machine node");
                }).thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    assertSingleNode(helper, fixture);
                    seedOutputs(automation);
                }).thenWaitUntil(() -> assertFactoryExported(helper, fixture, automation, 3 * laneTotal(automation)))
                .thenIdle(25).thenExecute(() -> assertFactoryExported(helper, fixture, automation,
                        3 * laneTotal(automation))).thenSucceed();
    }

    private static void runFactory(GameTestHelper helper, Block block, int expectedLanes) {
        Network fixture = place(helper, block, true, AEItems.ITEM_CELL_1K.stack());
        IMekanismMagicAutomation automation = (IMekanismMagicAutomation) fixture.machine();
        helper.assertTrue(automation.mekanismMagicPatternOutputs().size() == expectedLanes,
                "Factory direct-output contract omitted a physical processing lane");
        supplyOff(helper, fixture.machine());
        automation.mekanismMagicEnergyContainer().setEnergy(0);
        IInventorySlot input = automation.mekanismMagicPatternInputs().getFirst();
        ((BasicInventorySlot) input).setStackUnchecked(new ItemStack(Items.DIRT, 9));
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    assertSingleNode(helper, fixture);
                    seedOutputs(automation);
                }).thenWaitUntil(() -> assertFactoryExported(helper, fixture, automation, laneTotal(automation)))
                .thenIdle(25).thenExecute(() -> {
                    assertFactoryExported(helper, fixture, automation, laneTotal(automation));
                    helper.assertTrue(input.getStack().is(Items.DIRT) && input.getCount() == 9
                                    && stored(fixture, AEItemKey.of(Items.DIRT)) == 0,
                            "Factory export removed its unprocessed input lane");
                }).thenSucceed();
    }

    private static void seedOutputs(IMekanismMagicAutomation automation) {
        var outputs = automation.mekanismMagicPatternOutputs();
        for (int lane = 0; lane < outputs.size(); lane++) {
            ((BasicInventorySlot) outputs.get(lane)).setStackUnchecked(new ItemStack(Items.STONE, lane % 16 + 1));
        }
    }

    private static long laneTotal(IMekanismMagicAutomation automation) {
        long result = 0;
        for (int lane = 0; lane < automation.mekanismMagicPatternOutputs().size(); lane++) result += lane % 16 + 1;
        return result;
    }

    private static void assertFactoryExported(GameTestHelper helper, Network fixture,
                                               IMekanismMagicAutomation automation, long expected) {
        helper.assertTrue(automation.mekanismMagicPatternOutputs().stream().allMatch(IInventorySlot::isEmpty)
                        && stored(fixture, AEItemKey.of(Items.STONE)) == expected,
                "Factory lanes did not export their exact combined count once");
        assertSingleNode(helper, fixture);
    }

    private static boolean skipForbidden(GameTestHelper helper) {
        if (ModCompatibility.forbiddenArcanusLoaded()) return false;
        helper.succeed();
        return true;
    }

    private static ItemStack forbiddenItem(String path) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path))
                .orElseThrow().getDefaultInstance();
    }

    private static Network place(GameTestHelper helper, Block block, boolean powered, ItemStack... cells) {
        helper.setBlock(MACHINE, block);
        TileEntityMekanism machine = helper.getBlockEntity(MACHINE);
        if (powered) helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE);
        for (int slot = 0; slot < cells.length; slot++) {
            helper.assertTrue(drive.getInternalInventory().insertItem(slot, cells[slot], false).isEmpty(),
                    "Physical drive rejected its storage cell");
        }
        return new Network(machine, drive);
    }

    private static ItemStack magicDisk() {
        return new ItemStack(MagicStorageCells.CELLS.get(1).get());
    }

    private static void install(GameTestHelper helper, DriveBlockEntity drive, ItemStack disk) {
        helper.assertTrue(drive.getInternalInventory().insertItem(0, disk, false).isEmpty(),
                "Physical drive rejected its replacement disk");
    }

    private static ItemStack removeDisk(GameTestHelper helper, Network fixture) {
        ItemStack result = fixture.drive().getInternalInventory().extractItem(0, 1, false);
        helper.assertTrue(!result.isEmpty(), "Physical drive did not return its installed disk");
        return result;
    }

    private static ItemStack roundTrip(GameTestHelper helper, ItemStack disk) {
        return ItemStack.parse(helper.getLevel().registryAccess(),
                disk.save(helper.getLevel().registryAccess())).orElseThrow();
    }

    private static void supplyOff(GameTestHelper helper, TileEntityMekanism machine) {
        if (MachineAeResourceHooks.isEnabled(machine)) {
            helper.assertTrue(MachineAeResourceHooks.toggleSupply(machine), "Could not disable AE input supply");
        }
        helper.assertTrue(!MachineAeResourceHooks.isEnabled(machine), "AE supply did not stay disabled");
    }

    private static void ready(GameTestHelper helper, Network fixture, AEKey... keys) {
        IInWorldGridNodeHost host = helper.getLevel().getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                helper.absolutePos(MACHINE), (Void) null);
        IGridNode node = host == null ? null : host.getGridNode(Direction.EAST);
        helper.assertTrue(node != null && node.isActive() && fixture.drive().isPowered()
                        && node.getGrid() == fixture.drive().getMainNode().getGrid(),
                "Waiting for the one machine node to join its directly connected powered drive");
        for (AEKey key : keys) {
            helper.assertTrue(key != null && fixture.storage().insert(key, 1,
                            Actionable.SIMULATE, IActionSource.empty()) == 1,
                    "Waiting for the physical disk to accept " + key);
        }
    }

    private static void assertSingleNode(GameTestHelper helper, Network fixture) {
        helper.assertTrue(fixture.drive().getMainNode().getGrid().size() == 3,
                "Direct export created an extra machine node beside the energy cell and drive");
    }

    private static AEKey[] essenceKeys(GameTestHelper helper) {
        AEKey[] result = new AEKey[4];
        for (int channel = 0; channel < 4; channel++) {
            result[channel] = MagicResourceKeys.essence(channel);
            helper.assertTrue(result[channel] != null, "Missing canonical essence key " + channel);
        }
        return result;
    }

    private static void setBanks(GameTestHelper helper, EssenceCondenserBlockEntity machine, int[] paid) {
        CompoundTag state = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        state.getCompound(EssenceCondenserBlockEntity.STATE_TAG).putIntArray("banks", paid);
        machine.loadWithComponents(state, helper.getLevel().registryAccess());
    }

    private static void assertBanks(GameTestHelper helper, EssenceCondenserBlockEntity machine, int[] paid) {
        for (int channel = 0; channel < 4; channel++) {
            helper.assertTrue(machine.getStoredEssence(channel) == paid[channel],
                    "Unavailable storage debited typed essence bank " + channel);
        }
    }

    private static void assertConserved(GameTestHelper helper, Network fixture,
                                        EssenceCondenserBlockEntity machine, int[] paid) {
        for (int channel = 0; channel < 4; channel++) {
            helper.assertTrue(machine.getStoredEssence(channel) + storedEssence(fixture, channel) == paid[channel],
                    "Direct export lost, duplicated or mixed paid essence channel " + channel);
        }
    }

    private static void assertExported(GameTestHelper helper, Network fixture,
                                       EssenceCondenserBlockEntity machine, int[] paid) {
        assertConserved(helper, fixture, machine, paid);
        for (int channel = 0; channel < 4; channel++) {
            helper.assertTrue(machine.getStoredEssence(channel) == 0,
                    "Waiting for condenser bank to export directly to magic disk " + channel);
        }
        assertSingleNode(helper, fixture);
    }

    private static long storedEssence(Network fixture, int channel) {
        return stored(fixture, MagicResourceKeys.essence(channel));
    }

    private static long stored(Network fixture, AEKey key) {
        return fixture.storage().getAvailableStacks().get(key);
    }

    private record Network(TileEntityMekanism machine, DriveBlockEntity drive) {
        private MEStorage storage() {
            return drive.getMainNode().getGrid().getStorageService().getInventory();
        }
    }
}
