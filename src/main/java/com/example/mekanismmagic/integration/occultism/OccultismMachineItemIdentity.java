package com.example.mekanismmagic.integration.occultism;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Arrays;
import java.util.Set;

/**
 * Stable identities for machine-produced Occultism miners and their JEI examples.
 * This is not an AE key comparator or an inventory-wide NBT cleanup.
 */
public final class OccultismMachineItemIdentity {
    private static final String MINER_CLASS =
            "com.klikli_dev.occultism.common.item.spirit.MinerSpiritItem";
    private static final ResourceLocation SPIRIT_NAME =
            ResourceLocation.fromNamespaceAndPath("occultism", "spirit_name");
    // Audited in Occultism 1.222.0 and 1.224.2: their spirit name is cosmetic.
    // Do not assume this for arbitrary data-pack outputs or other components.
    private static final Set<String> STABLE_NAMED_OUTPUTS = Set.of(
            "knowledge_tablet", "ender_satchel", "infused_pickaxe", "dimensional_matrix",
            "satchel", "ritual_satchel_t1", "ritual_satchel_t2", "storage_remote", "iesnium_butcher_knife");

    private OccultismMachineItemIdentity() {
    }

    public static boolean isNativeMiner(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && MINER_CLASS.equals(stack.getItem().getClass().getName());
    }

    public static boolean isStableNamedOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("occultism") && STABLE_NAMED_OUTPUTS.contains(id.getPath());
    }

    /**
     * Always copies: recipe results and Ingredient examples can be shared by the
     * original recipe manager. Only the cosmetic spirit name is standardized.
     * Damage, enchantments, custom names and explicitly configured mining stats
     * are retained; the native getter fills only missing mining-time/roll values.
     */
    public static ItemStack normalizeMiner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = stack.copy();
        if (!isNativeMiner(result)) {
            return result;
        }
        @SuppressWarnings("unchecked")
        DataComponentType<String> name = (DataComponentType<String>)
                BuiltInRegistries.DATA_COMPONENT_TYPE.get(SPIRIT_NAME);
        if (name != null) {
            String defaultName = result.getItem().getDefaultInstance().get(name);
            // Do not remove the component: a later tooltip would generate a new name.
            result.set(name, defaultName == null || defaultName.isBlank()
                    ? "Mekanism Magic" : defaultName);
        }
        result.getMaxStackSize();
        return result;
    }

    /** JEI-only view; matching continues to use the untouched original Ingredient. */
    public static Ingredient jeiIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()
                || Arrays.stream(ingredient.getItems()).noneMatch(OccultismMachineItemIdentity::isNativeMiner)) {
            return ingredient;
        }
        return Ingredient.of(Arrays.stream(ingredient.getItems())
                .map(OccultismMachineItemIdentity::normalizeMiner));
    }

    /**
     * A bound book itself acquires a random name during ordinary crafting/load.
     * The existing Dictionary of Spirits module accepts its unbound counterpart;
     * show that deterministic automation path instead of encoding a random book.
     */
    public static Ingredient jeiUnboundActivation(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ingredient;
        }
        return Ingredient.of(Arrays.stream(ingredient.getItems()).map(stack -> {
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String prefix = "book_of_binding_bound_";
            if (id.getNamespace().equals("occultism") && id.getPath().startsWith(prefix)) {
                var unboundId = ResourceLocation.fromNamespaceAndPath("occultism",
                        "book_of_binding_" + id.getPath().substring(prefix.length()));
                var unbound = BuiltInRegistries.ITEM.getOptional(unboundId);
                if (unbound.isPresent()) {
                    return new ItemStack(unbound.get(), stack.getCount());
                }
            }
            return stack.copy();
        }));
    }
}
