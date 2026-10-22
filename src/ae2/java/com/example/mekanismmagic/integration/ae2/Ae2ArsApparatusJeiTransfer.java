package com.example.mekanismmagic.integration.ae2;

import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusJeiData;
import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusRecipeViews;
import com.example.mekanismmagic.integration.arsnouveau.client.ArsNouveauJeiIntegration;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class Ae2ArsApparatusJeiTransfer extends Ae2ExactMachineJeiTransfer<ArsApparatusJeiData> {
    private Ae2ArsApparatusJeiTransfer(IRecipeTransferHandlerHelper helper) { super(helper); }

    public static void register(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new Ae2ArsApparatusJeiTransfer(registration.getTransferHelper()),
                ArsNouveauJeiIntegration.APPARATUS_RECIPE_TYPE);
    }

    @Override public RecipeType<ArsApparatusJeiData> getRecipeType() {
        return ArsNouveauJeiIntegration.APPARATUS_RECIPE_TYPE;
    }

    @Override protected List<ItemStack> displayedInputs(IRecipeSlotsView slots) {
        List<ItemStack> inputs = new ArrayList<>();
        inputs.add(slots.findSlotByName("reagent")
                .filter(slot -> slot.getRole() == RecipeIngredientRole.INPUT)
                .flatMap(slot -> slot.getDisplayedItemStack()).map(ItemStack::copy).orElse(ItemStack.EMPTY));
        slots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(slot -> slot.getSlotName().map(name -> name.startsWith("pedestal_")).orElse(false))
                .sorted(Comparator.comparingInt(slot -> Integer.parseInt(
                        slot.getSlotName().orElseThrow().substring("pedestal_".length()))))
                .forEach(slot -> inputs.add(slot.getDisplayedItemStack().map(ItemStack::copy).orElse(ItemStack.EMPTY)));
        return List.copyOf(inputs);
    }

    @Override protected Optional<ItemStack> resolve(Player player, ArsApparatusJeiData recipe,
                                                  List<ItemStack> inputs) {
        return ArsApparatusRecipeViews.isCurrent(player.level(), recipe)
                ? ArsApparatusRecipeViews.resolveExact(player.level(), recipe, inputs) : Optional.empty();
    }
}
