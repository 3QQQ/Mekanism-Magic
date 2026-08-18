package com.example.mekanismmagic.block;

import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class NativeMachineBlock<TILE extends TileEntityMekanism>
        extends BlockTile<TILE, Machine<TILE>> {
    public NativeMachineBlock(Machine<TILE> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }
}
