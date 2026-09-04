package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceNetworkAccess;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Pulls Ars Energistique's Source key through a machine's active AE node. */
final class Ae2ArsSourceNetworkAccess
        implements ArsSourceNetworkAccess.Handler {
    static final Ae2ArsSourceNetworkAccess INSTANCE =
            new Ae2ArsSourceNetworkAccess();

    private Ae2ArsSourceNetworkAccess() {
    }

    @Override
    public ArsSourceNetworkAccess.PullResult pullInto(
            BlockEntity machine, ISourceTile target, int requested) {
        if (!(machine.getLevel() instanceof ServerLevel level)
                || requested <= 0) {
            return ArsSourceNetworkAccess.PullResult.NOT_CONNECTED;
        }
        IInWorldGridNodeHost host = level.getCapability(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                machine.getBlockPos(), (Void) null);
        IGridNode node = activeNode(host);
        AEKey sourceKey = Ae2SourceInterfaceStorage.sourceKey();
        if (node == null || sourceKey == null) {
            return ArsSourceNetworkAccess.PullResult.NOT_CONNECTED;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            return ArsSourceNetworkAccess.PullResult.NOT_CONNECTED;
        }

        int before = Math.max(0, target.getSource());
        int free = Math.max(0, target.getMaxSource() - before);
        int transfer = Math.min(requested, free);
        if (transfer <= 0) {
            return ArsSourceNetworkAccess.PullResult.connected(0);
        }
        IActionHost actionHost = host instanceof IActionHost actionable
                ? actionable : () -> node;
        IActionSource actionSource = IActionSource.ofMachine(actionHost);
        long extracted = StorageHelper.poweredExtraction(
                grid.getEnergyService(),
                grid.getStorageService().getInventory(), sourceKey,
                transfer, actionSource, Actionable.MODULATE);
        if (extracted <= 0) {
            return ArsSourceNetworkAccess.PullResult.connected(0);
        }

        target.addSource((int) extracted);
        int accepted = Math.max(0, target.getSource() - before);
        if (accepted < extracted) {
            long remainder = extracted - accepted;
            long restored = StorageHelper.poweredInsert(
                    grid.getEnergyService(),
                    grid.getStorageService().getInventory(), sourceKey,
                    remainder, actionSource, Actionable.MODULATE);
            if (restored != remainder) {
                MekanismMagic.LOGGER.error(
                        "Unable to restore {} Source rejected by AE-connected "
                                + "machine {} at {} (restored {})",
                        remainder, machine.getType(), machine.getBlockPos(),
                        restored);
            }
        }
        return ArsSourceNetworkAccess.PullResult.connected(accepted);
    }

    private static IGridNode activeNode(IInWorldGridNodeHost host) {
        if (host == null) {
            return null;
        }
        if (host instanceof IActionHost actionHost) {
            IGridNode actionable = actionHost.getActionableNode();
            if (isActive(actionable)) {
                return actionable;
            }
        }
        for (Direction direction : Direction.values()) {
            IGridNode candidate = host.getGridNode(direction);
            if (isActive(candidate)) {
                return candidate;
            }
        }
        IGridNode unsided = host.getGridNode(null);
        return isActive(unsided) ? unsided : null;
    }

    private static boolean isActive(IGridNode node) {
        return node != null && node.isActive() && node.getGrid() != null;
    }
}
