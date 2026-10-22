package com.example.mekanismmagic.integration.naturesaura;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

public final class TreeRitualProcessorContainer extends MekanismTileContainer<TreeRitualProcessorBlockEntity> {
    public TreeRitualProcessorContainer(int id, Inventory inventory, TreeRitualProcessorBlockEntity tile) {
        super(NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_CONTAINER, id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return NatureAuraScreenLayout.TREE_INVENTORY_Y;
    }
}
