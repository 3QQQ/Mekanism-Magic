package com.example.mekanismmagic.integration.common.cleanup;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Explicit retirement, not migration: old machines and their owned contents have no replacement or drops. */
public final class RetiredEssenceMachines {
    public static final Set<String> IDS = Set.of(
            "mekanism_magic:soul_extractor_processor",
            "mekanism_magic:simulated_blood_extractor",
            "mekanism_magic:experience_bottler");

    private RetiredEssenceMachines() { }

    /** Runs before palette decoding so one missing old ID cannot invalidate an otherwise healthy section. */
    public static boolean clearFromChunk(CompoundTag chunk) {
        boolean changed = false;
        for (Tag entry : chunk.getList("sections", Tag.TAG_COMPOUND)) {
            CompoundTag section = (CompoundTag) entry;
            ListTag palette = section.getCompound("block_states").getList("palette", Tag.TAG_COMPOUND);
            for (Tag stateEntry : palette) {
                CompoundTag state = (CompoundTag) stateEntry;
                if (IDS.contains(state.getString("Name"))) {
                    state.putString("Name", "minecraft:air");
                    state.remove("Properties");
                    changed = true;
                }
            }
        }
        changed |= removeOwnedEntries(chunk.getList("block_entities", Tag.TAG_COMPOUND), "id");
        changed |= removeOwnedEntries(chunk.getList("block_ticks", Tag.TAG_COMPOUND), "i");
        if (changed) chunk.putBoolean("shouldSave", true);
        return changed;
    }

    private static boolean removeOwnedEntries(ListTag entries, String idKey) {
        boolean changed = false;
        for (int index = entries.size() - 1; index >= 0; index--) {
            if (IDS.contains(entries.getCompound(index).getString(idKey))) {
                entries.remove(index);
                changed = true;
            }
        }
        return changed;
    }
}
