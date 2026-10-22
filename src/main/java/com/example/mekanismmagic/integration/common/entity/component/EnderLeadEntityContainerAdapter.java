package com.example.mekanismmagic.integration.common.entity.component;

import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerAdapter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.Set;

/**
 * Apothic Enchanting's ender leads, including their native release durability.
 * Resolves components through the registry so the mod remains optional.
 */
public final class EnderLeadEntityContainerAdapter
        implements EntityContainerAdapter {
    public static final EnderLeadEntityContainerAdapter INSTANCE =
            new EnderLeadEntityContainerAdapter();
    private static final String MOD_ID = "apothic_enchanting";
    private static final Set<String> CONTAINER_PATHS = Set.of(
            "flimsy_ender_lead", "ender_lead", "occult_ender_lead");
    private static final ResourceLocation ENTITY_TYPE_COMPONENT =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "leashed_entity_type");
    private static final ResourceLocation ENTITY_NAME_COMPONENT =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "leashed_entity_name");

    private EnderLeadEntityContainerAdapter() {
    }

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public Optional<CapturedEntity> capturedEntity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !MOD_ID.equals(itemId.getNamespace())
                || !CONTAINER_PATHS.contains(itemId.getPath())) {
            return Optional.empty();
        }
        Object typeValue = ComponentEntityContainerSupport.componentValue(
                stack, ENTITY_TYPE_COMPONENT).orElse(null);
        if (!(typeValue instanceof EntityType<?> entityType)) {
            return Optional.empty();
        }
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(
                entityType);
        // Defaulted registries can return their default ID for unknown values.
        if (entityId == null
                || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)
                || BuiltInRegistries.ENTITY_TYPE.get(entityId) != entityType) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.ENTITY_DATA);
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        // The lead's own release code uses its type component over the NBT id.
        return ComponentEntityContainerSupport.capturedEntity(
                entityId.toString(), data.copyTag());
    }

    @Override
    public Optional<ItemStack> previewEmpty(ItemStack stack) {
        if (capturedEntity(stack).isEmpty()) {
            return Optional.empty();
        }
        ItemStack result = stack.copyWithCount(1);
        clearEntityComponents(result);
        // Keep a possible remainder slot reserved without rolling Unbreaking.
        return Optional.of(result);
    }

    @Override
    public Optional<ItemStack> emptyCopy(ItemStack stack, ServerLevel level) {
        Optional<ItemStack> preview = previewEmpty(stack);
        if (preview.isEmpty()) {
            return Optional.empty();
        }
        ItemStack result = stack.copyWithCount(1);
        if (level == null) {
            // Without server enchantment/random context, never waive damage.
            return requiresReleaseDamage(result)
                    ? Optional.empty() : preview;
        }
        // Match native release ordering: enchantment predicates still see the
        // captured entity while durability is processed, then clear the lead.
        result.hurtAndBreak(1, level, (ServerPlayer) null, brokenItem -> {
        });
        clearEntityComponents(result);
        // A successful release may consume the last durability and the lead.
        return Optional.of(result);
    }

    @Override
    public boolean empty(ItemStack stack) {
        if (capturedEntity(stack).isEmpty() || requiresReleaseDamage(stack)) {
            return false;
        }
        clearEntityComponents(stack);
        return true;
    }

    private static boolean requiresReleaseDamage(ItemStack stack) {
        return stack.isDamageableItem() && !stack.has(DataComponents.UNBREAKABLE);
    }

    private static void clearEntityComponents(ItemStack stack) {
        stack.remove(DataComponents.ENTITY_DATA);
        ComponentEntityContainerSupport.component(ENTITY_TYPE_COMPONENT)
                .ifPresent(stack::remove);
        ComponentEntityContainerSupport.component(ENTITY_NAME_COMPONENT)
                .ifPresent(stack::remove);
    }
}
