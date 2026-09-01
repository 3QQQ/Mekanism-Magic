package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.network.chat.Component;

public final class MinerJeiCategory extends MagicJeiCategory<
        OccultismRecipeBridge.MinerJeiData> {
    public MinerJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper, MekanismMagicJeiPlugin.MINER_TYPE,
                Component.translatable("jei.mekanism_magic.miner"),
                guiHelper.createDrawableItemLike(
                        NativeMekanismRegistries.DIMENSION_MINER_BLOCK.asItem()),
                176, 70);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.MinerJeiData recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        JeiSlotMarker.mark(
                        input(builder.addInputSlot(42, 27)),
                        JeiSlotMarker.Kind.INPUT, "miner_input")
                .addIngredients(recipe.input());
        JeiSlotMarker.mark(
                        output(builder.addOutputSlot(122, 27)),
                        JeiSlotMarker.Kind.OUTPUT, "output")
                .addItemStack(recipe.output());
    }
}
