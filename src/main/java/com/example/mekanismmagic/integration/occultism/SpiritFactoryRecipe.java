package com.example.mekanismmagic.integration.occultism;


import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/**
 * Adapter from the runtime Occultism recipe bridge to Mekanism's factory
 * cached-recipe pipeline.
 */
public final class SpiritFactoryRecipe extends ItemStackToItemStackRecipe {
    private static final RecipeType<ItemStackToItemStackRecipe> TYPE = new RecipeType<>() {
    };
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final RecipeSerializer<SpiritFactoryRecipe> SERIALIZER =
            (RecipeSerializer) RecipeSerializer.SHAPELESS_RECIPE;

    private final ResourceLocation occultismId;
    private final int duration;
    private final int inputCount;
    private final ItemStack source;
    private final ItemStack output;

    public SpiritFactoryRecipe(ItemStack input, ItemStack source,
                               OccultismRecipeBridge.RecipeResult result) {
        super(result.id(), IngredientCreatorAccess.item().from(
                        Ingredient.of(input.getItem()),
                        result.inputs().isEmpty() ? 1 : result.inputs().get(0).count()),
                result.output());
        this.occultismId = result.id();
        this.duration = result.duration();
        this.inputCount = result.inputs().isEmpty() ? 1 : result.inputs().get(0).count();
        this.source = source.copy();
        this.source.setCount(1);
        this.output = result.output().copy();
    }

    public ResourceLocation occultismId() {
        return occultismId;
    }

    public int duration() {
        return duration;
    }

    public int inputCount() {
        return inputCount;
    }

    public boolean sameSource(ItemStack current) {
        return ItemStack.isSameItemSameTags(source, current);
    }

    @Override
    public List<ItemStack> getOutputDefinition() {
        return List.of(output.copy());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ResourceLocation getId() {
        return occultismId;
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
        return output.copy();
    }
}

