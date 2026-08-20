package com.example.mekanismmagic.integration.common.entity;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Optional-mod boundary for items that retain a captured entity when emptied.
 */
public interface EntityContainerAdapter {
    String modId();

    Optional<CapturedEntity> capturedEntity(ItemStack stack);

    boolean empty(ItemStack stack);
}
