package com.example.mekanismmagic.integration.mekenergistics;

import com.beipuo.mekenergistics.api.upgrade.MePatternAutomation;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.registries.BuiltInRegistries;

/** One eligibility decision for all three in-machine Mek Energistics upgrades. */
public final class MekEnergisticsUpgradeEligibility {
    private MekEnergisticsUpgradeEligibility() {
    }

    public static boolean allows(TileEntityMekanism tile,
                                 MeUpgradeType upgrade) {
        if (tile == null || upgrade == null) {
            return false;
        }
        var id = BuiltInRegistries.BLOCK.getKey(
                tile.getBlockState().getBlock());
        if (id == null || !MekanismMagic.MOD_ID.equals(id.getNamespace())) {
            // Never narrow Mek Energistics' own or another mod's machines.
            return true;
        }
        return tile instanceof IMekanismMagicAutomation host
                && host.mekanismMagicSupportsPatternAutomation()
                && MePatternAutomation.registeredBlockIds().contains(id)
                && tile instanceof MeUpgradeRecipeMachineAdapter adapter
                && adapter.isMeUpgradeTarget();
    }
}
