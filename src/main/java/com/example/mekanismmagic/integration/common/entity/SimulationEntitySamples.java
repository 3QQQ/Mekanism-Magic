package com.example.mekanismmagic.integration.common.entity;

import com.example.mekanismmagic.item.RitualSpawnEggItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Optional;

/** Read-only samples for the Drygmy/blood simulators, NOT consumable sacrifice containers. */
public final class SimulationEntitySamples {
    private SimulationEntitySamples() { }

    public static boolean isSample(ItemStack stack) { return read(stack).isPresent(); }

    public static Optional<CapturedEntity> read(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        try {
            if (stack.getItem() instanceof SpawnEggItem egg) {
                var dataComponent = stack.get(DataComponents.ENTITY_DATA);
                CompoundTag data = dataComponent == null ? new CompoundTag() : dataComponent.copyTag();
                // Native getType silently falls back for a malformed override. An explicit
                // invalid ID must not masquerade as the egg's default specimen in a machine.
                EntityType<?> override = data.contains("id") ? explicitType(data).orElse(null) : null;
                if (data.contains("id") && override == null) return Optional.empty();
                EntityType<?> type = egg.getType(stack.copyWithCount(1));
                if (override != null && type != override) return Optional.empty();
                return sample(type, data);
            }
            if (stack.getItem() instanceof RitualSpawnEggItem) {
                CompoundTag data = RitualSpawnEggItem.data(stack);
                // A random pool or conditional chicken fallback is not one stable entity.
                // Do not reroll such an egg every time the simulator rebuilds its cache.
                if (data.contains(RitualSpawnEggItem.ENTITY_TAG)
                        || data.contains("ritual_chicken_fallback_denominator")) return Optional.empty();
                return explicitType(data).flatMap(type -> sample(type, data));
            }
            // Preserve all existing captured-container behavior and its component adapters.
            return EntityContainerRegistry.capturedEntity(stack);
        } catch (RuntimeException malformedOrUnresolved) {
            return Optional.empty();
        }
    }

    private static Optional<EntityType<?>> explicitType(CompoundTag data) {
        if (!data.contains("id", Tag.TAG_STRING)) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(data.getString("id"));
        return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? Optional.empty() : Optional.of(BuiltInRegistries.ENTITY_TYPE.get(id));
    }

    private static Optional<CapturedEntity> sample(EntityType<?> type, CompoundTag source) {
        if (type == null || type == EntityType.PLAYER || !DefaultAttributes.hasSupplier(type)) return Optional.empty();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) || BuiltInRegistries.ENTITY_TYPE.get(id) != type) {
            return Optional.empty();
        }
        if (source.contains("Passengers")
                && (!(source.get("Passengers") instanceof ListTag passengers) || !passengers.isEmpty())) return Optional.empty();
        CompoundTag data = source.copy();
        data.putString("id", id.toString());
        return Optional.of(new CapturedEntity(id, data));
    }
}
