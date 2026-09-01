package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.config.MagicClientConfig;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiInsetToggleElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** In-screen switch for the persistent dark and light GUI schemes. */
public final class GuiThemeToggleTab<TILE extends TileEntityMekanism>
        extends GuiInsetToggleElement<TILE> {
    private static final ResourceLocation DARK_ICON =
            ResourceLocation.withDefaultNamespace(
                    "textures/item/echo_shard.png");
    private static final ResourceLocation LIGHT_ICON =
            ResourceLocation.withDefaultNamespace(
                    "textures/item/glowstone_dust.png");

    public GuiThemeToggleTab(IGuiWrapper gui, TILE tile) {
        super(gui, tile, -26, MagicLeftControlLayout.themeY(gui),
                26, 18, true,
                DARK_ICON, LIGHT_ICON, MagicClientConfig::isLight);
        updateThemeTooltip();
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        MekanismRenderer.color(graphics, MagicGuiTheme.isLight()
                ? MagicGuiTheme.accentSource()
                : MagicGuiTheme.accentRitual());
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        MagicClientConfig.toggleTheme();
        updateThemeTooltip();
    }

    private void updateThemeTooltip() {
        Component name = Component.translatable(MagicGuiTheme.isLight()
                ? "gui.mekanism_magic.theme.light"
                : "gui.mekanism_magic.theme.dark");
        setTooltip(Tooltip.create(Component.translatable(
                "gui.mekanism_magic.theme_toggle", name)));
    }
}
