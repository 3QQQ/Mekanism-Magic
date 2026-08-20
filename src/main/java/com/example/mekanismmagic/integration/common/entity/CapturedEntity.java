package com.example.mekanismmagic.integration.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Stable entity-container data exposed to recipe integrations.
 */
public record CapturedEntity(ResourceLocation entityId, CompoundTag entityData) {
    public CapturedEntity {
        Objects.requireNonNull(entityId, "entityId");
        entityData = entityData == null ? new CompoundTag() : entityData.copy();
    }

    @Override
    public CompoundTag entityData() {
        return entityData.copy();
    }
}
