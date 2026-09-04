package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Offline assertions for Source networks larger than the int capability. */
public final class MagicSourceNetworkStorageSelfTest {
    private MagicSourceNetworkStorageSelfTest() {
    }

    public static void main(String[] args) {
        AtomicLong capacity = new AtomicLong(6_000_000_000L);
        AtomicInteger changes = new AtomicInteger();
        NetworkSourceStorage storage = new NetworkSourceStorage(
                capacity::get, changes::incrementAndGet);

        require(storage.receiveSource(4_000_000_000L, true)
                        == 4_000_000_000L,
                "long insertion simulation was truncated");
        require(storage.getStoredSource() == 0 && changes.get() == 0,
                "long insertion simulation mutated storage");
        require(storage.receiveSource(4_000_000_000L, false)
                        == 4_000_000_000L,
                "first large network share was not accepted");
        require(storage.receiveSource(2_000_000_000, false)
                        == 2_000_000_000,
                "second network share was not accepted");
        require(storage.getStoredSource() == 6_000_000_000L,
                "network merge overflowed or lost Source");
        require(storage.getSource() == Integer.MAX_VALUE
                        && storage.getSourceCapacity() == Integer.MAX_VALUE,
                "int capability view did not saturate safely");
        require(storage.receiveSource(1, false) == 0,
                "full long network accepted extra Source");

        int extracted = storage.extractSource(Integer.MAX_VALUE, false);
        require(extracted == Integer.MAX_VALUE
                        && storage.getStoredSource() == 3_852_516_353L,
                "int-sized extraction corrupted long storage");
        require(storage.serializeNBT(null) instanceof LongTag tag
                        && tag.getAsLong() == 3_852_516_353L,
                "long network amount was truncated in serialization");

        storage.deserializeNBT(null, IntTag.valueOf(123_456));
        require(storage.getStoredSource() == 123_456,
                "legacy int Source tag was not restored");
        storage.setStoredSourceExact(5_500_000_000L);
        storage.setMaxSource(1);
        require(storage.getStoredSource() == 5_500_000_000L,
                "int setMaxSource truncated dynamic network capacity");

        capacity.set(5_000_000_000L);
        storage.clampToCapacity();
        require(storage.getStoredSource() == 5_000_000_000L,
                "capacity shrink did not clamp long storage");
        boolean rejected = false;
        try {
            storage.setStoredSourceExact(5_000_000_001L);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected,
                "exact merge commit silently clamped an invalid amount");

        capacity.set(6_000_000_000L);
        storage.setStoredSource(0);
        long inserted = 0;
        for (int i = 0; i < 3; i++) {
            inserted += storage.receiveSource(2_000_000_000, false);
        }
        require(inserted == 6_000_000_000L
                        && storage.getStoredSource() == inserted,
                "repeated ISourceCap insertion overflowed at int boundary");
        long drained = 0;
        while (storage.getStoredSource() > 0) {
            drained += storage.extractSource(Integer.MAX_VALUE, false);
        }
        require(drained == inserted,
                "repeated ISourceCap extraction lost long-network Source");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
