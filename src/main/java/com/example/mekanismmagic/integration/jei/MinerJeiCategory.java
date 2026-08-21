package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;

public final class MinerJeiCategory extends AbstractRecipeCategory<
        OccultismRecipeBridge.MinerJeiData> {
    public MinerJeiCategory(IGuiHelper guiHelper) {
        super(MekanismMagicJeiPlugin.MINER_TYPE,
                Component.translatable("jei.mekanism_magic.miner"),
                guiHelper.createDrawableItemLike(
                        NativeMekanismRegistries.DIMENSION_MINER_BLOCK.getSecondary()),
                176, 70);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.MinerJeiData recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        JeiSlotMarker.mark(
                        builder.addInputSlot(42, 27)
                                .setStandardSlotBackground(),
                        JeiSlotMarker.Kind.INPUT, "miner_input")
                .addIngredients(recipe.input());
        JeiSlotMarker.mark(
                        builder.addOutputSlot(122, 27)
                                .setOutputSlotBackground(),
                        JeiSlotMarker.Kind.OUTPUT, "output")
                .addItemStack(recipe.output());
    }
}
