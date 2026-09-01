package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.gui.ArsIntegratedSideConfig;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceMachineBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared screen for Mekanism machines with an Ars Nouveau Source buffer.
 */
public abstract class ArsSourceMachineScreen<
        TILE extends ArsSourceMachineBlockEntity>
        extends NativeMagicMachineScreen<TILE, MekanismTileContainer<TILE>> {
    protected ArsSourceMachineScreen(
            MekanismTileContainer<TILE> container,
            Inventory inventory, Component title, int imageHeight) {
        super(container, inventory, title, imageHeight);
    }

    /**
     * Match Mekanism's normal electric-machine work area first. The Source
     * bar is added separately below so it never replaces the native energy
     * bar, progress texture, or energy information tab.
     */
    @Override
    protected void addWorkGuiElements() {
        addMekanismWorkGuiElements(true);
    }

    @Override
    protected void addMachineGuiElements() {
        if (showSourceBar()) {
            addRenderableWidget(new GuiSourceBar(this, getTileEntity(),
                    sourceBarX(), 16, sourceBarHeight()));
        }
        if (showSourceSideConfig()) {
            ArsIntegratedSideConfig.install(this, getTileEntity(), children(),
                    this::addRenderableWidget);
        }
        addArsMachineGuiElements();
    }

    protected boolean showSourceBar() {
        return true;
    }

    protected boolean showSourceSideConfig() {
        return true;
    }

    protected void addArsMachineGuiElements() {
    }

    protected int sourceBarX() {
        return imageWidth - 20;
    }

    protected int sourceBarHeight() {
        return 52;
    }
}
