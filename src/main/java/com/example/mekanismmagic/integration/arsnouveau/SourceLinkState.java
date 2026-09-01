package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.common.block.tile.CreativeSourceJarTile;
import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persistent, bounded set of direct Source-jar links for one machine. */
public final class SourceLinkState {
    private static final String NBT_KEY = "ars_linked_source_jars";
    private static final String DIMENSION_NBT_KEY =
            "ars_linked_source_dimension";
    private static final int MAX_LINKS = 32;

    private final Set<BlockPos> sourceJars = new LinkedHashSet<>();
    private ResourceLocation dimension;

    public boolean link(ResourceLocation linkDimension, BlockPos host,
                        BlockPos sourceJar) {
        if (sourceJar == null || sourceJar.equals(host)) {
            return false;
        }
        if (dimension != null && linkDimension != null
                && !dimension.equals(linkDimension)) {
            // The machine may have been broken, carried through a portal and
            // placed again. An explicit new link safely rebases it instead of
            // ever interpreting old coordinates in the new dimension.
            sourceJars.clear();
            dimension = linkDimension;
        }
        if (dimension == null) {
            dimension = linkDimension;
        }
        BlockPos immutable = sourceJar.immutable();
        if (sourceJars.contains(immutable)) {
            return true;
        }
        return sourceJars.size() < MAX_LINKS && sourceJars.add(immutable);
    }

    public int clear() {
        int removed = sourceJars.size();
        sourceJars.clear();
        dimension = null;
        return removed;
    }

    public List<BlockPos> snapshot() {
        return List.copyOf(sourceJars);
    }

    public void replace(Collection<BlockPos> positions,
                        ResourceLocation linkDimension) {
        sourceJars.clear();
        dimension = positions == null || positions.isEmpty()
                ? null : linkDimension;
        if (positions == null) {
            return;
        }
        for (BlockPos position : positions) {
            if (position != null && sourceJars.size() < MAX_LINKS) {
                sourceJars.add(position.immutable());
            }
        }
    }

    public void save(CompoundTag tag) {
        tag.putLongArray(NBT_KEY, sourceJars.stream()
                .mapToLong(BlockPos::asLong).toArray());
        if (dimension != null) {
            tag.putString(DIMENSION_NBT_KEY, dimension.toString());
        } else {
            tag.remove(DIMENSION_NBT_KEY);
        }
    }

    public void load(CompoundTag tag) {
        sourceJars.clear();
        dimension = ResourceLocation.tryParse(
                tag.getString(DIMENSION_NBT_KEY));
        for (long packed : tag.getLongArray(NBT_KEY)) {
            if (sourceJars.size() >= MAX_LINKS) {
                break;
            }
            sourceJars.add(BlockPos.of(packed));
        }
    }

    /** Pulls only from explicitly linked, currently loaded original jars. */
    public int pullInto(ISourceTile target, Level level, int maxAmount) {
        if (target == null || level == null || level.isClientSide()
                || maxAmount <= 0 || (dimension != null
                && !dimension.equals(level.dimension().location()))) {
            return 0;
        }
        int initial = Math.max(0, target.getSource());
        int remaining = Math.min(maxAmount,
                Math.max(0, target.getMaxSource() - initial));
        for (BlockPos sourcePos : sourceJars) {
            if (remaining <= 0) {
                break;
            }
            if (!level.isLoaded(sourcePos)
                    || !(level.getBlockEntity(sourcePos)
                    instanceof SourceJarTile source)) {
                continue;
            }
            if (source instanceof CreativeSourceJarTile) {
                remaining -= addAndMeasure(target, remaining);
                continue;
            }
            int before = Math.max(0, source.getSource());
            int requested = Math.min(remaining, before);
            if (requested <= 0) {
                continue;
            }
            source.removeSource(requested);
            int removed = Math.max(0,
                    before - Math.max(0, source.getSource()));
            int accepted = addAndMeasure(target, removed);
            if (accepted < removed) {
                source.addSource(removed - accepted);
            }
            remaining -= accepted;
        }
        return Math.max(0, target.getSource() - initial);
    }

    private static int addAndMeasure(ISourceTile target, int amount) {
        int before = Math.max(0, target.getSource());
        target.addSource(Math.max(0, amount));
        return Math.max(0, target.getSource() - before);
    }
}
