package com.example.mekanismmagic.integration.arsnouveau.client;

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

    @Override
    protected void addMachineGuiElements() {
        addRenderableWidget(new GuiSourceBar(this, getTileEntity(),
                sourceBarX(), 16, sourceBarHeight()));
        addArsMachineGuiElements();
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
