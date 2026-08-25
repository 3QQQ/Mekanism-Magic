package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.FeSourceConverterBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class FeSourceConverterScreen
        extends ArsSourceMachineScreen<FeSourceConverterBlockEntity> {
    public FeSourceConverterScreen(
            MekanismTileContainer<FeSourceConverterBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 166);
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected int workProgressX() {
        return 78;
    }
}
