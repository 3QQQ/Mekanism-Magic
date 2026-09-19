package com.example.mekanismmagic.integration.common.entity.component;

import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Industrial Foregoing 3.6 stores saveWithoutId data plus an entity key. */
public final class IndustrialForegoingEntityContainerAdapter
        implements EntityContainerAdapter {
    public static final IndustrialForegoingEntityContainerAdapter INSTANCE =
            new IndustrialForegoingEntityContainerAdapter();
    private static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath(
            "industrialforegoing", "mob_imprisonment_tool");
    private static final ResourceLocation ENTITY_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("industrialforegoing", "mob_imprisonment");

    private IndustrialForegoingEntityContainerAdapter() {
    }

    @Override
    public String modId() {
        return "industrialforegoing";
    }

    @Override
    public Optional<CapturedEntity> capturedEntity(ItemStack stack) {
        if (!ComponentEntityContainerSupport.isItem(stack, ITEM)) {
            return Optional.empty();
        }
        return ComponentEntityContainerSupport.componentValue(stack, ENTITY_COMPONENT)
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .filter(tag -> tag.contains("entity", Tag.TAG_STRING))
                .flatMap(tag -> ComponentEntityContainerSupport.capturedEntity(
                        tag.getString("entity"), tag));
    }

    @Override
    public boolean empty(ItemStack stack) {
        if (capturedEntity(stack).isEmpty()) {
            return false;
        }
        return ComponentEntityContainerSupport.component(ENTITY_COMPONENT)
                .map(type -> stack.remove(type) != null)
                .orElse(false);
    }
}
