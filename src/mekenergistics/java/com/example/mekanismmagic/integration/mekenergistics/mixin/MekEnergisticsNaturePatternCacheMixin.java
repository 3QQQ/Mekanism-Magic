package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.example.mekanismmagic.integration.mekenergistics
        .MekEnergisticsNaturePatternCacheRefresher;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers live server-side MekE providers after each pattern rebuild. */
@Mixin(value = AbstractMeAeSupport.class, remap = false)
public abstract class MekEnergisticsNaturePatternCacheMixin {
    @Shadow @Final protected TileEntityMekanism ownerTile;

    @Inject(method = "updatePatterns", at = @At("RETURN"))
    private void mekanismMagic$trackNaturePatternProvider(
            CallbackInfo ci) {
        var level = ownerTile.getLevel();
        if (level != null && !level.isClientSide()) {
            MekEnergisticsNaturePatternCacheRefresher.track(
                    (AbstractMeAeSupport<?>) (Object) this);
        }
    }
}
