package com.example.mekanismmagic.integration.common.energy;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import com.example.mekanismmagic.integration.forbiddenarcanus.ClibanoProcessorBlockEntity;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicLong;

/** Real machine attributes and containers, with a deterministic world-tick clock. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineEnergyMeterGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private MachineEnergyMeterGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void chargingAndSimulationDoNotHideOrInventProcessingUsage(GameTestHelper helper) {
        var tile = place(helper);
        AtomicLong clock = new AtomicLong(100);
        var meter = new MeteredMachineEnergyContainer<>(tile, tile, clock::get);
        var original = MachineEnergyContainer.input(tile, tile);
        long nominalRate = meter.getEnergyPerTick();
        helper.assertTrue(nominalRate == original.getEnergyPerTick()
                        && meter.getMaxEnergy() == original.getMaxEnergy(),
                "Meter changed the registered machine energy rate or capacity");
        meter.setEnergy(1_000);
        helper.assertTrue(meter.extract(90, Action.SIMULATE, AutomationType.INTERNAL) == 90
                        && meter.getEnergy() == 1_000,
                "Simulated recipe extraction changed stored energy");
        helper.assertTrue(meter.insert(300, Action.SIMULATE, AutomationType.EXTERNAL) == 0
                        && meter.getEnergy() == 1_000,
                "Simulated charging changed stored energy");
        helper.assertTrue(meter.extract(100, Action.EXECUTE, AutomationType.INTERNAL) == 100,
                "A real recipe extraction changed amount");
        meter.insert(300, Action.EXECUTE, AutomationType.EXTERNAL);
        meter.insert(50, Action.EXECUTE, AutomationType.MANUAL);
        meter.insert(25, Action.EXECUTE, AutomationType.INTERNAL);
        helper.assertTrue(meter.getEnergy() == 1_275 && meter.getLastProcessingUsage() == 0,
                "Same-tick charging was lost or an incomplete tick leaked into the meter");
        clock.incrementAndGet();
        helper.assertTrue(meter.getLastProcessingUsage() == 100,
                "Charging masked actual processing, or simulation was counted");
        meter.extract(40, Action.EXECUTE, AutomationType.INTERNAL);
        meter.extract(60, Action.EXECUTE, AutomationType.INTERNAL);
        helper.assertTrue(meter.getLastProcessingUsage() == 100,
                "Current processing replaced the previous completed tick");
        clock.incrementAndGet();
        helper.assertTrue(meter.getLastProcessingUsage() == 100 && meter.getEnergyPerTick() == nominalRate,
                "Multiple processing events failed to accumulate or modified the nominal billing rate");
        clock.incrementAndGet();
        helper.assertTrue(meter.getLastProcessingUsage() == 0, "Idle tick retained a stale usage sample");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void partialExtractionAndOriginalAutomationPermissionsArePreserved(GameTestHelper helper) {
        var tile = place(helper);
        AtomicLong clock = new AtomicLong(200);
        var meter = new MeteredMachineEnergyContainer<>(tile, tile, clock::get);
        meter.setEnergy(125);
        helper.assertTrue(meter.extract(500, Action.SIMULATE, AutomationType.INTERNAL) == 125
                        && meter.getEnergy() == 125,
                "Partial simulated extraction changed the normal container result");
        helper.assertTrue(meter.extract(60, Action.EXECUTE, AutomationType.EXTERNAL) == 0
                        && meter.getEnergy() == 125,
                "Input-only machine now allows external power extraction");
        helper.assertTrue(meter.extract(500, Action.EXECUTE, AutomationType.INTERNAL) == 125,
                "Partial processing did not return the actual extracted amount");
        helper.assertTrue(meter.extract(1, Action.EXECUTE, AutomationType.INTERNAL) == 0,
                "Empty extraction unexpectedly supplied energy");
        meter.insert(50, Action.EXECUTE, AutomationType.EXTERNAL);
        helper.assertTrue(meter.extract(70, Action.EXECUTE, AutomationType.INTERNAL) == 50,
                "Second partial extraction changed amount");
        meter.insert(30, Action.EXECUTE, AutomationType.MANUAL);
        helper.assertTrue(meter.extract(20, Action.EXECUTE, AutomationType.MANUAL) == 20,
                "Meter changed normal manual extraction permission");
        clock.incrementAndGet();
        helper.assertTrue(meter.getLastProcessingUsage() == 175 && meter.getEnergy() == 10,
                "Meter counted requested rather than extracted energy, or counted manual withdrawals");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void onlyAcceptedExplicitRefundsReduceProcessingUsage(GameTestHelper helper) {
        var tile = place(helper);
        AtomicLong clock = new AtomicLong(300);
        var meter = new MeteredMachineEnergyContainer<>(tile, tile, clock::get);
        meter.setEnergy(1_000);
        meter.extract(300, Action.EXECUTE, AutomationType.INTERNAL);
        meter.refundInternalEnergy(120);
        helper.assertTrue(meter.getEnergy() == 820, "Explicit rollback failed to restore its energy");
        clock.incrementAndGet();
        helper.assertTrue(meter.getLastProcessingUsage() == 180, "Rollback was reported as processing consumption");

        long capacity = meter.getMaxEnergy();
        meter.setEnergy(capacity);
        meter.extract(100, Action.EXECUTE, AutomationType.INTERNAL);
        meter.insert(95, Action.EXECUTE, AutomationType.EXTERNAL);
        meter.refundInternalEnergy(80);
        meter.refundInternalEnergy(80);
        meter.refundInternalEnergy(-1);
        helper.assertTrue(meter.getEnergy() == capacity, "Partial or rejected refunds changed the capacity contract");
        clock.incrementAndGet();
        helper.assertTrue(meter.getLastProcessingUsage() == 95,
                "Refund accounting subtracted requested energy instead of the five joules actually accepted");
        meter.extract(40, Action.EXECUTE, AutomationType.INTERNAL);
        meter.refundInternalEnergy(40);
        clock.incrementAndGet();
        helper.assertTrue(meter.getLastProcessingUsage() == 0, "Fully rolled-back work retained a nonzero usage sample");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void legacyEnergyNbtDoesNotPersistOrRestoreInstantaneousUsage(GameTestHelper helper) {
        var tile = place(helper);
        var registries = helper.getLevel().registryAccess();
        var original = MachineEnergyContainer.input(tile, tile);
        original.setEnergy(1_234);
        CompoundTag legacy = original.serializeNBT(registries);
        AtomicLong clock = new AtomicLong(400);
        var meter = new MeteredMachineEnergyContainer<>(tile, tile, clock::get);
        meter.deserializeNBT(registries, legacy.copy());
        helper.assertTrue(meter.getEnergy() == 1_234 && meter.getLastProcessingUsage() == 0,
                "Loading legacy energy data lost stored energy or invented usage history");
        meter.extract(234, Action.EXECUTE, AutomationType.INTERNAL);
        clock.incrementAndGet();
        helper.assertTrue(meter.getLastProcessingUsage() == 234, "The loaded legacy container did not meter work");
        original.setEnergy(1_000);
        CompoundTag saved = meter.serializeNBT(registries);
        helper.assertTrue(saved.equals(original.serializeNBT(registries)),
                "Instantaneous usage or sample ticks leaked into saved energy NBT");
        var restored = new MeteredMachineEnergyContainer<>(tile, tile, clock::get);
        restored.deserializeNBT(registries, saved.copy());
        helper.assertTrue(restored.getEnergy() == 1_000 && restored.getLastProcessingUsage() == 0,
                "Freshly restored meter inherited another container's processing history");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void reloadingActualMachineClearsCompletedAndPendingUsage(GameTestHelper helper) {
        var tile = place(helper);
        // Let the newly placed block finish its onLoad lifecycle before
        // creating usage that the subsequent NBT reload must clear.
        helper.runAfterDelay(1, () -> {
            var meter = (MeteredMachineEnergyContainer<?>) tile.getNativeEnergyContainer();
            var registries = helper.getLevel().registryAccess();
            meter.setEnergy(1_000);
            CompoundTag saved = tile.saveWithoutMetadata(registries);
            meter.extract(125, Action.EXECUTE, AutomationType.INTERNAL);
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(meter.getLastProcessingUsage() == 125,
                        "Actual machine did not expose the previous world's complete processing tick");
                meter.extract(75, Action.EXECUTE, AutomationType.INTERNAL);
                tile.loadAdditional(saved, registries);
                helper.assertTrue(meter.getLastProcessingUsage() == 0,
                        "Reloading a live machine retained completed usage history");
                helper.runAfterDelay(1, () -> {
                    helper.assertTrue(meter.getEnergy() == 1_000 && meter.getLastProcessingUsage() == 0,
                            "Machine reload lost durable energy or retained pending instantaneous usage");
                    helper.succeed();
                });
            });
        });
    }

    private static ClibanoProcessorBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get());
        ClibanoProcessorBlockEntity tile = helper.getBlockEntity(POS);
        helper.assertTrue(tile.getNativeEnergyContainer() instanceof MeteredMachineEnergyContainer<?>,
                "Actual native machine was not wired to the processing meter");
        for (int slot = 0; slot < 2; slot++) {
            helper.assertTrue(tile.getInputSlot(slot).isEmpty(), "Meter fixture unexpectedly contains recipe material");
        }
        return tile;
    }
}
