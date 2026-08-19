package com.example.mekanismmagic.recipe;

import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Strict crafting-table recipe for the four highest-tier Marid pentacles.
 */
public final class UltimateMiniRitualRecipe extends CustomRecipe {
    public static final RecipeSerializer<UltimateMiniRitualRecipe> SERIALIZER =
            new SimpleCraftingRecipeSerializer<>(UltimateMiniRitualRecipe::new);

    private static final String[] REQUIRED_PENTACLES = {
            "occultism:craft_marid",
            "occultism:possess_marid",
            "occultism:summon_marid",
            "occultism:summon_unbound_marid"
    };

    public UltimateMiniRitualRecipe(ResourceLocation id,
                                    CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        if (input.getContainerSize() < 9) {
            return false;
        }
        return isPentacle(input.getItem(0), REQUIRED_PENTACLES[0])
                && isItem(input.getItem(1), "mekanism:alloy_atomic")
                && isPentacle(input.getItem(2), REQUIRED_PENTACLES[1])
                && isItem(input.getItem(3),
                "occultism:spirit_attuned_crystal")
                && isItem(input.getItem(4), "minecraft:nether_star")
                && isItem(input.getItem(5),
                "occultism:spirit_attuned_crystal")
                && isPentacle(input.getItem(6), REQUIRED_PENTACLES[2])
                && isItem(input.getItem(7),
                "mekanism:ultimate_control_circuit")
                && isPentacle(input.getItem(8), REQUIRED_PENTACLES[3]);
    }

    @Override
    public ItemStack assemble(CraftingContainer input,
                              RegistryAccess registries) {
        return new ItemStack(MekanismMagic.ULTIMATE_MINI_RITUAL.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return new ItemStack(MekanismMagic.ULTIMATE_MINI_RITUAL.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    private static boolean isPentacle(ItemStack stack, String required) {
        if (stack.isEmpty()
                || stack.getItem() != MekanismMagic.MINI_RITUAL.get()) {
            return false;
        }
        net.minecraft.nbt.CompoundTag data = stack.getTag();
        return data != null && required.equals(data.getString("pentacle"));
    }

    private static boolean isItem(ItemStack stack, String id) {
        return !stack.isEmpty()
                && id.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString());
    }
}
