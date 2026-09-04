package com.example.mekanismmagic.container;

import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

public final class NativeMiniRitualAssemblerContainer
        extends MekanismTileContainer<NativeMiniRitualAssemblerBlockEntity> {
    public NativeMiniRitualAssemblerContainer(
            int id, Inventory inventory,
            NativeMiniRitualAssemblerBlockEntity tile) {
        super(com.example.mekanismmagic.NativeMekanismRegistries
                        .MINI_RITUAL_ASSEMBLER_CONTAINER,
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
