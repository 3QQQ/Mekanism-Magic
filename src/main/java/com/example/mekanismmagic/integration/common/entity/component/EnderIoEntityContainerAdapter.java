package com.example.mekanismmagic.integration.common.entity.component;

import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Reads EnderIO's registered Soul codec, including its legacy entityTag form. */
public final class EnderIoEntityContainerAdapter implements EntityContainerAdapter {
    public static final EnderIoEntityContainerAdapter INSTANCE =
            new EnderIoEntityContainerAdapter();
    private static final ResourceLocation ITEM =
            ResourceLocation.fromNamespaceAndPath("enderio", "soul_vial");
    private static final ResourceLocation SOUL_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("enderio", "soul");

    private EnderIoEntityContainerAdapter() {
    }

    @Override
    public String modId() {
        return "enderio";
    }

    @Override
    public Optional<CapturedEntity> capturedEntity(ItemStack stack) {
        if (!ComponentEntityContainerSupport.isItem(stack, ITEM)) {
            return Optional.empty();
        }
        return ComponentEntityContainerSupport.encodedComponent(stack, SOUL_COMPONENT)
                .flatMap(EnderIoEntityContainerAdapter::decodeSoul);
    }

    private static Optional<CapturedEntity> decodeSoul(CompoundTag soul) {
        if (soul.contains("entity_type") || soul.contains("entity_tag")) {
            if (!soul.contains("entity_type", Tag.TAG_STRING)
                    || !soul.contains("entity_tag", Tag.TAG_COMPOUND)) {
                return Optional.empty();
            }
            // A type with an empty tag is a valid Soul.of(EntityType).
            return ComponentEntityContainerSupport.capturedEntity(
                    soul.getString("entity_type"), soul.getCompound("entity_tag"));
        }
        if (!soul.contains("entityTag", Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag entity = soul.getCompound("entityTag");
        if (!entity.contains("id", Tag.TAG_STRING)) {
            return Optional.empty();
        }
        return ComponentEntityContainerSupport.capturedEntity(
                entity.getString("id"), entity);
    }

    @Override
    public boolean empty(ItemStack stack) {
        if (capturedEntity(stack).isEmpty()) {
            return false;
        }
        // Native release returns SOUL_VIAL.getDefaultInstance(), including
        // removal of entity_max_health and unrelated custom components.
        ComponentEntityContainerSupport.resetToDefaultComponents(stack);
        return true;
    }
}
