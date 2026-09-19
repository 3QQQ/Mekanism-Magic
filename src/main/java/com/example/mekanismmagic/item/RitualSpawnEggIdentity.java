package com.example.mekanismmagic.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Read-only presentation/recipe identity over existing and legacy egg data. Never writes display data into saved stacks. */
public final class RitualSpawnEggIdentity {
    private RitualSpawnEggIdentity() { }

    public static ResourceLocation job(ItemStack stack) {
        CompoundTag data = RitualSpawnEggItem.data(stack);
        String value = data.getString("ritual_initialize_spirit_job");
        if (value.isBlank()) value = data.getCompound("spiritJob").getString("factoryId");
        return value.isBlank() ? null : ResourceLocation.tryParse(value);
    }

    public static int ownerMode(CompoundTag data) {
        return data.contains("ritual_owner_mode") ? data.getInt("ritual_owner_mode")
                : data.getBoolean("ritual_assign_owner") ? 1 : 0;
    }

    public static int chanceDenominator(ItemStack stack) {
        return Math.max(1, RitualSpawnEggItem.data(stack).getInt("ritual_chicken_fallback_denominator"));
    }

    public static Component jobName(ResourceLocation job) {
        String path = job.getPath().replace("crystallize_tier", "crystal_tier");
        return Component.translatableWithFallback("job." + job.getNamespace() + "." + path, job.toString());
    }

    public static Component targetName(ItemStack stack) {
        ResourceLocation tag = RitualSpawnEggItem.entityTag(stack);
        if (tag != null) {
            return Component.translatableWithFallback("tooltip.mekanism_magic.ritual_spawn_egg.pool."
                    + tag.getNamespace() + "." + tag.getPath(), "#" + tag);
        }
        ResourceLocation id = RitualSpawnEggItem.entityId(stack);
        if (id == null) return Component.translatable("tooltip.mekanism_magic.ritual_spawn_egg.unknown");
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getDescription())
                .orElseGet(() -> Component.literal(id.toString()));
    }

    public static Component name(ItemStack stack) {
        if (RitualSpawnEggItem.entityId(stack) == null && RitualSpawnEggItem.entityTag(stack) == null) {
            return Component.translatable("item.mekanism_magic.ritual_spawn_egg");
        }
        var job = job(stack);
        Component name = job != null
                ? Component.translatable("item.mekanism_magic.ritual_spawn_egg.job", targetName(stack), jobName(job))
                : Component.translatable("item.mekanism_magic.ritual_spawn_egg.target", targetName(stack));
        if (RitualSpawnEggItem.entityTag(stack) != null) {
            name = Component.translatable("item.mekanism_magic.ritual_spawn_egg.random", name);
        }
        int chance = chanceDenominator(stack);
        int owner = ownerMode(RitualSpawnEggItem.data(stack));
        if (chance > 1) name = Component.translatable("item.mekanism_magic.ritual_spawn_egg.chance", name, chance);
        else if (owner == 2) name = Component.translatable("item.mekanism_magic.ritual_spawn_egg.resurrected", name);
        else if (owner == 1 && job == null) name = Component.translatable("item.mekanism_magic.ritual_spawn_egg.owned", name);
        return name;
    }

    /**
     * JEI recipe identity: target, job, ownership, chance and meaningful entity traits.
     * Individual names/UUIDs/positions and a calling book's generated binding must not break recipe lookup.
     * The same identity is used for both JEI's modern and legacy APIs and ingredient-list reconciliation.
     */
    public static String variantKey(ItemStack stack) {
        CompoundTag data = RitualSpawnEggItem.data(stack);
        if (!data.contains("id", Tag.TAG_STRING) && !data.contains(RitualSpawnEggItem.ENTITY_TAG, Tag.TAG_STRING)) return "";
        int owner = ownerMode(data);
        var job = job(stack);
        String book = data.getCompound("ritual_calling_book").getString("id");
        boolean hasBook = data.contains("ritual_calling_book", Tag.TAG_COMPOUND);
        for (String transientKey : List.of("CustomName", "CustomNameVisible", "UUID", "UUIDMost", "UUIDLeast",
                "Pos", "Motion", "Rotation", "Owner", "OwnerUUID", "FamiliarOwner", "FamiliarOwnerUUID",
                "ritual_generate_spirit_name", "ritual_assign_owner", "ritual_owner_mode",
                "ritual_calling_book", "ritual_initialize_spirit_job", "ritual_chicken_fallback_denominator")) {
            data.remove(transientKey);
        }
        if (data.contains(RitualSpawnEggItem.ENTITY_TAG, Tag.TAG_STRING)) data.remove("id");
        CompoundTag jobData = data.getCompound("spiritJob");
        jobData.remove("factoryId");
        if (jobData.isEmpty()) data.remove("spiritJob");
        else data.put("spiritJob", jobData);
        CompoundTag identity = new CompoundTag();
        identity.put("entity_traits", data);
        if (job != null) identity.putString("job", job.toString());
        identity.putInt("owner_mode", owner);
        identity.putInt("chance_denominator", chanceDenominator(stack));
        if (hasBook) identity.putString("calling_book", book);
        return canonical(identity);
    }

    /** Sorted compounds, ordered lists and typed primitive tags avoid unstable SNBT/key iteration and delimiter collisions. */
    private static String canonical(Tag value) {
        if (value instanceof CompoundTag compound) {
            StringBuilder result = new StringBuilder("{");
            for (String key : compound.getAllKeys().stream().sorted().toList()) {
                result.append(key.length()).append(':').append(key).append('=').append(canonical(compound.get(key))).append(';');
            }
            return result.append('}').toString();
        }
        if (value instanceof ListTag list) {
            StringBuilder result = new StringBuilder("[");
            for (Tag child : list) result.append(canonical(child)).append(';');
            return result.append(']').toString();
        }
        return value.getId() + ":" + value;
    }

    /** Shell identifies the entity/rank; spots identify the job or random pool. This does not instantiate entities. */
    public static int color(ItemStack stack, int tint) {
        if (tint < 0 || tint > 1) return -1;
        ResourceLocation target = RitualSpawnEggItem.entityTag(stack);
        if (target == null) target = RitualSpawnEggItem.entityId(stack);
        String targetId = target == null ? "" : target.toString();
        if (tint == 0) return 0xFF000000 | switch (targetId) {
            case "occultism:foliot" -> 0xD6BC89;
            case "occultism:djinni" -> 0x59B7DB;
            case "occultism:afrit", "occultism:afrit_wild" -> 0xE58144;
            case "occultism:marid", "occultism:marid_unbound" -> 0xA07DDD;
            case "" -> 0xBA91D8;
            default -> hashedColor(targetId, 0.43F, 0.86F);
        };
        ResourceLocation job = job(stack);
        if (job == null) return 0xFF000000 | (chanceDenominator(stack) > 1 ? 0xFFE477
                : hashedColor(targetId + "/spots/" + ownerMode(RitualSpawnEggItem.data(stack)), 0.78F, 0.49F));
        String path = job.getPath();
        if (path.startsWith("crush_tier")) return 0xFFAC3348;
        if (path.startsWith("crystal_tier") || path.startsWith("crystallize_tier")) return 0xFF3BE2D6;
        if (path.startsWith("smelt_tier")) return 0xFFFFDF65;
        return 0xFF000000 | hashedColor(job.toString(), 0.80F, 0.52F);
    }

    private static int hashedColor(String key, float saturation, float value) {
        return Mth.hsvToRgb(Math.floorMod(key.hashCode(), 360) / 360F, saturation, value);
    }
}
