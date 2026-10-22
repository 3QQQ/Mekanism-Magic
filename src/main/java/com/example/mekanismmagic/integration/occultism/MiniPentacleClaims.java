package com.example.mekanismmagic.integration.occultism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dimension-local ownership, including permanent invalidation of legacy receipts. */
public final class MiniPentacleClaims extends SavedData {
    public static final String DATA_NAME = "mekanism_magic_mini_pentacle_claims";
    private static final Factory<MiniPentacleClaims> FACTORY = new Factory<>(
            MiniPentacleClaims::new, MiniPentacleClaims::load, null);
    private final Map<Long, UUID> claims = new HashMap<>();
    private final Set<Long> retiredLegacyPositions = new HashSet<>();
    private boolean transaction;

    public static MiniPentacleClaims get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    boolean begin() {
        if (transaction) {
            return false;
        }
        transaction = true;
        return true;
    }

    void end() {
        transaction = false;
    }

    boolean claimed(Collection<BlockPos> positions) {
        return positions.stream().anyMatch(pos -> claims.containsKey(pos.asLong()));
    }

    boolean owns(UUID lease, Collection<BlockPos> positions) {
        return !positions.isEmpty() && positions.stream()
                .allMatch(pos -> lease.equals(claims.get(pos.asLong())));
    }

    boolean legacyInvalid(Collection<BlockPos> positions) {
        return positions.stream().anyMatch(pos ->
                retiredLegacyPositions.contains(pos.asLong())
                        || claims.containsKey(pos.asLong()));
    }

    void claim(UUID lease, Collection<BlockPos> positions) {
        positions.forEach(pos -> claims.put(pos.asLong(), lease));
        setDirty();
    }

    void retire(UUID lease, Collection<BlockPos> removed) {
        if (lease != null) {
            claims.entrySet().removeIf(entry -> lease.equals(entry.getValue()));
        }
        // Never clear these on rebuilding or deploying here. Old deployed items
        // have no generation ID, and may be in an unloaded chest or offline player.
        removed.forEach(pos -> retiredLegacyPositions.add(pos.asLong()));
        setDirty();
    }

    public static MiniPentacleClaims load(CompoundTag tag,
                                           HolderLookup.Provider registries) {
        MiniPentacleClaims result = new MiniPentacleClaims();
        for (Tag raw : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("lease")) {
                result.claims.put(entry.getLong("position"), entry.getUUID("lease"));
            }
        }
        for (long position : tag.getLongArray("retired_legacy_positions")) {
            result.retiredLegacyPositions.add(position);
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        claims.forEach((position, lease) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("position", position);
            entry.putUUID("lease", lease);
            entries.add(entry);
        });
        tag.put("claims", entries);
        tag.putLongArray("retired_legacy_positions", retiredLegacyPositions.stream()
                .mapToLong(Long::longValue).toArray());
        return tag;
    }
}
