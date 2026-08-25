package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import mekanism.api.Action;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.content.network.transmitter.BufferedTransmitter;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.lib.transmitter.CompatibleTransmitterValidator;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.acceptor.AbstractAcceptorCache;
import mekanism.common.lib.transmitter.acceptor.AcceptorCache;
import mekanism.common.tier.PipeTier;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.EnumUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Mekanism BufferedTransmitter specialization whose buffer is Ars Source.
 */
public final class MagicSourceTransmitter extends BufferedTransmitter<
        ISourceCap, MagicSourceNetwork, SourceStorage,
        MagicSourceTransmitter> {
    public final PipeTier tier;
    private final SourceStorage buffer;
    private int saveShare;

    public MagicSourceTransmitter(
            Holder<Block> blockProvider,
            TileEntityTransmitter tile) {
        /*
         * Do not advertise one of Mekanism's built-in TransmissionType values
         * here.  Every built-in type is backed by a concrete network class
         * (for example FLUID -> FluidNetwork -> MechanicalPipe).  Advertising
         * FLUID would make the vanilla orphan path finder consider this
         * transmitter part of a FluidNetwork and the network would later cast
         * it to MechanicalPipe while committing a split/merge.
         *
         * The source pipe has its own network and its own compatibility
         * predicate below, so it intentionally has no built-in transmission
         * type.
         */
        super(tile);
        tier = Attribute.getTier(blockProvider, PipeTier.class);
        buffer = new SourceStorage(tier.getPipeCapacity(),
                tier.getPipePullAmount(), tier.getPipePullAmount()) {
            @Override
            public void onContentsChanged() {
                MagicSourceTransmitter.this.onContentsChanged();
            }
        };
    }

    @Override
    protected AbstractAcceptorCache<ISourceCap, ?> createAcceptorCache() {
        return new AcceptorCache<>(
                getTransmitterTile(),
                CapabilityRegistry.SOURCE_CAPABILITY);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AcceptorCache<ISourceCap> getAcceptorCache() {
        return (AcceptorCache<ISourceCap>) super.getAcceptorCache();
    }

    @Override
    protected void pullFromAcceptors() {
        if (!hasPullSide || getAvailablePull() <= 0) {
            return;
        }
        for (Direction side : EnumUtils.DIRECTIONS) {
            if (!isConnectionType(side, ConnectionType.PULL)) {
                continue;
            }
            ISourceCap acceptor = getAcceptor(side);
            if (acceptor == null) {
                continue;
            }
            int amount = Math.min(getAvailablePull(),
                    acceptor.extractSource(
                            getAvailablePull(), true));
            if (amount <= 0) {
                continue;
            }
            int accepted = takeSource(amount, Action.SIMULATE);
            if (accepted != amount) {
                continue;
            }
            int extracted = acceptor.extractSource(
                    amount, false);
            if (extracted > 0) {
                takeSource(extracted, Action.EXECUTE);
            }
        }
    }

    private int getAvailablePull() {
        if (hasTransmitterNetwork()) {
            return Math.min(tier.getPipePullAmount(),
                    getTransmitterNetwork().getNeeded());
        }
        return Math.min(tier.getPipePullAmount(),
                buffer.getSourceCapacity() - buffer.getSource());
    }

    public int takeSource(int amount, Action action) {
        if (amount <= 0) {
            return 0;
        }
        ISourceCap target = hasTransmitterNetwork()
                ? getTransmitterNetwork().getBuffer() : buffer;
        return target.receiveSource(amount, action.execute() ? false : true);
    }

    @Override
    public long getCapacity() {
        return tier.getPipeCapacity();
    }

    @Override
    public SourceStorage getBufferWithFallback() {
        if (buffer.getSource() == 0 && hasTransmitterNetwork()) {
            return getTransmitterNetwork().getBuffer();
        }
        return buffer;
    }

    @Override
    public boolean noBufferOrFallback() {
        return getBufferWithFallback().getSource() <= 0;
    }

    @Override
    public SourceStorage releaseShare() {
        SourceStorage share = new SourceStorage(
                (int) getCapacity(), tier.getPipePullAmount(),
                tier.getPipePullAmount());
        share.setSource(buffer.getSource());
        buffer.setSource(0);
        return share;
    }

    @Override
    public SourceStorage getShare() {
        return buffer;
    }

    @Override
    public void takeShare() {
        if (hasTransmitterNetwork() && saveShare > 0) {
            MagicSourceNetwork network = getTransmitterNetwork();
            int taken = network.getBuffer().extractSource(
                    saveShare, false);
            buffer.setSource(Math.min(
                    taken, buffer.getSourceCapacity()));
        }
    }

    public void setSaveShare(int amount) {
        saveShare = Math.max(0, amount);
    }

    public int getSource() {
        return getBufferWithFallback().getSource();
    }

    public int getSourceCapacity() {
        return hasTransmitterNetwork()
                ? getTransmitterNetwork().getBuffer()
                .getSourceCapacity() : buffer.getSourceCapacity();
    }

    public @Nullable ISourceCap getSourceCapability(
            @Nullable Direction side) {
        ISourceCap base = hasTransmitterNetwork()
                ? getTransmitterNetwork().getBuffer() : buffer;
        if (side == null) {
            return base;
        }
        return switch (getConnectionTypeRaw(side)) {
            case NONE -> null;
            case NORMAL -> base;
            case PUSH -> new SourceSidedView(base, false, true);
            case PULL -> new SourceSidedView(base, true, false);
        };
    }

    @Override
    public CompatibleTransmitterValidator<ISourceCap,
            MagicSourceNetwork, MagicSourceTransmitter>
    getNewOrphanValidator() {
        return new SourceTransmitterValidator();
    }

    /**
     * Mekanism's base implementation compares the advertised
     * {@code TransmissionType} sets.  The source pipe deliberately advertises
     * no built-in type, so use an exact transmitter-class check instead.  It
     * is important that this method is also used by
     * {@code isValidTransmitterBasic}; the orphan path finder calls that
     * method while rebuilding a network after a pipe is removed.
     */
    @Override
    public boolean supportsTransmissionType(
            Transmitter<?, ?, ?> transmitter) {
        return transmitter instanceof MagicSourceTransmitter;
    }

    @Override
    public boolean supportsTransmissionType(
            TileEntityTransmitter transmitter) {
        return transmitter.getTransmitter() instanceof MagicSourceTransmitter;
    }

    @Override
    public boolean isValidTransmitter(
            TileEntityTransmitter transmitter, Direction side) {
        return transmitter.getTransmitter() instanceof MagicSourceTransmitter
                && super.isValidTransmitter(transmitter, side);
    }

    @Override
    protected boolean isValidAcceptor(
            @Nullable net.minecraft.world.level.block.entity.BlockEntity tile,
            Direction side) {
        if (tile instanceof TileEntityTransmitter) {
            return false;
        }
        return getAcceptorCache().getConnectedAcceptor(side) != null;
    }

    @Override
    public MagicSourceNetwork createEmptyNetworkWithID(UUID networkId) {
        return new MagicSourceNetwork(networkId);
    }

    @Override
    public MagicSourceNetwork createNetworkByMerging(
            Collection<MagicSourceNetwork> networks) {
        return new MagicSourceNetwork(networks);
    }

    @Override
    public void read(
            HolderLookup.Provider provider, CompoundTag tag) {
        super.read(provider, tag);
        saveShare = tag.getInt("magic_source_save_share");
        if (saveShare == 0 && tag.contains("magic_source_buffer")) {
            saveShare = tag.getInt("magic_source_buffer");
        }
        buffer.setSource(saveShare);
    }

    @Override
    public CompoundTag write(
            HolderLookup.Provider provider, CompoundTag tag) {
        super.write(provider, tag);
        if (hasTransmitterNetwork()) {
            getTransmitterNetwork().validateSaveShares(this);
        } else {
            saveShare = buffer.getSource();
        }
        tag.remove("magic_source_buffer");
        tag.putInt("magic_source_save_share", saveShare);
        return tag;
    }

    public void onContentsChanged() {
        getTransmitterTile().setChanged();
        if (hasTransmitterNetwork()) {
            getTransmitterNetwork().markDirty();
        }
    }

    @Override
    protected boolean canHaveIncompatibleNetworks() {
        return true;
    }

    @Override
    protected void handleContentsUpdateTag(
            MagicSourceNetwork network, CompoundTag tag,
            HolderLookup.Provider provider) {
        super.handleContentsUpdateTag(network, tag, provider);
        if (tag.contains("magic_source")) {
            network.lastSource = tag.getInt("magic_source");
        }
        if (tag.contains("scale")) {
            network.currentScale = tag.getFloat("scale");
        }
    }

    private static final class SourceTransmitterValidator
            extends CompatibleTransmitterValidator<ISourceCap,
            MagicSourceNetwork, MagicSourceTransmitter> {
        @Override
        public boolean isTransmitterCompatible(
                mekanism.common.content.network.transmitter.Transmitter<
                        ?, ?, ?> transmitter) {
            return transmitter instanceof MagicSourceTransmitter;
        }
    }

    private static final class SourceSidedView implements ISourceCap {
        private final ISourceCap source;
        private final boolean receive;
        private final boolean extract;

        private SourceSidedView(
                ISourceCap source, boolean receive, boolean extract) {
            this.source = source;
            this.receive = receive;
            this.extract = extract;
        }

        @Override
        public boolean canAcceptSource(int amount) {
            return receive && source.canAcceptSource(amount);
        }

        @Override
        public boolean canProvideSource(int amount) {
            return extract && source.canProvideSource(amount);
        }

        @Override
        public int getMaxExtract() {
            return extract ? source.getMaxExtract() : 0;
        }

        @Override
        public int getMaxReceive() {
            return receive ? source.getMaxReceive() : 0;
        }

        @Override
        public int getSource() {
            return source.getSource();
        }

        @Override
        public int getSourceCapacity() {
            return source.getSourceCapacity();
        }

        @Override
        public void setSource(int amount) {
            source.setSource(amount);
        }

        @Override
        public void setMaxSource(int amount) {
            source.setMaxSource(amount);
        }

        @Override
        public int receiveSource(int amount, boolean simulate) {
            return receive ? source.receiveSource(amount, simulate) : 0;
        }

        @Override
        public int extractSource(int amount, boolean simulate) {
            return extract ? source.extractSource(amount, simulate) : 0;
        }
    }
}
