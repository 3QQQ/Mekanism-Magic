package com.example.mekanismmagic.integration.ae2;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.example.mekanismmagic.integration.naturesaura
        .NatureAuraRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.client
        .NaturesAuraJeiIntegration;
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

/** Writes signed, non-consumable altar context into AE processing patterns. */
public final class Ae2NatureAltarJeiTransfer implements
        IRecipeTransferHandler<PatternEncodingTermMenu,
                NatureAuraRecipeBridge.AltarRecipeView> {
    private final IRecipeTransferHandlerHelper helper;

    private Ae2NatureAltarJeiTransfer(
            IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    public static void register(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new Ae2NatureAltarJeiTransfer(
                        registration.getTransferHelper()),
                NaturesAuraJeiIntegration.NATURE_ALTAR_RECIPE_TYPE);
    }

    @Override
    public Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        // Wireless and extended terminals subclass the same menu.
        return Optional.empty();
    }

    @Override
    public RecipeType<NatureAuraRecipeBridge.AltarRecipeView>
    getRecipeType() {
        return NaturesAuraJeiIntegration.NATURE_ALTAR_RECIPE_TYPE;
    }

    @Override
    public IRecipeTransferError transferRecipe(
            PatternEncodingTermMenu menu,
            NatureAuraRecipeBridge.AltarRecipeView recipe,
            IRecipeSlotsView recipeSlots, Player player,
            boolean maxTransfer, boolean doTransfer) {
        List<GenericStack> inputChoices = Arrays.stream(
                        recipe.input().getItems())
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .map(GenericStack::fromItemStack)
                .filter(Objects::nonNull)
                .toList();
        GenericStack context = GenericStack.fromItemStack(
                NatureAuraRecipeBridge.createPatternContext(
                        player.level(), recipe.id()));
        GenericStack output = GenericStack.fromItemStack(recipe.output());
        if (inputChoices.isEmpty() || context == null || output == null) {
            return helper.createInternalError();
        }
        if (doTransfer) {
            EncodingHelper.encodeProcessingRecipe(menu,
                    List.of(inputChoices, List.of(context)),
                    List.of(output));
        }
        return null;
    }
}
