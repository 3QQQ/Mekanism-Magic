package com.example.mekanismmagic.client.gui;

import mekanism.client.render.IFancyFontRenderer;

/**
 * Opts a screen into the complete Mekanism Magic GUI skin, including text
 * rendered directly by the screen rather than by a child {@code GuiElement}.
 */
public interface MagicThemedScreen extends IFancyFontRenderer {
    @Override
    default int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }

    @Override
    default int headingTextColor() {
        return MagicGuiTheme.textPrimary();
    }

    @Override
    default int subheadingTextColor() {
        return MagicGuiTheme.textMuted();
    }

    @Override
    default int screenTextColor() {
        return MagicGuiTheme.textScreen();
    }

    @Override
    default int activeButtonTextColor() {
        return MagicGuiTheme.activeButtonText();
    }

    @Override
    default int inactiveButtonTextColor() {
        return MagicGuiTheme.inactiveButtonText();
    }
}
