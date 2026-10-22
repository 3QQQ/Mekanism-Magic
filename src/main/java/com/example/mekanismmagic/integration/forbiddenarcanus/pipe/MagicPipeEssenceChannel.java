package com.example.mekanismmagic.integration.forbiddenarcanus.pipe;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourceNetwork;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceHost;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import mekanism.api.Action;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Four independent long ledgers on one pipe network; one ordered active type at a time. */
public final class MagicPipeEssenceChannel implements MagicPipeResourceHooks.NetworkExtension {
    private static final int[] COLORS = {0xE8B65C, 0xB794EC, 0xB7465D, 0x7DDA87};
    private final MagicSourceNetwork network;
    private final long[] stored = new long[EssenceResources.CHANNELS];
    private long capacity;
    private int currentChannel;
    private int endpointCursor;
    private long lastSyncTick = Long.MIN_VALUE;
    private int lastSyncedScale = -1;
    private int lastSyncedColor;

    MagicPipeEssenceChannel(MagicSourceNetwork network) { this.network = network; }

    public long stored(int channel) { return EssenceResources.validChannel(channel) ? stored[channel] : 0L; }
    public long capacity(int channel) { return EssenceResources.validChannel(channel) ? capacity : 0L; }
    public int currentChannel() { return currentChannel; }
    public boolean isEmpty() {
        for (long value : stored) if (value > 0) return false;
        return true;
    }

    float renderScale() {
        if (capacity <= 0) return 0F;
        long fullest = 0;
        for (long value : stored) fullest = Math.max(fullest, value);
        return Math.clamp((float) (fullest / (double) capacity), 0F, 1F);
    }
    int renderColor() {
        if (stored[currentChannel] > 0) return COLORS[currentChannel];
        for (int channel = 0; channel < stored.length; channel++) {
            if (stored[channel] > 0) return COLORS[channel];
        }
        return COLORS[currentChannel];
    }

    @Override public void updateCapacity() {
        long sum = 0;
        for (MagicSourceTransmitter pipe : network.getTransmitters()) {
            if (extension(pipe).ownsNetworkShare(network)) {
                sum = saturatedAdd(sum, extension(pipe).tierCapacity());
            }
        }
        capacity = sum;
    }

    @Override public void clampBuffer() {
        updateCapacity();
        for (int channel = 0; channel < stored.length; channel++) {
            if (stored[channel] <= capacity) continue;
            long overflow = stored[channel] - capacity;
            var pipes = network.getTransmitters().stream()
                    .filter(pipe -> extension(pipe).ownsNetworkShare(network)).iterator();
            if (!pipes.hasNext()) continue; // no owner to move to; keep the balance
            extension(pipes.next()).acceptOverflow(channel, overflow);
            stored[channel] -= overflow;
            changed();
        }
    }

    @Override public void absorbBuffer(MagicSourceTransmitter pipe) {
        MagicPipeEssenceTransmitter essence = extension(pipe);
        if (!essence.ownsNetworkShare(network) || !pipe.isValid() || pipe.isUpgrading()) return;
        boolean wasEmpty = isEmpty();
        updateCapacity();
        for (int channel = 0; channel < stored.length; channel++) {
            long accepted = Math.min(essence.local(channel), Math.max(0L, capacity - stored[channel]));
            if (accepted <= 0) continue;
            stored[channel] += accepted;
            essence.consumeLocal(channel, accepted);
            changed();
        }
        if (wasEmpty && !isEmpty()) currentChannel = essence.cursor();
    }

    @Override public boolean isCompatibleWith(MagicSourceNetwork other) {
        MagicPipeEssenceChannel rhs = MagicPipeResourceHooks.networkExtension(other, MagicPipeEssenceChannel.class);
        if (rhs == null) return isEmpty();
        long combinedCapacity = saturatedAdd(capacity, rhs.capacity);
        for (int channel = 0; channel < stored.length; channel++) {
            if (stored[channel] > Long.MAX_VALUE - rhs.stored[channel]
                    || stored[channel] + rhs.stored[channel] > combinedCapacity) return false;
        }
        return true;
    }

    @Override public void adoptFrom(MagicSourceNetwork other) {
        MagicPipeEssenceChannel rhs = MagicPipeResourceHooks.networkExtension(other, MagicPipeEssenceChannel.class);
        if (rhs == null || !isCompatibleWith(other)) throw new IllegalArgumentException("Incompatible essence networks");
        boolean wasEmpty = isEmpty();
        updateCapacity();
        for (int channel = 0; channel < stored.length; channel++) {
            stored[channel] += rhs.stored[channel];
            rhs.stored[channel] = 0;
        }
        if (wasEmpty) currentChannel = rhs.currentChannel;
        changed();
        rhs.changed();
    }

    @Override public void updateSaveShares() {
        List<MagicSourceTransmitter> pipes = network.getTransmitters().stream()
                .filter(pipe -> extension(pipe).ownsNetworkShare(network)).toList();
        if (pipes.isEmpty()) return;
        for (int channel = 0; channel < stored.length; channel++) {
            long remaining = stored[channel];
            for (int index = 0; index < pipes.size(); index++) {
                MagicPipeEssenceTransmitter pipe = extension(pipes.get(index));
                long share = index == pipes.size() - 1 ? remaining
                        : Math.min(remaining, pipe.tierCapacity());
                pipe.setSaveShare(channel, share, currentChannel);
                remaining -= share;
            }
        }
    }

    long extract(int channel, long requested) {
        if (!EssenceResources.validChannel(channel) || requested <= 0) return 0;
        long moved = Math.min(requested, stored[channel]);
        if (moved > 0) {
            stored[channel] -= moved;
            changed();
        }
        return moved;
    }

    @Override public void tickServer() {
        var active = network.getTransmitters().stream()
                .filter(pipe -> pipe.isValid() && extension(pipe).ownsNetworkShare(network)).toList();
        if (active.isEmpty()) return;
        var first = active.getFirst();
        if (first.getLevel() == null || first.getLevel().isClientSide()) return;
        updateCapacity();
        for (MagicSourceTransmitter pipe : network.getTransmitters()) absorbBuffer(pipe);
        List<Endpoint> endpoints = endpoints();
        // Idle essence attachments must not rotate the saved cursor (and dirty the network)
        // when this pipe is only being used for Source/Aura or all producers are empty.
        if (isEmpty() && !hasAvailableSource(endpoints)) {
            sync(first.getLevel().getGameTime());
            return;
        }
        int channel = currentChannel;
        List<Endpoint> sinks = endpoints.stream().filter(endpoint -> endpoint.canInsert(channel)).toList();
        if (!sinks.isEmpty()) {
            boolean sinkRoom = sinks.stream().anyMatch(endpoint -> endpoint.free(channel) > 0);
            if (!sinkRoom) {
                advance();
            } else {
                Map<MagicPipeEssenceTransmitter, Integer> pushBudget = new IdentityHashMap<>();
                push(channel, sinks, pushBudget);
                List<Endpoint> sources = endpoints.stream().filter(endpoint -> endpoint.canExtract(channel)).toList();
                if (stored[channel] < capacity) pull(channel, sources, sinks);
                push(channel, sinks, pushBudget);
                boolean sourceRemaining = sources.stream().anyMatch(endpoint -> endpoint.available(channel) > 0);
                if (!sourceRemaining && stored[channel] == 0) advance();
            }
        }
        sync(first.getLevel().getGameTime());
    }

    private static boolean hasAvailableSource(List<Endpoint> endpoints) {
        for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
            for (Endpoint endpoint : endpoints) {
                if (endpoint.canExtract(channel) && endpoint.available(channel) > 0) return true;
            }
        }
        return false;
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> result = new ArrayList<>();
        for (MagicSourceTransmitter pipe : network.getTransmitters()) {
            MagicPipeEssenceTransmitter essence = extension(pipe);
            if (!essence.ownsNetworkShare(network)) continue;
            for (Direction side : Direction.values()) {
                if (!essence.enabled(side)) continue;
                EssenceResourceHost host = essence.endpoint(side);
                if (host != null) result.add(new Endpoint(essence, host, side, essence.mode(side)));
            }
        }
        return result;
    }

    private void pull(int channel, List<Endpoint> sources, List<Endpoint> sinks) {
        long sinkRoom = 0;
        for (Endpoint sink : sinks) sinkRoom = saturatedAdd(sinkRoom, sink.free(channel));
        if (sinkRoom <= 0) return;
        Map<MagicPipeEssenceTransmitter, Integer> budget = new IdentityHashMap<>();
        for (Endpoint source : rotate(sources)) {
            int left = budget.computeIfAbsent(source.pipe(), MagicPipeEssenceTransmitter::transferRate);
            int request = (int) Math.min(Math.min((long) left, capacity - stored[channel]), sinkRoom);
            if (request <= 0) continue;
            int possible = bounded(request, source.host().extractEssence(channel, request, Action.SIMULATE));
            if (possible <= 0) continue;
            int moved = bounded(possible, source.host().extractEssence(channel, possible, Action.EXECUTE));
            if (moved <= 0) continue;
            stored[channel] += moved;
            sinkRoom -= moved;
            budget.put(source.pipe(), left - moved);
            changed();
        }
    }

    private void push(int channel, List<Endpoint> sinks,
                      Map<MagicPipeEssenceTransmitter, Integer> budget) {
        for (Endpoint sink : rotate(sinks)) {
            int left = budget.computeIfAbsent(sink.pipe(), MagicPipeEssenceTransmitter::transferRate);
            int request = (int) Math.min((long) left, stored[channel]);
            if (request <= 0) continue;
            int possible = bounded(request, sink.host().insertEssence(channel, request, Action.SIMULATE));
            if (possible <= 0) continue;
            int moved = bounded(possible, sink.host().insertEssence(channel, possible, Action.EXECUTE));
            if (moved <= 0) continue;
            stored[channel] -= moved;
            budget.put(sink.pipe(), left - moved);
            changed();
        }
    }

    private List<Endpoint> rotate(List<Endpoint> ports) {
        if (ports.size() < 2) return ports;
        int start = Math.floorMod(endpointCursor++, ports.size());
        List<Endpoint> ordered = new ArrayList<>(ports.size());
        for (int index = 0; index < ports.size(); index++) ordered.add(ports.get((start + index) % ports.size()));
        return ordered;
    }

    private void advance() {
        currentChannel = (currentChannel + 1) % EssenceResources.CHANNELS;
        changed();
    }

    private void sync(long tick) {
        if (lastSyncTick != Long.MIN_VALUE && tick - lastSyncTick < 10) return;
        lastSyncTick = tick;
        int scale = Math.round(renderScale() * 255F);
        int color = renderColor();
        if (scale != lastSyncedScale || color != lastSyncedColor) {
            lastSyncedScale = scale;
            lastSyncedColor = color;
            for (MagicSourceTransmitter pipe : network.getTransmitters()) pipe.getTransmitterTile().sendUpdatePacket();
        }
    }

    private void changed() { network.markDirty(); }
    private static MagicPipeEssenceTransmitter extension(MagicSourceTransmitter pipe) {
        MagicPipeEssenceTransmitter essence = MagicPipeResourceHooks.pipeExtension(pipe, MagicPipeEssenceTransmitter.class);
        if (essence == null) throw new IllegalStateException("Pipe has no essence extension");
        return essence;
    }
    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
    private static int bounded(int requested, int reported) { return Math.max(0, Math.min(requested, reported)); }

    private record Endpoint(MagicPipeEssenceTransmitter pipe, EssenceResourceHost host,
                            Direction side, ConnectionType mode) {
        boolean canExtract(int channel) {
            return mode == ConnectionType.PULL && host.canOutputEssence(channel, side.getOpposite());
        }
        boolean canInsert(int channel) {
            return (mode == ConnectionType.NORMAL || mode == ConnectionType.PUSH)
                    && host.canInputEssence(channel, side.getOpposite());
        }
        int available(int channel) { return Math.max(0, host.getStoredEssence(channel)); }
        int free(int channel) {
            return Math.max(0, host.getEssenceCapacity(channel) - host.getStoredEssence(channel));
        }
    }
}
