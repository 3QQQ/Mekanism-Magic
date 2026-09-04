package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.common.network.MachineNetworkLifecycleHooks;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional native AE2 provider for Ars imbuement recipes.
 *
 * <p>The provider is a lifecycle sidecar: neither it nor its published
 * patterns strongly retain the host block entity. Node state is preserved
 * across chunk unload/revive and destroyed only when the block is actually
 * removed.</p>
 */
public final class Ae2ImbuementProvider
        implements IInWorldGridNodeHost, ICraftingProvider {
    private static final String NODE_TAG =
            "mekanism_magic_imbuement_ae";
    private static final Map<ImbuementProcessorBlockEntity,
            Ae2ImbuementProvider> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static final MachineNetworkLifecycleHooks.Handler LIFECYCLE_HOOK =
            new LifecycleHook();

    private final WeakReference<ImbuementProcessorBlockEntity> tileReference;
    private IManagedGridNode node;
    private CompoundTag retainedNodeData;

    private Ae2ImbuementProvider(ImbuementProcessorBlockEntity tile) {
        tileReference = new WeakReference<>(tile);
        node = createConfiguredNode(tile);
    }

    private IManagedGridNode createConfiguredNode(
            ImbuementProcessorBlockEntity tile) {
        return GridHelper.createManagedNode(this, new Listener())
                .setInWorldNode(true)
                .setTagName(NODE_TAG)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(1.0)
                .addService(ICraftingProvider.class, this)
                .setVisualRepresentation(AEItemKey.of(
                        new ItemStack(tile.getBlockState().getBlock())));
    }

    private ImbuementProcessorBlockEntity tile() {
        return tileReference.get();
    }

    public static Ae2ImbuementProvider forTile(
            ImbuementProcessorBlockEntity tile) {
        return INSTANCES.computeIfAbsent(tile, Ae2ImbuementProvider::new);
    }

    private static Ae2ImbuementProvider existing(
            ImbuementProcessorBlockEntity tile) {
        return INSTANCES.get(tile);
    }

    private static void remove(ImbuementProcessorBlockEntity tile) {
        Ae2ImbuementProvider provider = INSTANCES.remove(tile);
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
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    private void createNodeIfNeeded() {
        ImbuementProcessorBlockEntity tile = tile();
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
        ImbuementProcessorBlockEntity tile = tile();
        if (tile != null && !tile.isRemoved()) {
            GridHelper.onFirstTick(tile, ignored -> createNodeIfNeeded());
        }
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        ImbuementProcessorBlockEntity tile = tile();
        Level level = tile == null ? null : tile.getLevel();
        if (level == null) {
            return List.of();
        }
        return ArsNouveauRecipeScanner.scan(
                        level.getRecipeManager()).stream()
                .map(holder -> new Ae2ImbuementPattern(tile, holder))
                .filter(Ae2ImbuementPattern::isUsable)
                .map(pattern -> (IPatternDetails) pattern)
                .toList();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern,
                               KeyCounter[] inputItems) {
        ImbuementProcessorBlockEntity tile = tile();
        if (tile == null || tile.isRemoved()
                || !(pattern instanceof Ae2ImbuementPattern imbuement)
                || imbuement.tile() != tile
                || !imbuement.matchesCurrentRecipe(tile.getLevel())
                || isHostBusy(tile)) {
            return false;
        }
        ItemStack input = collectInput(
                imbuement, inputItems, tile.getLevel());
        if (input.isEmpty()) {
            return false;
        }
        return tile.acceptAutomatedImbuementInput(
                input,
                imbuement.requiresCatalystIdentifier(),
                imbuement.catalystId().toString());
    }

    private static ItemStack collectInput(
            Ae2ImbuementPattern pattern,
            KeyCounter[] inputItems,
            Level level) {
        if (level == null || inputItems == null
                || inputItems.length == 0) {
            return ItemStack.EMPTY;
        }
        AEItemKey selectedKey = null;
        long total = 0L;
        boolean found = false;
        for (KeyCounter counter : inputItems) {
            if (counter == null) {
                return ItemStack.EMPTY;
            }
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount < 0L
                        || !(entry.getKey() instanceof AEItemKey itemKey)
                        || !pattern.acceptsInput(itemKey, level)) {
                    return ItemStack.EMPTY;
                }
                if (amount == 0L) {
                    continue;
                }
                if (selectedKey != null && !selectedKey.equals(itemKey)) {
                    return ItemStack.EMPTY;
                }
                selectedKey = itemKey;
                found = true;
                try {
                    total = Math.addExact(total, amount);
                } catch (ArithmeticException overflow) {
                    return ItemStack.EMPTY;
                }
                if (total > Integer.MAX_VALUE) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return !found || selectedKey == null
                || total != pattern.expectedInputAmount()
                ? ItemStack.EMPTY
                : selectedKey.toStack((int) total);
    }

    @Override
    public boolean isBusy() {
        ImbuementProcessorBlockEntity tile = tile();
        return tile == null || tile.isRemoved() || isHostBusy(tile);
    }

    private static boolean isHostBusy(
            ImbuementProcessorBlockEntity tile) {
        return tile.mekanismMagicIsBusy()
                || tile.getNativeInputSlot() == null
                || !tile.getNativeInputSlot().getStack().isEmpty();
    }

    @Override
    public int getPatternPriority() {
        return 0;
    }

    static void refreshAllPatterns() {
        List<Ae2ImbuementProvider> providers;
        synchronized (INSTANCES) {
            providers = List.copyOf(INSTANCES.values());
        }
        for (Ae2ImbuementProvider provider : providers) {
            if (provider.tile() == null || !provider.node.isReady()) {
                continue;
            }
            IGridNode gridNode = provider.node.getNode();
            if (gridNode != null && gridNode.getGrid() != null) {
                gridNode.getGrid().getCraftingService()
                        .refreshNodeCraftingProvider(gridNode);
            }
        }
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
        ImbuementProcessorBlockEntity tile = tile();
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

    private static final class Listener
            implements IGridNodeListener<Ae2ImbuementProvider> {
        @Override
        public void onSaveChanges(Ae2ImbuementProvider owner,
                                  IGridNode gridNode) {
            ImbuementProcessorBlockEntity tile = owner.tile();
            if (tile != null) {
                tile.setChanged();
            }
        }
    }

    private static final class LifecycleHook
            implements MachineNetworkLifecycleHooks.Handler {
        private static ImbuementProcessorBlockEntity supported(
                NativeMagicMachineBlockEntity tile) {
            return tile instanceof ImbuementProcessorBlockEntity imbuement
                    ? imbuement : null;
        }

        @Override
        public void onLoad(NativeMagicMachineBlockEntity tile) {
            ImbuementProcessorBlockEntity imbuement = supported(tile);
            if (imbuement != null) {
                forTile(imbuement).createNodeOnFirstTick();
            }
        }

        @Override
        public void onRemoved(NativeMagicMachineBlockEntity tile) {
            ImbuementProcessorBlockEntity imbuement = supported(tile);
            Ae2ImbuementProvider provider = imbuement == null
                    ? null : existing(imbuement);
            if (provider != null) {
                provider.suspendNode();
            }
        }

        @Override
        public void onRevived(NativeMagicMachineBlockEntity tile) {
            ImbuementProcessorBlockEntity imbuement = supported(tile);
            if (imbuement != null) {
                forTile(imbuement).createNodeOnFirstTick();
            }
        }

        @Override
        public void onChunkUnloaded(NativeMagicMachineBlockEntity tile) {
            ImbuementProcessorBlockEntity imbuement = supported(tile);
            Ae2ImbuementProvider provider = imbuement == null
                    ? null : existing(imbuement);
            if (provider != null) {
                provider.suspendNode();
            }
        }

        @Override
        public void onBlockRemoved(NativeMagicMachineBlockEntity tile) {
            ImbuementProcessorBlockEntity imbuement = supported(tile);
            if (imbuement != null) {
                remove(imbuement);
            }
        }

        @Override
        public void save(NativeMagicMachineBlockEntity tile,
                         CompoundTag tag,
                         HolderLookup.Provider registries) {
            ImbuementProcessorBlockEntity imbuement = supported(tile);
            Ae2ImbuementProvider provider = imbuement == null
                    ? null : existing(imbuement);
            if (provider != null) {
                provider.save(tag);
            }
        }

        @Override
        public void load(NativeMagicMachineBlockEntity tile,
                         CompoundTag tag,
                         HolderLookup.Provider registries) {
            ImbuementProcessorBlockEntity imbuement = supported(tile);
            if (imbuement != null) {
                forTile(imbuement).load(tag);
            }
        }
    }
}
