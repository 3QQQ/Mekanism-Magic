package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.SpiritRecipeGroup;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class SpiritJeiCategory extends MagicJeiCategory<
        OccultismRecipeBridge.SpiritJeiData> {
    private final SpiritRecipeGroup group;

    public SpiritJeiCategory(IGuiHelper guiHelper) {
        this(guiHelper, SpiritRecipeGroup.SPIRIT_FIRE);
    }

    public SpiritJeiCategory(IGuiHelper guiHelper, SpiritRecipeGroup group) {
        super(guiHelper, SpiritJeiRecipeTypes.type(group),
                Component.translatable(group.titleKey()),
                guiHelper.createDrawableItemLike(BuiltInRegistries.ITEM.get(group.iconId())),
                176, 70);
        this.group = group;
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

    @Override
    public boolean isHandled(OccultismRecipeBridge.SpiritJeiData recipe) {
        return SpiritRecipeGroup.classify(recipe) == group;
    }

    @Override
    public ResourceLocation getRegistryName(OccultismRecipeBridge.SpiritJeiData recipe) {
        return SpiritRecipeGroup.displayId(recipe);
    }
}
