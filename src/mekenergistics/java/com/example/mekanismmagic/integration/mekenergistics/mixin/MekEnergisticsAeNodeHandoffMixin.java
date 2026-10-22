package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineRuntime;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Handoff the fallback node before MekE creates its own, not one tick later. */
@Mixin(value = MeUpgradeRecipeMachineRuntime.class, remap = false)
public abstract class MekEnergisticsAeNodeHandoffMixin {
    @Shadow @Final private TileEntityMekanism tile;

    @Inject(method = "createNodeIfActive", at = @At("HEAD"))
    private void mekanismMagic$retireFallbackBeforeUpgradeNode(
            boolean active, CallbackInfo ci) {
        MachineAeResourceHooks.beforeExternalNodeCreate(tile, active);
    }

    @Inject(method = "destroyNode", at = @At("RETURN"))
    private void mekanismMagic$reviveFallbackAfterUpgradeNode(
            CallbackInfo ci) {
        MachineAeResourceHooks.afterExternalNodeDestroy(tile);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/beipuo/mekenergistics/blockentity/support/MeRecipeMachineAeSupport;create(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            shift = At.Shift.BEFORE))
    private void mekanismMagic$retireBeforeRuntimeTransition(
            boolean active, boolean changed,
            CallbackInfoReturnable<Boolean> cir) {
        MachineAeResourceHooks.beforeExternalNodeCreate(tile, true);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/beipuo/mekenergistics/blockentity/support/MeRecipeMachineAeSupport;destroyNode()V",
            shift = At.Shift.AFTER))
    private void mekanismMagic$reviveAfterRuntimeTransition(
            boolean active, boolean changed,
            CallbackInfoReturnable<Boolean> cir) {
        MachineAeResourceHooks.afterExternalNodeDestroy(tile);
    }
}
