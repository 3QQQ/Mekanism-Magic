package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.block.tile.CreativeSourceJarTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Uses Ars Nouveau's nearby Source provider discovery while treating a
 * machine's own Source storage as the first provider.
 */
public final class ArsSourceInteraction {
    private ArsSourceInteraction() {
    }

    public static boolean hasSource(
            ISourceTile local, @Nullable Level level, BlockPos pos,
            int radius, int amount) {
        if (amount <= 0) {
            return true;
        }
        // The periodically filled internal tank is authoritative. Recipe
        // checks must not each rescan the world or silently reserve the same
        // external Source for several parallel factory lanes.
        return Math.max(0, local.getSource()) >= amount;
    }

    public static boolean consumeSource(
            ISourceTile local, @Nullable Level level, BlockPos pos,
            int radius, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (level == null || level.isClientSide()) {
            return false;
        }
        int before = Math.max(0, local.getSource());
        if (before < amount) {
            return false;
        }
        local.removeSource(amount);
        return before - Math.max(0, local.getSource()) >= amount;
    }

    /** Pulls Source from an optional storage network attached to the machine. */
    public static int pullConnectedNetworkSource(
            ISourceTile local, @Nullable Level level, BlockPos pos,
            int maxAmount) {
        if (level == null || level.isClientSide() || maxAmount <= 0) {
            return 0;
        }
        return ArsSourceNetworkAccess.pullInto(
                level.getBlockEntity(pos), local, maxAmount);
    }

    /**
     * Pulls Source from ordinary Ars jars and registered special providers.
     * The transfer is server-only, skips the machine itself, deduplicates
     * providers, and rolls back any amount the local storage cannot accept.
     */
    public static int pullNearbySource(
            ISourceTile local, @Nullable Level level, BlockPos pos,
            int radius, int maxAmount) {
        if (level == null || level.isClientSide() || maxAmount <= 0) {
            return 0;
        }
        int initial = Math.max(0, local.getSource());
        int capacity = Math.max(initial, local.getMaxSource());
        int remaining = Math.min(maxAmount, capacity - initial);
        if (remaining <= 0) {
            return 0;
        }
        for (ISourceTile source : externalSources(
                local, level, pos, radius)) {
            if (remaining <= 0) {
                break;
            }
            if (source instanceof CreativeSourceJarTile) {
                addAndMeasure(local, remaining);
                break;
            }
            int sourceBefore = Math.max(0, source.getSource());
            int requested = Math.min(remaining, sourceBefore);
            if (requested <= 0) {
                continue;
            }
            source.removeSource(requested);
            int removed = Math.max(0,
                    sourceBefore - Math.max(0, source.getSource()));
            if (removed <= 0) {
                continue;
            }
            int accepted = addAndMeasure(local, removed);
            if (accepted < removed) {
                source.addSource(removed - accepted);
            }
            remaining -= accepted;
        }
        return Math.max(0, local.getSource() - initial);
    }

    private static List<ISourceTile> externalSources(
            ISourceTile local, Level level, BlockPos pos, int radius) {
        Set<ISourceTile> seen = Collections.newSetFromMap(
                new IdentityHashMap<>());
        List<ISpecialSourceProvider> providers = new ArrayList<>(
                SourceUtil.canTakeSource(pos, level, Math.max(0, radius)));
        providers.sort(Comparator
                .comparingInt((ISpecialSourceProvider provider) -> {
                    BlockPos sourcePos = provider == null
                            ? null : provider.getCurrentPos();
                    return sourcePos == null ? Integer.MAX_VALUE
                            : sourcePos.distManhattan(pos);
                })
                .thenComparingLong(provider -> {
                    BlockPos sourcePos = provider == null
                            ? null : provider.getCurrentPos();
                    return sourcePos == null ? Long.MAX_VALUE
                            : sourcePos.asLong();
                }));
        List<ISourceTile> result = new ArrayList<>();
        for (ISpecialSourceProvider provider : providers) {
            if (provider == null || !provider.isValid()) {
                continue;
            }
            BlockPos sourcePos = provider.getCurrentPos();
            if (sourcePos == null || Objects.equals(sourcePos, pos)) {
                continue;
            }
            ISourceTile source = provider.getSource();
            // Mekanism Magic consumers are registered with SourceManager so
            // Ars can discover them, but they are tanks, not wireless source
            // producers. Excluding them prevents machines from repeatedly
            // draining one another while ignoring sided Source modes.
            if (source != null && source != local
                    && !(source instanceof SourceLinkHost)
                    && seen.add(source)) {
                result.add(source);
            }
        }
        return result;
    }

    private static int addAndMeasure(ISourceTile target, int amount) {
        int before = Math.max(0, target.getSource());
        target.addSource(Math.max(0, amount));
        return Math.max(0, target.getSource() - before);
    }

}
