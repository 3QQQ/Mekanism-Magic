package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.SourceGeneratorBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SourceGeneratorScreen
        extends ArsSourceMachineScreen<SourceGeneratorBlockEntity> {
    public SourceGeneratorScreen(
            MekanismTileContainer<SourceGeneratorBlockEntity> container,
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
