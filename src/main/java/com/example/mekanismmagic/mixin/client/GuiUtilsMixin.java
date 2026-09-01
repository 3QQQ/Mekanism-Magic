package com.example.mekanismmagic.mixin.client;

import com.example.mekanismmagic.client.gui.MagicGuiTextures;
import mekanism.client.gui.GuiUtils;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = GuiUtils.class, remap = false)
abstract class GuiUtilsMixin {
    @ModifyVariable(method = "renderExtendedTexture", at = @At("HEAD"),
            argsOnly = true, ordinal = 0)
    private static ResourceLocation mekanismMagic$themeExtendedTexture(
            ResourceLocation original) {
        return MagicGuiTextures.resolve(original);
    }

    @ModifyVariable(method = "renderBackgroundTexture", at = @At("HEAD"),
            argsOnly = true, ordinal = 0)
    private static ResourceLocation mekanismMagic$themeBackgroundTexture(
            ResourceLocation original) {
        return MagicGuiTextures.resolve(original);
    }
}
