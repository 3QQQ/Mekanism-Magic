package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class RitualJeiCategory extends AbstractRecipeCategory<
        OccultismRecipeBridge.RitualJeiData> {
    public RitualJeiCategory(IGuiHelper guiHelper) {
        super(MekanismMagicJeiPlugin.RITUAL_TYPE,
                Component.translatable("jei.mekanism_magic.ritual"),
                guiHelper.createDrawableItemLike(NativeMekanismRegistries.RITUAL_BLOCK.getSecondary()),
                176, 110);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.RitualJeiData recipe,
                          IFocusGroup focuses) {
        JeiSlotMarker.mark(
                        builder.addSlot(RecipeIngredientRole.CATALYST, 4, 46)
                                .setStandardSlotBackground(),
                        JeiSlotMarker.Kind.RITUAL_SELECTOR,
                        "ritual_selector")
                .addItemStack(recipe.selector());
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemStack(
                        OccultismRecipeBridge.createPentacleMiniRitual(
                                recipe.pentacleId()));
        for (int index = 0; index < Math.min(16, recipe.ingredients().size()); index++) {
            int row = index / 8;
            int column = index % 8;
            var ingredient = recipe.ingredients().get(index);
            if (!ingredient.isEmpty() && ingredient.getItems().length > 0) {
                JeiSlotMarker.mark(
                                builder.addInputSlot(4 + column * 18,
                                                4 + row * 18)
                                        .setStandardSlotBackground(),
                                JeiSlotMarker.Kind.INPUT, "input_" + index)
                        .addIngredients(ingredient);
            }
        }
        if (recipe.activation() != null
                && !recipe.activation().isEmpty()
                && recipe.activation().getItems().length > 0) {
            JeiSlotMarker.mark(
                            builder.addInputSlot(22, 46)
                                    .setStandardSlotBackground(),
                            JeiSlotMarker.Kind.ACTIVATION, "activation")
                    .addIngredients(recipe.activation());
        }
        for (int index = 0; index < Math.min(4, recipe.sacrifices().size()); index++) {
            JeiSlotMarker.mark(
                            builder.addInputSlot(58 + index * 18, 46)
                                    .setStandardSlotBackground(),
                            JeiSlotMarker.Kind.SACRIFICE,
                            "sacrifice_" + index)
                    .addItemStack(recipe.sacrifices().get(index));
        }
        JeiSlotMarker.mark(
                        builder.addOutputSlot(148, 22)
                                .setOutputSlotBackground(),
                        JeiSlotMarker.Kind.OUTPUT, "output")
                .addItemStack(recipe.output());
    }

    @Override
    public ResourceLocation getRegistryName(
            OccultismRecipeBridge.RitualJeiData recipe) {
        return recipe.recipeId();
    }
}
