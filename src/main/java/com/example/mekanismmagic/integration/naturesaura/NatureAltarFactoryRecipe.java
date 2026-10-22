package com.example.mekanismmagic.integration.naturesaura;

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
import java.util.Objects;

/**
 * Immutable adapter from one Nature's Aura altar recipe snapshot to
 * Mekanism's item-to-item factory pipeline.
 *
 * <p>The original {@link Ingredient} is retained instead of narrowing it to
 * the item currently present in a process slot. This keeps tags and custom
 * ingredient semantics intact across factory sorting and cache rebuilds.</p>
 */
public final class NatureAltarFactoryRecipe
        extends BasicItemStackToItemStackRecipe {
    private static final RecipeType<ItemStackToItemStackRecipe> TYPE =
            new RecipeType<>() {
            };
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final RecipeSerializer<NatureAltarFactoryRecipe>
            SERIALIZER = (RecipeSerializer) RecipeSerializer.SHAPELESS_RECIPE;

    private final ResourceLocation altarId;
    private final Ingredient altarInput;
    private final Ingredient catalyst;
    private final int auraCost;
    private final int duration;
    private final long catalogVersion;
    private final String recipeSignature;

    public NatureAltarFactoryRecipe(
            NatureAuraRecipeBridge.AltarRecipeView recipe,
            long catalogVersion) {
        super(ItemStackIngredient.of(new SizedIngredient(
                        Objects.requireNonNull(recipe, "recipe").input(), 1)),
                recipe.output(), TYPE);
        this.altarId = recipe.id();
        this.altarInput = recipe.input();
        this.catalyst = recipe.catalyst();
        this.auraCost = recipe.auraCost();
        this.duration = recipe.duration();
        this.catalogVersion = catalogVersion;
        this.recipeSignature = NatureAuraRecipeBridge
                .recipeSignature(recipe);
    }

    public ResourceLocation altarId() {
        return altarId;
    }

    public ResourceLocation altarRecipeId() {
        return altarId;
    }

    public Ingredient altarInput() {
        return altarInput;
    }

    public Ingredient catalyst() {
        return catalyst;
    }

    public boolean requiresCatalyst() {
        return !catalyst.isEmpty();
    }

    public boolean catalystMatches(ItemStack stack) {
        return !stack.isEmpty() && catalyst.test(stack);
    }

    public int auraCost() {
        return auraCost;
    }

    public int duration() {
        return duration;
    }

    public long catalogVersion() {
        return catalogVersion;
    }

    public String recipeSignature() {
        return recipeSignature;
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
