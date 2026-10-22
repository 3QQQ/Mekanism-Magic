package com.example.mekanismmagic.integration.naturesaura;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;

/** Encodes the recipe's fixed entity type without constructing an entity or running spawn initialization. */
public final class AnimalSpawnEggs {
    private AnimalSpawnEggs() { }

    public static boolean supported(EntityType<?> type) {
        if (type == null || type == EntityType.PLAYER || !type.canSummon()) return false;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null) == type;
    }

    public static ItemStack forType(EntityType<?> type) {
        if (!supported(type)) return ItemStack.EMPTY;
        SpawnEggItem nativeEgg = SpawnEggItem.byId(type);
        if (nativeEgg != null) return new ItemStack(nativeEgg);
        // Nature-only worlds cannot depend on the Occultism-only ritual_spawn_egg registry entry.
        ItemStack result = new ItemStack(NaturesAuraRegistries.ANIMAL_SPAWN_EGG.get());
        CompoundTag data = new CompoundTag();
        data.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        result.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        return result;
    }
}
