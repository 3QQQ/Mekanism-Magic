package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Preserves Ars relay/ISourceTile discovery without hard-linking shared tiles. */
public final class ArsMagicSourcePipeBlockEntity
        extends MagicSourcePipeBlockEntity implements ISourceTile {
    public ArsMagicSourcePipeBlockEntity(
            Holder<Block> block, BlockPos pos, BlockState state) {
        super(block, pos, state);
    }
}
