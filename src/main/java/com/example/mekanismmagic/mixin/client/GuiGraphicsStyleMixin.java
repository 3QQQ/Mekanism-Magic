package com.example.mekanismmagic.mixin.client;

import com.example.mekanismmagic.client.gui.MagicGuiStyle;
import com.example.mekanismmagic.client.gui.MagicGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Covers direct-blitted tab and slot glyphs that bypass GuiTexturedElement. */
@Mixin(GuiGraphics.class)
abstract class GuiGraphicsStyleMixin {
    @ModifyVariable(method = "innerBlit", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ResourceLocation mekanismMagic$packGlyph(ResourceLocation texture) {
        return MagicGuiStyle.isTerminal() ? MagicGuiTextures.resolve(texture) : texture;
    }
}
