package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dependency-free bridge from Ars machines to optional storage networks.
 *
 * <p>The AE implementation lives in the optional AE2 source set. Keeping the
 * contract here lets every Source-consuming machine use the same refill path
 * without linking the normal Ars machine classes to AE2 or Ars
 * Energistique.</p>
 */
public final class ArsSourceNetworkAccess {
    private static final List<Handler> HANDLERS =
            new CopyOnWriteArrayList<>();

    private ArsSourceNetworkAccess() {
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

    /**
     * Pulls at most {@code requested} Source into the machine's normal local
     * tank. The first connected network is authoritative; a connected but
     * empty network returns zero so the caller may still fall back to linked
     * or nearby Ars jars.
     */
    public static int pullInto(BlockEntity machine, ISourceTile target,
                               int requested) {
        if (machine == null || target == null || requested <= 0) {
            return 0;
        }
        for (Handler handler : HANDLERS) {
            PullResult result = handler.pullInto(machine, target, requested);
            if (result != null && result.connected()) {
                return Math.max(0, Math.min(requested, result.moved()));
            }
        }
        return 0;
    }

    @FunctionalInterface
    public interface Handler {
        PullResult pullInto(BlockEntity machine, ISourceTile target,
                            int requested);
    }

    public record PullResult(boolean connected, int moved) {
        public static final PullResult NOT_CONNECTED =
                new PullResult(false, 0);

        public PullResult {
            moved = Math.max(0, moved);
        }

        public static PullResult connected(int moved) {
            return new PullResult(true, moved);
        }
    }
}
