package com.example.mekanismmagic.block;

import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class NativeMachineBlock<TILE extends TileEntityMekanism>
        extends BlockTile<TILE, Machine<TILE>> {
    public NativeMachineBlock(Machine<TILE> type, BlockBehaviour.Properties properties) {
        // The detailed machine models do not fill every boundary face. Marking
        // them non-occluding keeps adjacent solid-block faces from being culled.
        super(type, properties.noOcclusion());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            RandomSource random) {
        // Machine work state is represented by model animation; world particles are disabled.
    }
}
