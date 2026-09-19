package com.example.mekanismmagic.integration.common.entity.component;

import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerAdapter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

/** Just Dire Things 1.5 keeps an authoritative type alongside ENTITY_DATA. */
public final class JustDireThingsEntityContainerAdapter
        implements EntityContainerAdapter {
    public static final JustDireThingsEntityContainerAdapter INSTANCE =
            new JustDireThingsEntityContainerAdapter();
    private static final ResourceLocation ITEM =
            ResourceLocation.fromNamespaceAndPath("justdirethings", "creaturecatcher");
    private static final ResourceLocation TYPE_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("justdirethings", "entitytype");

    private JustDireThingsEntityContainerAdapter() {
    }

    @Override
    public String modId() {
        return "justdirethings";
    }

    @Override
    public Optional<CapturedEntity> capturedEntity(ItemStack stack) {
        if (!ComponentEntityContainerSupport.isItem(stack, ITEM)) {
            return Optional.empty();
        }
        Optional<String> entityType = ComponentEntityContainerSupport
                .componentValue(stack, TYPE_COMPONENT)
                .filter(String.class::isInstance)
                .map(String.class::cast);
        if (entityType.isEmpty()) {
            return Optional.empty();
        }
        Object data = stack.get(DataComponents.ENTITY_DATA);
        if (data != null && !(data instanceof CustomData)) {
            return Optional.empty();
        }
        // Native getEntityFromItemStack also accepts a type without saved data.
        CompoundTag tag = data == null ? new CompoundTag() : ((CustomData) data).copyTag();
        return ComponentEntityContainerSupport.capturedEntity(entityType.get(), tag);
    }

    @Override
    public boolean empty(ItemStack stack) {
        if (capturedEntity(stack).isEmpty()) {
            return false;
        }
        // Native release constructs a new default creature catcher.
        ComponentEntityContainerSupport.resetToDefaultComponents(stack);
        return true;
    }
}
