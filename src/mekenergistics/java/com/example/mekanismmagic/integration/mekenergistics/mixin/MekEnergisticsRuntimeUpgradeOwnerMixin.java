package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineRuntime;
import com.beipuo.mekenergistics.upgrade.MeUpgradeStateOwner;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.integration.mekenergistics.MekEnergisticsUpgradeEligibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The container's real owner is Runtime$1, not the tile adapter interface. */
@Mixin(targets = "com.beipuo.mekenergistics.upgrade."
        + "MeUpgradeRecipeMachineRuntime$1", remap = false)
public abstract class MekEnergisticsRuntimeUpgradeOwnerMixin
        implements MeUpgradeStateOwner {
    @Shadow @Final private MeUpgradeRecipeMachineRuntime this$0;

    @Override
    public boolean supportsUpgrade(MeUpgradeType type) {
        return MekEnergisticsUpgradeEligibility.allows(
                ((MekEnergisticsRuntimeTileAccessor) (Object) this$0)
                        .mekanismMagic$getUpgradeTile(), type);
    }
}
