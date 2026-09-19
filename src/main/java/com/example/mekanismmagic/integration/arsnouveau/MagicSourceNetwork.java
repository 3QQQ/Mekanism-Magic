package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.network.PipeSourceStorage;
import com.example.mekanismmagic.integration.common.network.BasicPipeSourceStorage;
import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import mekanism.common.MekanismLang;
import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.distribution.Target;
import mekanism.common.lib.transmitter.DynamicBufferedNetwork;
import mekanism.common.network.PacketUtils;
import mekanism.common.network.to_client.transmitter.PacketNetworkScale;
import mekanism.common.util.EmitUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Mekanism DynamicBufferedNetwork implementation for Ars Nouveau Source.
 */
public final class MagicSourceNetwork extends DynamicBufferedNetwork<
        PipeSourceStorage, MagicSourceNetwork, PipeSourceStorage,
        MagicSourceTransmitter> {
    public final PipeSourceStorage sourceStorage;
    private final NetworkSourceStorage networkSourceStorage;
    private final MagicPipeResourceHooks.NetworkExtension extension;
    public int lastTransferAmount;
    public int lastSource;

    public MagicSourceNetwork(UUID networkId) {
        super(networkId);
        // Match Mekanism's FluidNetwork: the shared network storage and normal
        // output are unrestricted by pump rate. Only PULL connections apply
        // the local pipe tier's pull limit.
        networkSourceStorage = new NetworkSourceStorage(
                () -> Math.max(1L, MagicSourceNetwork.this.getCapacity()),
                MagicSourceNetwork.this::markDirty);
        sourceStorage = networkSourceStorage;
        extension = MagicPipeResourceHooks.createNetwork(this);
    }

    public MagicSourceNetwork(
            Collection<MagicSourceNetwork> networks) {
        this(UUID.randomUUID());
        if (!canMergeAll(networks)) {
            throw new IllegalArgumentException(
                    "Cannot merge Source networks without conserving contents");
        }
        adoptAllAndRegister(networks);
    }

    @Override
    protected void forceScaleUpdate() {
        currentScale = sourceScale();
    }

    @Override
    public PipeSourceStorage getBuffer() {
        return sourceStorage;
    }

    public MagicPipeResourceHooks.NetworkExtension extension() {
        return extension;
    }

    @Override
    public void absorbBuffer(MagicSourceTransmitter transmitter) {
        PipeSourceStorage share = transmitter.releaseShare();
        int amount = share.getSource();
        if (amount > 0) {
            long accepted = networkSourceStorage.receiveSource(
                    amount, false);
            int rejected = amount - (int) accepted;
            if (rejected > 0) {
                // A malformed/over-capacity orphan must keep its remainder in
                // the physical pipe. Never silently delete Source while a
                // network is being rebuilt.
                int restored = transmitter.getShare().receiveSource(
                        rejected, false);
                if (restored != rejected) {
                    throw new IllegalStateException(
                            "Unable to restore rejected Source pipe share");
                }
            }
        }
        extension.absorbBuffer(transmitter);
    }

    @Override
    public void clampBuffer() {
        networkSourceStorage.clampToCapacity();
        extension.clampBuffer();
    }

    @Override
    protected synchronized void updateCapacity(
            MagicSourceTransmitter transmitter) {
        super.updateCapacity(transmitter);
        networkSourceStorage.clampToCapacity();
        extension.updateCapacity();
    }

    @Override
    public synchronized void updateCapacity() {
        super.updateCapacity();
        networkSourceStorage.clampToCapacity();
        extension.updateCapacity();
    }

    @Override
    public Collection<MagicSourceTransmitter>
    getTransmitters() {
        return super.getTransmitters();
    }

    @Override
    public java.util.List<MagicSourceTransmitter>
    adoptTransmittersAndAcceptorsFrom(MagicSourceNetwork other) {
        if (!extension.isCompatibleWith(other)) {
            throw new IllegalArgumentException("Incompatible magic pipe resources");
        }
        long ownSource = networkSourceStorage.getStoredSource();
        long otherSource = other.networkSourceStorage.getStoredSource();
        long mergedSource = addExactOrReject(ownSource, otherSource);
        long projectedCapacity = saturatedAdd(
                getCapacity(), other.getCapacity());
        if (mergedSource > Math.max(1L, projectedCapacity)) {
            throw new IllegalStateException(
                    "Source contents exceed projected merged capacity");
        }
        java.util.List<MagicSourceTransmitter> updated =
                super.adoptTransmittersAndAcceptorsFrom(other);
        networkSourceStorage.setStoredSourceExact(mergedSource);
        other.networkSourceStorage.setStoredSourceExact(0);
        extension.adoptFrom(other);
        return updated;
    }

    @Override
    public boolean isCompatibleWith(MagicSourceNetwork other) {
        return super.isCompatibleWith(other)
                && canAdd(networkSourceStorage.getStoredSource(),
                other.networkSourceStorage.getStoredSource())
                && extension.isCompatibleWith(other);
    }

    @Override
    public void onUpdate() {
        float previousScale = currentScale;
        super.onUpdate();
        // A freshly split/rebuilt network calculates its correct scale while
        // transmitters are committed, before the first update tick. In that
        // case previousScale already equals currentScale, but needsUpdate is
        // still set by DynamicBufferedNetwork.validTransmittersAdded(). Send
        // the initial scale anyway or the new client network remains at zero
        // until its contents happen to change again.
        if (!isRemote() && (needsUpdate || previousScale != currentScale)) {
            PacketUtils.sendToAllTracking(
                    this, new PacketNetworkScale(this));
        }
        needsUpdate = false;
        if (!isRemote()) {
            extension.tickServer();
        }
        int amount = sourceStorage.getSource();
        if (amount <= 0) {
            lastTransferAmount = 0;
            lastSource = 0;
            return;
        }
        SourceTarget target = null;
        ObjectIterator<Long2ObjectMap.Entry<Map<Direction, PipeSourceStorage>>>
                iterator = acceptorCache.getAcceptorFastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Map<Direction, PipeSourceStorage>> entry =
                    iterator.next();
            for (PipeSourceStorage acceptor : entry.getValue().values()) {
                if (target == null) {
                    target = new SourceTarget();
                }
                target.addHandler(new SourceEndpoint(acceptor));
            }
        }
        lastTransferAmount = target == null ? 0
                : EmitUtils.sendToAcceptors(target, amount, amount);
        if (lastTransferAmount > 0) {
            sourceStorage.extractSource(lastTransferAmount, false);
        }
        lastSource = sourceStorage.getSource();
    }

    @Override
    protected float computeContentScale() {
        float scale = sourceScale();
        float result = Math.max(currentScale, scale);
        if (lastTransferAmount > 0 && result < 1) {
            result = Math.min(1, result + 0.02F);
        } else if (lastTransferAmount <= 0 && result > 0) {
            result = Math.max(scale, result - 0.02F);
        }
        return result;
    }

    @Override
    protected void updateSaveShares(
            MagicSourceTransmitter triggerTransmitter) {
        long remaining = networkSourceStorage.getStoredSource();
        for (MagicSourceTransmitter transmitter
                : getTransmitters()) {
            if (transmitter.getShare().getSource() > 0) {
                // A departing pipe may already own its local share while it
                // is still present in the old network's transmitter map.
                continue;
            }
            int share = (int) Math.min(
                    transmitter.getCapacity(), remaining);
            transmitter.setSaveShare(share);
            remaining -= share;
        }
        extension.updateSaveShares();
    }

    @Override
    public Component getTextComponent() {
        return MekanismLang.NETWORK_DESCRIPTION.translate(
                Component.translatable(
                        "block.mekanism_magic.magic_source_pipe"),
                transmittersSize(), getAcceptorCount());
    }

    public int getNeeded() {
        long needed = Math.max(0L,
                Math.max(1L, getCapacity())
                        - networkSourceStorage.getStoredSource());
        return (int) Math.min(Integer.MAX_VALUE, needed);
    }

    public int getStored() {
        return sourceStorage.getSource();
    }

    public long getStoredLong() {
        return networkSourceStorage.getStoredSource();
    }

    private float sourceScale() {
        long capacity = Math.max(1L, getCapacity());
        return (float) Math.min(1D,
                networkSourceStorage.getStoredSource()
                        / (double) capacity);
    }

    static boolean canMergeAll(Collection<MagicSourceNetwork> networks) {
        if (networks == null) {
            return false;
        }
        long total = 0;
        for (MagicSourceNetwork network : networks) {
            if (network == null || !canAdd(total,
                    network.networkSourceStorage.getStoredSource())) {
                return false;
            }
            total += network.networkSourceStorage.getStoredSource();
            for (MagicSourceNetwork other : networks) {
                if (other == null || other != network
                        && !network.extension.isCompatibleWith(other)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canAdd(long first, long second) {
        return first >= 0 && second >= 0
                && first <= Long.MAX_VALUE - second;
    }

    private static long addExactOrReject(long first, long second) {
        if (!canAdd(first, second)) {
            throw new IllegalArgumentException(
                    "Source network contents exceed long range");
        }
        return first + second;
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second
                ? Long.MAX_VALUE : first + second;
    }

    private static final class SourceTarget
            extends Target<SourceEndpoint, Integer> {
        private SourceTarget() {
            // Avoid a second full acceptor-cache traversal merely to obtain
            // an exact allocation size. Most Source networks have only a
            // handful of endpoints, and ArrayList grows cheaply when needed.
            super(8);
        }

        @Override
        protected void acceptAmount(
                SourceEndpoint handler, SplitInfo splitInfo,
                Integer resource, long amount) {
            int accepted = handler.receive(amount, false);
            splitInfo.send(accepted);
        }

        @Override
        protected long simulate(
                SourceEndpoint handler, Integer resource, long amount) {
            return handler.receive(amount, true);
        }
    }

    private record SourceEndpoint(PipeSourceStorage acceptor) {
        private int receive(long requested, boolean simulate) {
            int amount = (int) Math.min(Integer.MAX_VALUE, requested);
            return acceptor.receiveSource(amount, simulate);
        }
    }
}

/**
 * Ars exposes Source through an int-based capability, but a Mekanism network
 * can contain enough high-tier transmitters for its aggregate capacity to
 * exceed {@link Integer#MAX_VALUE}. Keep the authoritative amount as a long;
 * individual capability operations still transfer int-sized chunks.
 */
final class NetworkSourceStorage extends BasicPipeSourceStorage {
    private final LongSupplier capacitySupplier;
    private final Runnable listener;
    private long storedSource;

    NetworkSourceStorage(LongSupplier capacitySupplier, Runnable listener) {
        super(1, Integer.MAX_VALUE, Integer.MAX_VALUE);
        this.capacitySupplier = capacitySupplier;
        this.listener = listener;
    }

    synchronized long getStoredSource() {
        return storedSource;
    }

    private long capacity() {
        return Math.max(1L, capacitySupplier.getAsLong());
    }

    synchronized long receiveSource(long requested, boolean simulate) {
        if (requested <= 0) {
            return 0;
        }
        long accepted = Math.min(requested, capacity() - storedSource);
        if (!simulate && accepted > 0) {
            storedSource += accepted;
            listener.run();
        }
        return accepted;
    }

    @Override
    public synchronized int receiveSource(int requested, boolean simulate) {
        return (int) receiveSource((long) requested, simulate);
    }

    @Override
    public synchronized int extractSource(int requested, boolean simulate) {
        if (requested <= 0) {
            return 0;
        }
        int extracted = (int) Math.min(requested, storedSource);
        if (!simulate && extracted > 0) {
            storedSource -= extracted;
            listener.run();
        }
        return extracted;
    }

    @Override
    public synchronized int getSource() {
        return (int) Math.min(Integer.MAX_VALUE, storedSource);
    }

    @Override
    public int getSourceCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, capacity());
    }

    @Override
    public synchronized void setSource(int source) {
        setStoredSource(Math.max(0L, source));
    }

    @Override
    public void setMaxSource(int ignored) {
        // Capacity is derived from the live transmitter set. Accepting an
        // int here would truncate large networks.
        clampToCapacity();
    }

    synchronized void setStoredSource(long source) {
        long clamped = Math.max(0L, Math.min(capacity(), source));
        if (clamped != storedSource) {
            storedSource = clamped;
            listener.run();
        }
    }

    synchronized void setStoredSourceExact(long source) {
        if (source < 0 || source > capacity()) {
            throw new IllegalArgumentException(
                    "Source amount does not fit network capacity");
        }
        if (source != storedSource) {
            storedSource = source;
            listener.run();
        }
    }

    synchronized void clampToCapacity() {
        setStoredSource(storedSource);
    }

    @Override
    public synchronized Tag serializeNBT(
            HolderLookup.@NotNull Provider provider) {
        return LongTag.valueOf(storedSource);
    }

    @Override
    public synchronized void deserializeNBT(
            HolderLookup.@NotNull Provider provider,
            @NotNull Tag nbt) {
        long restored;
        if (nbt instanceof LongTag longTag) {
            restored = longTag.getAsLong();
        } else if (nbt instanceof IntTag intTag) {
            restored = intTag.getAsInt();
        } else {
            throw new IllegalArgumentException(
                    "Source network storage requires an integer tag");
        }
        setStoredSource(restored);
    }
}
