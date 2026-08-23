package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.SourceAmplifierBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SourceAmplifierScreen
        extends ArsSourceMachineScreen<SourceAmplifierBlockEntity> {
    public SourceAmplifierScreen(
            MekanismTileContainer<SourceAmplifierBlockEntity> container,
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
