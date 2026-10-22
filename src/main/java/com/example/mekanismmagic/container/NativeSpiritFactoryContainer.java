package com.example.mekanismmagic.container;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.blockentity.NativeSpiritFactoryBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Factory-layout container using this addon's menu type instead of
 * Mekanism's built-in factory menu type.
 */
public final class NativeSpiritFactoryContainer
        extends MekanismTileContainer<NativeSpiritFactoryBlockEntity> {
    public NativeSpiritFactoryContainer(int id, Inventory inventory,
                                        NativeSpiritFactoryBlockEntity tile) {
        super(NativeMekanismRegistries.SPIRIT_FACTORY_CONTAINER, id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return MachinePanelLayout.inventoryY(MachinePanelLayout.FACTORY_HEIGHT);
    }

    @Override
    protected int getInventoryXOffset() {
        return MachinePanelLayout.inventoryX(getTileEntity().tier
                == mekanism.common.tier.FactoryTier.ULTIMATE ? 210 : 176);
    }
}
