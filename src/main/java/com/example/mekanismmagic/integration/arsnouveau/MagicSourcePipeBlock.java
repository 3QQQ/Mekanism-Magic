package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.common.block.transmitter.BlockLargeTransmitter;
import mekanism.common.content.blocktype.BlockTypeTile;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Native Mekanism large-transmitter block. All connection shapes, neighbor
 * updates, configurator hit selection and collision handling come directly
 * from BlockLargeTransmitter/BlockTransmitter.
 */
public final class MagicSourcePipeBlock
        extends BlockLargeTransmitter<MagicSourcePipeBlockEntity> {
    public MagicSourcePipeBlock(
            BlockTypeTile<MagicSourcePipeBlockEntity> type) {
        super(type, properties -> properties
                .requiresCorrectToolForDrops()
                .noOcclusion());
    }
}
