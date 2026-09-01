package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
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

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Mekanism DynamicBufferedNetwork implementation for Ars Nouveau Source.
 */
public final class MagicSourceNetwork extends DynamicBufferedNetwork<
        ISourceCap, MagicSourceNetwork, SourceStorage,
        MagicSourceTransmitter> {
    public final SourceStorage sourceStorage;
    public int lastTransferAmount;
    public int lastSource;

    public MagicSourceNetwork(UUID networkId) {
        super(networkId);
        // Match Mekanism's FluidNetwork: the shared network storage and normal
        // output are unrestricted by pump rate. Only PULL connections apply
        // the local pipe tier's pull limit.
        sourceStorage = new SourceStorage(1, Integer.MAX_VALUE,
                Integer.MAX_VALUE) {
            @Override
            public int getSourceCapacity() {
                return (int) Math.max(1, Math.min(
                        Integer.MAX_VALUE,
                        MagicSourceNetwork.this.getCapacity()));
            }

            @Override
            public void onContentsChanged() {
                MagicSourceNetwork.this.markDirty();
            }
        };
    }

    public MagicSourceNetwork(
            Collection<MagicSourceNetwork> networks) {
        this(UUID.randomUUID());
        adoptAllAndRegister(networks);
    }

    @Override
    protected void forceScaleUpdate() {
        currentScale = sourceStorage.getSourceCapacity() <= 0
                ? 0 : sourceStorage.getSource()
                / (float) sourceStorage.getSourceCapacity();
    }

    @Override
    public SourceStorage getBuffer() {
        return sourceStorage;
    }

    @Override
    public void absorbBuffer(MagicSourceTransmitter transmitter) {
        SourceStorage share = transmitter.releaseShare();
        if (share.getSource() > 0) {
            sourceStorage.setSource(Math.min(
                    sourceStorage.getSourceCapacity(),
                    sourceStorage.getSource()
                            + share.getSource()));
        }
    }

    @Override
    public void clampBuffer() {
        int capacity = sourceStorage.getSourceCapacity();
        if (sourceStorage.getSource() > capacity) {
            sourceStorage.setSource(capacity);
        }
    }

    @Override
    protected synchronized void updateCapacity(
            MagicSourceTransmitter transmitter) {
        super.updateCapacity(transmitter);
        sourceStorage.setMaxSource((int) Math.max(1,
                Math.min(Integer.MAX_VALUE, getCapacity())));
    }

    @Override
    public synchronized void updateCapacity() {
        super.updateCapacity();
        sourceStorage.setMaxSource((int) Math.max(1,
                Math.min(Integer.MAX_VALUE, getCapacity())));
    }

    @Override
    public Collection<MagicSourceTransmitter>
    getTransmitters() {
        return super.getTransmitters();
    }

    @Override
    public java.util.List<MagicSourceTransmitter>
    adoptTransmittersAndAcceptorsFrom(MagicSourceNetwork other) {
        int ownSource = sourceStorage.getSource();
        int otherSource = other.sourceStorage.getSource();
        java.util.List<MagicSourceTransmitter> updated =
                super.adoptTransmittersAndAcceptorsFrom(other);
        sourceStorage.setSource(Math.min(
                sourceStorage.getSourceCapacity(),
                ownSource + otherSource));
        return updated;
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
        int amount = sourceStorage.getSource();
        if (amount <= 0) {
            lastTransferAmount = 0;
            lastSource = 0;
            return;
        }
        SourceTarget target = null;
        ObjectIterator<Long2ObjectMap.Entry<Map<Direction, ISourceCap>>>
                iterator = acceptorCache.getAcceptorFastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Map<Direction, ISourceCap>> entry =
                    iterator.next();
            for (ISourceCap acceptor : entry.getValue().values()) {
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
        float scale = sourceStorage.getSourceCapacity() <= 0 ? 0
                : sourceStorage.getSource()
                / (float) sourceStorage.getSourceCapacity();
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
        int remaining = sourceStorage.getSource();
        for (MagicSourceTransmitter transmitter
                : getTransmitters()) {
            int share = (int) Math.min(
                    transmitter.getCapacity(), remaining);
            transmitter.setSaveShare(share);
            remaining -= share;
        }
    }

    @Override
    public Component getTextComponent() {
        return MekanismLang.NETWORK_DESCRIPTION.translate(
                Component.translatable(
                        "block.mekanism_magic.magic_source_pipe"),
                transmittersSize(), getAcceptorCount());
    }

    public int getNeeded() {
        return Math.max(0,
                sourceStorage.getSourceCapacity()
                        - sourceStorage.getSource());
    }

    public int getStored() {
        return sourceStorage.getSource();
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

    private record SourceEndpoint(ISourceCap acceptor) {
        private int receive(long requested, boolean simulate) {
            int amount = (int) Math.min(Integer.MAX_VALUE, requested);
            return acceptor.receiveSource(amount, simulate);
        }
    }
}
