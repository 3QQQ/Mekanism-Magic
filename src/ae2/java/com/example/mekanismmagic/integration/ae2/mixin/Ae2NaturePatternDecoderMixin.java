package com.example.mekanismmagic.integration.ae2.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEPatternDecoder;
import com.example.mekanismmagic.integration.ae2.Ae2NatureAltarPattern;
import com.example.mekanismmagic.integration.ae2.Ae2AuraBottlingProvider;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Decodes Nature altar context without loading the Ars integration. */
@Mixin(value = AEPatternDecoder.class, remap = false)
public abstract class Ae2NaturePatternDecoderMixin {
    @Inject(method = "decodePattern", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$decodeNatureAltarPattern(
            AEItemKey definition, Level level,
            CallbackInfoReturnable<IPatternDetails> cir) {
        if (Ae2AuraBottlingProvider.containsPatternMarker(definition)) {
            cir.setReturnValue(Ae2AuraBottlingProvider.tryDecode(
                    definition));
            return;
        }
        if (Ae2NatureAltarPattern.containsNatureAltarContext(definition)) {
            cir.setReturnValue(Ae2NatureAltarPattern.tryDecode(
                    definition, level));
        }
    }
}
