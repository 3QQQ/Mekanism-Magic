package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.common.network
        .MagicSourceExternalEndpointHooks;
import com.example.mekanismmagic.integration.common.network.PipeSourceStorage;
import com.example.mekanismmagic.integration.common.network.BasicPipeSourceStorage;
import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import com.example.mekanismmagic.integration.common.network.SourceAcceptorHooks;
import mekanism.api.Action;
import mekanism.api.tier.BaseTier;
import mekanism.api.tier.ITier;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.content.network.transmitter.BufferedTransmitter;
import mekanism.common.content.network.transmitter.IUpgradeableTransmitter;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.lib.transmitter.CompatibleTransmitterValidator;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.acceptor.AbstractAcceptorCache;
import mekanism.common.tier.PipeTier;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.upgrade.transmitter.TransmitterUpgradeData;
import mekanism.common.util.EnumUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

/**
 * Mekanism BufferedTransmitter specialization whose buffer is Ars Source.
 */
public final class MagicSourceTransmitter extends BufferedTransmitter<
        PipeSourceStorage, MagicSourceNetwork, PipeSourceStorage,
        MagicSourceTransmitter> implements
        IUpgradeableTransmitter<MagicSourcePipeUpgradeData> {
    private static final ITier EXTRAS_SAFE_TERMINAL_TIER =
            () -> BaseTier.CREATIVE;
    public final PipeTier tier;
    public final MagicSourcePipeTier sourceTier;
    private final BasicPipeSourceStorage buffer;
    private final MagicPipeResourceHooks.PipeExtension extension;
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
        AttributeMagicSourcePipeTier sourceTierAttribute = Attribute.get(
                blockProvider, AttributeMagicSourcePipeTier.class);
        sourceTier = sourceTierAttribute == null
                ? MagicSourcePipeTier.valueOf(tier.name())
                : sourceTierAttribute.tier();
        // Mekanism's local pipe buffer is capacity-limited, not pump-rate
        // limited. The tier pull rate is applied only by pullFromAcceptors().
        buffer = new BasicPipeSourceStorage(getTierCapacity(),
                getTierCapacity(), getTierCapacity()) {
            @Override
            public void onContentsChanged() {
                MagicSourceTransmitter.this.onContentsChanged();
            }
        };
        extension = MagicPipeResourceHooks.createPipe(this);
    }

    @Override
    protected AbstractAcceptorCache<PipeSourceStorage, ?> createAcceptorCache() {
        return SourceAcceptorHooks.create(getTransmitterTile());
    }

    public MagicPipeResourceHooks.PipeExtension extension() {
        return extension;
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
            PipeSourceStorage acceptor = getAcceptor(side);
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
            return Math.min(getTransferRate(),
                    getTransmitterNetwork().getNeeded());
        }
        return Math.min(getTransferRate(),
                buffer.getSourceCapacity() - buffer.getSource());
    }

    public int takeSource(int amount, Action action) {
        if (amount <= 0) {
            return 0;
        }
        PipeSourceStorage target = hasTransmitterNetwork()
                ? getTransmitterNetwork().getBuffer() : buffer;
        return target.receiveSource(amount, action.execute() ? false : true);
    }

    @Override
    public long getCapacity() {
        return getTierCapacity();
    }

    public int getTierCapacity() {
        return MagicSourcePipeTierStats.capacity(sourceTier);
    }

    public int getTransferRate() {
        return MagicSourcePipeTierStats.pullRate(sourceTier);
    }

    @Override
    public ITier getTier() {
        /*
         * Mekanism Extras 1.4.0's radiance-alloy item hard-casts every
         * ultimate transmitter to one of Mekanism's five built-in tile
         * classes. A custom ultimate transmitter would otherwise reach its
         * null target path and can crash. Until Extras exposes a generic
         * Ultimate -> Absolute hook, report this pipe as terminal only to the
         * alloy API while retaining the real PipeTier for all Source stats.
         */
        if (sourceTier.isExtendedTier()
                || tier == PipeTier.ULTIMATE
                && ModCompatibility.mekanismExtrasLoaded()) {
            return EXTRAS_SAFE_TERMINAL_TIER;
        }
        return tier;
    }

    @Override
    public MagicSourcePipeUpgradeData getUpgradeData() {
        return new MagicSourcePipeUpgradeData(redstoneReactive,
                getConnectionTypesRaw(), buffer.getSource(),
                extension.writeUpgradeData());
    }

    @Override
    public boolean dataTypeMatches(@NotNull TransmitterUpgradeData data) {
        return data instanceof MagicSourcePipeUpgradeData;
    }

    @Override
    public void parseUpgradeData(@NotNull MagicSourcePipeUpgradeData data) {
        redstoneReactive = data.redstoneReactive;
        setConnectionTypesRaw(data.connectionTypes);
        buffer.setSource(Math.min(data.source, buffer.getSourceCapacity()));
        extension.parseUpgradeData(data.resources);
    }

    @Override
    public PipeSourceStorage getBufferWithFallback() {
        if (buffer.getSource() == 0 && hasTransmitterNetwork()) {
            return getTransmitterNetwork().getBuffer();
        }
        return buffer;
    }

    @Override
    public boolean noBufferOrFallback() {
        return getBufferWithFallback().getSource() <= 0 && extension.isEmpty();
    }

    @Override
    public PipeSourceStorage releaseShare() {
        PipeSourceStorage share = new BasicPipeSourceStorage(
                (int) getCapacity(), (int) getCapacity(),
                (int) getCapacity());
        share.setSource(buffer.getSource());
        buffer.setSource(0);
        return share;
    }

    @Override
    public PipeSourceStorage getShare() {
        return buffer;
    }

    @Override
    public void takeShare() {
        // Chunk accessibility and actual unloading can both request a share
        // before the old topology is invalidated. Never claim it twice.
        if (buffer.getSource() == 0 && hasTransmitterNetwork() && saveShare > 0) {
            MagicSourceNetwork network = getTransmitterNetwork();
            int taken = network.getBuffer().extractSource(
                    saveShare, false);
            buffer.setSource(Math.min(
                    taken, buffer.getSourceCapacity()));
        }
        extension.takeShare();
    }

    public void setSaveShare(int amount) {
        int share = Math.max(0, amount);
        if (saveShare != share) {
            saveShare = share;
            getTransmitterTile().markForSave();
        }
    }

    public int getSource() {
        return getBufferWithFallback().getSource();
    }

    public int getSourceCapacity() {
        return hasTransmitterNetwork()
                ? getTransmitterNetwork().getBuffer()
                .getSourceCapacity() : buffer.getSourceCapacity();
    }

    public @Nullable PipeSourceStorage getSourceCapability(
            @Nullable Direction side) {
        return sourceCapabilityEnabled(side) ? new SourceSidedView(side) : null;
    }

    private boolean sourceCapabilityEnabled(@Nullable Direction side) {
        return isValid() && !isRedstoneActivated()
                && (side == null || getConnectionTypeRaw(side) != ConnectionType.NONE);
    }

    @Override
    public CompatibleTransmitterValidator<PipeSourceStorage,
            MagicSourceNetwork, MagicSourceTransmitter>
    getNewOrphanValidator() {
        return new SourceTransmitterValidator(extension.createValidator());
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
        // Keep Mekanism's native Source-capability decision first. Optional
        // storage integrations may additionally identify structural endpoints
        // without hard-linking this Ars class to their API.
        if (super.isValidAcceptor(tile, side)) {
            return true;
        }
        if (tile == null) {
            return false;
        }
        var blockId = BuiltInRegistries.BLOCK.getKey(
                tile.getBlockState().getBlock());
        return ("ae2".equals(blockId.getNamespace())
                && "interface".equals(blockId.getPath()))
                || MagicSourceExternalEndpointHooks.isEndpoint(
                getLevel(), tile.getBlockPos(), side.getOpposite())
                || extension.isEndpoint(
                getLevel(), tile.getBlockPos(), side.getOpposite());
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
        extension.read(provider, tag);
    }

    @Override
    public CompoundTag write(
            HolderLookup.Provider provider, CompoundTag tag) {
        super.write(provider, tag);
        if (buffer.getSource() > 0 || !hasTransmitterNetwork()) {
            saveShare = buffer.getSource();
        } else {
            getTransmitterNetwork().validateSaveShares(this);
        }
        tag.remove("magic_source_buffer");
        tag.putInt("magic_source_save_share", saveShare);
        extension.write(provider, tag);
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

    @Override
    public boolean handleUpdateTag(@NotNull CompoundTag tag,
                                    HolderLookup.@NotNull Provider provider) {
        boolean refreshModel = super.handleUpdateTag(tag, provider);
        // The base class returns early for an unchanged network UUID. Extra
        // resource render packets still carry new contents in that case.
        extension.readUpdateTag(tag);
        return refreshModel;
    }

    private static final class SourceTransmitterValidator
            extends CompatibleTransmitterValidator<PipeSourceStorage,
            MagicSourceNetwork, MagicSourceTransmitter> {
        private final MagicPipeResourceHooks.ResourceValidator resources;

        private SourceTransmitterValidator(
                MagicPipeResourceHooks.ResourceValidator resources) {
            this.resources = resources;
        }

        @Override
        public boolean isNetworkCompatible(MagicSourceNetwork network) {
            return resources.canAcceptNetwork(network);
        }

        @Override
        public boolean isTransmitterCompatible(
                mekanism.common.content.network.transmitter.Transmitter<
                        ?, ?, ?> transmitter) {
            return transmitter instanceof MagicSourceTransmitter pipe
                    && resources.canAcceptPipe(pipe);
        }
    }

    private final class SourceSidedView implements PipeSourceStorage {
        private final @Nullable Direction side;

        private SourceSidedView(@Nullable Direction side) {
            this.side = side;
        }

        private PipeSourceStorage source() {
            // Resolve on every operation: a retained capability must follow
            // the placed pipe through orphaning, network merges and splits.
            return hasTransmitterNetwork() ? getTransmitterNetwork().getBuffer() : buffer;
        }

        private boolean receive() {
            return sourceCapabilityEnabled(side)
                    && (side == null || getConnectionTypeRaw(side) != ConnectionType.PUSH);
        }

        private boolean extract() {
            return sourceCapabilityEnabled(side)
                    && (side == null || getConnectionTypeRaw(side) != ConnectionType.PULL);
        }

        @Override
        public boolean canAcceptSource(int amount) {
            return receive() && source().canAcceptSource(amount);
        }

        @Override
        public boolean canProvideSource(int amount) {
            return extract() && source().canProvideSource(amount);
        }

        @Override
        public int getMaxExtract() {
            return extract() ? source().getMaxExtract() : 0;
        }

        @Override
        public int getMaxReceive() {
            return receive() ? source().getMaxReceive() : 0;
        }

        @Override
        public int getSource() {
            return source().getSource();
        }

        @Override
        public int getSourceCapacity() {
            return source().getSourceCapacity();
        }

        @Override
        public void setSource(int amount) {
            // Only the unsided view retains the original administrative setter.
            if (side == null && sourceCapabilityEnabled(null)) {
                source().setSource(amount);
            }
        }

        @Override
        public void setMaxSource(int amount) {
            if (side == null && sourceCapabilityEnabled(null)) {
                source().setMaxSource(amount);
            }
        }

        @Override
        public int receiveSource(int amount, boolean simulate) {
            return receive() ? source().receiveSource(amount, simulate) : 0;
        }

        @Override
        public int extractSource(int amount, boolean simulate) {
            return extract() ? source().extractSource(amount, simulate) : 0;
        }
    }
}
