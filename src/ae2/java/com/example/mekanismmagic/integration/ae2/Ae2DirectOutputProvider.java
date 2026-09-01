package com.example.mekanismmagic.integration.ae2;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.features.IPlayerRegistry;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.network
        .MachineDirectOutputHooks;
import mekanism.api.Action;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Native, always-present AE output node for selected producer machines. */
public final class Ae2DirectOutputProvider
        implements IInWorldGridNodeHost, IActionHost {
    private static final String NODE_TAG = "mekanism_magic_direct_ae";
    private static final int MAX_POWERED_INSERTS_PER_TICK = 8;
    private static final int REJECTED_KEY_RETRY_TICKS = 20;
    private static final Map<NativeMagicMachineBlockEntity,
            Ae2DirectOutputProvider> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static final MachineDirectOutputHooks.Handler HOOK =
            new DirectOutputHook();

    private final WeakReference<NativeMagicMachineBlockEntity> tileReference;
    private IManagedGridNode node;
    private final IActionSource actionSource;
    private CompoundTag retainedNodeData;
    private final Map<AEItemKey, Long> retryAfterByKey = new HashMap<>();
    private final Set<AEItemKey> attemptedKeysThisTick = new HashSet<>();
    private long insertBudgetGameTime = Long.MIN_VALUE;
    private int poweredInsertsThisTick;

    private Ae2DirectOutputProvider(NativeMagicMachineBlockEntity tile) {
        tileReference = new WeakReference<>(tile);
        node = createConfiguredNode(tile);
        actionSource = IActionSource.ofMachine(this);
    }

    private IManagedGridNode createConfiguredNode(
            NativeMagicMachineBlockEntity tile) {
        ItemStack visual = new ItemStack(tile.getBlockState().getBlock());
        return GridHelper.createManagedNode(this, new Listener())
                .setInWorldNode(true)
                .setTagName(NODE_TAG)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(AEItemKey.of(visual));
    }

    private NativeMagicMachineBlockEntity tile() {
        return tileReference.get();
    }

    public static Ae2DirectOutputProvider forTile(
            NativeMagicMachineBlockEntity tile) {
        return INSTANCES.computeIfAbsent(
                tile, Ae2DirectOutputProvider::new);
    }

    private static Ae2DirectOutputProvider existing(
            NativeMagicMachineBlockEntity tile) {
        return INSTANCES.get(tile);
    }

    private static void remove(NativeMagicMachineBlockEntity tile) {
        Ae2DirectOutputProvider provider = INSTANCES.remove(tile);
        if (provider != null) {
            provider.node.destroy();
        }
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        createNodeIfNeeded();
        return node.getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        createNodeIfNeeded();
        return node.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    private void createNodeIfNeeded() {
        NativeMagicMachineBlockEntity tile = tile();
        if (tile == null || tile.isRemoved()) {
            return;
        }
        Level level = tile.getLevel();
        if (level == null || level.isClientSide() || node.isReady()) {
            return;
        }
        if (level instanceof ServerLevel serverLevel
                && tile.getOwnerUUID() != null) {
            int ownerId = IPlayerRegistry.getMapping(serverLevel)
                    .getPlayerId(tile.getOwnerUUID());
            node.setOwningPlayerId(ownerId);
        }
        node.create(level, tile.getBlockPos());
        retainedNodeData = null;
    }

    private void createNodeOnFirstTick() {
        NativeMagicMachineBlockEntity tile = tile();
        if (tile != null && !tile.isRemoved()) {
            GridHelper.onFirstTick(tile, ignored -> createNodeIfNeeded());
        }
    }

    private boolean exportOutputs() {
        NativeMagicMachineBlockEntity tile = tile();
        if (tile == null || tile.isRemoved()) {
            return false;
        }
        if (tile.getLevel() == null || tile.getLevel().isClientSide()) {
            return false;
        }
        createNodeIfNeeded();
        if (!node.isActive()) {
            return false;
        }
        var grid = node.getGrid();
        if (grid == null) {
            return false;
        }
        long gameTime = tile.getLevel().getGameTime();
        beginInsertTick(gameTime);
        if (poweredInsertsThisTick >= MAX_POWERED_INSERTS_PER_TICK) {
            return false;
        }
        IEnergySource energy = grid.getEnergyService();
        var storage = grid.getStorageService().getInventory();
        Map<AEItemKey, SlotGroup> groups = new LinkedHashMap<>();
        for (IInventorySlot slot : tile.mekanismMagicPatternOutputs()) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }
            int available = Math.max(0, slot.getCount());
            available = Math.min(available,
                    slot.shrinkStack(available, Action.SIMULATE));
            if (available <= 0) {
                continue;
            }
            groups.computeIfAbsent(key, SlotGroup::new)
                    .add(slot, available);
        }
        boolean changed = false;
        for (SlotGroup group : groups.values()) {
            if (poweredInsertsThisTick >= MAX_POWERED_INSERTS_PER_TICK) {
                break;
            }
            long accepted = insertIntoNetworkDetailed(
                    energy, storage, group.key, group.amount, gameTime)
                    .accepted();
            if (accepted <= 0) {
                continue;
            }
            long remaining = accepted;
            for (SlotSource source : group.sources) {
                if (remaining <= 0) {
                    break;
                }
                int requested = (int) Math.min(
                        remaining, source.available);
                int removed = source.slot.shrinkStack(
                        requested, Action.EXECUTE);
                if (removed > 0) {
                    remaining -= removed;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private long insertDirect(ItemStack template, long amount) {
        return insertDirectDetailed(template, amount).accepted();
    }

    private MachineDirectOutputHooks.DirectInsertResult
    insertDirectDetailed(ItemStack template, long amount) {
        NativeMagicMachineBlockEntity tile = tile();
        if (tile == null || tile.isRemoved() || template.isEmpty()
                || amount <= 0 || tile.getLevel() == null
                || tile.getLevel().isClientSide()) {
            return new MachineDirectOutputHooks.DirectInsertResult(
                    0L, MachineDirectOutputHooks.DirectNetworkStatus.OFFLINE,
                    false, Long.MAX_VALUE);
        }
        createNodeIfNeeded();
        if (!node.isActive()) {
            return new MachineDirectOutputHooks.DirectInsertResult(
                    0L, MachineDirectOutputHooks.DirectNetworkStatus.OFFLINE,
                    false, Long.MAX_VALUE);
        }
        var grid = node.getGrid();
        if (grid == null) {
            return new MachineDirectOutputHooks.DirectInsertResult(
                    0L, MachineDirectOutputHooks.DirectNetworkStatus.OFFLINE,
                    false, Long.MAX_VALUE);
        }
        long gameTime = tile.getLevel().getGameTime();
        AEItemKey key = AEItemKey.of(template);
        if (key == null) {
            return new MachineDirectOutputHooks.DirectInsertResult(
                    0L, MachineDirectOutputHooks.DirectNetworkStatus.BLOCKED,
                    false, Long.MAX_VALUE);
        }
        return insertIntoNetworkDetailed(
                grid.getEnergyService(),
                grid.getStorageService().getInventory(),
                key, amount, gameTime);
    }

    private MachineDirectOutputHooks.DirectNetworkStatus networkStatus() {
        NativeMagicMachineBlockEntity tile = tile();
        if (tile == null || tile.isRemoved() || tile.getLevel() == null
                || tile.getLevel().isClientSide()) {
            return MachineDirectOutputHooks.DirectNetworkStatus.OFFLINE;
        }
        createNodeIfNeeded();
        if (!node.isActive() || node.getGrid() == null) {
            return MachineDirectOutputHooks.DirectNetworkStatus.OFFLINE;
        }
        long gameTime = tile.getLevel().getGameTime();
        retryAfterByKey.entrySet().removeIf(
                entry -> entry.getValue() <= gameTime);
        return retryAfterByKey.isEmpty()
                ? MachineDirectOutputHooks.DirectNetworkStatus.ONLINE
                : MachineDirectOutputHooks.DirectNetworkStatus.BLOCKED;
    }

    private MachineDirectOutputHooks.DirectInsertResult
    insertIntoNetworkDetailed(
            IEnergySource energy, MEStorage storage, AEItemKey key,
            long amount, long gameTime) {
        if (amount <= 0) {
            return new MachineDirectOutputHooks.DirectInsertResult(
                    0L, MachineDirectOutputHooks.DirectNetworkStatus.ONLINE,
                    false, Long.MAX_VALUE);
        }
        beginInsertTick(gameTime);
        long keyRetry = retryAfterByKey.getOrDefault(
                key, Long.MIN_VALUE);
        if (keyRetry > gameTime) {
            return new MachineDirectOutputHooks.DirectInsertResult(
                    0L, MachineDirectOutputHooks.DirectNetworkStatus.BLOCKED,
                    false, keyRetry);
        }
        if (poweredInsertsThisTick >= MAX_POWERED_INSERTS_PER_TICK
                || attemptedKeysThisTick.contains(key)) {
            return new MachineDirectOutputHooks.DirectInsertResult(
                    0L, MachineDirectOutputHooks.DirectNetworkStatus.ONLINE,
                    false, gameTime + 1);
        }
        attemptedKeysThisTick.add(key);
        poweredInsertsThisTick++;
        long inserted = Math.min(amount, Math.max(0L,
                StorageHelper.poweredInsert(
                        energy, storage, key, amount, actionSource)));
        if (inserted < amount) {
            long retryAt = gameTime + REJECTED_KEY_RETRY_TICKS;
            retryAfterByKey.put(key, retryAt);
            return new MachineDirectOutputHooks.DirectInsertResult(
                    inserted,
                    MachineDirectOutputHooks.DirectNetworkStatus.BLOCKED,
                    true, retryAt);
        } else {
            retryAfterByKey.remove(key);
            return new MachineDirectOutputHooks.DirectInsertResult(
                    inserted,
                    MachineDirectOutputHooks.DirectNetworkStatus.ONLINE,
                    true, Long.MAX_VALUE);
        }
    }

    private void beginInsertTick(long gameTime) {
        if (insertBudgetGameTime == gameTime) {
            return;
        }
        insertBudgetGameTime = gameTime;
        poweredInsertsThisTick = 0;
        attemptedKeysThisTick.clear();
        retryAfterByKey.entrySet().removeIf(
                entry -> entry.getValue() <= gameTime);
    }

    private void resetInsertScheduling() {
        // A POWER state change can be caused synchronously by the energy
        // consumed inside poweredInsert. Keep the current tick's call count
        // and attempted-key set intact so a state callback cannot bypass the
        // hard eight-call budget. The normal game-time rollover resets both.
        retryAfterByKey.clear();
    }

    private void save(CompoundTag tag) {
        if (node.isReady()) {
            node.saveToNBT(tag);
        } else if (retainedNodeData != null
                && retainedNodeData.contains(NODE_TAG)) {
            tag.put(NODE_TAG, retainedNodeData
                    .getCompound(NODE_TAG).copy());
        }
    }

    private void load(CompoundTag tag) {
        retainedNodeData = copyNodeData(tag);
        node.loadFromNBT(tag);
    }

    private void suspendNode() {
        NativeMagicMachineBlockEntity tile = tile();
        if (tile == null) {
            node.destroy();
            return;
        }
        CompoundTag saved = new CompoundTag();
        node.saveToNBT(saved);
        if (saved.contains(NODE_TAG)) {
            retainedNodeData = copyNodeData(saved);
        }
        node.destroy();
        node = createConfiguredNode(tile);
        if (retainedNodeData != null) {
            node.loadFromNBT(retainedNodeData);
        }
    }

    private static CompoundTag copyNodeData(CompoundTag source) {
        if (source == null || !source.contains(NODE_TAG)) {
            return null;
        }
        CompoundTag copy = new CompoundTag();
        copy.put(NODE_TAG, source.getCompound(NODE_TAG).copy());
        return copy;
    }

    private static final class Listener implements
            IGridNodeListener<Ae2DirectOutputProvider> {
        @Override
        public void onSaveChanges(Ae2DirectOutputProvider owner,
                                  IGridNode gridNode) {
            NativeMagicMachineBlockEntity tile = owner.tile();
            if (tile != null) {
                tile.setChanged();
            }
        }

        @Override
        public void onStateChanged(Ae2DirectOutputProvider owner,
                                   IGridNode gridNode, State state) {
            owner.resetInsertScheduling();
        }

        @Override
        public void onGridChanged(Ae2DirectOutputProvider owner,
                                  IGridNode gridNode) {
            owner.resetInsertScheduling();
        }
    }

    private static final class SlotGroup {
        private final AEItemKey key;
        private final List<SlotSource> sources = new ArrayList<>();
        private long amount;

        private SlotGroup(AEItemKey key) {
            this.key = key;
        }

        private void add(IInventorySlot slot, int available) {
            sources.add(new SlotSource(slot, available));
            amount += available;
        }
    }

    private record SlotSource(IInventorySlot slot, int available) {
    }

    private static final class DirectOutputHook
            implements MachineDirectOutputHooks.Handler {
        private static boolean supports(NativeMagicMachineBlockEntity tile) {
            return tile.mekanismMagicSupportsDirectNetworkOutput();
        }

        @Override
        public boolean tick(NativeMagicMachineBlockEntity tile) {
            return supports(tile) && forTile(tile).exportOutputs();
        }

        @Override
        public long insert(NativeMagicMachineBlockEntity tile,
                           ItemStack template, long amount) {
            return supports(tile)
                    ? forTile(tile).insertDirect(template, amount) : 0L;
        }

        @Override
        public MachineDirectOutputHooks.DirectInsertResult insertDetailed(
                NativeMagicMachineBlockEntity tile,
                ItemStack template, long amount) {
            return supports(tile)
                    ? forTile(tile).insertDirectDetailed(template, amount)
                    : new MachineDirectOutputHooks.DirectInsertResult(
                    0L, MachineDirectOutputHooks.DirectNetworkStatus
                    .UNAVAILABLE, false, Long.MAX_VALUE);
        }

        @Override
        public MachineDirectOutputHooks.DirectNetworkStatus status(
                NativeMagicMachineBlockEntity tile) {
            return supports(tile)
                    ? forTile(tile).networkStatus()
                    : MachineDirectOutputHooks.DirectNetworkStatus
                    .UNAVAILABLE;
        }

        @Override
        public void onLoad(NativeMagicMachineBlockEntity tile) {
            if (supports(tile)) {
                forTile(tile).createNodeOnFirstTick();
            }
        }

        @Override
        public void onRemoved(NativeMagicMachineBlockEntity tile) {
            if (supports(tile)) {
                Ae2DirectOutputProvider provider = existing(tile);
                if (provider != null) {
                    provider.suspendNode();
                }
            }
        }

        @Override
        public void onRevived(NativeMagicMachineBlockEntity tile) {
            if (supports(tile)) {
                forTile(tile).createNodeOnFirstTick();
            }
        }

        @Override
        public void onChunkUnloaded(NativeMagicMachineBlockEntity tile) {
            if (supports(tile)) {
                Ae2DirectOutputProvider provider = existing(tile);
                if (provider != null) {
                    provider.suspendNode();
                }
            }
        }

        @Override
        public void onBlockRemoved(NativeMagicMachineBlockEntity tile) {
            if (supports(tile)) {
                remove(tile);
            }
        }

        @Override
        public void save(NativeMagicMachineBlockEntity tile,
                         CompoundTag tag,
                         HolderLookup.Provider registries) {
            Ae2DirectOutputProvider provider = existing(tile);
            if (provider != null) {
                provider.save(tag);
            }
        }

        @Override
        public void load(NativeMagicMachineBlockEntity tile,
                         CompoundTag tag,
                         HolderLookup.Provider registries) {
            if (supports(tile)) {
                forTile(tile).load(tag);
            }
        }
    }
}
