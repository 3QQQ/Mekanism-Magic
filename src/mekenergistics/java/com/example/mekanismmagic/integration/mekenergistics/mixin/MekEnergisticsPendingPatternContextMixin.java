package com.example.mekanismmagic.integration.mekenergistics.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.example.mekanismmagic.integration.mekenergistics
        .MekEnergisticsPendingPatternContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exposes the definition of the legacy pending batch being fed. */
@Mixin(targets = "com.beipuo.mekenergistics.blockentity.support."
        + "MeSmartPatternMultiplication$PendingPattern", remap = false)
public abstract class MekEnergisticsPendingPatternContextMixin {
    @Shadow
    @Final
    private AEKey definition;

    @Inject(method = "toKeyCounters(J)[Lappeng/api/stacks/KeyCounter;",
            at = @At("HEAD"))
    private void mekanismMagic$capturePendingDefinition(
            long copies,
            CallbackInfoReturnable<KeyCounter[]> cir) {
        MekEnergisticsPendingPatternContext.capture(definition);
    }
}
