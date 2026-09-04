package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.basic.BasicItemStackToItemStackRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

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
    private final ItemStack inputTemplate;
    private final long selectionNonce;
    private final long processingRevision;
    private final boolean randomTrade;
    private final Supplier<ItemStack> currentSource;
    private final LongSupplier currentNonce;

    public SpiritFactoryRecipe(ItemStack input, ItemStack source,
                               MachineRecipeResult result,
                               long selectionNonce,
                               boolean randomTrade,
                               Supplier<ItemStack> currentSource,
                               LongSupplier currentNonce) {
        super(ItemStackIngredient.of(new SizedIngredient(
                        DataComponentIngredient.of(true,
                                input.copyWithCount(1)),
                        result.inputs().isEmpty() ? 1 : result.inputs().getFirst().count())),
                result.output(), TYPE);
        this.occultismId = result.id();
        this.duration = result.duration();
        this.inputCount = result.inputs().isEmpty() ? 1 : result.inputs().getFirst().count();
        this.source = source.copyWithCount(1);
        this.inputTemplate = input.copyWithCount(1);
        this.selectionNonce = selectionNonce;
        this.processingRevision = OccultismRecipeBridge
                .spiritProcessingRevision();
        this.randomTrade = randomTrade;
        this.currentSource = currentSource;
        this.currentNonce = currentNonce;
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

    public boolean sameInput(ItemStack current) {
        return ItemStack.isSameItemSameComponents(
                inputTemplate, current);
    }

    public boolean sameSelectionNonce(long current) {
        return !randomTrade || selectionNonce == current;
    }

    public boolean sameProcessingRevision() {
        return processingRevision == OccultismRecipeBridge
                .spiritProcessingRevision();
    }

    public boolean randomTrade() {
        return randomTrade;
    }

    @Override
    public boolean test(ItemStack input) {
        ItemStack liveSource = currentSource == null
                ? ItemStack.EMPTY : currentSource.get();
        long liveNonce = currentNonce == null
                ? selectionNonce : currentNonce.getAsLong();
        return super.test(input) && sameInput(input)
                && sameSource(liveSource)
                && sameSelectionNonce(liveNonce)
                && sameProcessingRevision();
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
