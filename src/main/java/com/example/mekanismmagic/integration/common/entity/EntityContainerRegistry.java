package com.example.mekanismmagic.integration.common.entity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ordered registry of reusable entity-container integrations.
 */
public final class EntityContainerRegistry {
    private static final List<EntityContainerAdapter> ADAPTERS = new ArrayList<>();

    private EntityContainerRegistry() {
    }

    public static synchronized void register(EntityContainerAdapter adapter) {
        boolean duplicate = ADAPTERS.stream()
                .anyMatch(existing -> existing.modId().equals(adapter.modId())
                        && existing.getClass().equals(adapter.getClass()));
        if (!duplicate) {
            ADAPTERS.add(adapter);
        }
    }

    public static Optional<CapturedEntity> capturedEntity(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (EntityContainerAdapter adapter : adapters()) {
            Optional<CapturedEntity> captured = validated(adapter.capturedEntity(stack));
            if (captured.isPresent()) {
                return captured;
            }
        }
        return Optional.empty();
    }

    public static boolean isFilled(ItemStack stack) {
        return capturedEntity(stack).isPresent();
    }

    /** A ritual consumes exactly one entity. Never silently destroy captured passengers. */
    public static boolean isSacrificeContainer(ItemStack stack) {
        return previewEmpty(stack).isPresent();
    }

    public static Optional<ItemStack> previewEmpty(ItemStack stack) {
        return emptied(stack, null, true);
    }

    public static Optional<ItemStack> emptyCopy(ItemStack stack, @Nullable ServerLevel level) {
        return emptied(stack, level, false);
    }

    private static Optional<ItemStack> emptied(ItemStack stack, @Nullable ServerLevel level,
                                               boolean preview) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (EntityContainerAdapter adapter : adapters()) {
            Optional<CapturedEntity> captured = validated(adapter.capturedEntity(stack));
            if (captured.isEmpty()) {
                continue;
            }
            var tag = captured.get().entityData();
            if (tag.contains("Passengers")
                    && (!(tag.get("Passengers") instanceof ListTag passengers) || !passengers.isEmpty())) {
                return Optional.empty();
            }
            ItemStack single = stack.copyWithCount(1);
            Optional<ItemStack> result = preview
                    ? adapter.previewEmpty(single) : adapter.emptyCopy(single, level);
            if (result.isEmpty() || result.get().getCount() > 1
                    || isFilled(result.get())) {
                return Optional.empty();
            }
            return result.map(ItemStack::copy);
        }
        return Optional.empty();
    }

    public static boolean empty(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (EntityContainerAdapter adapter : adapters()) {
            if (validated(adapter.capturedEntity(stack)).isPresent() && adapter.empty(stack)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized List<EntityContainerAdapter> adapters() {
        return List.copyOf(ADAPTERS);
    }

    private static Optional<CapturedEntity> validated(Optional<CapturedEntity> captured) {
        return captured.filter(value -> BuiltInRegistries.ENTITY_TYPE.containsKey(value.entityId())
                && BuiltInRegistries.ENTITY_TYPE.get(value.entityId()) != EntityType.PLAYER);
    }
}
