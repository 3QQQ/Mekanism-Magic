package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Defines data-pack-extensible Drygmy filtering and fixed custom drops. */
final class DrygmyLootPolicy {
    static final TagKey<Item> LOOT_BLACKLIST = itemTag(
            "drygmy_loot_blacklist");
    static final TagKey<Item> ALLOW_DAMAGEABLE_LOOT = itemTag(
            "drygmy_allow_damageable_loot");

    private static final int MAX_FIXED_DROP_COUNT = 1 << 8;
    private static final List<SpecialLootRule> SPECIAL_LOOT = List.of(
            special("nether_star", "minecraft", "nether_star"),
            special("wilden_tribute", "ars_nouveau",
                    "wilden_tribute"));

    private DrygmyLootPolicy() {
    }

    /**
     * Removes equipment clutter before the simulator performs random
     * selection. Ordinary material drops remain namespace-agnostic.
     */
    static void filterCandidates(List<ItemStack> candidates) {
        candidates.removeIf(stack -> !shouldKeep(stack));
    }

    /**
     * Promotes custom-death drops that are absent from loot tables into fixed
     * outputs. Removing matching candidates first prevents a data pack or
     * global loot modifier from making the same item both fixed and randomly
     * selectable.
     */
    static List<ItemStack> extractFixedOutputs(
            List<ItemStack> candidates, List<LivingEntity> entities,
            int operationMultiplier) {
        List<ItemStack> fixedOutputs = new ArrayList<>();
        for (SpecialLootRule rule : SPECIAL_LOOT) {
            Set<EntityType<?>> matchingTypes = new HashSet<>();
            for (LivingEntity entity : entities) {
                if (entity.getType().is(rule.sources())) {
                    matchingTypes.add(entity.getType());
                }
            }
            if (!matchingTypes.isEmpty()) {
                Item item = BuiltInRegistries.ITEM
                        .getOptional(rule.itemId()).orElse(null);
                if (item == null) {
                    continue;
                }
                ItemStack fixed = removeMatchingCandidate(
                        candidates, item);
                if (fixed.isEmpty()) {
                    fixed = new ItemStack(item);
                }
                if (shouldKeep(fixed)) {
                    fixed.setCount(fixedDropCount(
                            operationMultiplier, matchingTypes.size()));
                    fixedOutputs.add(fixed);
                }
            }
        }
        return fixedOutputs;
    }

    static boolean shouldKeep(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return shouldKeepClassification(
                false,
                stack.is(LOOT_BLACKLIST),
                stack.is(ALLOW_DAMAGEABLE_LOOT),
                stack.isDamageableItem(),
                stack.is(Tags.Items.TOOLS),
                stack.is(Tags.Items.ARMORS));
    }

    /** Package-private classification seam for the offline policy test. */
    static boolean shouldKeepClassification(
            boolean empty, boolean blacklisted, boolean allowDamageable,
            boolean damageable, boolean commonTool, boolean commonArmor) {
        if (empty || blacklisted) {
            return false;
        }
        return allowDamageable
                || !(damageable || commonTool || commonArmor);
    }

    static int fixedDropCount(int operationMultiplier,
                              int matchingEntityTypes) {
        if (matchingEntityTypes <= 0) {
            return 0;
        }
        long perType = Math.min(MAX_FIXED_DROP_COUNT,
                Math.max(1, operationMultiplier));
        return (int) Math.min(Integer.MAX_VALUE,
                perType * matchingEntityTypes);
    }

    static boolean hasFixedDropMapping(String tagPath,
                                       ResourceLocation itemId) {
        for (SpecialLootRule rule : SPECIAL_LOOT) {
            if (rule.sources().location().getPath().equals(tagPath)
                    && rule.itemId().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack removeMatchingCandidate(
            List<ItemStack> candidates, Item item) {
        ItemStack template = ItemStack.EMPTY;
        Iterator<ItemStack> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            ItemStack candidate = iterator.next();
            if (candidate.is(item)) {
                if (template.isEmpty()) {
                    template = candidate.copyWithCount(1);
                }
                iterator.remove();
            }
        }
        return template;
    }

    private static SpecialLootRule special(
            String path, String itemNamespace, String itemPath) {
        return new SpecialLootRule(entityTag(
                "drygmy_special_loot/" + path),
                ResourceLocation.fromNamespaceAndPath(
                        itemNamespace, itemPath));
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(
                        MekanismMagic.MOD_ID, path));
    }

    private static TagKey<EntityType<?>> entityTag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(
                        MekanismMagic.MOD_ID, path));
    }

    private record SpecialLootRule(TagKey<EntityType<?>> sources,
                                   ResourceLocation itemId) {
    }
}
