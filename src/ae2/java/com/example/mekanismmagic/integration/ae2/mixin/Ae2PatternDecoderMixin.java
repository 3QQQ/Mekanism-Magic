package com.example.mekanismmagic.integration.ae2.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEPatternDecoder;
import com.example.mekanismmagic.integration.ae2
        .Ae2IdentifierImbuementPattern;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEPatternDecoder.class, remap = false)
public abstract class Ae2PatternDecoderMixin {
    @Inject(method = "decodePattern", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$decodeIdentifierPattern(
            AEItemKey definition, Level level,
            CallbackInfoReturnable<IPatternDetails> cir) {
        Ae2IdentifierImbuementPattern pattern =
                Ae2IdentifierImbuementPattern.tryDecode(definition, level);
        if (pattern != null || Ae2IdentifierImbuementPattern
                .containsCatalystIdentifier(definition)) {
            // Returning null is intentional for an invalid/ambiguous
            // identifier pattern. It prevents AE's normal processing decoder
            // from treating the non-consumable identifier as a material.
            cir.setReturnValue(pattern);
        }
    }
}
