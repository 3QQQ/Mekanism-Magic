package com.example.mekanismmagic.integration.ae2;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.client.ArsNouveauJeiIntegration;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Writes identifier-aware imbuement recipes into AE2 processing patterns. */
public final class Ae2IdentifierImbuementJeiTransfer implements
        IRecipeTransferHandler<PatternEncodingTermMenu,
                ArsNouveauRecipeBridge.IdentifierImbuementJeiData> {
    private final IRecipeTransferHandlerHelper helper;

    private Ae2IdentifierImbuementJeiTransfer(
            IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    public static void register(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new Ae2IdentifierImbuementJeiTransfer(
                        registration.getTransferHelper()),
                ArsNouveauJeiIntegration
                        .IDENTIFIER_IMBUEMENT_RECIPE_TYPE);
    }

    @Override
    public Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        // Match subclasses used by AE2 wireless/extended terminals as well as
        // the base terminal. Returning the base TYPE restricted the handler
        // to only the wired vanilla menu even when the runtime menu inherited
        // PatternEncodingTermMenu.
        return Optional.empty();
    }

    @Override
    public RecipeType<ArsNouveauRecipeBridge.IdentifierImbuementJeiData>
    getRecipeType() {
        return ArsNouveauJeiIntegration
                .IDENTIFIER_IMBUEMENT_RECIPE_TYPE;
    }

    @Override
    public IRecipeTransferError transferRecipe(
            PatternEncodingTermMenu menu,
            ArsNouveauRecipeBridge.IdentifierImbuementJeiData recipe,
            IRecipeSlotsView recipeSlots, Player player,
            boolean maxTransfer, boolean doTransfer) {
        List<GenericStack> reagentChoices = Arrays.stream(
                        recipe.input().getItems())
                .map(stack -> stack.copyWithCount(1))
                .map(GenericStack::fromItemStack)
                .filter(Objects::nonNull)
                .toList();
        GenericStack identifier = GenericStack.fromItemStack(
                recipe.identifier().copyWithCount(1));
        GenericStack output = GenericStack.fromItemStack(
                recipe.output().copy());
        if (reagentChoices.isEmpty()
                || identifier == null || output == null) {
            return helper.createInternalError();
        }
        if (doTransfer) {
            EncodingHelper.encodeProcessingRecipe(menu,
                    List.of(reagentChoices, List.of(identifier)),
                    List.of(output));
        }
        return null;
    }
}
