package com.example.mekanismmagic.mixin.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.config.MagicClientConfig;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pauses this mod's atlas animations while the client animation option is off. */
@Mixin(SpriteContents.class)
abstract class SpriteContentsAnimationMixin {
    @Shadow
    @Final
    private ResourceLocation name;

    @Inject(method = "createTicker", at = @At("RETURN"), cancellable = true)
    private void mekanismMagic$wrapAnimatedTicker(
            CallbackInfoReturnable<SpriteTicker> callback) {
        SpriteTicker ticker = callback.getReturnValue();
        if (ticker != null
                && MekanismMagic.MOD_ID.equals(name.getNamespace())) {
            callback.setReturnValue(new ConfigurableTicker(ticker));
        }
    }

    private record ConfigurableTicker(SpriteTicker delegate)
            implements SpriteTicker {
        @Override
        public void tickAndUpload(int x, int y) {
            if (MagicClientConfig.animationsEnabled()) {
                delegate.tickAndUpload(x, y);
            }
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
