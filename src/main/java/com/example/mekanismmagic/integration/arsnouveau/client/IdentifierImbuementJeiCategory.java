package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** JEI view of the recipe shape consumed by our imbuement machines. */
public final class IdentifierImbuementJeiCategory
        extends MagicJeiCategory<
        ArsNouveauRecipeBridge.IdentifierImbuementJeiData> {
    public IdentifierImbuementJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                ArsNouveauJeiIntegration
                        .IDENTIFIER_IMBUEMENT_RECIPE_TYPE,
                Component.translatable(
                        "jei.mekanism_magic.identifier_imbuement"),
                guiHelper.createDrawableItemLike(
                        ArsNouveauRegistries
                                .IMBUEMENT_PROCESSOR_BLOCK.asItem()),
                150, 72);
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            ArsNouveauRecipeBridge.IdentifierImbuementJeiData recipe,
            IFocusGroup focuses) {
        // Mark the persistent identifier as an INPUT for recipe-transfer
        // compatibility. AE2 JEI Integration's universal handler ignores the
        // CATALYST role; our decoded pattern still removes this marker from
        // planning and physical dispatch, so it remains non-consumable.
        input(builder.addInputSlot(10, 27))
                .setSlotName("catalyst_identifier")
                .addItemStack(recipe.identifier())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable(
                                "jei.mekanism_magic.identifier_imbuement"
                                        + ".identifier_hint")));
        input(builder.addInputSlot(58, 27))
                .setSlotName("input")
                .addIngredients(recipe.input());
        output(builder.addOutputSlot(122, 27))
                .setSlotName("output")
                .addItemStack(recipe.output())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable(
                                "jei.mekanism_magic.identifier_imbuement"
                                        + ".source_cost",
                                recipe.sourceCost())));
    }

    @Override
    public ResourceLocation getRegistryName(
            ArsNouveauRecipeBridge.IdentifierImbuementJeiData recipe) {
        return recipe.id();
    }
}
