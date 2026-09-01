package com.example.mekanismmagic.mixin.client;

import com.example.mekanismmagic.client.gui.MagicGuiTextures;
import mekanism.client.gui.element.GuiTexturedElement;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuiTexturedElement.class, remap = false)
abstract class GuiTexturedElementMixin {
    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void mekanismMagic$themeResource(
            CallbackInfoReturnable<ResourceLocation> callback) {
        callback.setReturnValue(MagicGuiTextures.resolve(
                callback.getReturnValue()));
    }
}
