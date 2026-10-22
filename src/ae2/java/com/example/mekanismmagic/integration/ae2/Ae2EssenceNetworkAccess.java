package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceRatePolicy;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceHost;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import mekanism.api.Action;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Powered, exact-key AE refill of the Forge's four existing essence banks. */
public final class Ae2EssenceNetworkAccess
        implements Ae2MachineResourceAccess.ResourceExtension {
    public static final Ae2EssenceNetworkAccess INSTANCE =
            new Ae2EssenceNetworkAccess();
    private static final int[] BITS = {
            MachineAeResourceHooks.ESSENCE_AUREAL,
            MachineAeResourceHooks.ESSENCE_SOULS,
            MachineAeResourceHooks.ESSENCE_BLOOD,
            MachineAeResourceHooks.ESSENCE_EXPERIENCE
    };

    private Ae2EssenceNetworkAccess() { }

    @Override
    public int mask(BlockEntity tile) {
        return inputMask(tile) | outputMask(tile);
    }

    @Override
    public int inputMask(BlockEntity tile) {
        if (!(tile instanceof EssenceResourceHost host)) return 0;
        int mask = 0;
        for (int channel = 0; channel < EssenceResources.CHANNELS;
             channel++) {
            if (host.canInputEssence(channel)) {
                mask |= BITS[channel];
            }
        }
        return mask;
    }

    @Override
    public int outputMask(BlockEntity tile) {
        if (!(tile instanceof EssenceResourceHost host)) return 0;
        int mask = 0;
        for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
            if (host.canOutputEssence(channel) && !host.canInputEssence(channel)) mask |= BITS[channel];
        }
        return mask;
    }

    @Override
    public void export(BlockEntity tile, IGrid grid, IActionSource actor) {
        if (!(tile instanceof EssenceResourceHost host)) return;
        for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
            // Consumer banks and bidirectional relays are not generated output.
            if (!host.canOutputEssence(channel) || host.canInputEssence(channel)) continue;
            final int fixed = channel;
            int maximum = Math.min(MachineAeResourceRatePolicy.MAX_AURA_PER_TICK,
                    Math.max(0, host.extractEssence(channel,
                            MachineAeResourceRatePolicy.MAX_AURA_PER_TICK, Action.SIMULATE)));
            Ae2MachineOutputAccess.exportResource(tile, grid, actor,
                    MagicResourceKeys.essence(channel), maximum, BITS[channel],
                    request -> host.extractEssence(fixed, (int) request, Action.EXECUTE),
                    request -> host.refundExtractedEssence(fixed, (int) request, Action.EXECUTE));
        }
    }

    @Override
    public void tick(BlockEntity tile, IGrid grid,
                     IActionSource actor) {
        if (!(tile instanceof EssenceResourceHost host)
                || !(tile.getLevel() instanceof ServerLevel)
                || grid == null || actor == null) return;
        for (int channel = 0; channel < EssenceResources.CHANNELS;
             channel++) {
            if (!host.canInputEssence(channel)
                    || !MachineAeResourceHooks.isInputEnabled(tile, BITS[channel])) continue;
            AEKey key = MagicResourceKeys.essence(channel);
            if (key == null) continue;
            int free = Math.max(0, host.getEssenceCapacity(channel)
                    - host.getStoredEssence(channel));
            if (free <= 0) continue;
            int request = MachineAeResourceRatePolicy.auraBudget(
                    host.getEssenceCapacity(channel), free);
            request = Math.min(request, Math.max(0,
                    host.insertEssence(channel, request,
                            Action.SIMULATE)));
            if (request <= 0) continue;
            long withdrawn = StorageHelper.poweredExtraction(
                    grid.getEnergyService(),
                    grid.getStorageService().getInventory(), key,
                    request, actor, Actionable.MODULATE);
            if (withdrawn <= 0L) continue;
            int received = (int) Math.min(request, withdrawn);
            int accepted = Math.max(0, Math.min(received,
                    host.insertEssence(channel, received,
                            Action.EXECUTE)));
            long unpaid = withdrawn - accepted;
            if (unpaid > 0L) {
                Ae2MachineResourceAccess.refundOrQueue(tile, grid,
                        actor, key, unpaid);
            }
            MachineAeResourceHooks.recordTransfer(tile,
                    BITS[channel], accepted);
        }
    }
}
