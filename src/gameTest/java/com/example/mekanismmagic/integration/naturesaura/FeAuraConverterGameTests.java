package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.common.network.MagicPipeRegistries;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.api.Upgrade;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** FE conservation, real Aura delivery and generator persistence regressions. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FeAuraConverterGameTests {
    private static final BlockPos MACHINE = new BlockPos(3, 2, 3);
    private static final int OUTPUT_PER_TICK = 64_000;

    private FeAuraConverterGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void modifiedNativeRfRatioCannotCreatePositiveEnergyLoop(GameTestHelper helper) {
        var setting = de.ellpeck.naturesaura.ModConfig.instance.auraToRFRatio;
        double previous = setting.get();
        try {
            setting.set(125D);
            FeAuraConverterBlockEntity machine = place(helper, MACHINE);
            machine.getComponent().addUpgrades(Upgrade.ENERGY, 8);
            long unitFe = machine.getFePerAura();
            helper.assertTrue(unitFe > 125D,
                    "Energy upgrades bypassed the live native Aura-to-FE conversion floor");
            long payment = energyCost(machine, 7);
            machine.getNativeEnergyContainer().setEnergy(payment);
            helper.assertTrue(machine.generateAura(helper.getLevel()) == 7
                            && machine.getNativeEnergyContainer().getEnergy() == 0
                            && machine.getStoredAura() == 7,
                    "Loop-safe price was displayed but not charged for actual generation");
        } finally {
            setting.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fullAndNearlyFullBuffersChargeOnlyCommittedAura(
            GameTestHelper helper) {
        FeAuraConverterBlockEntity machine = place(helper, MACHINE);
        long initialEnergy = energyCost(machine, machine.getGenerationLimit()) * 2L;
        machine.getNativeEnergyContainer().setEnergy(initialEnergy);
        helper.assertTrue(machine.storeAuraExact(machine.getAuraCapacity(), false),
                "Could not fill the converter's Aura buffer");

        helper.assertTrue(machine.generateAura(helper.getLevel()) == 0
                        && machine.getStoredAura() == machine.getAuraCapacity()
                        && machine.getNativeEnergyContainer().getEnergy() == initialEnergy,
                "A full Aura buffer consumed FE or overflowed");

        int room = Math.min(7, machine.getGenerationLimit() - 1);
        helper.assertTrue(room > 0 && machine.drainAuraExact(room, false),
                "Could not create a partially available Aura buffer");
        helper.assertTrue(machine.generateAura(helper.getLevel()) == room
                        && machine.getStoredAura() == machine.getAuraCapacity()
                        && machine.getNativeEnergyContainer().getEnergy()
                        == initialEnergy - energyCost(machine, room),
                "Partial capacity charged FE for Aura that did not fit");
        long afterFill = machine.getNativeEnergyContainer().getEnergy();
        helper.assertTrue(machine.generateAura(helper.getLevel()) == 0
                        && machine.getNativeEnergyContainer().getEnergy() == afterFill,
                "Retrying generation against a full buffer consumed FE");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void partialFundingPreservesFractionalAuraEnergy(
            GameTestHelper helper) {
        FeAuraConverterBlockEntity machine = place(helper, MACHINE);
        long cost = energyCost(machine, 1);
        int affordable = Math.min(11, machine.getGenerationLimit() - 1);
        helper.assertTrue(cost > 0 && affordable > 0,
                "The converter has no usable FE cost or generation rate");
        long initialEnergy = energyCost(machine, affordable + 1) - 1;
        long remainder = initialEnergy - energyCost(machine, affordable);
        machine.getNativeEnergyContainer().setEnergy(initialEnergy);

        helper.assertTrue(machine.generateAura(helper.getLevel()) == affordable
                        && machine.getStoredAura() == affordable
                        && machine.getNativeEnergyContainer().getEnergy() == remainder,
                "Partial funding stopped generation or spent the fractional FE remainder");
        helper.assertTrue(machine.generateAura(helper.getLevel()) == 0
                        && machine.getStoredAura() == affordable
                        && machine.getNativeEnergyContainer().getEnergy() == remainder,
                "Less than one Aura's FE generated Aura or lost energy");

        machine.getNativeEnergyContainer().setEnergy(cost);
        helper.assertTrue(machine.generateAura(helper.getLevel()) == 1
                        && machine.getStoredAura() == affordable + 1
                        && machine.getNativeEnergyContainer().getEnergy() == 0,
                "Exactly one Aura's FE failed to produce exactly one Aura");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void redstonePausePreservesEnergyAndResumesGeneration(
            GameTestHelper helper) {
        for (Direction side : Direction.values()) {
            helper.setBlock(MACHINE.relative(side), Blocks.AIR);
        }
        FeAuraConverterBlockEntity machine = place(helper, MACHINE);
        setAllSides(machine, NatureAuraMachineBlockEntity.AuraSideMode.NONE);
        long energy = energyCost(machine, machine.getGenerationLimit());
        machine.getNativeEnergyContainer().setEnergy(energy);
        machine.setControlType(RedstoneControl.HIGH);
        machine.onUpdateServer();
        helper.assertTrue(machine.getLastGeneratedAura() == 0
                        && machine.getStoredAura() == 0
                        && machine.getNativeEnergyContainer().getEnergy() == energy,
                "An unpowered HIGH redstone setting generated Aura or consumed FE");

        machine.setControlType(RedstoneControl.DISABLED);
        machine.onUpdateServer();
        helper.assertTrue(machine.getLastGeneratedAura() == machine.getGenerationLimit()
                        && machine.getStoredAura() == machine.getGenerationLimit()
                        && machine.getNativeEnergyContainer().getEnergy() == 0,
                "Re-enabling the converter did not resume its exact FE/Aura transaction");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void adjacentOutputAndExternalDrainShareOneTickBudget(
            GameTestHelper helper) {
        FeAuraConverterBlockEntity donor = place(helper, MACHINE);
        FeAuraConverterBlockEntity west = place(helper, MACHINE.west());
        FeAuraConverterBlockEntity east = place(helper, MACHINE.east());
        for (Direction side : Direction.values()) {
            helper.assertTrue(donor.getAuraSideMode(side)
                            == NatureAuraMachineBlockEntity.AuraSideMode.OUTPUT,
                    "A fresh converter did not default to Aura output on " + side);
        }
        helper.assertTrue(donor.getAuraContainer(Direction.UP).storeAura(1, false) == 0,
                "An output-only converter accepted externally inserted Aura");
        setAllSides(west, NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        setAllSides(east, NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        int roomPerSink = 24_000;
        int initialSinkAura = west.getAuraCapacity() - roomPerSink;
        helper.assertTrue(donor.storeAuraExact(OUTPUT_PER_TICK * 2, false)
                        && west.storeAuraExact(initialSinkAura, false)
                        && east.storeAuraExact(initialSinkAura, false),
                "Could not seed the donor and partially full adjacent sinks");

        var retained = donor.getAuraContainer(Direction.UP);
        helper.assertTrue(retained.drainAura(OUTPUT_PER_TICK, true) == OUTPUT_PER_TICK
                        && donor.getStoredAura() == OUTPUT_PER_TICK * 2,
                "Simulating output consumed Aura or changed the shared budget");
        int manualDrain = retained.drainAura(16_000, false);
        helper.assertTrue(manualDrain == 16_000,
                "Could not reserve part of the output budget with external extraction");
        donor.onUpdateServer();
        int received = west.getStoredAura() + east.getStoredAura() - initialSinkAura * 2;
        helper.assertTrue(west.getStoredAura() == west.getAuraCapacity()
                        && east.getStoredAura() == east.getAuraCapacity()
                        && received + manualDrain == OUTPUT_PER_TICK
                        && donor.getStoredAura() == OUTPUT_PER_TICK,
                "Adjacent ejection lost Aura or multiplied the output budget by side");
        helper.assertTrue(retained.drainAura(1, false) == 0
                        && donor.getAuraContainer(Direction.DOWN).drainAura(1, false) == 0,
                "Another side or retained capability bypassed the exhausted tick budget");
        helper.assertTrue(donor.getLastGeneratedAura() == 0
                        && donor.getNativeEnergyContainer().getEnergy() == 0,
                "Delivering stored Aura required FE or reported new generation");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void generatorFeedsNormalSharedPipeWithoutPullMode(
            GameTestHelper helper) {
        BlockPos donorPos = new BlockPos(1, 2, 3);
        BlockPos pipePos = new BlockPos(2, 2, 3);
        BlockPos sinkPos = new BlockPos(3, 2, 3);
        FeAuraConverterBlockEntity donor = place(helper, donorPos);
        FeAuraConverterBlockEntity sink = place(helper, sinkPos);
        setAllSides(donor, NatureAuraMachineBlockEntity.AuraSideMode.NONE);
        donor.setAuraSideMode(Direction.EAST, NatureAuraMachineBlockEntity.AuraSideMode.OUTPUT);
        setAllSides(sink, NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        helper.setBlock(pipePos, MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK.get());
        MagicSourcePipeBlockEntity pipeTile = helper.getBlockEntity(pipePos);
        MagicSourceTransmitter pipe = pipeTile.getTransmitter();
        pipe.setConnectionTypeRaw(Direction.EAST, ConnectionType.PUSH);
        pipe.onModeChange(Direction.EAST);
        helper.startSequence().thenExecuteAfter(5, () -> {
            helper.assertTrue(pipe.getConnectionType(Direction.WEST) == ConnectionType.NORMAL,
                    "The test's generator-side pipe connection is not NORMAL");
            helper.assertTrue(donor.storeAuraExact(10_000, false),
                    "Could not seed stored Aura for normal-pipe output");
        }).thenExecuteAfter(10, () -> {
            helper.assertTrue(sink.getStoredAura() == 10_000
                            && donor.getStoredAura() == 0,
                    "The converter did not push stored Aura into a NORMAL pipe connection");
            helper.assertTrue(donor.getNativeEnergyContainer().getEnergy() == 0
                            && sink.getNativeEnergyContainer().getEnergy() == 0,
                    "Stored Aura delivery needed converter generation energy");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void savedItemRestoresAuraEnergyAndSideConfiguration(
            GameTestHelper helper) {
        FeAuraConverterBlockEntity original = place(helper, new BlockPos(1, 2, 3));
        setAllSides(original, NatureAuraMachineBlockEntity.AuraSideMode.NONE);
        original.setAuraSideMode(Direction.UP, NatureAuraMachineBlockEntity.AuraSideMode.INPUT_OUTPUT);
        original.setAuraSideMode(Direction.EAST, NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        original.setControlType(RedstoneControl.HIGH);
        original.getNativeEnergyContainer().setEnergy(123_456L);
        helper.assertTrue(original.storeAuraExact(1_234_567, false),
                "Could not seed Aura before saving the converter item");
        ItemStack saved = new ItemStack(NaturesAuraRegistries.FE_AURA_CONVERTER_BLOCK.get());
        original.saveToItem(saved, helper.getLevel().registryAccess());
        ItemStack beforeRestore = saved.copy();

        BlockPos target = new BlockPos(4, 2, 3);
        FeAuraConverterBlockEntity restored = place(helper, target);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null,
                        helper.absolutePos(target), saved),
                "The converter item did not contain restorable Aura state");
        restored.applyComponentsFromItemStack(saved);
        helper.assertTrue(restored.getStoredAura() == 1_234_567
                        && restored.getNativeEnergyContainer().getEnergy() == 123_456L
                        && !restored.hasAuraTypeMismatch()
                        && restored.getControlType() == RedstoneControl.HIGH,
                "Break/place restoration changed saved Aura, FE, type or redstone mode");
        for (Direction side : Direction.values()) {
            helper.assertTrue(restored.getAuraSideMode(side) == original.getAuraSideMode(side),
                    "The converter item's Aura side configuration changed on " + side);
        }
        helper.assertTrue(ItemStack.matches(saved, beforeRestore),
                "Restoring the converter mutated the saved item");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void foreignSavedAuraPreventsGenerationAndOutput(
            GameTestHelper helper) {
        FeAuraConverterBlockEntity machine = place(helper, MACHINE);
        FeAuraConverterBlockEntity sink = place(helper, MACHINE.east());
        setAllSides(sink, NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        helper.assertTrue(machine.storeAuraExact(1_234, false),
                "Could not seed Aura before the foreign-type reload");
        machine.getNativeEnergyContainer().setEnergy(100_000L);
        IAuraType localType = IAuraType.forLevel(helper.getLevel());
        IAuraType foreign = NaturesAuraAPI.AURA_TYPES.values().stream()
                .filter(type -> !localType.isSimilar(type)).findFirst().orElseThrow();
        CompoundTag saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.putString("nature_aura_type", foreign.getName().toString());
        machine.loadAdditional(saved, helper.getLevel().registryAccess());
        long beforeEnergy = machine.getNativeEnergyContainer().getEnergy();
        machine.onUpdateServer();
        helper.assertTrue(machine.hasAuraTypeMismatch()
                        && machine.getStoredAura() == 1_234
                        && machine.getLastGeneratedAura() == 0
                        && machine.getNativeEnergyContainer().getEnergy() == beforeEnergy
                        && sink.getStoredAura() == 0,
                "A foreign saved Aura type was converted, exported or charged FE");
        helper.assertTrue(machine.getAuraContainer(Direction.UP).drainAura(1, false) == 0,
                "A foreign saved Aura type escaped through external extraction");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void speedAndEnergyUpgradesPreserveGenerationAccounting(
            GameTestHelper helper) {
        FeAuraConverterBlockEntity machine = place(helper, MACHINE);
        int baseRate = machine.getGenerationLimit();
        machine.getComponent().addUpgrades(Upgrade.SPEED, 8);
        int acceleratedRate = machine.getGenerationLimit();
        long acceleratedCost = machine.getFePerAura();
        helper.assertTrue(machine.getComponent().getUpgrades(Upgrade.SPEED) == 8
                        && acceleratedRate > baseRate
                        && acceleratedRate <= OUTPUT_PER_TICK,
                "Speed upgrades did not accelerate generation within the output limit");
        machine.getComponent().addUpgrades(Upgrade.ENERGY, 8);
        long efficientCost = machine.getFePerAura();
        helper.assertTrue(machine.getComponent().getUpgrades(Upgrade.ENERGY) == 8
                        && efficientCost > 0 && efficientCost < acceleratedCost
                        && machine.getGenerationLimit() == acceleratedRate,
                "Energy upgrades failed to reduce FE cost while preserving generation rate");

        long initialEnergy = energyCost(machine, acceleratedRate);
        helper.assertTrue(initialEnergy <= machine.getNativeEnergyContainer().getMaxEnergy(),
                "The upgraded converter cannot fund one full generation tick");
        machine.getNativeEnergyContainer().setEnergy(initialEnergy);
        helper.assertTrue(machine.generateAura(helper.getLevel()) == acceleratedRate
                        && machine.getStoredAura() == acceleratedRate
                        && machine.getNativeEnergyContainer().getEnergy() == 0,
                "Upgraded generation lost its exact FE/Aura accounting");
        helper.succeed();
    }

    private static FeAuraConverterBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, NaturesAuraRegistries.FE_AURA_CONVERTER_BLOCK.get());
        FeAuraConverterBlockEntity machine = helper.getBlockEntity(pos);
        machine.getNativeEnergyContainer().setEnergy(0);
        return machine;
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fuelSpeedUpgradesNeedNoEnergyContainerOrChargingSlot(
            GameTestHelper helper) {
        helper.setBlock(MACHINE,
                NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK.get());
        AuraFuelGeneratorBlockEntity machine = helper.getBlockEntity(MACHINE);
        helper.assertTrue(machine.getNativeEnergyContainer() == null
                        && machine.getNativeEnergySlot() == null
                        && machine.getConfig().getConfig(TransmissionType.ENERGY) == null
                        && machine.getConfig().getConfig(TransmissionType.ITEM)
                        .getSlotInfo(DataType.ENERGY) == null,
                "The fuel generator acquired an FE container, charging slot or ENERGY port");
        machine.getNativeInputSlot().setStack(new ItemStack(Items.TRIDENT));
        int baseGenerated = machine.generateAura(helper.getLevel());
        helper.assertTrue(baseGenerated == AuraFuelGeneratorBlockEntity.BASE_AURA_PER_TICK,
                "The fuel generator did not produce its base rate without FE");
        machine.getComponent().addUpgrades(Upgrade.SPEED, 8);
        machine.recalculateUpgrades(Upgrade.SPEED);
        int accelerated = machine.generateAura(helper.getLevel());
        helper.assertTrue(machine.getComponent().getUpgrades(Upgrade.SPEED) == 8
                        && accelerated > baseGenerated
                        && accelerated <= OUTPUT_PER_TICK
                        && machine.getStoredAura() == baseGenerated + accelerated
                        && machine.getNativeEnergyContainer() == null
                        && machine.getNativeEnergySlot() == null,
                "Speed upgrades failed or introduced FE storage on the fuel generator");
        helper.succeed();
    }

    /** FE is converted once per committed batch into Mekanism's stored Joules. */
    private static long energyCost(FeAuraConverterBlockEntity machine, int aura) {
        return (long) Math.ceil(aura * (double) machine.getFePerAura()
                * EnergyUnit.FORGE_ENERGY.getConversion());
    }

    private static void setAllSides(NatureAuraMachineBlockEntity machine,
                                    NatureAuraMachineBlockEntity.AuraSideMode mode) {
        for (Direction side : Direction.values()) {
            machine.setAuraSideMode(side, mode);
        }
    }
}
