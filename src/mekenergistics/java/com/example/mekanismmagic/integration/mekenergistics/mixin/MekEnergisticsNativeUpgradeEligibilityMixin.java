package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MeMekanismUpgrades;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.integration.mekenergistics.MekEnergisticsUpgradeEligibility;
import mekanism.api.Upgrade;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentUpgrade;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also guard the native upgrade slot and direct TileComponentUpgrade writes. */
@Mixin(value = TileComponentUpgrade.class, remap = false)
public abstract class MekEnergisticsNativeUpgradeEligibilityMixin {
    @Shadow @Final private TileEntityMekanism tile;

    @Inject(method = "supports", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$hideUnsupportedMeUpgrade(
            Upgrade upgrade, CallbackInfoReturnable<Boolean> cir) {
        MeUpgradeType type = MeMekanismUpgrades.toType(upgrade);
        if (type != null
                && !MekEnergisticsUpgradeEligibility.allows(tile, type)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addUpgrades(Lmekanism/api/Upgrade;I)I",
            at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$rejectDirectMeUpgrade(
            Upgrade upgrade, int maxAvailable,
            CallbackInfoReturnable<Integer> cir) {
        MeUpgradeType type = MeMekanismUpgrades.toType(upgrade);
        if (type != null
                && !MekEnergisticsUpgradeEligibility.allows(tile, type)) {
            cir.setReturnValue(0);
        }
    }
}
