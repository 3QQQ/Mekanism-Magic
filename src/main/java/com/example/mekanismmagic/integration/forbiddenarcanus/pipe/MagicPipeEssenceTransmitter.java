package com.example.mekanismmagic.integration.forbiddenarcanus.pipe;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeTier;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceNetwork;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceCapabilities;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceHost;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import java.util.EnumMap;

/** Four typed local save shares on the existing Source pipe segment. */
public final class MagicPipeEssenceTransmitter implements MagicPipeResourceHooks.PipeExtension {
    private static final String SHARES = "magic_pipe_essence_shares";
    private static final String CURSOR = "magic_pipe_essence_cursor";
    private static final String SCALE = "magic_pipe_essence_scale";
    private static final String COLOR = "magic_pipe_essence_color";
    private final MagicSourceTransmitter pipe;
    private final long[] local = new long[EssenceResources.CHANNELS];
    private final long[] saveShare = new long[EssenceResources.CHANNELS];
    private final EnumMap<Direction, BlockCapabilityCache<EssenceResourceHost, Direction>> endpoints =
            new EnumMap<>(Direction.class);
    private int saveCursor;
    private float clientScale;
    private int clientColor = 0x9F9FDA;
    private MagicSourceNetwork claimedFrom;

    MagicPipeEssenceTransmitter(MagicSourceTransmitter pipe) { this.pipe = pipe; }

    public int tierCapacity() { return pipe.sourceTier.capacity(); }
    public int transferRate() { return pipe.sourceTier.pullRate(); }
    public static int tierCapacity(MagicSourcePipeTier tier) { return tier.capacity(); }
    public static int transferRate(MagicSourcePipeTier tier) { return tier.pullRate(); }

    long local(int channel) { return local[channel]; }
    int cursor() { return saveCursor; }
    void consumeLocal(int channel, long amount) {
        if (amount <= 0 || amount > local[channel]) throw new IllegalArgumentException("Invalid local essence debit");
        local[channel] -= amount;
        pipe.onContentsChanged();
    }
    void acceptOverflow(int channel, long amount) {
        if (amount <= 0) return;
        local[channel] = Math.addExact(local[channel], amount);
        pipe.onContentsChanged();
    }

    EssenceResourceHost endpoint(Direction side) {
        if (!(pipe.getLevel() instanceof ServerLevel level)) return null;
        var cache = endpoints.computeIfAbsent(side, direction -> BlockCapabilityCache.create(
                EssenceResourceCapabilities.ESSENCE, level, pipe.getBlockPos().relative(direction),
                direction.getOpposite(), () -> !pipe.getTransmitterTile().isRemoved(),
                () -> pipe.getTransmitterTile().setForceUpdate()));
        return cache.getCapability();
    }

    boolean enabled(Direction side) {
        return pipe.isValid() && !pipe.isRedstoneActivated()
                && pipe.getConnectionTypeRaw(side) != ConnectionType.NONE;
    }
    ConnectionType mode(Direction side) { return pipe.getConnectionTypeRaw(side); }
    MagicSourceTransmitter pipe() { return pipe; }

    boolean ownsNetworkShare(MagicSourceNetwork network) {
        return pipe.hasTransmitterNetwork() && pipe.getTransmitterNetwork() == network
                && claimedFrom != network;
    }

    @Override public void tickServer() {
        if (claimedFrom != null && !pipe.isUpgrading() && pipe.isValid()
                && pipe.hasTransmitterNetwork() && pipe.getLevel() instanceof ServerLevel level
                && level.getBlockEntity(pipe.getBlockPos()) == pipe.getTransmitterTile()) {
            // A quick chunk accessibility false->true can cancel Mekanism's
            // pending invalidation and retain the same network object. Only
            // the live physical segment's own tick may release this claim;
            // a stale network scanning an upgrading segment must not do so.
            claimedFrom = null;
        }
    }

    @Override public boolean isEndpoint(Level level, BlockPos pos, Direction side) {
        Direction ownSide = side.getOpposite();
        if (!pos.equals(pipe.getBlockPos().relative(ownSide))) return false;
        EssenceResourceHost host = endpoint(ownSide);
        if (host == null) return false;
        for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
            if (host.canInputEssence(channel, ownSide.getOpposite())
                    || host.canOutputEssence(channel, ownSide.getOpposite())) return true;
        }
        return false;
    }

    @Override public void sideChanged(Direction side) {
        pipe.getTransmitterTile().setForceUpdate();
    }
    @Override public void redstoneChanged(boolean powered) {
        pipe.getTransmitterTile().setForceUpdate();
    }
    @Override public boolean isEmpty() {
        for (long amount : local) if (amount > 0) return false;
        MagicPipeEssenceChannel network = channel();
        return network == null || network.isEmpty();
    }

    private MagicPipeEssenceChannel channel() {
        return pipe.hasTransmitterNetwork() ? MagicPipeResourceHooks.networkExtension(
                pipe.getTransmitterNetwork(), MagicPipeEssenceChannel.class) : null;
    }

    void setSaveShare(int channel, long amount, int cursor) {
        if (saveShare[channel] != amount || saveCursor != cursor) {
            saveShare[channel] = amount;
            saveCursor = cursor;
            pipe.getTransmitterTile().markForSave();
        }
    }

    @Override public void takeShare() {
        MagicPipeEssenceChannel network = channel();
        if (network == null || claimedFrom == pipe.getTransmitterNetwork()) return;
        network.updateSaveShares();
        // A topology map may retain us until the registry's next pass. From
        // this point our local share belongs to the departing physical pipe.
        claimedFrom = pipe.getTransmitterNetwork();
        for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
            long taken = network.extract(channel, saveShare[channel]);
            if (taken > 0) acceptOverflow(channel, taken);
            saveShare[channel] = 0;
        }
        saveCursor = network.currentChannel();
    }

    @Override public void read(HolderLookup.Provider provider, CompoundTag tag) {
        claimedFrom = null;
        CompoundTag data = tag.getCompound(SHARES);
        for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
            String id = EssenceResources.IDS.get(channel).toString();
            local[channel] = Math.max(0L, data.getLong(id));
            saveShare[channel] = 0;
        }
        saveCursor = Math.floorMod(tag.getInt(CURSOR), EssenceResources.CHANNELS);
    }

    @Override public void write(HolderLookup.Provider provider, CompoundTag tag) {
        MagicPipeEssenceChannel network = channel();
        boolean attached = network != null && ownsNetworkShare(pipe.getTransmitterNetwork());
        if (attached) network.updateSaveShares();
        CompoundTag shares = new CompoundTag();
        for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
            long amount = Math.addExact(local[channel], attached ? saveShare[channel] : 0L);
            if (amount > 0) shares.putLong(EssenceResources.IDS.get(channel).toString(), amount);
        }
        tag.put(SHARES, shares);
        tag.putInt(CURSOR, network == null ? saveCursor : network.currentChannel());
    }

    @Override public CompoundTag writeUpgradeData() {
        CompoundTag tag = new CompoundTag();
        // startUpgrading already withdrew our share. Unlike a world save,
        // this payload must never allocate another part of the old network.
        CompoundTag shares = new CompoundTag();
        for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
            if (local[channel] > 0) shares.putLong(EssenceResources.IDS.get(channel).toString(), local[channel]);
        }
        tag.put(SHARES, shares);
        tag.putInt(CURSOR, saveCursor);
        return tag;
    }
    @Override public void parseUpgradeData(CompoundTag tag) { read(null, tag); }

    @Override public void writeUpdateTag(CompoundTag tag) {
        MagicPipeEssenceChannel network = channel();
        if (network != null) {
            tag.putFloat(SCALE, network.renderScale());
            tag.putInt(COLOR, network.renderColor());
        } else {
            long total = 0;
            for (long amount : local) total = total > Long.MAX_VALUE - amount
                    ? Long.MAX_VALUE : total + amount;
            tag.putFloat(SCALE, Math.min(1F, total / (float) Math.max(1, 4L * tierCapacity())));
            tag.putInt(COLOR, 0x9F9FDA);
        }
    }
    @Override public void readUpdateTag(CompoundTag tag) {
        if (tag.contains(SCALE, Tag.TAG_ANY_NUMERIC)) {
            float scale = tag.getFloat(SCALE);
            clientScale = Float.isFinite(scale) ? Math.clamp(scale, 0F, 1F) : 0F;
        }
        if (tag.contains(COLOR, Tag.TAG_ANY_NUMERIC)) clientColor = tag.getInt(COLOR);
    }
    @Override public float renderScale() { return clientScale; }
    @Override public int renderColor() { return clientColor; }
}
