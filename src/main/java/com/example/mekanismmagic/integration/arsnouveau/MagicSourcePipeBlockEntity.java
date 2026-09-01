package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
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

    @NotNull
    @Override
    protected BlockState upgradeResult(@NotNull BlockState current,
                                       @NotNull BaseTier tier) {
        BlockRegistryObject<?, ?> target = switch (tier) {
            case BASIC -> ArsNouveauRegistries.MAGIC_SOURCE_PIPE_BLOCK;
            case ADVANCED ->
                    ArsNouveauRegistries.ADVANCED_MAGIC_SOURCE_PIPE_BLOCK;
            case ELITE -> ArsNouveauRegistries.ELITE_MAGIC_SOURCE_PIPE_BLOCK;
            case ULTIMATE ->
                    ArsNouveauRegistries.ULTIMATE_MAGIC_SOURCE_PIPE_BLOCK;
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
        invalidateCapability(
                com.hollingsworth.arsnouveau.setup.registry
                        .CapabilityRegistry.SOURCE_CAPABILITY,
                side);
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
        return getTransmitter().getTransferRate();
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
    public int addSource(int amount, boolean simulate) {
        ISourceCap storage = getSourceStorage();
        return storage == null ? 0
                : storage.receiveSource(amount, simulate);
    }

    @Override
    public int removeSource(int amount) {
        return setSource(getSource() - amount);
    }

    @Override
    public int removeSource(int amount, boolean simulate) {
        ISourceCap storage = getSourceStorage();
        return storage == null ? 0
                : storage.extractSource(amount, simulate);
    }
}
