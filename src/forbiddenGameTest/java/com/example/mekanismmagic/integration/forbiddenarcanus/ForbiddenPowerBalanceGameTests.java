package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import mekanism.api.Upgrade;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForbiddenPowerBalanceGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeContainersUseTheSameProfileAndPreserveStoredEnergyAcrossReload(GameTestHelper helper) {
        record Sample(Block block, int joules) { }
        List<Sample> samples = List.of(
                new Sample(ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get(), MagicMachineEnergyProfiles.MATERIAL_PROCESSING),
                new Sample(ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get(), MagicMachineEnergyProfiles.ADVANCED_RITUAL),
                new Sample(ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get(), MagicMachineEnergyProfiles.MATERIAL_PROCESSING));
        for (Sample sample : samples) {
            helper.setBlock(POS, Blocks.AIR);
            helper.setBlock(POS, sample.block());
            NativeMagicMachineBlockEntity tile = helper.getBlockEntity(POS);
            var energy = tile.getNativeEnergyContainer();
            long capacity = energy.getBaseMaxEnergy();
            helper.assertTrue(energy.getBaseEnergyPerTick() == sample.joules() && energy.getEnergyPerTick() == sample.joules(),
                    "Registered nominal usage diverges from the fixed native reference profile");
            tile.getComponent().addUpgrades(Upgrade.SPEED, 8);
            tile.recalculateUpgrades(Upgrade.SPEED);
            helper.assertTrue(energy.getEnergyPerTick() == MekanismUtils.getEnergyPerTick(tile, sample.joules()),
                    "Speed applied a profile multiplier twice");
            tile.getComponent().addUpgrades(Upgrade.ENERGY, 8);
            tile.recalculateUpgrades(Upgrade.ENERGY);
            helper.assertTrue(energy.getEnergyPerTick() == MekanismUtils.getEnergyPerTick(tile, sample.joules())
                            && energy.getBaseMaxEnergy() == capacity, "Balance change altered base storage or upgrade semantics");
            energy.setEnergy(123_456);
            tile.loadAdditional(tile.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(energy.getEnergy() == 123_456 && energy.getBaseEnergyPerTick() == sample.joules(),
                    "Reload lost durable energy or reverted the base price");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void ordinaryClibanoWorkChargesTheSmelterProfileWithoutChangingNativeDuration(GameTestHelper helper) {
        var recipe = ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer()).clibanoRecipes().stream()
                .filter(view -> view.requiredFire() == FireType.FIRE && view.requiredEnhancer().isEmpty())
                .findFirst().orElseThrow();
        for (int[] upgrades : new int[][]{{0, 0}, {8, 0}, {8, 8}}) {
            helper.setBlock(POS, Blocks.AIR);
            helper.setBlock(POS, ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get());
            ClibanoProcessorBlockEntity tile = helper.getBlockEntity(POS);
            tile.setControlType(RedstoneControl.DISABLED);
            for (int index = 0; index < (recipe.doubleInput() ? 2 : 1); index++) {
                ItemStack ingredient = recipe.ingredients().get(index).examples().getFirst().copyWithCount(1);
                tile.getInputSlot(index).setStack(ingredient);
            }
            if (upgrades[0] > 0) tile.getComponent().addUpgrades(Upgrade.SPEED, upgrades[0]);
            if (upgrades[1] > 0) tile.getComponent().addUpgrades(Upgrade.ENERGY, upgrades[1]);
            tile.recalculateUpgrades(Upgrade.SPEED);
            tile.recalculateUpgrades(Upgrade.ENERGY);
            var energy = tile.getNativeEnergyContainer();
            energy.setEnergy(energy.getMaxEnergy());
            long before = energy.getEnergy();
            tile.onUpdateServer();
            long expected = MekanismUtils.getEnergyPerTick(tile, MagicMachineEnergyProfiles.MATERIAL_PROCESSING);
            helper.assertTrue(before - energy.getEnergy() == expected && energy.getEnergyPerTick() == expected,
                    "Actual Clibano processing did not charge the same smelter profile as its container");
            helper.assertTrue(tile.getProgressRequired() == MekanismUtils.getTicks(tile, recipe.cookingTimes().forFire(FireType.FIRE)),
                    "Power rebalance changed original Clibano timing");
        }
        helper.succeed();
    }
}
