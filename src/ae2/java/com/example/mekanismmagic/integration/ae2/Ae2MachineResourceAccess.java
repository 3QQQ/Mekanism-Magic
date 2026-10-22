package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.common.network.MachineAeNodeOwnershipHooks;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceRatePolicy;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IEnergyConversionHelper;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Pulls bounded native resources through the machine's sole active AE node. */
public final class Ae2MachineResourceAccess
        implements MachineAeResourceHooks.Handler {
    public static final long MAX_ENERGY_J_PER_TICK =
            MachineAeResourceRatePolicy.MAX_ENERGY_J_PER_TICK;
    private static final double MIN_AE_RESERVE = 10.0;
    private static final double AE_RESERVE_FRACTION = 0.05;
    private static final String PENDING_TAG = "magic_native_ae_pending_refunds";
    private static final List<ResourceExtension> EXTENSIONS =
            new CopyOnWriteArrayList<>();
    private static final Map<BlockEntity, NodeState> NODE_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<BlockEntity, PendingRefunds> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile ExternalFeSource externalFeSource;
    public static final Ae2MachineResourceAccess INSTANCE =
            new Ae2MachineResourceAccess();

    private Ae2MachineResourceAccess() { }

    public static void register(ResourceExtension extension) {
        if (extension != null) EXTENSIONS.add(extension);
    }

    public static void registerExternalFeSource(ExternalFeSource source) {
        externalFeSource = source;
    }

    static int resourceMask(BlockEntity tile) {
        // Keep the legacy status/Jade resource field at its original seven
        // resource bits. Item/fluid direction controls have separate trackers.
        return featureMask(tile) & MachineAeResourceHooks.ALL_INPUT_MASK;
    }

    static int featureMask(BlockEntity tile) {
        return INSTANCE.inputSupportMask(tile) | INSTANCE.outputSupportMask(tile);
    }

    @Override
    public int inputSupportMask(BlockEntity tile) {
        if (!(tile instanceof IMekanismMagicAutomation machine)) return 0;
        int mask = machine.mekanismMagicEnergyContainer() == null
                ? 0 : MachineAeResourceHooks.ENERGY;
        for (ResourceExtension extension : EXTENSIONS) {
            mask |= extension.inputMask(tile);
        }
        return mask;
    }

    @Override
    public int outputSupportMask(BlockEntity tile) {
        if (!(tile instanceof IMekanismMagicAutomation machine)) return 0;
        int mask = machine.mekanismMagicPatternOutputs().isEmpty()
                ? 0 : MachineAeResourceHooks.OUTPUT_ITEMS;
        // Drygmy owns the only real fluid-production queue. Essence's PFAA
        // tank views share its magic ledger and are not a second AE output.
        if ("drygmy_simulator".equals(machine.mekanismMagicMachineId().getPath())) {
            mask |= MachineAeResourceHooks.OUTPUT_FLUIDS;
        }
        for (ResourceExtension extension : EXTENSIONS) {
            mask |= extension.outputMask(tile);
        }
        return mask;
    }

    @Override
    public MachineAeResourceHooks.Connection connection(BlockEntity tile) {
        int mask = resourceMask(tile);
        if (featureMask(tile) == 0) return MachineAeResourceHooks.Connection.UNSUPPORTED;
        // This read path deliberately never creates a managed node or pulls a
        // resource. It reflects the most recent server tick instead.
        NodeState observed = NODE_STATES.get(tile);
        return new MachineAeResourceHooks.Connection(true,
                observed != null && observed.connected,
                observed != null && observed.active, mask);
    }

    @Override
    public void tick(BlockEntity tile) {
        if (!(tile instanceof TileEntityMekanism machine)
                || !(tile instanceof IMekanismMagicAutomation automation)
                || !(tile.getLevel() instanceof ServerLevel level)) return;
        int mask = resourceMask(tile);
        if (featureMask(tile) == 0) return;

        // Arbitration runs even with supply switched off: installing a MekE
        // upgrade must retire the fallback node immediately, not leave a
        // second channel until this machine is re-enabled.
        if (Ae2MachineResourceProvider.eligible(tile)) {
            Ae2MachineResourceProvider fallback =
                    Ae2MachineResourceProvider.forTile(machine);
            fallback.reconcileOwnership();
            if (!MachineAeNodeOwnershipHooks.externallyOwned(tile)) {
                fallback.getActionableNode();
            }
        }
        IInWorldGridNodeHost host = level.getCapability(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                tile.getBlockPos(), (Void) null);
        IGridNode node = findNode(host);
        boolean connected = node != null && node.getGrid() != null
                && !node.getConnections().isEmpty();
        boolean active = connected && node.isActive();
        NODE_STATES.put(tile, new NodeState(connected, active));
        if (!active) return;
        IGrid grid = node.getGrid();
        IActionHost actionHost = host instanceof IActionHost actionable
                ? actionable : () -> node;
        IActionSource source = IActionSource.ofMachine(actionHost);
        if (!flushPending(tile, grid, source)) return;
        if (!Ae2MachineOutputAccess.flushPending(tile, grid, source)) return;
        for (ResourceExtension extension : EXTENSIONS) {
            if (extension.outputMask(tile) != 0) {
                extension.export(tile, grid, source);
            }
        }
        Ae2MachineOutputAccess.exportItems(tile, grid, source);
        Ae2MachineResourceProvider.tickOptionalCraftingService(
                tile, grid, source);
        if ((mask & MachineAeResourceHooks.ENERGY) != 0
                && MachineAeResourceHooks.isInputEnabled(tile, MachineAeResourceHooks.ENERGY)) {
            refillEnergy(tile, automation.mekanismMagicEnergyContainer(),
                    grid, source);
        }
        for (ResourceExtension extension : EXTENSIONS) {
            if (extension.inputMask(tile) != 0) {
                extension.tick(tile, grid, source);
            }
        }
    }

    private static IGridNode findNode(IInWorldGridNodeHost host) {
        if (host == null) return null;
        if (host instanceof IActionHost actionHost) {
            IGridNode candidate = actionHost.getActionableNode();
            if (candidate != null) return candidate;
        }
        for (Direction side : Direction.values()) {
            IGridNode candidate = host.getGridNode(side);
            if (candidate != null) return candidate;
        }
        return host.getGridNode(null);
    }

    private static void refillEnergy(BlockEntity tile,
                                     IEnergyContainer local, IGrid grid,
                                     IActionSource actor) {
        // MekE's installed runtime already refills its own local energy. A
        // second native pull would debit the network twice in the same tick.
        if (local == null || grid == null
                || MachineAeNodeOwnershipHooks.externallyOwned(tile)) return;
        IEnergyConversion conversion = IEnergyConversionHelper.INSTANCE
                .feConversion();
        if (!conversion.isEnabled() || !Double.isFinite(
                conversion.getConversion())
                || conversion.getConversion() <= 0.0) return;
        long freeJ = Math.max(0L,
                local.getMaxEnergy() - Math.max(0L, local.getEnergy()));
        long nominal = local instanceof MachineEnergyContainer<?> machineEnergy
                ? machineEnergy.getEnergyPerTick() : 0L;
        int parallelLanes = tile instanceof IMekanismMagicAutomation host
                && host.mekanismMagicGroupParallelItemInputs()
                ? Math.max(1, host.mekanismMagicPatternInputs().size()) : 1;
        long budgetJ = MachineAeResourceRatePolicy.energyBudget(
                local.getMaxEnergy(), freeJ, nominal, parallelLanes);
        if (budgetJ <= 0L) return;
        // Restrict the transaction to whole FE units. This avoids treating
        // Mekanism Joules as FE or creating fractional conversion energy.
        long wholeFe = conversion.convertTo(budgetJ);
        if (wholeFe <= 0L) return;
        long offerJ = conversion.convertFrom(wholeFe);
        long previewJ = offerJ - local.insert(offerJ,
                Action.SIMULATE, AutomationType.EXTERNAL);
        wholeFe = Math.min(wholeFe, conversion.convertTo(
                Math.max(0L, previewJ)));
        if (wholeFe <= 0L) return;

        ExternalFeSource storedFe = externalFeSource;
        if (storedFe != null) {
            long available = Math.max(0L, Math.min(wholeFe,
                    storedFe.extractFe(grid, actor, wholeFe,
                            Actionable.SIMULATE)));
            if (available > 0) {
                long withdrawn = Math.max(0L, Math.min(available,
                        storedFe.extractFe(grid, actor, available,
                                Actionable.MODULATE)));
                if (withdrawn > 0) {
                    long acceptedJ = insertConvertedFe(local,
                            conversion, withdrawn);
                    long chargedFe = Math.min(withdrawn,
                            ceilToLong(conversion.convertToDouble(
                                    acceptedJ)));
                    long refundFe = withdrawn - chargedFe;
                    if (refundFe > 0) {
                        long restored = Math.max(0L, Math.min(refundFe,
                                storedFe.refundFe(grid, actor, refundFe)));
                        if (restored < refundFe) {
                            pending(tile).fe += refundFe - restored;
                            tile.setChanged();
                        }
                    }
                    MachineAeResourceHooks.recordTransfer(tile,
                            MachineAeResourceHooks.ENERGY, acceptedJ);
                    return;
                }
            }
        }

        IEnergyService network = grid.getEnergyService();
        double stored = network.getStoredPower();
        double capacity = network.getMaxStoredPower();
        if (!Double.isFinite(stored) || stored <= 0.0) return;
        double reserve = MIN_AE_RESERVE;
        if (Double.isFinite(capacity) && capacity > 0.0) {
            reserve = Math.max(reserve,
                    capacity * AE_RESERVE_FRACTION);
        }
        double spendable = Math.max(0.0, stored - reserve);
        long affordableFe = (long) Math.floor(
                PowerUnit.AE.convertTo(PowerUnit.FE, spendable));
        wholeFe = Math.min(wholeFe, affordableFe);
        if (wholeFe <= 0L) return;
        double requestAe = PowerUnit.FE.convertTo(PowerUnit.AE,
                wholeFe);
        double extractedAe = network.extractAEPower(
                requestAe, Actionable.MODULATE, PowerMultiplier.ONE);
        if (!Double.isFinite(extractedAe) || extractedAe <= 0.0) return;
        long obtainedFe = Math.min(wholeFe,
                Math.max(0L, (long) Math.floor(
                        PowerUnit.AE.convertTo(PowerUnit.FE,
                                extractedAe))));
        long acceptedJ = insertConvertedFe(local, conversion,
                obtainedFe);
        long chargedFe = Math.min(obtainedFe,
                ceilToLong(conversion.convertToDouble(acceptedJ)));
        double usedAe = PowerUnit.FE.convertTo(PowerUnit.AE,
                chargedFe);
        double refundAe = Math.max(0.0, extractedAe - usedAe);
        if (refundAe > 0.0) {
            double rejected = network.injectPower(refundAe,
                    Actionable.MODULATE);
            if (rejected > 0.000001) {
                pending(tile).ae += rejected;
                tile.setChanged();
            }
        }
        MachineAeResourceHooks.recordTransfer(tile,
                MachineAeResourceHooks.ENERGY, acceptedJ);
    }

    private static long insertConvertedFe(IEnergyContainer local,
                                           IEnergyConversion conversion,
                                           long fe) {
        if (fe <= 0L) return 0L;
        long joules = conversion.convertFrom(fe);
        if (joules <= 0L) return 0L;
        long remainder = local.insert(joules, Action.EXECUTE,
                AutomationType.EXTERNAL);
        return Math.max(0L, joules - Math.max(0L, remainder));
    }

    private static long ceilToLong(double value) {
        if (!Double.isFinite(value) || value <= 0.0) return 0L;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE
                : (long) Math.ceil(value);
    }

    private static PendingRefunds pending(BlockEntity tile) {
        return PENDING.computeIfAbsent(tile,
                ignored -> new PendingRefunds());
    }

    public static boolean hasPendingRefunds(BlockEntity tile) {
        PendingRefunds state = PENDING.get(tile);
        return state != null && !state.isEmpty();
    }

    /** Return magic units without a second powered transaction; queue any shortfall. */
    public static void refundOrQueue(BlockEntity tile, IGrid grid,
                                     IActionSource actor, AEKey key,
                                     long amount) {
        if (amount <= 0 || tile == null || grid == null || key == null)
            return;
        ResourceLocation id = MagicResourceKeys.resourceId(key);
        if (id == null) {
            throw new IllegalArgumentException(
                    "Cannot persist noncanonical AE magic refund key " + key);
        }
        long restored = grid.getStorageService().getInventory().insert(
                key, amount, Actionable.MODULATE,
                actor);
        long remainder = amount - Math.max(0L,
                Math.min(amount, restored));
        if (remainder > 0L) {
            pending(tile).magic.merge(id, remainder,
                    Math::addExact);
            tile.setChanged();
        }
    }

    /** Replays debts before any new withdrawal, including while supply is off. */
    private static boolean flushPending(BlockEntity tile, IGrid grid,
                                        IActionSource actor) {
        PendingRefunds refunds = PENDING.get(tile);
        if (refunds == null || refunds.isEmpty()) return true;
        MEStorage storage = grid.getStorageService().getInventory();
        for (var entry : List.copyOf(refunds.magic.entrySet())) {
            AEKey key = MagicResourceKeys.key(entry.getKey());
            if (key == null) continue;
            long requested = entry.getValue();
            long restored = storage.insert(key, requested,
                    Actionable.MODULATE, actor);
            long remaining = requested - Math.max(0L,
                    Math.min(requested, restored));
            if (remaining == 0L) refunds.magic.remove(entry.getKey());
            else refunds.magic.put(entry.getKey(), remaining);
        }
        if (refunds.ae > 0.0) {
            refunds.ae = Math.max(0.0,
                    grid.getEnergyService().injectPower(
                            refunds.ae, Actionable.MODULATE));
        }
        ExternalFeSource storedFe = externalFeSource;
        if (refunds.fe > 0L && storedFe != null) {
            long restored = storedFe.refundFe(grid, actor, refunds.fe);
            refunds.fe -= Math.max(0L,
                    Math.min(refunds.fe, restored));
        }
        tile.setChanged();
        if (refunds.isEmpty()) {
            PENDING.remove(tile);
            return true;
        }
        return false;
    }

    @Override
    public void onLoad(BlockEntity tile) {
        if (Ae2MachineResourceProvider.eligible(tile)
                && !MachineAeNodeOwnershipHooks.externallyOwned(tile)) {
            Ae2MachineResourceProvider.forTile(
                    (TileEntityMekanism) tile).onLoad();
        }
    }

    @Override
    public void onRemoved(BlockEntity tile) {
        suspend(tile);
    }

    @Override
    public void onRevived(BlockEntity tile) {
        onLoad(tile);
    }

    @Override
    public void onOwnershipChanged(BlockEntity tile) {
        // This callback runs before MekE creates, or just after it destroys,
        // its managed node. Only arbitrate node ownership here; a resource
        // tick/capability lookup would recurse into MekE's transition.
        if (Ae2MachineResourceProvider.eligible(tile)
                && tile instanceof TileEntityMekanism machine) {
            Ae2MachineResourceProvider provider =
                    Ae2MachineResourceProvider.existing(machine);
            if (provider != null) provider.reconcileOwnership();
        }
        NODE_STATES.remove(tile);
    }

    @Override
    public void beforeExternalNodeCreate(BlockEntity tile,
                                         boolean active) {
        if (Ae2MachineResourceProvider.eligible(tile)
                && tile instanceof TileEntityMekanism machine) {
            Ae2MachineResourceProvider provider =
                    active ? Ae2MachineResourceProvider.forTile(machine)
                            : Ae2MachineResourceProvider.existing(machine);
            if (provider != null) provider.beforeExternalNodeCreate(active);
        }
        NODE_STATES.remove(tile);
    }

    @Override
    public void afterExternalNodeDestroy(BlockEntity tile) {
        if (Ae2MachineResourceProvider.eligible(tile)
                && tile instanceof TileEntityMekanism machine) {
            Ae2MachineResourceProvider provider =
                    Ae2MachineResourceProvider.existing(machine);
            if (provider != null) provider.afterExternalNodeDestroy();
        }
        NODE_STATES.remove(tile);
    }

    @Override
    public void onChunkUnloaded(BlockEntity tile) {
        suspend(tile);
    }

    private static void suspend(BlockEntity tile) {
        NODE_STATES.remove(tile);
        if (tile instanceof TileEntityMekanism machine) {
            Ae2MachineResourceProvider provider =
                    Ae2MachineResourceProvider.existing(machine);
            if (provider != null) provider.suspendNode();
        }
    }

    @Override
    public void onBlockRemoved(BlockEntity tile) {
        NODE_STATES.remove(tile);
        // Do not discard an outstanding debt here. MachineDropPreserver calls
        // saveToItem on the removed tile, which carries the pending NBT into
        // its dropped block item; a later placement can replay the refund.
        // The weak-map entry disappears naturally once the old tile is gone.
        PendingRefunds owed = PENDING.get(tile);
        if (owed != null && !owed.isEmpty()) {
            MekanismMagic.LOGGER.warn(
                    "AE resource refund debt follows removed machine {} at {}: {}",
                    tile.getType(), tile.getBlockPos(), owed);
        }
        if (tile instanceof TileEntityMekanism machine) {
            Ae2MachineResourceProvider.remove(machine);
        }
    }

    @Override
    public void save(BlockEntity tile, CompoundTag tag,
                     HolderLookup.Provider registries) {
        Ae2MachineOutputAccess.save(tile, tag, registries);
        if (tile instanceof TileEntityMekanism machine) {
            Ae2MachineResourceProvider provider =
                    Ae2MachineResourceProvider.existing(machine);
            if (provider != null) provider.save(tag);
        }
        PendingRefunds refunds = PENDING.get(tile);
        if (refunds != null && !refunds.isEmpty()) {
            CompoundTag saved = new CompoundTag();
            saved.putDouble("ae", refunds.ae);
            saved.putLong("fe", refunds.fe);
            ListTag entries = new ListTag();
            refunds.magic.forEach((id, amount) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id.toString());
                entry.putLong("amount", amount);
                entries.add(entry);
            });
            saved.put("magic", entries);
            tag.put(PENDING_TAG, saved);
        }
    }

    @Override
    public void load(BlockEntity tile, CompoundTag tag,
                     HolderLookup.Provider registries) {
        Ae2MachineOutputAccess.load(tile, tag, registries);
        if (Ae2MachineResourceProvider.eligible(tile)) {
            Ae2MachineResourceProvider.forTile(
                    (TileEntityMekanism) tile).load(tag);
        }
        // A saved tile may be loaded more than once during revive or upgrade
        // migration. Replace the complete ledger; never merge the same debt.
        PENDING.remove(tile);
        if (tag.contains(PENDING_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag saved = tag.getCompound(PENDING_TAG);
            PendingRefunds refunds = new PendingRefunds();
            double savedAe = saved.getDouble("ae");
            refunds.ae = Double.isFinite(savedAe)
                    ? Math.max(0.0, savedAe) : 0.0;
            refunds.fe = Math.max(0L, saved.getLong("fe"));
            for (Tag element : saved.getList("magic", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) element;
                ResourceLocation id = ResourceLocation.tryParse(
                        entry.getString("id"));
                long amount = Math.max(0L, entry.getLong("amount"));
                if (id != null && amount > 0L) {
                    refunds.magic.merge(id, amount,
                            (oldAmount, nextAmount) ->
                                    oldAmount > Long.MAX_VALUE - nextAmount
                                            ? Long.MAX_VALUE
                                            : oldAmount + nextAmount);
                }
            }
            if (!refunds.isEmpty()) PENDING.put(tile, refunds);
        }
    }

    private record NodeState(boolean connected, boolean active) { }

    private static final class PendingRefunds {
        private final Map<ResourceLocation, Long> magic =
                new LinkedHashMap<>();
        private double ae;
        private long fe;

        private boolean isEmpty() {
            return magic.isEmpty() && ae <= 0.000001 && fe <= 0L;
        }

        @Override
        public String toString() {
            return "magic=" + magic + ",ae=" + ae + ",fe=" + fe;
        }
    }

    public interface ExternalFeSource {
        long extractFe(IGrid grid, IActionSource actor, long maxFe,
                       Actionable mode);

        long refundFe(IGrid grid, IActionSource actor, long amountFe);
    }

    /** Resource-specific Source/Aura adapter, loaded only with its owning mod. */
    public interface ResourceExtension {
        int mask(BlockEntity tile);

        default int inputMask(BlockEntity tile) { return mask(tile); }

        default int outputMask(BlockEntity tile) { return 0; }

        default void export(BlockEntity tile, IGrid grid, IActionSource source) { }

        void tick(BlockEntity tile, IGrid grid, IActionSource source);
    }
}
