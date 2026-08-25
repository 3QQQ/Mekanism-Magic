package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import mekanism.common.block.states.TransmitterType;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Real Mekanism transmitter tile. Connection masks, configuration,
 * transmitter networks and lifecycle are provided by TileEntityTransmitter.
 */
public final class MagicSourcePipeBlockEntity extends TileEntityTransmitter
        implements ISourceTile {
    public MagicSourcePipeBlockEntity(
            Holder<Block> blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
    }

    @Override
    protected MagicSourceTransmitter createTransmitter(
            Holder<Block> blockProvider) {
        return new MagicSourceTransmitter(blockProvider, this);
    }

    @Override
    public MagicSourceTransmitter getTransmitter() {
        return (MagicSourceTransmitter) super.getTransmitter();
    }

    @Override
    protected void onUpdateServer() {
        getTransmitter().pullFromAcceptors();
        super.onUpdateServer();
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.MECHANICAL_PIPE;
    }

    @Override
    public CompoundTag getUpdateTag(
            HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (getTransmitter().hasTransmitterNetwork()) {
            MagicSourceNetwork network =
                    getTransmitter().getTransmitterNetwork();
            tag.putInt("magic_source",
                    network.getBuffer().getSource());
            tag.putFloat("scale", network.currentScale);
        } else {
            tag.putInt("magic_source",
                    getTransmitter().getShare().getSource());
            tag.putFloat("scale", 0);
        }
        return tag;
    }

    @Override
    public void sideChanged(
            Direction side,
            mekanism.common.lib.transmitter.ConnectionType old,
            mekanism.common.lib.transmitter.ConnectionType type) {
        super.sideChanged(side, old, type);
        if (type == mekanism.common.lib.transmitter.ConnectionType.NONE) {
            invalidateCapability(
                    com.hollingsworth.arsnouveau.setup.registry
                            .CapabilityRegistry.SOURCE_CAPABILITY,
                    side);
        } else if (old
                == mekanism.common.lib.transmitter.ConnectionType.NONE) {
            invalidateCapabilities();
        }
    }

    public @Nullable ISourceCap getSourceStorage(
            @Nullable Direction side) {
        return getTransmitter().getSourceCapability(side);
    }

    public ISourceCap getSourceStorage() {
        return getTransmitter().getSourceCapability(null);
    }

    @Override
    public int getTransferRate() {
        return getTransmitter().tier.getPipePullAmount();
    }

    @Override
    public boolean canAcceptSource() {
        return getSource() < getMaxSource();
    }

    @Override
    public boolean canProvideSource() {
        return getSource() > 0;
    }

    @Override
    public int getSource() {
        return getTransmitter().getBufferWithFallback().getSource();
    }

    @Override
    public int getMaxSource() {
        return getTransmitter().getSourceCapacity();
    }

    @Override
    public int setSource(int amount) {
        getTransmitter().getBufferWithFallback().setSource(amount);
        return getSource();
    }

    @Override
    public int addSource(int amount) {
        return setSource(getSource() + amount);
    }

    @Override
    public int removeSource(int amount) {
        return setSource(getSource() - amount);
    }
}
