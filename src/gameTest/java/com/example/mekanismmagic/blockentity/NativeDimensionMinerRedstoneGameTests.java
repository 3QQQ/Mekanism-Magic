package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.api.Upgrade;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Redstone pauses production while keeping paid item delivery and prepared rolls intact. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeDimensionMinerRedstoneGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private NativeDimensionMinerRedstoneGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void redstoneHighAndLowDoNotPrepareOrChargeANewMiningRoll(GameTestHelper helper) {
        var tile = place(helper);
        tile.getNativeInputSlot().setStack(miner(helper));
        tile.getNativeEnergyContainer().setEnergy(100_000);
        for (RedstoneControl mode : List.of(RedstoneControl.HIGH, RedstoneControl.LOW)) {
            signal(helper, tile, mode == RedstoneControl.LOW);
            tile.setControlType(mode);
            helper.assertTrue(!tile.canFunction(), "Test did not stop the miner's native redstone gate");
            long energy = tile.getNativeEnergyContainer().getEnergy();
            tick(tile, 8);
            helper.assertTrue(tile.developmentPendingOutputs().isEmpty() && tile.getProgress() == 0
                            && tile.getNativeEnergyContainer().getEnergy() == energy && !tile.getActive(),
                    "Stopped miner prepared a fresh random roll, advanced or consumed energy in " + mode);
        }
        helper.assertTrue(!tile.supportsMode(RedstoneControl.PULSE), "Unexpected pulse capability must have a dedicated acceptance test");
        tile.setControlType(RedstoneControl.PULSE);
        helper.assertTrue(tile.getControlType() == RedstoneControl.DISABLED,
                "Unsupported pulse mode no longer follows Mekanism's normal fallback");
        signal(helper, tile, false);
        tile.setControlType(RedstoneControl.HIGH);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void pausedMiningRollProgressAndFeeSurviveSaveAndResumeExactlyOnce(GameTestHelper helper) {
        for (RedstoneControl mode : List.of(RedstoneControl.HIGH, RedstoneControl.LOW)) {
            var tile = place(helper);
            helper.assertTrue(tile.seedDevelopmentTest(miner(helper)) > 0, "Could not prepare an actual weighted mining result");
            tile.getNativeEnergyContainer().setEnergy(100_000);
            signal(helper, tile, mode == RedstoneControl.LOW);
            tile.setControlType(mode);
            List<ItemStack> output = tile.developmentPendingOutputs();
            ItemStack input = tile.developmentPendingInput();
            int progress = tile.getProgress();
            int required = tile.getProgressRequired();
            int multiplier = tile.developmentPendingOperationMultiplier();
            long usage = tile.developmentPendingEnergyPerTick();
            long energy = tile.getNativeEnergyContainer().getEnergy();
            tick(tile, 8);
            assertFrozen(helper, tile, output, input, progress, required, multiplier, usage, energy);
            CompoundTag saved = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
            tile.loadAdditional(saved, helper.getLevel().registryAccess());
            tile.updatePower();
            helper.assertTrue(tile.getControlType() == mode && !tile.canFunction(), "Save/load lost the stopped redstone mode");
            tick(tile, 8);
            assertFrozen(helper, tile, output, input, progress, required, multiplier, usage, energy);
            signal(helper, tile, mode == RedstoneControl.HIGH);
            helper.assertTrue(tile.canFunction(), "Restoring native high/low signal conditions did not enable the miner");
            tick(tile, 1);
            helper.assertTrue(tile.developmentPendingOutputs().isEmpty()
                            && tile.getNativeEnergyContainer().getEnergy() == energy - usage,
                    "Resuming the almost-complete mining roll repeated preparation or charged more than one remaining tick");
            for (ItemStack stack : output) {
                long expected = output.stream().filter(candidate -> ItemStack.isSameItemSameComponents(candidate, stack))
                        .mapToLong(ItemStack::getCount).sum();
                helper.assertTrue(tile.developmentTotalOutput(stack) == expected,
                        "Resumed mining delivered a different weighted result or duplicate output");
            }
            signal(helper, tile, false);
            tile.setControlType(RedstoneControl.HIGH);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void stoppedMinerStillDrainsItsAlreadyPaidLongItemBuffer(GameTestHelper helper) {
        var tile = place(helper);
        tile.getNativeInputSlot().setStack(miner(helper));
        tile.seedBufferedOutputDevelopment(new ItemStack(Items.DIAMOND), 77);
        tile.getNativeEnergyContainer().setEnergy(100_000);
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 3);
        long visible = tile.mekanismMagicPatternOutputs().stream().mapToLong(slot -> slot.getCount()).sum();
        helper.assertTrue(visible > 0 && tile.developmentTotalOutput(new ItemStack(Items.DIAMOND)) == 77
                        && tile.developmentPendingOutputs().isEmpty() && tile.getNativeEnergyContainer().getEnergy() == 100_000,
                "Redstone either stopped paid-buffer delivery or enabled a new unpaid mining cycle");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void legacyPendingFeeRepricesWithoutRerollingOrResettingProgress(GameTestHelper helper) {
        var tile = place(helper);
        Upgrade stack = NativeMekanismRegistries.dimensionMinerStackUpgrade();
        helper.assertTrue(stack != null, "Pending-tariff migration requires the real stack upgrade");
        tile.getComponent().addUpgrades(stack, 2);
        tile.getComponent().addUpgrades(Upgrade.SPEED, 2);
        tile.getComponent().addUpgrades(Upgrade.ENERGY, 1);
        helper.assertTrue(tile.seedDevelopmentTest(miner(helper)) > 0, "Could not seed a legacy-priced mining batch");
        List<ItemStack> outputs = tile.developmentPendingOutputs();
        ItemStack input = tile.developmentPendingInput();
        int multiplier = tile.developmentPendingOperationMultiplier();
        helper.assertTrue(multiplier == 4, "Migration fixture did not prepare four operations");
        tile.getComponent().removeUpgrade(stack, true);
        int progress = tile.getProgress();
        int required = tile.getProgressRequired();
        long usage = Math.min(tile.getNativeEnergyContainer().getMaxEnergy(),
                MekanismUtils.getEnergyPerTick(tile, MagicMachineEnergyProfiles.RESOURCE_GATHERING) * multiplier);
        long oldUsage = MekanismUtils.getEnergyPerTick(tile, 800) * multiplier;
        helper.assertTrue(usage != oldUsage, "Legacy fixture does not distinguish old and new tariffs");
        signal(helper, tile, false);
        tile.setControlType(RedstoneControl.HIGH);
        tile.getNativeEnergyContainer().setEnergy(100_000);
        CompoundTag legacy = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
        legacy.putLong("miner_pending_energy_per_tick", oldUsage);
        tile.loadAdditional(legacy, helper.getLevel().registryAccess());
        tile.updatePower();
        tick(tile, 3);
        assertFrozen(helper, tile, outputs, input, progress, required, multiplier, usage, 100_000);
        helper.assertTrue(tile.getComponent().getUpgrades(stack) == 0,
                "Repricing restored removed stack upgrades instead of preserving only the prepared multiplier");
        CompoundTag repriced = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(repriced.getLong("miner_pending_energy_per_tick") == usage,
                "Saving the migrated miner retained its legacy tariff");
        tile.loadAdditional(repriced, helper.getLevel().registryAccess());
        tile.updatePower();
        tick(tile, 2);
        assertFrozen(helper, tile, outputs, input, progress, required, multiplier, usage, 100_000);
        signal(helper, tile, true);
        tick(tile, 1);
        helper.assertTrue(tile.developmentPendingOutputs().isEmpty()
                        && tile.getNativeEnergyContainer().getEnergy() == 100_000 - usage,
                "Resuming the migrated miner did not charge exactly one remaining tick at its new tariff");
        for (ItemStack output : outputs) {
            long expected = outputs.stream().filter(other -> ItemStack.isSameItemSameComponents(other, output))
                    .mapToLong(ItemStack::getCount).sum();
            helper.assertTrue(tile.developmentTotalOutput(output) == expected, "Tariff migration rerolled or duplicated mined output");
        }
        signal(helper, tile, false);
        helper.succeed();
    }

    private static NativeDimensionMinerBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.AIR);
        for (Direction side : Direction.values()) helper.setBlock(POS.relative(side), Blocks.AIR);
        helper.setBlock(POS, NativeMekanismRegistries.DIMENSION_MINER_BLOCK.get());
        NativeDimensionMinerBlockEntity tile = helper.getBlockEntity(POS);
        tile.updatePower();
        return tile;
    }

    private static ItemStack miner(GameTestHelper helper) {
        ItemStack miner = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(
                "occultism", "miner_foliot_unspecialized")).orElseThrow().getDefaultInstance();
        helper.assertTrue(OccultismRecipeBridge.isMinerItem(miner), "Native miner fixture is not valid");
        return miner;
    }

    private static void signal(GameTestHelper helper, NativeDimensionMinerBlockEntity tile, boolean powered) {
        helper.setBlock(POS.below(), powered ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
        tile.updatePower();
        helper.assertTrue(tile.isPowered() == powered, "Physical redstone block did not update native miner power");
    }

    private static void tick(NativeDimensionMinerBlockEntity tile, int ticks) {
        for (int tick = 0; tick < ticks; tick++) tile.onUpdateServer();
    }

    private static void assertFrozen(GameTestHelper helper, NativeDimensionMinerBlockEntity tile, List<ItemStack> output,
                                     ItemStack input, int progress, int required, int multiplier, long usage, long energy) {
        helper.assertTrue(ItemStack.listMatches(output, tile.developmentPendingOutputs())
                        && ItemStack.matches(input, tile.developmentPendingInput()) && tile.getProgress() == progress
                        && tile.getProgressRequired() == required && tile.developmentPendingOperationMultiplier() == multiplier
                        && tile.developmentPendingEnergyPerTick() == usage && tile.getNativeEnergyContainer().getEnergy() == energy
                        && !tile.getActive(),
                "Stopped mining mutated its prepared random result, context, progress, multiplier or energy payment");
    }
}
