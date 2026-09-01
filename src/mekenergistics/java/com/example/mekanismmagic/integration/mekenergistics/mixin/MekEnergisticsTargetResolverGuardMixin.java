package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.common.machine.MeMekanismMachine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Namespace-level safety boundary around Mek Energistics' generic target
 * resolver. The resolver intentionally guesses a replacement from registry
 * catalogs and factory recipe types; that is valid for known Mekanism
 * families but can never be valid for a Mekanism Magic block because our
 * machines retain their identity and install ME support in place.
 */
@Mixin(targets = "com.beipuo.mekenergistics.item.MeInstallerTargetResolver",
        remap = false)
public abstract class MekEnergisticsTargetResolverGuardMixin {
    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private static void mekanismMagic$rejectReplacementGuess(
            BlockState state,
            CallbackInfoReturnable<MeMekanismMachine> cir) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id != null && "mekanism_magic".equals(id.getNamespace())) {
            cir.setReturnValue(null);
        }
    }
}
