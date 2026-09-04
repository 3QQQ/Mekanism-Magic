package com.example.mekanismmagic.integration.mekenergistics.mixin;

import appeng.api.stacks.KeyCounter;
import com.beipuo.mekenergistics.blockentity.support.io.MeInputLayout;
import com.example.mekanismmagic.integration.mekenergistics
        .BoundedMePatternRouter;
import com.example.mekanismmagic.integration.mekenergistics
        .OccultismRitualMeRouter;
import com.example.mekanismmagic.integration.mekenergistics
        .SpiritPatternMeRouter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bounds already-persisted smart batches created by older addon builds. */
@Mixin(targets = "com.beipuo.mekenergistics.blockentity.support."
        + "AbstractMeAeSupport$1", remap = false)
public abstract class MekEnergisticsBoundedSmartFeederMixin {
    @Shadow
    @Final
    private MeInputLayout val$layout;

    @Inject(method = "feed", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$routeLargeUnorderedFeed(
            KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        if (SpiritPatternMeRouter.isSpiritLayout(val$layout)) {
            cir.setReturnValue(SpiritPatternMeRouter.refundPending(
                    val$layout, inputs));
        } else if (OccultismRitualMeRouter.isRitualLayout(val$layout)) {
            cir.setReturnValue(OccultismRitualMeRouter.routePending(
                    val$layout, inputs));
        } else if (mekanismMagic$requiresBoundedRouter()) {
            cir.setReturnValue(BoundedMePatternRouter.route(
                    inputs, val$layout.ports()));
        }
    }

    @Inject(method = "maxAcceptedCopies", at = @At("HEAD"),
            cancellable = true)
    private void mekanismMagic$boundLargeUnorderedCopies(
            KeyCounter[] inputs, CallbackInfoReturnable<Long> cir) {
        if (SpiritPatternMeRouter.isSpiritLayout(val$layout)) {
            // Feed exactly one legacy craft at a time so its full balance can
            // be returned through the network/recovery-buffer path.
            cir.setReturnValue(SpiritPatternMeRouter.owner(val$layout)
                    == null ? 0L : 1L);
        } else if (OccultismRitualMeRouter.isRitualLayout(val$layout)) {
            // Always allow one legacy copy to reach feed(). There its saved
            // definition is decoded and either role-routed or refunded. A
            // zero here would strand an invalid old batch forever.
            cir.setReturnValue(OccultismRitualMeRouter.owner(val$layout)
                    == null ? 0L : 1L);
        } else if (mekanismMagic$requiresBoundedRouter()) {
            cir.setReturnValue(BoundedMePatternRouter.maxAcceptedCopies(
                    inputs, val$layout.ports()));
        }
    }

    private boolean mekanismMagic$requiresBoundedRouter() {
        return BoundedMePatternRouter.isProtectedLayout(val$layout);
    }
}
