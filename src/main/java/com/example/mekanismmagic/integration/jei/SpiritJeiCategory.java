package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.blockentity.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;

public final class SpiritJeiCategory extends AbstractRecipeCategory<
        OccultismRecipeBridge.SpiritJeiData> {
    public SpiritJeiCategory(IGuiHelper guiHelper) {
        super(MekanismMagicJeiPlugin.SPIRIT_TYPE,
                Component.translatable("jei.mekanism_magic.spirit"),
                guiHelper.createDrawableItemLike(NativeMekanismRegistries.SPIRIT_BLOCK.getSecondary()),
                176, 70);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.SpiritJeiData recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 4, 27)
                .setStandardSlotBackground()
                .addItemStack(recipe.spirit());
        builder.addInputSlot(48, 27)
                .setStandardSlotBackground()
                .addItemStack(recipe.input());
        builder.addOutputSlot(148, 27)
                .setOutputSlotBackground()
                .addItemStack(recipe.output());
    }
}
