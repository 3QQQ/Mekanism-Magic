package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.container.MachinePanelLayout;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

/** One menu for all four non-consuming essence-sample lanes and recovery slots. */
public final class EssenceCondenserContainer
        extends MekanismTileContainer<EssenceCondenserBlockEntity> {
    public EssenceCondenserContainer(int id, Inventory inventory,
                                     EssenceCondenserBlockEntity tile) {
        super(ForbiddenArcanusRegistries.ESSENCE_CONDENSER_CONTAINER,
                id, inventory, tile);
    }

    @Override protected int getInventoryXOffset() {
        return MachinePanelLayout.inventoryX(MachinePanelLayout.ESSENCE_CONDENSER_WIDTH);
    }

    @Override protected int getInventoryYOffset() {
        return MachinePanelLayout.inventoryY(MachinePanelLayout.ESSENCE_CONDENSER_HEIGHT);
    }
}
