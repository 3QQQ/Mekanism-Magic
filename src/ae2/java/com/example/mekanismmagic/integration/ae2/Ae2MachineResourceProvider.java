package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.common.network.MachineAeNodeOwnershipHooks;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** One fallback AE node for machines without a native or installed-upgrade node. */
public final class Ae2MachineResourceProvider
        implements IInWorldGridNodeHost, IActionHost {
    private static final String NODE_TAG = "mekanism_magic_resource_ae";
    private static final Set<String> EXISTING_NATIVE_NODES = Set.of(
            "dimension_miner", "drygmy_simulator", "imbuement_processor");
    private static final Map<TileEntityMekanism, Ae2MachineResourceProvider>
            INSTANCES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile OptionalCraftingService optionalCraftingService;

    private final WeakReference<TileEntityMekanism> tileReference;
    private IManagedGridNode node;
    private CompoundTag retainedNodeData;
    private boolean externallyOwned;
    private boolean awaitingExternalDestroy;

    private Ae2MachineResourceProvider(TileEntityMekanism tile) {
        tileReference = new WeakReference<>(tile);
        node = createNode(tile);
        externallyOwned = MachineAeNodeOwnershipHooks.externallyOwned(tile);
    }

    public static Ae2MachineResourceProvider forTile(
            TileEntityMekanism tile) {
        return INSTANCES.computeIfAbsent(tile,
                Ae2MachineResourceProvider::new);
    }

    static Ae2MachineResourceProvider existing(
            TileEntityMekanism tile) {
        return INSTANCES.get(tile);
    }

    static void remove(TileEntityMekanism tile) {
        Ae2MachineResourceProvider provider = INSTANCES.remove(tile);
        if (provider != null) provider.node.destroy();
    }

    /** Optional mod service is installed during setup, without hard-linking it. */
    static void registerOptionalCraftingService(OptionalCraftingService service) {
        optionalCraftingService = service;
    }

    static boolean hasExistingNativeNode(BlockEntity tile) {
        return tile instanceof IMekanismMagicAutomation host
                && (host.mekanismMagicSupportsDirectNetworkOutput()
                || EXISTING_NATIVE_NODES.contains(
                host.mekanismMagicMachineId().getPath()));
    }

    static boolean eligible(BlockEntity tile) {
        return tile instanceof TileEntityMekanism
                && tile instanceof IMekanismMagicAutomation
                && !hasExistingNativeNode(tile)
                && Ae2MachineResourceAccess.featureMask(tile) != 0;
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (BlockEntityType<?> type :
                net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            var id = net.minecraft.core.registries.BuiltInRegistries
                    .BLOCK_ENTITY_TYPE.getKey(type);
            if (id != null && MekanismMagic.MOD_ID.equals(id.getNamespace())
                    && !EXISTING_NATIVE_NODES.contains(id.getPath())) {
                registerType(event, type);
            }
        }
    }

    private static <T extends BlockEntity> void registerType(
            RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                type, (blockEntity, side) -> {
                    if (!eligible(blockEntity)) return null;
                    Ae2MachineResourceProvider provider = forTile(
                            (TileEntityMekanism) blockEntity);
                    return provider.isYielding() ? null : provider;
                });
    }

    private IManagedGridNode createNode(TileEntityMekanism tile) {
        IManagedGridNode configured = GridHelper.createManagedNode(this, new Listener())
                .setInWorldNode(true)
                .setTagName(NODE_TAG)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(appeng.api.stacks.AEItemKey.of(
                        new ItemStack(tile.getBlockState().getBlock())));
        OptionalCraftingService service = optionalCraftingService;
        if (service != null) service.attach(configured, tile, this);
        return configured;
    }

    private TileEntityMekanism tile() {
        return tileReference.get();
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        createNodeIfNeeded();
        return externallyOwned ? null : node.getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        createNodeIfNeeded();
        return externallyOwned ? null : node.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    void reconcileOwnership() {
        TileEntityMekanism tile = tile();
        if (tile == null || tile.isRemoved()) return;
        boolean nowOwned = MachineAeNodeOwnershipHooks.externallyOwned(tile)
                || awaitingExternalDestroy;
        if (nowOwned == externallyOwned) return;
        externallyOwned = nowOwned;
        if (nowOwned) {
            suspendNode();
        } else {
            createNodeIfNeeded();
        }
        tile.invalidateCapabilities();
    }

    boolean isYielding() {
        TileEntityMekanism tile = tile();
        return awaitingExternalDestroy || externallyOwned
                || tile != null && MachineAeNodeOwnershipHooks
                .externallyOwned(tile);
    }

    void beforeExternalNodeCreate(boolean active) {
        TileEntityMekanism tile = tile();
        // createNodeIfActive(false) never creates a second node. Do not latch
        // that path: an upgrade removed before activation needs the fallback.
        if (active && tile != null
                && MachineAeNodeOwnershipHooks.externallyOwned(tile)) {
            awaitingExternalDestroy = true;
        }
        reconcileOwnership();
    }

    void afterExternalNodeDestroy() {
        awaitingExternalDestroy = false;
        reconcileOwnership();
    }

    private void createNodeIfNeeded() {
        TileEntityMekanism tile = tile();
        if (tile == null || tile.isRemoved()
                || !(tile.getLevel() instanceof ServerLevel level)) return;
        reconcileOwnership();
        if (externallyOwned || node.isReady()) return;
        if (tile.getOwnerUUID() != null) {
            node.setOwningPlayerId(IPlayerRegistry.getMapping(level)
                    .getPlayerId(tile.getOwnerUUID()));
        }
        node.create(level, tile.getBlockPos());
        retainedNodeData = null;
    }

    void onLoad() {
        TileEntityMekanism tile = tile();
        if (tile != null && !tile.isRemoved()) {
            GridHelper.onFirstTick(tile, ignored -> createNodeIfNeeded());
        }
    }

    void suspendNode() {
        TileEntityMekanism tile = tile();
        if (tile == null) {
            node.destroy();
            return;
        }
        CompoundTag saved = new CompoundTag();
        node.saveToNBT(saved);
        if (saved.contains(NODE_TAG)) retainedNodeData = copyNodeData(saved);
        node.destroy();
        node = createNode(tile);
        if (retainedNodeData != null) node.loadFromNBT(retainedNodeData);
    }

    void save(CompoundTag tag) {
        if (node.isReady()) node.saveToNBT(tag);
        else if (retainedNodeData != null) {
            tag.put(NODE_TAG, retainedNodeData
                    .getCompound(NODE_TAG).copy());
        }
    }

    void load(CompoundTag tag) {
        retainedNodeData = copyNodeData(tag);
        node.loadFromNBT(tag);
    }

    private static CompoundTag copyNodeData(CompoundTag source) {
        if (source == null || !source.contains(NODE_TAG)) return null;
        CompoundTag result = new CompoundTag();
        result.put(NODE_TAG, source.getCompound(NODE_TAG).copy());
        return result;
    }

    boolean connected() {
        IGridNode current = node.getNode();
        return !externallyOwned && current != null
                && current.getGrid() != null
                && !current.getConnections().isEmpty();
    }

    boolean active() {
        return connected() && node.isActive();
    }

    private static final class Listener implements
            IGridNodeListener<Ae2MachineResourceProvider> {
        @Override
        public void onSaveChanges(Ae2MachineResourceProvider owner,
                                  IGridNode gridNode) {
            TileEntityMekanism tile = owner.tile();
            if (tile != null) tile.setChanged();
        }

        @Override
        public void onStateChanged(Ae2MachineResourceProvider owner,
                                   IGridNode gridNode, State state) { }

        @Override
        public void onGridChanged(Ae2MachineResourceProvider owner,
                                  IGridNode gridNode) { }
    }

    interface OptionalCraftingService {
        void attach(IManagedGridNode node, TileEntityMekanism tile,
                    Ae2MachineResourceProvider host);

        default void tick(BlockEntity tile, appeng.api.networking.IGrid grid,
                          appeng.api.networking.security.IActionSource actor) { }
    }

    static void tickOptionalCraftingService(BlockEntity tile,
                                             appeng.api.networking.IGrid grid,
                                             appeng.api.networking.security.IActionSource actor) {
        OptionalCraftingService service = optionalCraftingService;
        if (service != null) service.tick(tile, grid, actor);
    }
}
