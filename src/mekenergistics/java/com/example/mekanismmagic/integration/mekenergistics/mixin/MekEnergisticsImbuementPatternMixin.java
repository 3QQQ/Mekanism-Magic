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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

/** Selects the encoded catalyst context without routing its item to a slot. */
@Mixin(value = AbstractMeAeSupport.class, remap = false)
public abstract class MekEnergisticsImbuementPatternMixin {
    @Shadow @Final protected TileEntityMekanism ownerTile;

    @Inject(method = "pushPatternWithAdapter", at = @At("HEAD"),
            cancellable = true)
    private void mekanismMagic$selectIdentifierContext(
            IPatternDetails pattern, KeyCounter[] inputItems,
            CallbackInfoReturnable<Boolean> cir) {
        if (pattern instanceof Ae2IdentifierImbuementPattern imbuement
                && ownerTile instanceof CatalystIdentifierSelectionHost host
                && !host.selectCatalystIdentifierForRecipe(
                imbuement.recipeId())) {
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
