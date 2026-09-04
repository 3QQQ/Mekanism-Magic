package com.example.mekanismmagic.integration.mekenergistics.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.blockentity.support.MeSmartPatternMultiplication;
import com.example.mekanismmagic.integration.ae2
        .Ae2IdentifierImbuementPattern;
import com.example.mekanismmagic.integration.arsnouveau
        .CatalystIdentifierSelectionHost;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

/** Selects the encoded catalyst context without routing its item to a slot. */
@Mixin(value = AbstractMeAeSupport.class, remap = false)
public abstract class MekEnergisticsImbuementPatternMixin {
    @Shadow @Final protected TileEntityMekanism ownerTile;
    @Unique private String mekanismMagic$pendingCatalystId;

    @Inject(method = "pushPatternWithAdapter", at = @At("HEAD"),
            cancellable = true)
    private void mekanismMagic$validateIdentifierContext(
            IPatternDetails pattern, KeyCounter[] inputItems,
            CallbackInfoReturnable<Boolean> cir) {
        mekanismMagic$pendingCatalystId = null;
        if (!(pattern instanceof Ae2IdentifierImbuementPattern imbuement)) {
            return;
        }
        if (!(ownerTile instanceof CatalystIdentifierSelectionHost host)
                || !imbuement.matchesCurrentRecipe(ownerTile.getLevel())) {
            cir.setReturnValue(false);
            return;
        }
        String catalystId = host.catalystIdentifierIdForRecipe(
                imbuement.recipeId());
        if (catalystId.isEmpty()
                || !host.canSelectCatalystIdentifierId(catalystId)) {
            cir.setReturnValue(false);
        } else {
            // Do not mutate the host yet. routePatternInputs may still reject
            // the job because the node is inactive, the machine is busy, or
            // its input lane cannot accept the complete stack.
            mekanismMagic$pendingCatalystId = catalystId;
        }
    }

    @Inject(method = "pushPatternWithAdapter", at = @At("RETURN"),
            cancellable = true)
    private void mekanismMagic$commitIdentifierContext(
            IPatternDetails pattern, KeyCounter[] inputItems,
            CallbackInfoReturnable<Boolean> cir) {
        String catalystId = mekanismMagic$pendingCatalystId;
        mekanismMagic$pendingCatalystId = null;
        if (catalystId == null || !Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        if (!(ownerTile instanceof CatalystIdentifierSelectionHost host)
                || !host.selectCatalystIdentifierId(catalystId)) {
            // Resolution and commit happen synchronously on the server thread,
            // so this can only fail after an exceptional host/catalog change.
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "dispatchWithSmartPatternFallback", at = @At("HEAD"),
            cancellable = true)
    private static void mekanismMagic$bypassQueuedContextSwitch(
            boolean exactPattern, boolean matchingDefinition,
            MeSmartPatternMultiplication multiplication,
            IPatternDetails pattern, KeyCounter[] inputItems,
            Runnable disableMultiplication, BooleanSupplier directDispatch,
            CallbackInfoReturnable<Boolean> cir) {
        if (pattern instanceof Ae2IdentifierImbuementPattern) {
            cir.setReturnValue(matchingDefinition
                    && directDispatch.getAsBoolean());
        }
    }
}
