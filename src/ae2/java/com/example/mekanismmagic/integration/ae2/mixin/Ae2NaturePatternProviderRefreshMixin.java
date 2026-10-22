package com.example.mekanismmagic.integration.ae2.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import com.example.mekanismmagic.integration.ae2
        .Ae2NaturePatternCacheRefresher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers live vanilla Pattern Providers after each successful rebuild. */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class Ae2NaturePatternProviderRefreshMixin {
    @Inject(method = "updatePatterns", at = @At("RETURN"))
    private void mekanismMagic$trackNaturePatternProvider(
            CallbackInfo ci) {
        Ae2NaturePatternCacheRefresher.track(
                (PatternProviderLogic) (Object) this);
    }
}
