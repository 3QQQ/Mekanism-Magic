package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.network.PipeSourceStorage;
import com.example.mekanismmagic.integration.common.network.MagicPipeRegistries;
import mekanism.common.block.states.TransmitterType;
import mekanism.api.tier.BaseTier;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Real Mekanism transmitter tile. Connection masks, configuration,
 * transmitter networks and lifecycle are provided by TileEntityTransmitter.
 */
public class MagicSourcePipeBlockEntity extends TileEntityTransmitter {
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
        getTransmitter().extension().tickServer();
        super.onUpdateServer();
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.MECHANICAL_PIPE;
    }

    @NotNull
    @Override
    protected BlockState upgradeResult(@NotNull BlockState current,
                                       @NotNull BaseTier tier) {
        BlockRegistryObject<?, ?> target = switch (tier) {
            case BASIC -> MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK;
            case ADVANCED ->
                    MagicPipeRegistries.ADVANCED_MAGIC_SOURCE_PIPE_BLOCK;
            case ELITE -> MagicPipeRegistries.ELITE_MAGIC_SOURCE_PIPE_BLOCK;
            case ULTIMATE ->
                    MagicPipeRegistries.ULTIMATE_MAGIC_SOURCE_PIPE_BLOCK;
            default -> null;
        };
        return target == null ? current
                : BlockStateHelper.copyStateData(current, target);
    }

    @Override
    public CompoundTag getUpdateTag(
            HolderLookup.Provider provider) {
        return addSourceRenderData(super.getUpdateTag(provider));
    }

    @NotNull
    @Override
    public CompoundTag getReducedUpdateTag(
            @NotNull HolderLookup.Provider provider) {
        // TileEntityTransmitter.sendUpdatePacket() uses this reduced tag for
        // network rebuilds. Supplying the scale here is what lets a newly
        // split client network render its inherited Source immediately.
        return addSourceRenderData(super.getReducedUpdateTag(provider));
    }

    private CompoundTag addSourceRenderData(CompoundTag tag) {
        if (getTransmitter().hasTransmitterNetwork()) {
            MagicSourceNetwork network =
                    getTransmitter().getTransmitterNetwork();
            tag.putInt("magic_source",
                    network.getBuffer().getSource());
            tag.putFloat("scale", network.currentScale);
        } else {
            int stored = getTransmitter().getShare().getSource();
            int capacity = getTransmitter().getShare()
                    .getSourceCapacity();
            tag.putInt("magic_source", stored);
            tag.putFloat("scale", capacity <= 0
                    ? 0 : stored / (float) capacity);
        }
        getTransmitter().extension().writeUpdateTag(tag);
        return tag;
    }

    @Override
    public void sideChanged(
            Direction side,
            mekanism.common.lib.transmitter.ConnectionType old,
            mekanism.common.lib.transmitter.ConnectionType type) {
        super.sideChanged(side, old, type);
        // Every connection mode carries different Source permissions. A
        // cached NORMAL/PUSH/PULL sided view is immutable, so invalidating
        // only NONE transitions leaves third-party machines with stale
        // receive/extract rights after a configurator change.
        invalidateCapabilities();
        getTransmitter().extension().sideChanged(side);
    }

    @Override
    public void redstoneChanged(boolean powered) {
        super.redstoneChanged(powered);
        invalidateCapabilities();
        getTransmitter().extension().redstoneChanged(powered);
    }

    public @Nullable PipeSourceStorage getSourceStorage(
            @Nullable Direction side) {
        return getTransmitter().getSourceCapability(side);
    }

    public PipeSourceStorage getSourceStorage() {
        return getTransmitter().getSourceCapability(null);
    }

    public int getTransferRate() {
        return getTransmitter().getTransferRate();
    }

    public boolean canAcceptSource() {
        PipeSourceStorage storage = getSourceStorage();
        return storage != null && storage.canAcceptSource(1);
    }

    public boolean canProvideSource() {
        PipeSourceStorage storage = getSourceStorage();
        return storage != null && storage.canProvideSource(1);
    }

    public int getSource() {
        return getTransmitter().getBufferWithFallback().getSource();
    }

    public int getMaxSource() {
        return getTransmitter().getSourceCapacity();
    }

    public int setSource(int amount) {
        if (getSourceStorage() != null) {
            getTransmitter().getBufferWithFallback().setSource(amount);
        }
        return getSource();
    }

    public int addSource(int amount) {
        PipeSourceStorage storage = getSourceStorage();
        if (storage != null) {
            storage.receiveSource(amount, false);
        }
        return getSource();
    }

    public int addSource(int amount, boolean simulate) {
        PipeSourceStorage storage = getSourceStorage();
        return storage == null ? 0
                : storage.receiveSource(amount, simulate);
    }

    public int removeSource(int amount) {
        PipeSourceStorage storage = getSourceStorage();
        if (storage != null) {
            storage.extractSource(amount, false);
        }
        return getSource();
    }

    public int removeSource(int amount, boolean simulate) {
        PipeSourceStorage storage = getSourceStorage();
        return storage == null ? 0
                : storage.extractSource(amount, simulate);
    }
}
