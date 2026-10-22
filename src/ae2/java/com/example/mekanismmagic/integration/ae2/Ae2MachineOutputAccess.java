package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.common.network.MachineAeNodeOwnershipHooks;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.LongUnaryOperator;

/** Paid outputs use the machine's existing node; this class never creates one. */
final class Ae2MachineOutputAccess {
    static final String PENDING_TAG = "magic_native_ae_pending_exports";
    private static final int MAX_INSERTS_PER_TICK = 8;
    private static final int REJECT_RETRY_TICKS = 20;
    private static final Map<BlockEntity, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Ae2MachineOutputAccess() { }

    static void exportItems(BlockEntity tile, IGrid grid, IActionSource actor) {
        if (!(tile instanceof IMekanismMagicAutomation machine)
                || !MachineAeResourceHooks.isOutputEnabled(tile, MachineAeResourceHooks.OUTPUT_ITEMS)
                || machine.mekanismMagicSupportsDirectNetworkOutput()
                || MachineAeNodeOwnershipHooks.externallyOwned(tile)) return;
        Set<IInventorySlot> protectedSlots = Collections.newSetFromMap(new IdentityHashMap<>());
        protectedSlots.addAll(machine.mekanismMagicPatternInputs());
        protectedSlots.addAll(machine.mekanismMagicPersistentInputs());
        protectedSlots.addAll(machine.mekanismMagicManualOnlySlots());
        Map<AEItemKey, List<IInventorySlot>> groups = new LinkedHashMap<>();
        for (IInventorySlot slot : machine.mekanismMagicPatternOutputs()) {
            if (protectedSlots.contains(slot)) continue;
            // EXTERNAL is essential: a condenser's partial working vessel is
            // player-extractable but not yet an automatic output.
            ItemStack preview = slot.extractItem(Integer.MAX_VALUE,
                    Action.SIMULATE, AutomationType.EXTERNAL);
            if (preview.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(preview);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
        }
        var outputs = List.copyOf(groups.entrySet());
        if (outputs.isEmpty()) return;
        int start = Math.floorMod(state(tile).itemCursor++, outputs.size());
        for (int offset = 0; offset < outputs.size(); offset++) {
            var group = outputs.get((start + offset) % outputs.size());
            AEItemKey key = group.getKey();
            long maximum = 0;
            for (IInventorySlot slot : group.getValue()) {
                ItemStack preview = slot.extractItem(Integer.MAX_VALUE,
                        Action.SIMULATE, AutomationType.EXTERNAL);
                if (!preview.isEmpty() && key.equals(AEItemKey.of(preview))) {
                    maximum += preview.getCount();
                }
            }
            Map<IInventorySlot, Integer> reservedSlots = new LinkedHashMap<>();
            exportResource(tile, grid, actor, key, maximum, 0,
                    requested -> {
                        long reserved = 0;
                        for (IInventorySlot slot : group.getValue()) {
                            int amount = (int) Math.min(Integer.MAX_VALUE, requested - reserved);
                            if (amount <= 0) break;
                            ItemStack current = slot.extractItem(amount,
                                    Action.SIMULATE, AutomationType.EXTERNAL);
                            if (current.isEmpty() || !key.equals(AEItemKey.of(current))) continue;
                            ItemStack extracted = slot.extractItem(amount,
                                    Action.EXECUTE, AutomationType.EXTERNAL);
                            reservedSlots.put(slot, extracted.getCount());
                            reserved += extracted.getCount();
                        }
                        return reserved;
                    }, requested -> {
                        long restored = 0;
                        for (var reservation : reservedSlots.entrySet()) {
                            int amount = (int) Math.min(reservation.getValue(), requested - restored);
                            if (amount <= 0) break;
                            restored += restoreItem(reservation.getKey(), key, amount);
                        }
                        return restored;
                    });
        }
    }

    /** Restore only our reserved stack; never overwrite callback-created output. */
    private static long restoreItem(IInventorySlot slot, AEItemKey key, int amount) {
        ItemStack current = slot.getStack();
        if (!current.isEmpty() && !key.equals(AEItemKey.of(current))) return 0;
        ItemStack template = key.toStack(1);
        int room = Math.max(0, Math.min(slot.getLimit(template), template.getMaxStackSize())
                - current.getCount());
        int restored = Math.min(amount, room);
        if (restored > 0) slot.setStack(key.toStack(current.getCount() + restored));
        return restored;
    }

    /**
     * Preview capacity, reserve locally BEFORE any network callback, commit,
     * then restore rejected units. A rollback which cannot fit remains owned
     * by the machine in a saved escrow, not a free network refund.
     */
    static long exportResource(BlockEntity tile, IGrid grid, IActionSource actor,
                               AEKey key, long maximum, int resourceBit,
                               LongUnaryOperator reserve, LongUnaryOperator restore) {
        if (tile == null || tile.isRemoved() || tile.getLevel() == null
                || tile.getLevel().isClientSide() || key == null || maximum <= 0) return 0;
        int outputKind = resourceBit == 0 ? MachineAeResourceHooks.OUTPUT_ITEMS : resourceBit;
        if (!MachineAeResourceHooks.isOutputEnabled(tile, outputKind)) return 0;
        State state = state(tile);
        if (!begin(tile, state, key)) return 0;
        try {
            long preview = bounded(maximum, StorageHelper.poweredInsert(
                    grid.getEnergyService(), grid.getStorageService().getInventory(),
                    key, maximum, actor, Actionable.SIMULATE));
            if (preview <= 0) {
                reject(tile, state, key);
                return 0;
            }
            // Simulation is external code too: it may remove/replace this BE.
            if (tile.isRemoved() || tile.getLevel().getBlockEntity(tile.getBlockPos()) != tile
                    || !MachineAeResourceHooks.isOutputEnabled(tile, outputKind)) return 0;
            long reserved = bounded(preview, reserve.applyAsLong(preview));
            if (reserved <= 0) return 0;
            state.pending.merge(key, reserved, Math::addExact);
            tile.setChanged();
            long inserted = 0;
            state.inFlight = key;
            try {
                inserted = bounded(reserved, StorageHelper.poweredInsert(
                        grid.getEnergyService(), grid.getStorageService().getInventory(),
                        key, reserved, actor));
                debitPending(state, key, inserted);
            } catch (RuntimeException failure) {
                // A broken storage callback may throw AFTER committing. Its
                // amount is unknowable: neither refund nor automatic replay
                // is safe. Preserve an explicitly quarantined reservation.
                state.uncertain.add(key);
                tile.setChanged();
                MekanismMagic.LOGGER.error("AE output commit failed for {} at {}; reserved {} of {} quarantined",
                        tile.getType(), tile.getBlockPos(), reserved, key, failure);
                return 0;
            } finally {
                state.inFlight = null;
            }
            long unaccepted = reserved - inserted;
            if (unaccepted > 0) {
                state.inFlight = key;
                try {
                    long restored = bounded(unaccepted, restore.applyAsLong(unaccepted));
                    debitPending(state, key, restored);
                } catch (RuntimeException failure) {
                    state.uncertain.add(key);
                    MekanismMagic.LOGGER.error("AE output rollback failed at {}; reservation quarantined",
                            tile.getBlockPos(), failure);
                } finally {
                    state.inFlight = null;
                }
            }
            tile.setChanged();
            if (inserted < reserved) reject(tile, state, key);
            MachineAeResourceHooks.recordTransfer(tile, resourceBit, inserted);
            return inserted;
        } finally {
            state.busy = false;
        }
    }

    /** Replay already-owned units before touching new outputs, even with supply off. */
    static boolean flushPending(BlockEntity tile, IGrid grid, IActionSource actor) {
        State state = state(tile);
        if (state.busy || tile.isRemoved()) return false;
        for (var entry : List.copyOf(state.pending.entrySet())) {
            if (state.uncertain.contains(entry.getKey())) continue;
            if (!begin(tile, state, entry.getKey())) continue;
            state.inFlight = entry.getKey();
            try {
                long inserted = bounded(entry.getValue(), StorageHelper.poweredInsert(
                        grid.getEnergyService(), grid.getStorageService().getInventory(),
                        entry.getKey(), entry.getValue(), actor));
                debitPending(state, entry.getKey(), inserted);
                if (inserted < entry.getValue()) reject(tile, state, entry.getKey());
                if (inserted > 0) tile.setChanged();
            } catch (RuntimeException failure) {
                state.uncertain.add(entry.getKey());
                tile.setChanged();
                MekanismMagic.LOGGER.error("AE pending output commit failed at {}; reservation quarantined",
                        tile.getBlockPos(), failure);
            } finally {
                state.inFlight = null;
                state.busy = false;
            }
        }
        return state.pending.isEmpty() && state.dormant.isEmpty();
    }

    private static boolean begin(BlockEntity tile, State state, AEKey key) {
        if (state.busy || tile.isRemoved() || tile.getLevel() == null
                || tile.getLevel().getBlockEntity(tile.getBlockPos()) != tile) return false;
        long now = tile.getLevel().getGameTime();
        if (now != state.tick) {
            state.tick = now;
            state.inserts = 0;
            state.attempted.clear();
            state.retryAfter.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        if (state.inserts >= MAX_INSERTS_PER_TICK || state.retryAfter.containsKey(key)
                || !state.attempted.add(key)) return false;
        state.inserts++;
        state.busy = true;
        return true;
    }

    private static void reject(BlockEntity tile, State state, AEKey key) {
        state.retryAfter.put(key, tile.getLevel().getGameTime() + REJECT_RETRY_TICKS);
    }

    private static long bounded(long maximum, long value) {
        return Math.max(0, Math.min(maximum, value));
    }

    private static void debitPending(State state, AEKey key, long amount) {
        long remaining = state.pending.getOrDefault(key, 0L) - amount;
        if (remaining <= 0) state.pending.remove(key);
        else state.pending.put(key, remaining);
    }

    private static State state(BlockEntity tile) {
        return STATES.computeIfAbsent(tile, ignored -> new State());
    }

    static void save(BlockEntity tile, CompoundTag tag, HolderLookup.Provider registries) {
        tag.remove(PENDING_TAG);
        State state = STATES.get(tile);
        if (state == null || state.pending.isEmpty() && state.dormant.isEmpty()) return;
        ListTag saved = state.dormant.copy();
        state.pending.forEach((key, amount) -> {
            CompoundTag entry = GenericStack.writeTag(registries, new GenericStack(key, amount));
            // A callback may save/drop the tile before insertion acknowledges
            // its accepted count. Such a snapshot must never replay blindly.
            if (state.uncertain.contains(key) || key.equals(state.inFlight)) {
                entry.putBoolean("uncertain", true);
            }
            saved.add(entry);
        });
        tag.put(PENDING_TAG, saved);
    }

    static void load(BlockEntity tile, CompoundTag tag, HolderLookup.Provider registries) {
        // Replace, do not merge: revive/load can be called on the same object twice.
        State loaded = new State();
        for (Tag entry : tag.getList(PENDING_TAG, Tag.TAG_COMPOUND)) {
            GenericStack stack;
            try {
                stack = GenericStack.readTag(registries, (CompoundTag) entry);
            } catch (RuntimeException missingKeyProvider) {
                loaded.dormant.add(entry.copy());
                continue;
            }
            // AE2 can report a successful decode as a missing-content ITEM.
            // Never turn an unavailable resource into that placeholder: keep
            // exact encoded ownership until a future load can decode it.
            if (stack == null || stack.what().get(AEComponents.MISSING_CONTENT_AEKEY_DATA) != null) {
                loaded.dormant.add(entry.copy());
                continue;
            }
            if (stack != null && stack.amount() > 0) loaded.pending.merge(
                    stack.what(), stack.amount(), (a, b) -> a > Long.MAX_VALUE - b
                            ? Long.MAX_VALUE : a + b);
            if (stack != null && ((CompoundTag) entry).getBoolean("uncertain")) {
                loaded.uncertain.add(stack.what());
            }
        }
        STATES.put(tile, loaded);
    }

    private static final class State {
        private final ListTag dormant = new ListTag();
        private final Map<AEKey, Long> pending = new LinkedHashMap<>();
        private final Map<AEKey, Long> retryAfter = new LinkedHashMap<>();
        private final Set<AEKey> attempted = new HashSet<>();
        private final Set<AEKey> uncertain = new HashSet<>();
        private long tick = Long.MIN_VALUE;
        private int inserts;
        private int itemCursor;
        private boolean busy;
        private AEKey inFlight;
    }
}
