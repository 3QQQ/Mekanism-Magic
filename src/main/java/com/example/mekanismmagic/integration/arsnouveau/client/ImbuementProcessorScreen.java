package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ImbuementProcessorScreen
        extends ArsSourceMachineScreen<ImbuementProcessorBlockEntity> {
    public ImbuementProcessorScreen(
            MekanismTileContainer<ImbuementProcessorBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 166);
    }

    @Override
    protected int workArrowX() {
        return 91;
    }

    @Override
    protected int workProgressX() {
        return 92;
    }
}
