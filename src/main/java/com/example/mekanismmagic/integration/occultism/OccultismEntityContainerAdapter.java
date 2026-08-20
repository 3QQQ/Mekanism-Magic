package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerAdapter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.Set;

/**
 * Reads Occultism soul containers backed by the vanilla ENTITY_DATA component.
 */
public final class OccultismEntityContainerAdapter
        implements EntityContainerAdapter {
    public static final OccultismEntityContainerAdapter INSTANCE =
            new OccultismEntityContainerAdapter();
    private static final Set<String> CONTAINER_PATHS = Set.of(
            "soul_gem",
            "fragile_soul_gem",
            "trinity_gem",
            "magic_lamp_empty"
    );

    private OccultismEntityContainerAdapter() {
    }

    @Override
    public String modId() {
        return ModCompatibility.OCCULTISM;
    }

    @Override
    public Optional<CapturedEntity> capturedEntity(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !modId().equals(itemId.getNamespace())
                || !CONTAINER_PATHS.contains(itemId.getPath())) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.ENTITY_DATA);
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        ResourceLocation entityId =
                ResourceLocation.tryParse(tag.getString("id"));
        return entityId == null ? Optional.empty()
                : Optional.of(new CapturedEntity(entityId, tag));
    }

    @Override
    public boolean empty(ItemStack stack) {
        if (capturedEntity(stack).isEmpty()) {
            return false;
        }
        stack.remove(DataComponents.ENTITY_DATA);
        return true;
    }
}
