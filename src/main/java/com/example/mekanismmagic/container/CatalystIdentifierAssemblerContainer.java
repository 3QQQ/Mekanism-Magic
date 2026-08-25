package com.example.mekanismmagic.container;

import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierAssemblerBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

public final class CatalystIdentifierAssemblerContainer
        extends MekanismTileContainer<CatalystIdentifierAssemblerBlockEntity> {
    public CatalystIdentifierAssemblerContainer(int id, Inventory inventory,
                                                CatalystIdentifierAssemblerBlockEntity tile) {
        super(com.example.mekanismmagic.integration.arsnouveau
                        .ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_CONTAINER,
                id, inventory, tile);
    }

    @Override
    protected int getInventoryXOffset() {
        return 25;
    }

    @Override
    protected int getInventoryYOffset() {
        return 126;
    }
}
