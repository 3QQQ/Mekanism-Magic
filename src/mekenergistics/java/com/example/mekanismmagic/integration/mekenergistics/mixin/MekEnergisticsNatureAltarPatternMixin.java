package com.example.mekanismmagic.integration.mekenergistics.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.blockentity.support
        .MeSmartPatternMultiplication;
import com.example.mekanismmagic.integration.ae2.Ae2NatureAltarPattern;
import com.example.mekanismmagic.integration.naturesaura
        .NatureAltarAutomationHost;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends a Nature altar recipe context and its complete real-input batch
 * through the machine's rollback-safe transaction boundary.
 */
@Mixin(value = AbstractMeAeSupport.class, remap = false)
public abstract class MekEnergisticsNatureAltarPatternMixin {
    @Shadow @Final protected TileEntityMekanism ownerTile;
    @Shadow @Final
    protected MeSmartPatternMultiplication smartPatternMultiplication;

    @Inject(method = "pushPatternWithAdapter",
            at = @At(value = "INVOKE", target =
                    "Lcom/beipuo/mekenergistics/blockentity/support/"
                            + "AbstractMeAeSupport;"
                            + "dispatchWithSmartPatternFallback(ZZLcom/"
                            + "beipuo/mekenergistics/blockentity/support/"
                            + "MeSmartPatternMultiplication;Lappeng/api/"
                            + "crafting/IPatternDetails;[Lappeng/api/stacks/"
                            + "KeyCounter;Ljava/lang/Runnable;Ljava/util/"
                            + "function/BooleanSupplier;)Z",
                    shift = At.Shift.BEFORE), cancellable = true)
    private void mekanismMagic$pushNatureAltarPattern(
            IPatternDetails pattern, KeyCounter[] inputItems,
            CallbackInfoReturnable<Boolean> cir) {
        if (!(pattern instanceof Ae2NatureAltarPattern altar)
                || !(ownerTile instanceof NatureAltarAutomationHost host)) {
            return;
        }
        // Never interleave a direct context transaction with an older batch
        // that is still being replayed from MekE's persistent queue.
        if (smartPatternMultiplication.hasPendingWork()) {
            cir.setReturnValue(false);
            return;
        }
        ItemStack input = altar.collectInput(
                inputItems, ownerTile.getLevel());
        cir.setReturnValue(!input.isEmpty()
                && host.acceptNatureAltarPattern(
                altar.recipeId(), input));
    }

    @Inject(method = "isSmartPatternMultiplicationEnabled",
            at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$disableQueuedNatureAltarBatches(
            CallbackInfoReturnable<Boolean> cir) {
        if (ownerTile instanceof NatureAltarAutomationHost) {
            // The host already commits the entire multiplied KeyCounter batch
            // atomically, so a second persistent multiplication layer would
            // only weaken context ordering and rollback guarantees.
            cir.setReturnValue(false);
        }
    }
}
