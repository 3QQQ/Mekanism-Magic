package com.example.mekanismmagic.integration.common.entity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Optional-mod boundary for items that retain a captured entity when emptied.
 */
public interface EntityContainerAdapter {
    String modId();

    Optional<CapturedEntity> capturedEntity(ItemStack stack);

    boolean empty(ItemStack stack);

    /** Conservative single-container return, without mutating inputs or rolling durability. */
    default Optional<ItemStack> previewEmpty(ItemStack stack) {
        if (stack.isEmpty() || capturedEntity(stack).isEmpty()) {
            return Optional.empty();
        }
        ItemStack copy = stack.copyWithCount(1);
        return empty(copy) ? Optional.of(copy) : Optional.empty();
    }

    /** Actual single-container return. An empty stack represents a consumed/broken vessel. */
    default Optional<ItemStack> emptyCopy(ItemStack stack, @Nullable ServerLevel level) {
        return previewEmpty(stack);
    }
}
