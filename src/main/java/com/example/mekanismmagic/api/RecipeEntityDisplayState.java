package com.example.mekanismmagic.api;

import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

/**
 * Compact render-only entity state shared by the server-side recipe machine
 * and its client-side block entity renderer.
 */
public final class RecipeEntityDisplayState {
    public static final String UPDATE_TAG = "magic_recipe_display_entity";

    private CompoundTag entityData = new CompoundTag();

    /** Returns true only when the value visible to clients changed. */
    public boolean update(ItemStack source) {
        CompoundTag next = resolve(source)
                .map(RecipeEntityDisplayState::sanitise)
                .orElseGet(CompoundTag::new);
        if (entityData.equals(next)) {
            return false;
        }
        entityData = next;
        return true;
    }

    public static boolean representsEntity(ItemStack source) {
        return resolve(source).isPresent();
    }

    public boolean clear() {
        if (entityData.isEmpty()) {
            return false;
        }
        entityData = new CompoundTag();
        return true;
    }

    /** Read-only by convention; renderers must copy before modifying. */
    public CompoundTag entityData() {
        return entityData;
    }

    public void writeUpdateTag(CompoundTag updateTag) {
        if (entityData.isEmpty()) {
            updateTag.remove(UPDATE_TAG);
        } else {
            updateTag.put(UPDATE_TAG, entityData.copy());
        }
    }

    public void readUpdateTag(CompoundTag updateTag) {
        entityData = updateTag.contains(UPDATE_TAG, CompoundTag.TAG_COMPOUND)
                ? updateTag.getCompound(UPDATE_TAG).copy()
                : new CompoundTag();
    }

    private static Optional<CompoundTag> resolve(ItemStack source) {
        if (source == null || source.isEmpty()) {
            return Optional.empty();
        }

        if (source.getItem() instanceof SpawnEggItem egg) {
            EntityType<?> type = egg.getType(source);
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) {
                return Optional.empty();
            }
            CompoundTag data = componentData(source);
            data.putString("id", id.toString());
            return Optional.of(data);
        }

        Optional<CapturedEntity> captured =
                EntityContainerRegistry.capturedEntity(source);
        if (captured.isPresent()) {
            CompoundTag data = captured.get().entityData();
            data.putString("id", captured.get().entityId().toString());
            return Optional.of(data);
        }

        CompoundTag data = componentData(source);
        ResourceLocation id = ResourceLocation.tryParse(data.getString("id"));
        if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return Optional.of(data);
        }

        ResourceLocation entityTagId = RitualSpawnEggItem.entityTag(source);
        if (entityTagId == null) {
            return Optional.empty();
        }
        TagKey<EntityType<?>> entityTag = TagKey.create(
                Registries.ENTITY_TYPE, entityTagId);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getTag(entityTag)
                .stream()
                .flatMap(named -> named.stream())
                .map(Holder::value)
                .findFirst()
                .orElse(null);
        ResourceLocation resolvedId = type == null ? null
                : BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (resolvedId == null) {
            return Optional.empty();
        }
        data.putString("id", resolvedId.toString());
        return Optional.of(data);
    }

    private static CompoundTag componentData(ItemStack source) {
        CustomData customData = source.get(DataComponents.ENTITY_DATA);
        return customData == null || customData.isEmpty()
                ? new CompoundTag() : customData.copyTag();
    }

    private static CompoundTag sanitise(CompoundTag original) {
        CompoundTag data = original.copy();
        // A display entity needs visual variant data, equipment and its job,
        // but must not inherit world position, velocity, passengers or UUID.
        data.remove("UUID");
        data.remove("Pos");
        data.remove("Motion");
        data.remove("Rotation");
        data.remove("Passengers");
        data.remove("Leash");
        return data;
    }
}
