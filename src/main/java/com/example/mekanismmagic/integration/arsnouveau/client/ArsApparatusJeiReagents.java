package com.example.mekanismmagic.integration.arsnouveau.client;

import com.hollingsworth.arsnouveau.api.registry.PerkRegistry;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ArmorUpgradeRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantmentRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Recipe lookup examples, separate from the bounded list of assembled previews. */
public final class ArsApparatusJeiReagents {
    private static volatile Map<ResourceKey<Enchantment>, List<ItemStack>>
            enchantmentInputs = new ConcurrentHashMap<>();

    private ArsApparatusJeiReagents() {
    }

    /** Registry/tag examples are only valid until the next client recipe reload. */
    public static void clear() {
        enchantmentInputs = new ConcurrentHashMap<>();
    }

    /**
     * Indexed only while JEI builds its ingredient lookup, never assembled.
     * All supported addon equipment stays discoverable through its usages even
     * though ordinary display pages assemble at most sixteen default examples.
     */
    public static List<ItemStack> indexReagents(Level level,
                                               IEnchantingRecipe recipe) {
        if (recipe instanceof EnchantmentRecipe enchantment && level != null) {
            return enchantmentInputs.computeIfAbsent(enchantment.enchantmentKey(),
                    ignored -> {
                        var holder = enchantment.holderFor(level);
                        return BuiltInRegistries.ITEM.stream()
                                .filter(item -> item == Items.BOOK
                                        || item == Items.ENCHANTED_BOOK
                                        || holder.value().canEnchant(
                                        item.getDefaultInstance()))
                                .map(Item::getDefaultInstance)
                                .map(ItemStack::copy).toList();
                    });
        }
        if (recipe instanceof ArmorUpgradeRecipe) {
            return PerkRegistry.getPerkProviderItems().stream()
                    .map(Item::getDefaultInstance).map(ItemStack::copy).toList();
        }
        if (recipe instanceof EnchantingApparatusRecipe apparatus) {
            return Arrays.stream(apparatus.reagent().getItems())
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> stack.copyWithCount(1)).toList();
        }
        return List.of();
    }

    /** Classify one real focus directly, without enumerating registry defaults. */
    public static boolean isReagentType(Level level, IEnchantingRecipe recipe,
                                        ItemStack focus) {
        if (recipe instanceof ArmorUpgradeRecipe) {
            return focus.has(DataComponentRegistry.ARMOR_PERKS.get())
                    || PerkRegistry.getPerkProviderItems().contains(focus.getItem());
        }
        if (recipe instanceof EnchantmentRecipe enchantment) {
            return focus.is(Items.BOOK) || focus.is(Items.ENCHANTED_BOOK)
                    || level != null
                    && enchantment.holderFor(level).value().canEnchant(focus);
        }
        if (recipe instanceof EnchantingApparatusRecipe apparatus) {
            return apparatus.reagent().test(focus)
                    || Arrays.stream(apparatus.reagent().getItems())
                    .anyMatch(candidate -> ItemStack.isSameItem(candidate, focus));
        }
        // An addon with no exposed reagent ingredient cannot safely declare
        // that an otherwise invalid real focus belongs only to a pedestal.
        return true;
    }
}
