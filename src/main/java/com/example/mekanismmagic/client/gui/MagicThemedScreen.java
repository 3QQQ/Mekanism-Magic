package com.example.mekanismmagic.client.gui;

import mekanism.client.render.IFancyFontRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared machine title and text contract, using the addon's unified skin.
 */
public interface MagicThemedScreen extends IFancyFontRenderer {
    /** Shared by ordinary machines and all factory tiers; never replaces the server's/custom title. */
    default void drawMachineHeader(GuiGraphics graphics, Component title,
                                   MagicGuiTheme.MachineStatus status, int imageWidth) {
        drawScrollingString(graphics, title, MagicGuiTheme.MACHINE_TITLE_X, MagicGuiTheme.MACHINE_TITLE_Y,
                TextAlignment.LEFT, MagicGuiTheme.textPrimary(),
                MagicGuiTheme.availableTitleWidth(font(), status, imageWidth), 0, false);
        MagicGuiTheme.renderStatus(graphics, font(), status, imageWidth, MagicGuiTheme.MACHINE_TITLE_Y);
    }

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
