package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CatalystIdentifierJeiCategory
        extends AbstractRecipeCategory<
        ArsNouveauRecipeBridge.CatalystIdentifierJeiData> {
    public CatalystIdentifierJeiCategory(IGuiHelper guiHelper) {
        super(ArsNouveauJeiIntegration.CATALYST_IDENTIFIER_RECIPE_TYPE,
                Component.translatable(
                        "jei.mekanism_magic.catalyst_identifier"),
                guiHelper.createDrawableItemLike(
                        ArsNouveauRegistries
                                .CATALYST_IDENTIFIER_ASSEMBLER_BLOCK.asItem()),
                126, 54);
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData recipe,
            IFocusGroup focuses) {
        for (int index = 0; index < recipe.catalysts().size()
                && index < 3; index++) {
            builder.addInputSlot(10 + index * 18, 18)
                    .setStandardSlotBackground()
                    .addIngredients(recipe.catalysts().get(index));
        }
        builder.addOutputSlot(100, 18)
                .setOutputSlotBackground()
                .addItemStack(recipe.output());
    }

    @Override
    public ResourceLocation getRegistryName(
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData recipe) {
        return recipe.id();
    }
}
