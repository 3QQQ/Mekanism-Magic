package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.naturesaura.AuraSideModeHost;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.window.GuiWindowCreatorTab;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Mekanism side configuration tab whose native window also exposes Aura as
 * a transmission-style configuration page.
 */
public final class AuraSideConfigurationTab<TILE extends
        mekanism.common.tile.base.TileEntityMekanism
        & mekanism.common.tile.interfaces.ISideConfiguration
        & AuraSideModeHost>
        extends GuiWindowCreatorTab<TILE, AuraSideConfigurationTab<TILE>> {
    private static final ResourceLocation WINDOW_TEXTURE =
            MekanismUtils.getResource(
                    MekanismUtils.ResourceType.GUI, "configuration.png");
    private static final SelectedWindowData WINDOW_DATA =
            new SelectedWindowData(
                    SelectedWindowData.WindowType.SIDE_CONFIG);
    private final TILE tile;

    public AuraSideConfigurationTab(
            IGuiWrapper gui, TILE tile,
            Supplier<AuraSideConfigurationTab<TILE>> supplier) {
        super(WINDOW_TEXTURE, gui, tile, -26, 6, 26, 18, true, supplier);
        this.tile = tile;
        setTooltip(MekanismLang.SIDE_CONFIG);
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        MekanismRenderer.color(graphics, mekanism.client.SpecialColors.TAB_CONFIGURATION);
    }

    @Override
    protected mekanism.client.gui.element.window.GuiWindow createWindow(
            SelectedWindowData data) {
        return new AuraSideConfigurationWindow<>(
                gui(), (getGuiWidth()
                - AuraSideConfigurationWindow.WINDOW_WIDTH) / 2, 15,
                tile, data);
    }

    @Override
    protected SelectedWindowData getNextWindowData() {
        return WINDOW_DATA;
    }

}
