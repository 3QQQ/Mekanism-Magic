package com.example.mekanismmagic.integration.mekenergistics;

import com.beipuo.mekenergistics.item.MeTierInstallerItem;
import com.beipuo.mekenergistics.upgrade.MeMekanismUpgrades;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeStateOwner;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.interfaces.IUpgradeTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** All MekE upgrades share the installer eligibility boundary. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MekEnergisticsUpgradeEligibilityGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private MekEnergisticsUpgradeEligibilityGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void unsupportedMachinesRejectEveryMeUpgradePath(
            GameTestHelper helper) {
        List<Block> unsupported = List.of(
                NativeMekanismRegistries.DIMENSION_MINER_BLOCK.get(),
                ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get(),
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get(),
                ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get());
        for (Block block : unsupported) {
            helper.setBlock(POS, Blocks.AIR);
            helper.setBlock(POS, block);
            TileEntityMekanism tile = helper.getBlockEntity(POS);
            helper.assertTrue(tile instanceof MeUpgradeRecipeMachineAdapter
                            && tile instanceof MeUpgradeStateOwner
                            && tile instanceof IUpgradeTile,
                    "Machine lost an upgrade adapter: " + block);
            var adapter = (MeUpgradeRecipeMachineAdapter) tile;
            var owner = (MeUpgradeStateOwner) tile;
            var component = ((IUpgradeTile) tile).getComponent();
            for (MeUpgradeType type : MeUpgradeType.values()) {
                var nativeUpgrade = MeMekanismUpgrades.forType(type);
                boolean eligible = MekEnergisticsUpgradeEligibility
                        .allows(tile, type);
                boolean supported = owner.supportsUpgrade(type);
                var container = adapter.getMeUpgradeContainer();
                var preflight = container.canInstall(type, 1);
                var committed = container.install(type);
                boolean installed = container.isInstalled(type);
                helper.assertTrue(!eligible && !supported
                                && !preflight.successful()
                                && !committed.successful() && !installed,
                        "Unsupported MekE " + type + " on " + block
                                + ": eligibility=" + eligible
                                + ", owner.supports=" + supported
                                + ", canInstall=" + preflight
                                + ", install=" + committed
                                + ", installed=" + installed);
                helper.assertTrue(!component.supports(nativeUpgrade)
                                && component.addUpgrades(nativeUpgrade, 1) == 0
                                && component.getUpgrades(nativeUpgrade) == 0,
                        "Native slot/direct write bypassed MekE eligibility: "
                                + block + "/" + type);
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            InteractionResult result = MeTierInstallerItem.tryInstall(
                    new ItemStack(Items.STICK), helper.getLevel(),
                    helper.absolutePos(POS), player);
            helper.assertTrue(result == InteractionResult.FAIL
                            && helper.getBlockState(POS).is(block),
                    "ME factory installer bypassed unsupported target gate: "
                            + block);
            helper.assertTrue(!tile.getInventorySlots(Direction.UP).isEmpty(),
                    "ME upgrade gate disabled ordinary top item I/O: " + block);
        }

        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, NativeMekanismRegistries.RITUAL_BLOCK.get());
        TileEntityMekanism supported = helper.getBlockEntity(POS);
        for (MeUpgradeType type : MeUpgradeType.values()) {
            helper.assertTrue(MekEnergisticsUpgradeEligibility
                            .allows(supported, type)
                            && ((MeUpgradeStateOwner) supported)
                            .supportsUpgrade(type),
                    "Supported ritual machine lost MekE upgrade eligibility: "
                            + type);
        }
        helper.succeed();
    }
}
