package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Optional Ars Nouveau integration that deliberately avoids hard-linking its
 * implementation classes. In Ars Nouveau 1.20.1 a filled mob jar stores its
 * captured entity at BlockEntityTag.entityTag.
 */
public final class ArsNouveauIntegration {
    private static final ResourceLocation MOB_JAR_ITEM =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mob_jar");
    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String ENTITY_TAG = "entityTag";
    private static final String ENTITY_ID = "entityId";
    private static final String EXTRA_MOB_DATA = "extraMobData";

    private ArsNouveauIntegration() {
    }

    public static boolean isFilledMobJar(ItemStack stack) {
        return isMobJar(stack) && !entityId(stack).isEmpty();
    }

    public static String entityId(ItemStack stack) {
        CompoundTag tag = entityTag(stack);
        if (tag == null) {
            return "";
        }
        ResourceLocation entityId = ResourceLocation.tryParse(tag.getString("id"));
        return entityId == null ? "" : entityId.toString();
    }

    /**
     * Returns a defensive copy of the captured entity data, including
     * Occultism's optional spiritJob.factoryId field.
     */
    public static CompoundTag entityTag(ItemStack stack) {
        if (!isMobJar(stack) || !stack.hasTag()) {
            return null;
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag blockEntityTag = root.getCompound(BLOCK_ENTITY_TAG);
        if (!blockEntityTag.contains(ENTITY_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag entityTag = blockEntityTag.getCompound(ENTITY_TAG);
        return entityTag.isEmpty() ? null : entityTag.copy();
    }

    /**
     * Removes only the captured mob component, leaving the empty jar item in
     * place for ritual sacrifice processing.
     */
    public static boolean emptyMobJar(ItemStack stack) {
        if (!isFilledMobJar(stack) || !stack.hasTag()) {
            return false;
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag blockEntityTag = root.getCompound(BLOCK_ENTITY_TAG).copy();
        blockEntityTag.remove(ENTITY_TAG);
        blockEntityTag.remove(ENTITY_ID);
        blockEntityTag.remove(EXTRA_MOB_DATA);
        if (blockEntityTag.isEmpty()) {
            root.remove(BLOCK_ENTITY_TAG);
        } else {
            root.put(BLOCK_ENTITY_TAG, blockEntityTag);
        }
        if (root.isEmpty()) {
            stack.setTag(null);
        }
        return true;
    }

    private static boolean isMobJar(ItemStack stack) {
        return !stack.isEmpty()
                && MOB_JAR_ITEM.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}

