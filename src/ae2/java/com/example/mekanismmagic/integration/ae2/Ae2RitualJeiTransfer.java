package com.example.mekanismmagic.integration.ae2;

import com.example.mekanismmagic.integration.jei.MekanismMagicJeiPlugin;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class Ae2RitualJeiTransfer extends Ae2ExactMachineJeiTransfer<OccultismRecipeBridge.RitualJeiData> {
    private Ae2RitualJeiTransfer(IRecipeTransferHandlerHelper helper) { super(helper); }

    public static void register(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new Ae2RitualJeiTransfer(registration.getTransferHelper()),
                MekanismMagicJeiPlugin.RITUAL_TYPE);
    }

    @Override public RecipeType<OccultismRecipeBridge.RitualJeiData> getRecipeType() {
        return MekanismMagicJeiPlugin.RITUAL_TYPE;
    }

    @Override protected List<ItemStack> displayedInputs(IRecipeSlotsView slots) {
        return slots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(slot -> slot.getSlotName().map(name -> name.startsWith("input_")
                        || name.equals("activation") || name.startsWith("sacrifice_")).orElse(false))
                .map(slot -> slot.getDisplayedItemStack().map(ItemStack::copy).orElse(ItemStack.EMPTY)).toList();
    }

    @Override protected Optional<ItemStack> resolve(Player player, OccultismRecipeBridge.RitualJeiData recipe,
                                                  List<ItemStack> inputs) {
        return OccultismRecipeBridge.resolveRitualJeiOutput(player.level(), recipe, inputs);
    }
}
