package com.example.mekanismmagic.integration.mekenergistics.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.blockentity.support
        .MeSmartPatternMultiplication;
import com.beipuo.mekenergistics.blockentity.support.io.MeInputLayout;
import com.example.mekanismmagic.blockentity
        .NativeMiniRitualAssemblerBlockEntity;
import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.mekenergistics
        .BoundedMePatternRouter;
import com.example.mekanismmagic.integration.mekenergistics
        .OccultismRitualMeRouter;
import com.example.mekanismmagic.integration.mekenergistics
        .MekEnergisticsPendingRefund;
import com.example.mekanismmagic.integration.mekenergistics
        .SpiritPatternMeRouter;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BooleanSupplier;
import java.util.List;

/**
 * Avoids Mek Energistics 3.0.6's permutation router for the two Occultism
 * machines with sixteen interchangeable material slots.
 */
@Mixin(value = AbstractMeAeSupport.class, remap = false)
public abstract class MekEnergisticsOccultismPatternMixin
        implements MekEnergisticsPendingRefund {
    @Shadow
    @Final
    protected TileEntityMekanism ownerTile;

    @Shadow
    @Final
    protected MeSmartPatternMultiplication smartPatternMultiplication;

    @Invoker("patternInputLayout")
    protected abstract MeInputLayout
    mekanismMagic$invokePatternInputLayout();

    @Invoker("refundToNetworkOrBuffer")
    protected abstract void mekanismMagic$invokePendingRefund(
            AEKey key, long amount);

    @Invoker("flushInterfaceRecovery")
    protected abstract boolean mekanismMagic$invokeFlushRecovery();

    @Shadow
    public abstract boolean hasInterfaceRecovery();

    @Shadow
    public abstract void alertAeTicker();

    @Override
    public void mekanismMagic$refundPending(AEKey key, long amount) {
        mekanismMagic$invokePendingRefund(key, amount);
    }

    @Shadow
    private static boolean dispatchWithSmartPatternFallback(
            boolean exactPattern, boolean matchingDefinition,
            MeSmartPatternMultiplication multiplication,
            IPatternDetails pattern, KeyCounter[] inputs,
            Runnable resetPending, BooleanSupplier directRoute) {
        throw new AssertionError();
    }

    @Redirect(method = "routePatternInputs",
            at = @At(value = "INVOKE", target =
                    "Lcom/beipuo/mekenergistics/blockentity/support/io/"
                            + "MeInputLayout;route([Lappeng/api/stacks/"
                            + "KeyCounter;)Z"))
    private boolean mekanismMagic$routeOccultismPattern(
            MeInputLayout layout, KeyCounter[] inputs) {
        if (ownerTile instanceof NativeRitualEngineBlockEntity ritual) {
            OccultismRitualMeRouter.protectLayout(layout, ritual,
                    (MekEnergisticsPendingRefund) (Object) this);
            return OccultismRitualMeRouter.route(
                    ritual, inputs, layout.ports());
        }
        if (ownerTile instanceof NativeMiniRitualAssemblerBlockEntity) {
            BoundedMePatternRouter.protectLayout(layout);
            return BoundedMePatternRouter.route(
                    inputs, layout.ports());
        }
        if (ownerTile instanceof IMekanismMagicAutomation host
                && SpiritPatternMeRouter.isSpiritHost(host)) {
            SpiritPatternMeRouter.protectLayout(layout, host,
                    (MekEnergisticsPendingRefund) (Object) this);
            return SpiritPatternMeRouter.route(inputs, layout.ports());
        }
        return layout.route(inputs);
    }

    @Redirect(method = "maxAcceptedCopies",
            at = @At(value = "INVOKE", target =
                    "Lcom/beipuo/mekenergistics/blockentity/support/io/"
                            + "MeInputLayout;maxAcceptedCopies([Lappeng/api/"
                            + "stacks/KeyCounter;)J"))
    private long mekanismMagic$boundedOccultismCopies(
            MeInputLayout layout, KeyCounter[] inputs) {
        if (ownerTile instanceof NativeRitualEngineBlockEntity ritual) {
            OccultismRitualMeRouter.protectLayout(layout, ritual,
                    (MekEnergisticsPendingRefund) (Object) this);
            return OccultismRitualMeRouter.maxAcceptedCopies(
                    ritual, inputs, layout.ports());
        }
        if (ownerTile instanceof NativeMiniRitualAssemblerBlockEntity) {
            BoundedMePatternRouter.protectLayout(layout);
            return BoundedMePatternRouter.maxAcceptedCopies(
                    inputs, layout.ports());
        }
        if (ownerTile instanceof IMekanismMagicAutomation host
                && SpiritPatternMeRouter.isSpiritHost(host)) {
            SpiritPatternMeRouter.protectLayout(layout, host,
                    (MekEnergisticsPendingRefund) (Object) this);
            return SpiritPatternMeRouter.maxAcceptedCopies(
                    inputs, layout.ports());
        }
        return layout.maxAcceptedCopies(inputs);
    }

    @Redirect(method = "pushPatternWithAdapter",
            at = @At(value = "INVOKE", target =
                    "Lcom/beipuo/mekenergistics/blockentity/support/"
                            + "AbstractMeAeSupport;"
                            + "dispatchWithSmartPatternFallback(ZZLcom/"
                            + "beipuo/mekenergistics/blockentity/support/"
                            + "MeSmartPatternMultiplication;Lappeng/api/"
                            + "crafting/IPatternDetails;[Lappeng/api/stacks/"
                            + "KeyCounter;Ljava/lang/Runnable;Ljava/util/"
                            + "function/BooleanSupplier;)Z"))
    private boolean mekanismMagic$dispatchOccultismDirectly(
            boolean exactPattern, boolean matchingDefinition,
            MeSmartPatternMultiplication multiplication,
            IPatternDetails pattern, KeyCounter[] inputs,
            Runnable resetPending, BooleanSupplier directRoute) {
        if (mekanismMagic$usesBoundedRouting()) {
            return matchingDefinition && directRoute.getAsBoolean();
        }
        return dispatchWithSmartPatternFallback(exactPattern,
                matchingDefinition, multiplication, pattern, inputs,
                resetPending, directRoute);
    }

    @Inject(method = "pushPatternWithAdapter",
            at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$rejectUnavailablePatternContext(
            IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> cir) {
        if (ownerTile instanceof IMekanismMagicAutomation host) {
            if (!host.mekanismMagicCanAdvertisePatterns()
                    || SpiritPatternMeRouter.isSpiritHost(host)
                    && !SpiritPatternMeRouter.matchesPattern(
                    host, pattern, inputs)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "getAvailablePatterns",
            at = @At("RETURN"), cancellable = true)
    private void mekanismMagic$hideUnavailablePatterns(
            CallbackInfoReturnable<List<IPatternDetails>> cir) {
        if (!(ownerTile instanceof IMekanismMagicAutomation host)) {
            return;
        }
        if (!host.mekanismMagicCanAdvertisePatterns()) {
            cir.setReturnValue(List.of());
        } else if (SpiritPatternMeRouter.isSpiritHost(host)) {
            List<IPatternDetails> available = cir.getReturnValue();
            cir.setReturnValue(available == null ? List.of()
                    : available.stream().filter(pattern ->
                    SpiritPatternMeRouter.matchesPattern(host, pattern))
                    .toList());
        }
    }

    /**
     * Context-free helper entry points are used by optional bridges such as
     * Data Energistics. They do not carry the pattern declaration, so a
     * SpiritJob machine cannot prove that the declared output still matches
     * its current bound spirit. Fail closed and keep the normal AE provider
     * path, which is validated above with the full pattern and actual inputs.
     */
    @Inject(method = "hasRegisteredPattern", at = @At("HEAD"),
            cancellable = true)
    private void mekanismMagic$rejectContextFreePatternLookup(
            IPatternDetails pattern,
            CallbackInfoReturnable<Boolean> cir) {
        if (mekanismMagic$isContextualSpiritHost()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "maxAcceptedCopies", at = @At("HEAD"),
            cancellable = true)
    private void mekanismMagic$rejectContextFreeCapacityProbe(
            KeyCounter[] inputs, CallbackInfoReturnable<Long> cir) {
        if (mekanismMagic$isContextualSpiritHost()) {
            cir.setReturnValue(0L);
        }
    }

    @Inject(method = "routeDataPatternInputs", at = @At("HEAD"),
            cancellable = true)
    private void mekanismMagic$rejectContextFreeDataRoute(
            KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        if (mekanismMagic$isContextualSpiritHost()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "enqueueSmartPattern", at = @At("HEAD"),
            cancellable = true)
    private void mekanismMagic$rejectPublicSmartQueue(
            IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> cir) {
        if (mekanismMagic$isContextualSpiritHost()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "processPassiveCrafting", at = @At("HEAD"),
            cancellable = true)
    private void mekanismMagic$rejectContextFreePassiveCrafting(
            boolean enabled, CallbackInfoReturnable<Boolean> cir) {
        if (mekanismMagic$isContextualSpiritHost()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "pushPatternWithAdapter",
            at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$waitForPersistedOccultismBatch(
            IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> cir) {
        if (ownerTile instanceof NativeRitualEngineBlockEntity ritual) {
            if (smartPatternMultiplication.hasPendingWork()
                    || !OccultismRitualMeRouter.matchesPattern(
                    ritual, pattern, inputs)) {
                cir.setReturnValue(false);
            }
        } else if (ownerTile
                instanceof NativeMiniRitualAssemblerBlockEntity
                && smartPatternMultiplication.hasPendingWork()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isSmartPatternMultiplicationEnabled",
            at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$reportOccultismSmartMultiplicationDisabled(
            CallbackInfoReturnable<Boolean> cir) {
        if (mekanismMagic$usesBoundedRouting()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "processSmartPatternViaAdapter",
            at = @At("HEAD"))
    private void mekanismMagic$protectPersistedOccultismBatches(
            CallbackInfoReturnable<Boolean> cir) {
        if (mekanismMagic$usesBoundedRouting()) {
            MeInputLayout layout =
                    mekanismMagic$invokePatternInputLayout();
            if (ownerTile
                    instanceof NativeRitualEngineBlockEntity ritual) {
                OccultismRitualMeRouter.protectLayout(layout, ritual,
                        (MekEnergisticsPendingRefund) (Object) this);
            } else if (ownerTile
                    instanceof NativeMiniRitualAssemblerBlockEntity) {
                BoundedMePatternRouter.protectLayout(layout);
            } else if (ownerTile
                    instanceof IMekanismMagicAutomation host
                    && SpiritPatternMeRouter.isSpiritHost(host)) {
                SpiritPatternMeRouter.protectLayout(layout, host,
                        (MekEnergisticsPendingRefund) (Object) this);
            } else {
                BoundedMePatternRouter.protectLayout(layout);
            }
        }
    }

    /**
     * Legacy smart queues can be refunded while the provider is in ordinary
     * pattern mode. MekE normally drains this recovery buffer only in
     * interface mode, so explicitly retry it here and keep the grid ticker
     * awake until every item has returned to the network.
     */
    @Inject(method = "processSmartPatternViaAdapter", at = @At("RETURN"),
            cancellable = true)
    private void mekanismMagic$flushSpiritRecoveryBuffer(
            CallbackInfoReturnable<Boolean> cir) {
        if (!mekanismMagic$isContextualSpiritHost()) {
            return;
        }
        boolean recovered = mekanismMagic$invokeFlushRecovery();
        if (hasInterfaceRecovery()) {
            alertAeTicker();
        }
        if (recovered) {
            cir.setReturnValue(true);
        }
    }

    private boolean mekanismMagic$usesBoundedRouting() {
        return ownerTile instanceof NativeRitualEngineBlockEntity
                || ownerTile instanceof NativeMiniRitualAssemblerBlockEntity
                || mekanismMagic$isContextualSpiritHost();
    }

    private boolean mekanismMagic$isContextualSpiritHost() {
        return ownerTile instanceof IMekanismMagicAutomation host
                && SpiritPatternMeRouter.isSpiritHost(host);
    }
}
