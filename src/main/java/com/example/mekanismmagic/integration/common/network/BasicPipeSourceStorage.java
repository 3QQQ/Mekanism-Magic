package com.example.mekanismmagic.integration.common.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

/** Capacity-limited local pipe buffer with int-sized Source operations. */
public class BasicPipeSourceStorage implements PipeSourceStorage, INBTSerializable<Tag> {
    private int source;
    private int capacity;
    private final int maxReceive;
    private final int maxExtract;

    public BasicPipeSourceStorage(int capacity, int maxReceive, int maxExtract) {
        this.capacity = Math.max(0, capacity);
        this.maxReceive = Math.max(0, maxReceive);
        this.maxExtract = Math.max(0, maxExtract);
    }

    @Override public int getMaxExtract() { return maxExtract; }
    @Override public int getMaxReceive() { return maxReceive; }
    @Override public int getSource() { return source; }
    @Override public int getSourceCapacity() { return capacity; }
    @Override public boolean canExtract() { return maxExtract > 0; }
    @Override public boolean canReceive() { return maxReceive > 0; }

    @Override
    public void setSource(int amount) {
        source = Math.clamp(amount, 0, capacity);
    }

    @Override
    public void setMaxSource(int amount) {
        capacity = Math.max(0, amount);
        setSource(source);
    }

    @Override
    public int receiveSource(int amount, boolean simulate) {
        if (amount <= 0 || !canReceive()) {
            return 0;
        }
        int accepted = Math.min(capacity - source, Math.min(maxReceive, amount));
        if (!simulate) {
            source += accepted;
            onContentsChanged();
        }
        return accepted;
    }

    @Override
    public int extractSource(int amount, boolean simulate) {
        if (amount <= 0 || !canExtract()) {
            return 0;
        }
        int extracted = Math.min(source, Math.min(maxExtract, amount));
        if (!simulate) {
            source -= extracted;
            onContentsChanged();
        }
        return extracted;
    }

    @Override
    public Tag serializeNBT(HolderLookup.@NotNull Provider provider) {
        return IntTag.valueOf(getSource());
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull Tag tag) {
        if (!(tag instanceof IntTag amount)) {
            throw new IllegalArgumentException("Source pipe storage requires an integer tag");
        }
        setSource(amount.getAsInt());
    }

    public void onContentsChanged() {}
}
