package com.example.mekanismmagic.integration.mekextras;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

/** Theme-aware menu boundary for the four extended spirit factories. */
public final class ExtraSpiritFactoryContainer
        extends MekanismTileContainer<ExtraSpiritFactoryBlockEntity> {
    public ExtraSpiritFactoryContainer(
            int id, Inventory inventory, ExtraSpiritFactoryBlockEntity tile) {
        super(MekanismExtrasSpiritFactories.SPIRIT_FACTORY_CONTAINER,
                id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return 85;
    }

    @Override
    protected int getInventoryXOffset() {
        return ExtraSpiritFactoryLayout.inventoryX(tile.tier.ordinal());
    }
}
