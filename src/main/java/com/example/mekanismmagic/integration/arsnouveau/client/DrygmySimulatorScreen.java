package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DrygmySimulatorScreen
        extends ArsSourceMachineScreen<DrygmySimulatorBlockEntity> {
    public DrygmySimulatorScreen(
            MekanismTileContainer<DrygmySimulatorBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = 210;
        inventoryLabelX = 26;
        inventoryLabelY = 114;
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected boolean showProgress() {
        return false;
    }
}
