package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceRatePolicy;
import com.example.mekanismmagic.integration.naturesaura.AuraSideModeHost;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraGeneratorBlockEntity;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Typed consumer refill and independent output of real generator Aura. */
public final class Ae2NatureAuraNetworkAccess
        implements Ae2MachineResourceAccess.ResourceExtension {
    public static final Ae2NatureAuraNetworkAccess INSTANCE =
            new Ae2NatureAuraNetworkAccess();
    public static final int MAX_AURA_PER_TICK =
            MachineAeResourceRatePolicy.MAX_AURA_PER_TICK;

    private Ae2NatureAuraNetworkAccess() { }

    @Override
    public int mask(BlockEntity tile) {
        return inputMask(tile) | outputMask(tile);
    }

    @Override
    public int inputMask(BlockEntity tile) {
        return tile instanceof AuraSideModeHost && !(tile instanceof NatureAuraGeneratorBlockEntity)
                ? MachineAeResourceHooks.AURA : 0;
    }

    @Override
    public int outputMask(BlockEntity tile) {
        return tile instanceof NatureAuraGeneratorBlockEntity
                ? MachineAeResourceHooks.AURA : 0;
    }

    private static boolean inputEnabled(BlockEntity tile) {
        return MachineAeResourceHooks.isInputEnabled(tile, MachineAeResourceHooks.AURA);
    }

    private static boolean outputEnabled(BlockEntity tile) {
        return MachineAeResourceHooks.isOutputEnabled(tile, MachineAeResourceHooks.AURA);
    }

    @Override
    public void tick(BlockEntity tile, IGrid grid,
                     IActionSource actionSource) {
        if (tile instanceof NatureAuraGeneratorBlockEntity || !inputEnabled(tile)) return;
        if (!(tile instanceof AuraSideModeHost machine)
                || machine.isCreativeAuraActive()
                || !(tile.getLevel() instanceof ServerLevel level)
                || grid == null) return;
        IAuraType type = IAuraType.forLevel(level);
        if (type == null) return;
        AEKey key = MagicResourceKeys.key(type.getName());
        if (key == null) return;
        int free = Math.max(0,
                machine.getAuraCapacity() - machine.getStoredAura());
        if (free <= 0) return;
        IAuraContainer local = inputContainer(tile, machine, type);
        if (local == null) return;
        int request = MachineAeResourceRatePolicy.auraBudget(
                machine.getAuraCapacity(), free);
        request = Math.max(0, Math.min(request,
                local.storeAura(request, true)));
        if (request <= 0) return;

        long withdrawn = StorageHelper.poweredExtraction(
                grid.getEnergyService(),
                grid.getStorageService().getInventory(), key,
                request, actionSource, Actionable.MODULATE);
        if (withdrawn <= 0) return;
        int accepted = Math.max(0, Math.min((int) withdrawn,
                local.storeAura((int) withdrawn, false)));
        long remainder = withdrawn - accepted;
        if (remainder > 0) {
            Ae2MachineResourceAccess.refundOrQueue(tile, grid,
                    actionSource, key, remainder);
        }
        MachineAeResourceHooks.recordTransfer(tile,
                MachineAeResourceHooks.AURA, accepted);
    }

    @Override
    public void export(BlockEntity tile, IGrid grid,
                       IActionSource actionSource) {
        if (!(tile instanceof NatureAuraGeneratorBlockEntity generator)
                || !outputEnabled(tile)
                || generator.isCreativeAuraActive()
                || !(tile.getLevel() instanceof ServerLevel)
                || grid == null || actionSource == null) return;
        exportGeneratedAura(tile, generator, grid, actionSource);
    }

    /** A generator's own selected type, never the surrounding dimension, is exported. */
    private static void exportGeneratedAura(BlockEntity tile,
                                            NatureAuraGeneratorBlockEntity generator,
                                            IGrid grid,
                                            IActionSource actionSource) {
        if (!generator.beginAuraOutputTransaction()) return;
        try {
            exportGeneratedAuraLocked(tile, generator, grid, actionSource);
        } finally {
            generator.endAuraOutputTransaction();
        }
    }

    private static void exportGeneratedAuraLocked(BlockEntity tile,
                                                   NatureAuraGeneratorBlockEntity generator,
                                                   IGrid grid,
                                                   IActionSource actionSource) {
        IAuraType type = generator.getGenerationAuraType();
        if (type == null || generator.hasAuraTypeMismatch()) return;
        AEKey key = MagicResourceKeys.key(type.getName());
        if (key == null) return;
        IAuraContainer output = null;
        for (Direction side : Direction.values()) {
            if (!generator.getAuraSideMode(side).allowsOutput()) continue;
            IAuraContainer candidate = generator.getAuraContainer(side);
            if (candidate != null && candidate.isAcceptableType(type)) {
                output = candidate;
                break;
            }
        }
        if (output == null) return;
        int budget = Math.min(MAX_AURA_PER_TICK,
                Math.max(0, output.drainAura(MAX_AURA_PER_TICK, true)));
        if (budget <= 0) return;
        IAuraContainer localOutput = output;
        Ae2MachineOutputAccess.exportResource(tile, grid, actionSource,
                key, budget, MachineAeResourceHooks.AURA,
                amount -> generator.isCreativeAuraActive() ? 0L
                        : localOutput.drainAura((int) amount, false),
                amount -> generator.refundGeneratedAura(type, (int) amount));
    }

    private static IAuraContainer inputContainer(
            BlockEntity tile, AuraSideModeHost machine,
            IAuraType type) {
        ServerLevel level = (ServerLevel) tile.getLevel();
        for (Direction side : Direction.values()) {
            if (!machine.getAuraSideMode(side).allowsInput()) continue;
            IAuraContainer candidate = level.getCapability(
                    NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                    tile.getBlockPos(), side);
            if (candidate != null && candidate.isAcceptableType(type)) {
                return candidate;
            }
        }
        return null;
    }
}
