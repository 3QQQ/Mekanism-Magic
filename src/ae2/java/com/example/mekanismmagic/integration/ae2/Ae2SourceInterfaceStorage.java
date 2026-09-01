package com.example.mekanismmagic.integration.ae2;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.AECapabilities;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Adapts an Ars Énergistique Source-marked interface inventory to Ars. */
final class Ae2SourceInterfaceStorage implements ISourceCap {
    private static final String SOURCE_KEY_CLASS =
            "gripe._90.arseng.me.key.SourceKey";
    private static volatile boolean sourceKeyResolved;
    private static AEKey sourceKey;

    private final GenericInternalInventory inventory;
    private final AEKey key;
    private int insertSlotHint;
    private int extractSlotHint;

    private Ae2SourceInterfaceStorage(GenericInternalInventory inventory,
                                      AEKey key) {
        this.inventory = inventory;
        this.key = key;
    }

    static ISourceCap create(GenericInternalInventory inventory) {
        AEKey key = sourceKey();
        return inventory == null || key == null
                ? null : new Ae2SourceInterfaceStorage(inventory, key);
    }

    static @Nullable ISourceCap create(
            Level level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction side) {
        ISourceCap direct = create(level.getCapability(
                AECapabilities.GENERIC_INTERNAL_INV,
                pos, state, blockEntity, side));
        if (direct != null || side != null) {
            return direct;
        }
        // A null context is used by Ars relays and other unsided callers.
        // For a cable bus it addresses only the center cable, so fall back to
        // the six face parts in stable vanilla direction order.
        ISourceCap fallback = null;
        for (Direction candidate : Direction.values()) {
            ISourceCap faced = create(level.getCapability(
                    AECapabilities.GENERIC_INTERNAL_INV,
                    pos, state, blockEntity, candidate));
            if (faced == null) {
                continue;
            }
            if (fallback == null) {
                fallback = faced;
            }
            if (faced.canProvideSource(1)
                    || faced.canAcceptSource(1)) {
                return faced;
            }
        }
        return fallback;
    }

    private static AEKey sourceKey() {
        if (!sourceKeyResolved) {
            synchronized (Ae2SourceInterfaceStorage.class) {
                if (!sourceKeyResolved) {
                    try {
                        Object value = Class.forName(SOURCE_KEY_CLASS)
                                .getField("KEY").get(null);
                        if (value instanceof AEKey resolved) {
                            sourceKey = resolved;
                        }
                    } catch (ReflectiveOperationException
                             | LinkageError ignored) {
                        sourceKey = null;
                    }
                    sourceKeyResolved = true;
                }
            }
        }
        return sourceKey;
    }

    @Override
    public int getMaxExtract() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxReceive() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canAcceptSource(int amount) {
        return insert(1, Actionable.SIMULATE) > 0;
    }

    @Override
    public boolean canProvideSource(int amount) {
        return extract(1, Actionable.SIMULATE) > 0;
    }

    @Override
    public int getSource() {
        return extract(Integer.MAX_VALUE, Actionable.SIMULATE);
    }

    @Override
    public int getSourceCapacity() {
        long capacity = 0L;
        for (int slot = 0; slot < inventory.size(); slot++) {
            AEKey existing = inventory.getKey(slot);
            if (existing == null || existing == key) {
                capacity = saturatingAdd(capacity,
                        inventory.getMaxAmount(key));
            }
        }
        return saturatedInt(capacity);
    }

    @Override
    public int receiveSource(int amount, boolean simulate) {
        return insert(Math.max(0, amount),
                Actionable.ofSimulate(simulate));
    }

    @Override
    public int extractSource(int amount, boolean simulate) {
        return extract(Math.max(0, amount),
                Actionable.ofSimulate(simulate));
    }

    private int insert(int amount, Actionable mode) {
        if (amount <= 0 || inventory.size() <= 0) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            inventory.beginBatch();
            try {
                return insertAcrossSlots(amount, mode);
            } finally {
                // A multi-slot interface transfer must still notify AE, but
                // only once after every accepted slot has been committed.
                inventory.endBatch();
            }
        }
        return insertAcrossSlots(amount, mode);
    }

    private int insertAcrossSlots(int amount, Actionable mode) {
        long inserted = 0L;
        int slots = inventory.size();
        int start = Math.floorMod(insertSlotHint, slots);
        int firstAcceptedSlot = -1;
        int lastAcceptedSlot = -1;
        for (int offset = 0; offset < slots && inserted < amount; offset++) {
            int slot = (start + offset) % slots;
            long accepted = inventory.insert(
                    slot, key, amount - inserted, mode);
            if (accepted > 0) {
                if (firstAcceptedSlot < 0) {
                    firstAcceptedSlot = slot;
                }
                lastAcceptedSlot = slot;
                inserted += accepted;
            }
        }
        if (firstAcceptedSlot >= 0) {
            // Keep execution on the same first viable slot found by the
            // preceding simulation. After a real transfer, prefer the last
            // slot that still had useful capacity for the next operation.
            insertSlotHint = mode == Actionable.SIMULATE
                    ? firstAcceptedSlot : lastAcceptedSlot;
        }
        return saturatedInt(inserted);
    }

    private int extract(int amount, Actionable mode) {
        if (amount <= 0 || inventory.size() <= 0) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            inventory.beginBatch();
            try {
                return extractAcrossSlots(amount, mode);
            } finally {
                inventory.endBatch();
            }
        }
        return extractAcrossSlots(amount, mode);
    }

    private int extractAcrossSlots(int amount, Actionable mode) {
        long extracted = 0L;
        int slots = inventory.size();
        int start = Math.floorMod(extractSlotHint, slots);
        int firstExtractedSlot = -1;
        int lastExtractedSlot = -1;
        for (int offset = 0; offset < slots && extracted < amount; offset++) {
            int slot = (start + offset) % slots;
            long removed = inventory.extract(
                    slot, key, amount - extracted, mode);
            if (removed > 0) {
                if (firstExtractedSlot < 0) {
                    firstExtractedSlot = slot;
                }
                lastExtractedSlot = slot;
                extracted += removed;
            }
        }
        if (firstExtractedSlot >= 0) {
            extractSlotHint = mode == Actionable.SIMULATE
                    ? firstExtractedSlot : lastExtractedSlot;
        }
        return saturatedInt(extracted);
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    private static int saturatedInt(long value) {
        return (int) Math.max(0L,
                Math.min(Integer.MAX_VALUE, value));
    }

    @Override
    public void setMaxSource(int max) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSource(int source) {
        throw new UnsupportedOperationException();
    }
}
