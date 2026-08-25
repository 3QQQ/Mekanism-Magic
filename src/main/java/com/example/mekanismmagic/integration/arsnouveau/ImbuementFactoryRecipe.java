package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.basic.BasicItemStackToItemStackRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;

public final class ImbuementFactoryRecipe
        extends BasicItemStackToItemStackRecipe {
    private static final RecipeType<ItemStackToItemStackRecipe> TYPE =
            new RecipeType<>() {
            };
    private static final RecipeSerializer<ImbuementFactoryRecipe> SERIALIZER =
            (RecipeSerializer<ImbuementFactoryRecipe>)
                    (RecipeSerializer<?>) RecipeSerializer.SHAPELESS_RECIPE;
    private final ResourceLocation id;
    private final ResourceLocation catalystId;
    private final int duration;
    private final int sourceCost;

    public ImbuementFactoryRecipe(ItemStack input, ItemStack identifier,
                                  com.example.mekanismmagic.integration.common.recipe
                                          .MachineRecipeResult result) {
        super(ItemStackIngredient.of(new SizedIngredient(
                        Ingredient.of(input.getItem()), 1)),
                result.output(), TYPE);
        this.id = result.id();
        this.catalystId = CatalystIdentifierItem.catalystId(identifier);
        this.duration = result.duration();
        this.sourceCost = result.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
    }

    public ResourceLocation imbuementId() {
        return id;
    }

    public ResourceLocation catalystId() {
        return catalystId;
    }

    public int duration() {
        return duration;
    }

    public int sourceCost() {
        return sourceCost;
    }

    public boolean sameIdentifier(ItemStack stack) {
        return CatalystIdentifierItem.catalystId(stack)
                .equals(catalystId);
    }

    @Override
    public List<ItemStack> getOutputDefinition() {
        return List.of(getOutputRaw().copy());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
