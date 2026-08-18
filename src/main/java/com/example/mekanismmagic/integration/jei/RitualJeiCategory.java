package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.blockentity.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;

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
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        builder.addInputSlot(4, 46)
                .setStandardSlotBackground()
                .addItemStack(recipe.selector());
        for (int index = 0; index < Math.min(16, recipe.ingredients().size()); index++) {
            int row = index / 8;
            int column = index % 8;
            var ingredient = recipe.ingredients().get(index);
            if (!ingredient.isEmpty() && ingredient.getItems().length > 0) {
                builder.addInputSlot(4 + column * 18, 4 + row * 18)
                        .setStandardSlotBackground()
                        .addIngredients(ingredient);
            }
        }
        if (recipe.activation() != null
                && !recipe.activation().isEmpty()
                && recipe.activation().getItems().length > 0) {
            builder.addInputSlot(22, 46)
                    .setStandardSlotBackground()
                    .addIngredients(recipe.activation());
        }
        for (int index = 0; index < Math.min(4, recipe.sacrifices().size()); index++) {
            builder.addInputSlot(58 + index * 18, 46)
                    .setStandardSlotBackground()
                    .addItemStack(recipe.sacrifices().get(index));
        }
        builder.addOutputSlot(148, 22)
                .setOutputSlotBackground()
                .addItemStack(recipe.output());
    }
}
