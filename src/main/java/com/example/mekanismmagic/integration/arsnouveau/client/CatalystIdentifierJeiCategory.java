package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CatalystIdentifierJeiCategory
        extends MagicJeiCategory<
        ArsNouveauRecipeBridge.CatalystIdentifierJeiData> {
    public CatalystIdentifierJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                ArsNouveauJeiIntegration.CATALYST_IDENTIFIER_RECIPE_TYPE,
                Component.translatable(
                        "jei.mekanism_magic.catalyst_identifier"),
                guiHelper.createDrawableItemLike(
                        ArsNouveauRegistries
                                .CATALYST_IDENTIFIER_ASSEMBLER_BLOCK.asItem()),
                126, 72);
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData recipe,
            IFocusGroup focuses) {
        int catalystCount = Math.min(9, recipe.catalysts().size());
        int rowCount = Math.ceilDiv(catalystCount, 3);
        int top = (72 - rowCount * 18) / 2;
        for (int index = 0; index < catalystCount; index++) {
            int row = index / 3;
            int column = index % 3;
            int rowSize = Math.min(3, catalystCount - row * 3);
            int left = 10 + (3 - rowSize) * 9;
            input(builder.addInputSlot(
                    left + column * 18, top + row * 18))
                    .addIngredients(recipe.catalysts().get(index));
        }
        output(builder.addOutputSlot(100, 27))
                .addItemStack(recipe.output());
    }

    @Override
    public ResourceLocation getRegistryName(
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData recipe) {
        return recipe.id();
    }
}
