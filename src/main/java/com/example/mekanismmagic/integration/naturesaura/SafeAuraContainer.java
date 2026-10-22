package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Bounded, listener-aware Aura storage for this addon's Nature's Aura
 * machines. Unlike the upstream basic container, invalid negative requests
 * and malformed persisted values cannot reverse or overflow a transfer.
 */
final class SafeAuraContainer
        implements IAuraContainer, INBTSerializable<CompoundTag> {
    private static final String STORED_AURA = "stored_aura";
    private static final int FALLBACK_COLOR = 0x42A6BC;

    private final Supplier<? extends IAuraType> typeSupplier;
    private final int capacity;
    private final int maxReceive;
    private final int maxExtract;
    private final Runnable listener;
    private int storedAura;

    SafeAuraContainer(Supplier<? extends IAuraType> typeSupplier,
                      int capacity, int maxReceive, int maxExtract,
                      Runnable listener) {
        this.typeSupplier = Objects.requireNonNull(
                typeSupplier, "typeSupplier");
        this.listener = Objects.requireNonNull(listener, "listener");
        if (capacity < 0 || maxReceive < 0 || maxExtract < 0) {
            throw new IllegalArgumentException(
                    "Aura capacity and transfer limits cannot be negative");
        }
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    @Override
    public synchronized int storeAura(int amount, boolean simulate) {
        if (amount <= 0 || maxReceive <= 0 || storedAura >= capacity) {
            return 0;
        }
        int accepted = Math.min(amount,
                Math.min(maxReceive, capacity - storedAura));
        if (!simulate && accepted > 0) {
            storedAura += accepted;
            listener.run();
        }
        return accepted;
    }

    @Override
    public synchronized int drainAura(int amount, boolean simulate) {
        if (amount <= 0 || maxExtract <= 0 || storedAura <= 0) {
            return 0;
        }
        int extracted = Math.min(amount,
                Math.min(maxExtract, storedAura));
        if (!simulate && extracted > 0) {
            storedAura -= extracted;
            listener.run();
        }
        return extracted;
    }

    synchronized boolean drainExact(int amount, boolean simulate) {
        if (amount < 0 || storedAura < amount) {
            return false;
        }
        if (!simulate && amount > 0) {
            storedAura -= amount;
            listener.run();
        }
        return true;
    }

    synchronized boolean storeExact(int amount, boolean simulate) {
        if (amount < 0 || capacity - storedAura < amount) {
            return false;
        }
        if (!simulate && amount > 0) {
            storedAura += amount;
            listener.run();
        }
        return true;
    }

    synchronized int setStoredAura(int amount) {
        int clamped = Math.max(0, Math.min(capacity, amount));
        if (clamped != storedAura) {
            storedAura = clamped;
            listener.run();
        }
        return storedAura;
    }

    synchronized void setStoredAuraFromSync(int amount) {
        storedAura = Math.max(0, Math.min(capacity, amount));
    }

    @Override
    public synchronized int getStoredAura() {
        return storedAura;
    }

    @Override
    public int getMaxAura() {
        return capacity;
    }

    int getMaxReceive() {
        return maxReceive;
    }

    int getMaxExtract() {
        return maxExtract;
    }

    @Override
    public int getAuraColor() {
        IAuraType type = typeSupplier.get();
        return type == null ? FALLBACK_COLOR : type.getColor();
    }

    @Override
    public boolean isAcceptableType(IAuraType candidate) {
        IAuraType current = typeSupplier.get();
        return candidate != null && current != null
                && candidate.isSimilar(current);
    }

    @Override
    public synchronized CompoundTag serializeNBT(
            HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(STORED_AURA, storedAura);
        return tag;
    }

    @Override
    public synchronized void deserializeNBT(
            HolderLookup.Provider registries, CompoundTag tag) {
        if (tag == null || !tag.contains(
                STORED_AURA, Tag.TAG_ANY_NUMERIC)) {
            storedAura = 0;
            return;
        }
        long loaded = tag.getLong(STORED_AURA);
        storedAura = (int) Math.max(0L,
                Math.min((long) capacity, loaded));
    }
}
