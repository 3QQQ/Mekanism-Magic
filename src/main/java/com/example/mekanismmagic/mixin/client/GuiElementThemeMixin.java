package com.example.mekanismmagic.mixin.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTextures;
import mekanism.client.SpecialColors;
import mekanism.client.gui.element.GuiElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes text inside inherited Mekanism tabs and windows match the skin. */
@Mixin(value = GuiElement.class, remap = false)
abstract class GuiElementThemeMixin {
    public int titleTextColor() {
        return MagicGuiTextures.isThemeActive()
                ? MagicGuiTheme.textPrimary()
                : SpecialColors.TEXT_TITLE.argb();
    }

    public int headingTextColor() {
        return MagicGuiTextures.isThemeActive()
                ? MagicGuiTheme.textPrimary()
                : SpecialColors.TEXT_HEADING.argb();
    }

    public int subheadingTextColor() {
        return MagicGuiTextures.isThemeActive()
                ? MagicGuiTheme.textMuted()
                : SpecialColors.TEXT_SUBHEADING.argb();
    }

    public int screenTextColor() {
        return MagicGuiTextures.isThemeActive()
                ? MagicGuiTheme.textScreen()
                : SpecialColors.TEXT_SCREEN.argb();
    }

    public int activeButtonTextColor() {
        return MagicGuiTextures.isThemeActive()
                ? MagicGuiTheme.activeButtonText()
                : SpecialColors.TEXT_ACTIVE_BUTTON.argb();
    }

    public int inactiveButtonTextColor() {
        return MagicGuiTextures.isThemeActive()
                ? MagicGuiTheme.inactiveButtonText()
                : SpecialColors.TEXT_INACTIVE_BUTTON.argb();
    }

    @Inject(method = "displayButtonTextShadow", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$flatButtonLabel(CallbackInfoReturnable<Boolean> callback) {
        if (com.example.mekanismmagic.client.gui.MagicGuiStyle.isTerminal()) callback.setReturnValue(false);
    }

    /** Keeps labels readable on the ice-blue hover surface. */
    @Inject(method = "getButtonTextColor", at = @At("RETURN"),
            cancellable = true)
    private void mekanismMagic$hoveredButtonText(
            int mouseX, int mouseY,
            CallbackInfoReturnable<Integer> callback) {
        GuiElement self = (GuiElement) (Object) this;
        if (MagicGuiTextures.isThemeActive() && self.active
                && callback.getReturnValue() == MagicGuiTheme.activeButtonText()
                && self.isMouseOverCheckWindows(mouseX, mouseY)) {
            callback.setReturnValue(MagicGuiTheme.hoveredButtonText());
        }
    }
}
