package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MeUpgradeConflictPolicy;
import com.beipuo.mekenergistics.upgrade.MeUpgradeContainer;
import com.beipuo.mekenergistics.upgrade.MeUpgradeStateOwner;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.integration.mekenergistics.MekEnergisticsUpgradeEligibility;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct API installs must obey the same target gate as the installer item. */
@Mixin(value = MeUpgradeContainer.class, remap = false)
public abstract class MekEnergisticsUpgradeContainerEligibilityMixin {
    @Shadow @Final private MeUpgradeStateOwner owner;

    @Inject(method = "canInstall", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$rejectUnsupportedUpgrade(
            MeUpgradeType type, int amount,
            CallbackInfoReturnable<MeUpgradeConflictPolicy.Result> cir) {
        // MekE recipe-machine runtimes pass their own inner StateOwner, not
        // the tile. That owner now delegates supportsUpgrade to the exact
        // machine eligibility policy; direct owners use the same policy here.
        if (owner instanceof TileEntityMekanism tile
                ? !MekEnergisticsUpgradeEligibility.allows(tile, type)
                : !owner.supportsUpgrade(type)) {
            cir.setReturnValue(MeUpgradeConflictPolicy.Result.unsupported());
        }
    }
}
