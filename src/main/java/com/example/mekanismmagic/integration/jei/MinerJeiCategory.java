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
                        NativeMekanismRegistries.DIMENSION_MINER_BLOCK.asItem()),
                176, 70);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.MinerJeiData recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        builder.addInputSlot(42, 27)
                .setStandardSlotBackground()
                .addIngredients(recipe.input());
        builder.addOutputSlot(122, 27)
                .setOutputSlotBackground()
                .addItemStack(recipe.output());
    }
}
