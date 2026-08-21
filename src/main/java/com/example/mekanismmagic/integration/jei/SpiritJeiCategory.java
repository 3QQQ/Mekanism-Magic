package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
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
                guiHelper.createDrawableItemLike(NativeMekanismRegistries.SPIRIT_BLOCK.asItem()),
                176, 70);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.SpiritJeiData recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        JeiSlotMarker.mark(
                        builder.addSlot(RecipeIngredientRole.CATALYST, 4, 27)
                                .setStandardSlotBackground(),
                        JeiSlotMarker.Kind.SPIRIT, "spirit")
                .addItemStack(recipe.spirit());
        JeiSlotMarker.mark(
                        builder.addInputSlot(48, 27)
                                .setStandardSlotBackground(),
                        JeiSlotMarker.Kind.INPUT, "input")
                .addItemStack(recipe.input());
        JeiSlotMarker.mark(
                        builder.addOutputSlot(148, 27)
                                .setOutputSlotBackground(),
                        JeiSlotMarker.Kind.OUTPUT, "output")
                .addItemStack(recipe.output());
    }
}
