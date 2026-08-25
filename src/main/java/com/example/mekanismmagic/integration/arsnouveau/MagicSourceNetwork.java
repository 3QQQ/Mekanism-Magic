package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import mekanism.common.MekanismLang;
import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.distribution.Target;
import mekanism.common.lib.transmitter.DynamicBufferedNetwork;
import mekanism.common.network.PacketUtils;
import mekanism.common.network.to_client.transmitter.PacketNetworkScale;
import mekanism.common.util.EmitUtils;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Mekanism DynamicBufferedNetwork implementation for Ars Nouveau Source.
 */
public final class MagicSourceNetwork extends DynamicBufferedNetwork<
        ISourceCap, MagicSourceNetwork, SourceStorage,
        MagicSourceTransmitter> {
    private static final int TRANSFER_RATE = 1_000;
    public final SourceStorage sourceStorage;
    public int lastTransferAmount;
    public int lastSource;

    public MagicSourceNetwork(UUID networkId) {
        super(networkId);
        sourceStorage = new SourceStorage(1, TRANSFER_RATE,
                TRANSFER_RATE) {
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
        int amount = Math.min(
                TRANSFER_RATE, sourceStorage.getSource());
        if (amount <= 0) {
            lastTransferAmount = 0;
            return;
        }
        SourceTarget target = null;
        for (Map<net.minecraft.core.Direction, ISourceCap> acceptors
                : acceptorCache.getAcceptorValues()) {
            for (ISourceCap acceptor : acceptors.values()) {
                if (!acceptor.canAcceptSource(1)) {
                    continue;
                }
                if (target == null) {
                    target = new SourceTarget(
                            acceptorCache.getAcceptorCount());
                }
                target.addHandler(acceptor);
            }
        }
        lastTransferAmount = target == null ? 0
                : EmitUtils.sendToAcceptors(target, amount, amount);
        if (lastTransferAmount > 0) {
            sourceStorage.extractSource(lastTransferAmount, false);
        }
        lastSource = sourceStorage.getSource();
        if (!isRemote() && previousScale != currentScale) {
            PacketUtils.sendToAllTracking(
                    this, new PacketNetworkScale(this));
        }
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
            extends Target<ISourceCap, Integer> {
        private SourceTarget(int expectedSize) {
            super(Math.max(1, expectedSize));
        }

        @Override
        protected void acceptAmount(
                ISourceCap handler, SplitInfo splitInfo,
                Integer resource, long amount) {
            int accepted = handler.receiveSource(
                    Math.min(Integer.MAX_VALUE, (int) amount),
                    false);
            splitInfo.send(accepted);
        }

        @Override
        protected long simulate(
                ISourceCap handler, Integer resource, long amount) {
            return handler.receiveSource(
                    Math.min(Integer.MAX_VALUE, (int) amount),
                    true);
        }
    }
}
