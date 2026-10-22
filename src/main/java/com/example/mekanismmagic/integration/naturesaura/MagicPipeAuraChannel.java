package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourceNetwork;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Independent long-sized Aura contents on a shared Mekanism topology. */
public final class MagicPipeAuraChannel
        implements MagicPipeResourceHooks.NetworkExtension, IAuraContainer {
    private final MagicSourceNetwork network;
    private long stored;
    private long capacity;
    private @Nullable ResourceLocation boundType;
    private long lastSyncTick = Long.MIN_VALUE;
    private int lastSyncedScale = -1;
    private int lastSyncedColor;

    MagicPipeAuraChannel(MagicSourceNetwork network) {
        this.network = network;
    }

    public long stored() { return stored; }
    public long capacity() { return capacity; }

    float scale() {
        return capacity <= 0 ? 0F : (float) (stored / (double) capacity);
    }

    @Override
    public void tickServer() {
        var transmitters = network.getTransmitters();
        if (transmitters.isEmpty()) {
            return;
        }
        var level = transmitters.iterator().next().getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        long tick = level.getGameTime();
        if (lastSyncTick != Long.MIN_VALUE && tick - lastSyncTick < 10) {
            return;
        }
        lastSyncTick = tick;
        int quantizedScale = stored <= 0 ? 0
                : Math.max(1, Math.round(scale() * 255F));
        int color = getAuraColor();
        if (quantizedScale != lastSyncedScale || color != lastSyncedColor) {
            lastSyncedScale = quantizedScale;
            lastSyncedColor = color;
            for (MagicSourceTransmitter pipe : transmitters) {
                pipe.getTransmitterTile().sendUpdatePacket();
            }
        }
    }

    @Nullable IAuraType type() {
        if (boundType != null) {
            return NaturesAuraAPI.AURA_TYPES.get(boundType);
        }
        for (MagicSourceTransmitter pipe : network.getTransmitters()) {
            IAuraType type = extension(pipe).localType();
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    @Nullable ResourceLocation typeId() {
        IAuraType type = type();
        return boundType != null ? boundType
                : type == null ? null : type.getName();
    }

    @Override
    public void updateCapacity() {
        long sum = 0;
        for (MagicSourceTransmitter pipe : network.getTransmitters()) {
            MagicPipeAuraTransmitter aura = extension(pipe);
            if (!aura.hasForeignShare()) {
                sum = saturatedAdd(sum, aura.tierCapacity());
            }
        }
        capacity = sum;
    }

    @Override
    public void clampBuffer() {
        updateCapacity();
        if (stored > capacity) {
            stored = capacity;
            changed();
        }
    }

    @Override
    public void absorbBuffer(MagicSourceTransmitter pipe) {
        MagicPipeAuraTransmitter aura = extension(pipe);
        if (aura.localStored() <= 0 || aura.hasForeignShare()) {
            return;
        }
        IAuraType shareType = aura.effectiveType();
        if (shareType == null || stored > 0
                && !NaturalAuraPipeSemantics.compatible(type(), shareType)) {
            return;
        }
        updateCapacity();
        int accepted = (int) Math.min(aura.localStored(),
                Math.max(0L, capacity - stored));
        if (accepted > 0) {
            boundType = shareType.getName();
            stored += accepted;
            aura.consumeLocal(accepted);
            changed();
        }
    }

    @Override
    public boolean isCompatibleWith(MagicSourceNetwork other) {
        MagicPipeAuraChannel aura = MagicPipeResourceHooks.networkExtension(other, MagicPipeAuraChannel.class);
        if (aura == null) {
            return stored == 0;
        }
        return stored <= Long.MAX_VALUE - aura.stored
                && (stored == 0 || aura.stored == 0
                || NaturalAuraPipeSemantics.compatible(type(), aura.type()));
    }

    @Override
    public void adoptFrom(MagicSourceNetwork other) {
        MagicPipeAuraChannel aura = MagicPipeResourceHooks.networkExtension(other, MagicPipeAuraChannel.class);
        if (aura == null) throw new IllegalArgumentException("Other network has no Aura channel");
        if (!isCompatibleWith(other)) {
            throw new IllegalArgumentException("Incompatible Aura channels");
        }
        updateCapacity();
        long merged = stored + aura.stored;
        if (merged > capacity) {
            throw new IllegalStateException("Merged Aura exceeds capacity");
        }
        if (stored == 0 && aura.stored > 0) {
            boundType = aura.typeId();
        }
        stored = merged;
        aura.stored = 0;
        aura.boundType = null;
        changed();
        aura.changed();
    }

    @Override
    public void updateSaveShares() {
        long remaining = stored;
        ResourceLocation type = typeId();
        for (MagicSourceTransmitter pipe : network.getTransmitters()) {
            MagicPipeAuraTransmitter aura = extension(pipe);
            if (aura.localStored() > 0) {
                continue;
            }
            int share = (int) Math.min(remaining, aura.tierCapacity());
            aura.setSaveShare(share, type);
            remaining -= share;
        }
    }

    @Override
    public int storeAura(int amount, boolean simulate) {
        IAuraType type = type();
        if (amount <= 0 || type == null) {
            return 0;
        }
        int accepted = (int) Math.min(amount,
                Math.max(0L, capacity - stored));
        if (!simulate && accepted > 0) {
            boundType = type.getName();
            stored += accepted;
            changed();
        }
        return accepted;
    }

    @Override
    public int drainAura(int amount, boolean simulate) {
        int drained = (int) Math.min(Math.max(0, amount), stored);
        if (!simulate && drained > 0) {
            stored -= drained;
            changed();
        }
        return drained;
    }

    @Override
    public int getStoredAura() {
        return (int) Math.min(Integer.MAX_VALUE, stored);
    }

    @Override
    public int getMaxAura() {
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    public int getAuraColor() {
        IAuraType type = type();
        return type == null ? 0x42A6BC : type.getColor();
    }

    @Override
    public boolean isAcceptableType(IAuraType candidate) {
        return NaturalAuraPipeSemantics.compatible(type(), candidate);
    }

    private void changed() {
        if (stored == 0) {
            boundType = null;
        }
        network.markDirty();
    }

    static MagicPipeAuraTransmitter extension(MagicSourceTransmitter pipe) {
        MagicPipeAuraTransmitter aura = MagicPipeResourceHooks.pipeExtension(pipe, MagicPipeAuraTransmitter.class);
        if (aura == null) throw new IllegalStateException("Pipe has no Aura extension");
        return aura;
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second
                ? Long.MAX_VALUE : first + second;
    }
}
