package com.example.mekanismmagic.integration.common.network;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dependency-free bridge for optional storage-network output. Implementations
 * live in optional source sets, so the core mod never loads AE2 classes when
 * AE2 is absent. Node lifecycle belongs to
 * {@link MachineNetworkLifecycleHooks}.
 */
public final class MachineDirectOutputHooks {
    private static final List<Handler> HANDLERS =
            new CopyOnWriteArrayList<>();

    private MachineDirectOutputHooks() {
    }

    public static void register(Handler handler) {
        if (handler == null) {
            return;
        }
        for (Handler existing : HANDLERS) {
            if (existing.getClass() == handler.getClass()) {
                return;
            }
        }
        HANDLERS.add(handler);
    }

    public static boolean tick(NativeMagicMachineBlockEntity tile) {
        boolean changed = false;
        for (Handler handler : HANDLERS) {
            changed |= handler.tick(tile);
        }
        return changed;
    }

    public static long insert(NativeMagicMachineBlockEntity tile,
                              ItemStack template, long amount) {
        return insertDetailed(tile, template, amount).accepted();
    }

    public static DirectInsertResult insertDetailed(
            NativeMagicMachineBlockEntity tile,
            ItemStack template, long amount) {
        long remaining = Math.max(0L, amount);
        long inserted = 0L;
        boolean attempted = false;
        long retryAt = Long.MAX_VALUE;
        DirectNetworkStatus status = DirectNetworkStatus.UNAVAILABLE;
        for (Handler handler : HANDLERS) {
            if (remaining <= 0) {
                break;
            }
            DirectInsertResult result = handler.insertDetailed(
                    tile, template, remaining);
            long accepted = Math.min(remaining,
                    Math.max(0L, result.accepted()));
            inserted += accepted;
            remaining -= accepted;
            attempted |= result.attempted();
            retryAt = Math.min(retryAt, result.retryAtGameTime());
            if (result.status().ordinal() > status.ordinal()) {
                status = result.status();
            }
        }
        return new DirectInsertResult(inserted, status, attempted, retryAt);
    }

    public static DirectNetworkStatus status(
            NativeMagicMachineBlockEntity tile) {
        DirectNetworkStatus result = DirectNetworkStatus.UNAVAILABLE;
        for (Handler handler : HANDLERS) {
            DirectNetworkStatus candidate = handler.status(tile);
            if (candidate.ordinal() > result.ordinal()) {
                result = candidate;
            }
        }
        return result;
    }

    public interface Handler {
        boolean tick(NativeMagicMachineBlockEntity tile);

        default long insert(NativeMagicMachineBlockEntity tile,
                            ItemStack template, long amount) {
            return 0L;
        }

        default DirectInsertResult insertDetailed(
                NativeMagicMachineBlockEntity tile,
                ItemStack template, long amount) {
            long accepted = insert(tile, template, amount);
            DirectNetworkStatus current = status(tile);
            return new DirectInsertResult(accepted, current,
                    accepted > 0, Long.MAX_VALUE);
        }

        default DirectNetworkStatus status(
                NativeMagicMachineBlockEntity tile) {
            return DirectNetworkStatus.UNAVAILABLE;
        }
    }

    public enum DirectNetworkStatus {
        UNAVAILABLE(false),
        OFFLINE(false),
        ONLINE(true),
        BLOCKED(true);

        private final boolean connected;

        DirectNetworkStatus(boolean connected) {
            this.connected = connected;
        }

        public boolean connected() {
            return connected;
        }
    }

    public record DirectInsertResult(
            long accepted,
            DirectNetworkStatus status,
            boolean attempted,
            long retryAtGameTime) {
        public boolean online() {
            return status.connected();
        }
    }
}
