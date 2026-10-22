package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.container.MachinePanelLayout;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

public final class OfferingProcessorContainer extends MekanismTileContainer<OfferingProcessorBlockEntity> {
    public OfferingProcessorContainer(int id, Inventory inventory, OfferingProcessorBlockEntity tile) {
        super(NaturesAuraRegistries.OFFERING_PROCESSOR_CONTAINER, id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return MachinePanelLayout.inventoryY(MachinePanelLayout.OFFERING_HEIGHT);
    }
}
