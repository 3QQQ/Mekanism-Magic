package com.example.mekanismmagic.integration.common.entity;

import net.minecraft.world.item.ItemStack;

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
            Optional<CapturedEntity> captured = adapter.capturedEntity(stack);
            if (captured.isPresent()) {
                return captured;
            }
        }
        return Optional.empty();
    }

    public static boolean isFilled(ItemStack stack) {
        return capturedEntity(stack).isPresent();
    }

    public static boolean empty(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (EntityContainerAdapter adapter : adapters()) {
            if (adapter.capturedEntity(stack).isPresent() && adapter.empty(stack)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized List<EntityContainerAdapter> adapters() {
        return List.copyOf(ADAPTERS);
    }
}
