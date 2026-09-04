package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;

public final class SpiritJeiCategory extends MagicJeiCategory<
        OccultismRecipeBridge.SpiritJeiData> {
    public SpiritJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper, MekanismMagicJeiPlugin.SPIRIT_TYPE,
                Component.translatable("jei.mekanism_magic.spirit"),
                guiHelper.createDrawableItemLike(NativeMekanismRegistries.SPIRIT_BLOCK.asItem()),
                176, 70);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.SpiritJeiData recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        JeiSlotMarker.mark(
                        input(builder.addSlot(
                                RecipeIngredientRole.CATALYST, 4, 27)),
                        JeiSlotMarker.Kind.SPIRIT, "spirit")
                .addItemStack(recipe.spirit());
        JeiSlotMarker.mark(
                        input(builder.addInputSlot(48, 27)),
                        JeiSlotMarker.Kind.INPUT, "input")
                .addIngredients(recipe.input());
        var output = JeiSlotMarker.mark(
                        output(builder.addOutputSlot(148, 27)),
                        JeiSlotMarker.Kind.OUTPUT, "output");
        output.addItemStack(recipe.output());
        if (recipe.weight() > 0) {
            output.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                    Component.translatable(
                            "jei.mekanism_magic.trade_weight",
                            recipe.weight())));
            output.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                    Component.translatable(
                            "jei.mekanism_magic.random_trade_no_pattern")));
        }
    }
}
