package com.example.mekanismmagic.integration.forbiddenarcanus;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

public final class HephaestusForgeProcessorContainer extends MekanismTileContainer<HephaestusForgeProcessorBlockEntity> {
    public HephaestusForgeProcessorContainer(int id, Inventory inventory, HephaestusForgeProcessorBlockEntity tile) {
        super(ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_CONTAINER, id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return com.example.mekanismmagic.container.MachinePanelLayout.inventoryY(
                com.example.mekanismmagic.container.MachinePanelLayout.FORGE_HEIGHT);
    }
}
