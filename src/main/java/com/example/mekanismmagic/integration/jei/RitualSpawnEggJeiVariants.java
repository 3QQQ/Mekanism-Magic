package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.item.RitualSpawnEggIdentity;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;

/** Searchable representative eggs from real supported recipe outputs, deduplicated by the same subtype key as JEI. */
final class RitualSpawnEggJeiVariants {
    private RitualSpawnEggJeiVariants() { }
    static List<ItemStack> collect(List<ItemStack> outputs) {
        var variants = new LinkedHashMap<String, ItemStack>();
        for (ItemStack stack : outputs) {
            if (!(stack.getItem() instanceof RitualSpawnEggItem)) continue;
            String key = RitualSpawnEggIdentity.variantKey(stack);
            if (!key.isEmpty()) variants.putIfAbsent(key, stack.copyWithCount(1));
        }
        return List.copyOf(variants.values());
    }
    static void reconcile(IIngredientManager manager, List<ItemStack> previous, List<ItemStack> fresh) {
        var oldKeys = previous.stream().map(RitualSpawnEggIdentity::variantKey).collect(java.util.stream.Collectors.toSet());
        var newKeys = fresh.stream().map(RitualSpawnEggIdentity::variantKey).collect(java.util.stream.Collectors.toSet());
        var removed = previous.stream().filter(stack -> !newKeys.contains(RitualSpawnEggIdentity.variantKey(stack))).toList();
        var added = fresh.stream().filter(stack -> !oldKeys.contains(RitualSpawnEggIdentity.variantKey(stack))).map(ItemStack::copy).toList();
        if (!removed.isEmpty()) manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, removed);
        if (!added.isEmpty()) manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, added);
    }
}
