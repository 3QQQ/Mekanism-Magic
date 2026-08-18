package com.example.mekanismmagic.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Spawn egg fallback for Occultism entities without a registered SpawnEggItem,
 * and for ritual recipes whose target is an entity tag.
 */
public final class RitualSpawnEggItem extends Item {
    public static final String ENTITY_TAG = "ritual_entity_tag";

    public RitualSpawnEggItem(Properties properties) {
        super(properties);
    }

    public static ItemStack forEntity(EntityType<?> entity, CompoundTag data) {
        ItemStack stack = new ItemStack(com.example.mekanismmagic.MekanismMagic.RITUAL_SPAWN_EGG.get());
        CompoundTag tag = data == null ? new CompoundTag() : data.copy();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity);
        if (id != null) {
            tag.putString("id", id.toString());
        }
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        return stack;
    }

    public static ItemStack forTag(TagKey<EntityType<?>> entityTag, CompoundTag data) {
        ItemStack stack = new ItemStack(com.example.mekanismmagic.MekanismMagic.RITUAL_SPAWN_EGG.get());
        CompoundTag tag = data == null ? new CompoundTag() : data.copy();
        tag.putString(ENTITY_TAG, entityTag.location().toString());
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        return stack;
    }

    public static ResourceLocation entityId(ItemStack stack) {
        CompoundTag data = data(stack);
        return ResourceLocation.tryParse(data.getString("id"));
    }

    public static ResourceLocation entityTag(ItemStack stack) {
        CompoundTag data = data(stack);
        return ResourceLocation.tryParse(data.getString(ENTITY_TAG));
    }

    private static CompoundTag data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.ENTITY_DATA);
        return data == null || data.isEmpty() ? new CompoundTag() : data.copyTag();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        EntityType<?> type = resolveEntity(context.getItemInHand(), level);
        if (type == null) {
            return InteractionResult.FAIL;
        }
        type.spawn(level, context.getItemInHand(), context.getPlayer(),
                context.getClickedPos().relative(context.getClickedFace()),
                MobSpawnType.SPAWN_EGG, true, false);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    private static EntityType<?> resolveEntity(ItemStack stack, ServerLevel level) {
        ResourceLocation tagId = entityTag(stack);
        if (tagId != null) {
            TagKey<EntityType<?>> tag = TagKey.create(
                    net.minecraft.core.registries.Registries.ENTITY_TYPE, tagId);
            List<EntityType<?>> candidates = BuiltInRegistries.ENTITY_TYPE.getTag(tag)
                    .stream().flatMap(named -> named.stream())
                    .map(Holder::value).toList();
            if (!candidates.isEmpty()) {
                return candidates.get(level.random.nextInt(candidates.size()));
            }
        }
        ResourceLocation id = entityId(stack);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
    }
}
