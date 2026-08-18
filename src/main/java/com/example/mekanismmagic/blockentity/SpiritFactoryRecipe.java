package com.example.mekanismmagic.blockentity;

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

/**
 * Adapter from the runtime Occultism recipe bridge to Mekanism's factory
 * cached-recipe pipeline.
 */
public final class SpiritFactoryRecipe extends BasicItemStackToItemStackRecipe {
    private static final RecipeType<ItemStackToItemStackRecipe> TYPE = new RecipeType<>() {
    };
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final RecipeSerializer<SpiritFactoryRecipe> SERIALIZER =
            (RecipeSerializer) RecipeSerializer.SHAPELESS_RECIPE;

    private final ResourceLocation occultismId;
    private final int duration;
    private final int inputCount;
    private final ItemStack source;

    public SpiritFactoryRecipe(ItemStack input, ItemStack source,
                               OccultismRecipeBridge.RecipeResult result) {
        super(ItemStackIngredient.of(new SizedIngredient(
                        Ingredient.of(input.getItem()),
                        result.inputs().isEmpty() ? 1 : result.inputs().getFirst().count())),
                result.output(), TYPE);
        this.occultismId = result.id();
        this.duration = result.duration();
        this.inputCount = result.inputs().isEmpty() ? 1 : result.inputs().getFirst().count();
        this.source = source.copyWithCount(1);
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
        return ItemStack.isSameItemSameComponents(source, current);
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
