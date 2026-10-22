package com.example.mekanismmagic.integration.common.entity.component;

import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;

/**
 * Accesses registered components without linking to optional mod classes.
 */
public final class ComponentEntityContainerSupport {
    private ComponentEntityContainerSupport() {
    }

    public static Optional<DataComponentType<?>> component(ResourceLocation id) {
        return id == null ? Optional.empty()
                : BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id);
    }

    /** The returned value belongs to the stack and must only be read. */
    public static Optional<Object> componentValue(ItemStack stack,
                                                  ResourceLocation id) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return component(id).flatMap(type ->
                Optional.ofNullable(stack.get(type)));
    }

    /** Uses the component's own codec and never returns its mutable data. */
    public static Optional<CompoundTag> encodedComponent(ItemStack stack,
                                                        ResourceLocation id) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return component(id).flatMap(type -> encodeComponent(stack, type));
    }

    private static <T> Optional<CompoundTag> encodeComponent(ItemStack stack,
                                                           DataComponentType<T> type) {
        try {
            T value = stack.get(type);
            var codec = type.codec();
            if (value == null || codec == null) {
                return Optional.empty();
            }
            return codec.encodeStart(NbtOps.INSTANCE, value).result()
                    .filter(CompoundTag.class::isInstance)
                    .map(CompoundTag.class::cast)
                    .map(CompoundTag::copy);
        } catch (RuntimeException ignored) {
            // Unsupported or malformed optional components are not sacrifices.
            return Optional.empty();
        }
    }

    public static boolean isItem(ItemStack stack, ResourceLocation itemId) {
        return !stack.isEmpty() && itemId != null
                && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Normalizes the authoritative type without mutating the supplied tag. */
    public static Optional<CapturedEntity> capturedEntity(String id,
                                                        CompoundTag entityData) {
        ResourceLocation entityId = id == null
                ? null : ResourceLocation.tryParse(id);
        // ENTITY_TYPE is a defaulted registry; a lookup alone can return a pig
        // for an unknown ID, so check membership before resolving the type.
        if (entityId == null
                || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)
                || BuiltInRegistries.ENTITY_TYPE.get(entityId) == EntityType.PLAYER) {
            return Optional.empty();
        }
        CompoundTag tag = entityData == null
                ? new CompoundTag() : entityData.copy();
        tag.putString("id", entityId.toString());
        return Optional.of(new CapturedEntity(entityId, tag));
    }

    /** Matches a returned default empty container while preserving item/count. */
    public static void resetToDefaultComponents(ItemStack stack) {
        ItemStack empty = stack.getItem().getDefaultInstance();
        for (DataComponentType<?> type : Set.copyOf(stack.getComponents().keySet())) {
            stack.remove(type);
        }
        stack.applyComponents(empty.getComponents());
    }
}
