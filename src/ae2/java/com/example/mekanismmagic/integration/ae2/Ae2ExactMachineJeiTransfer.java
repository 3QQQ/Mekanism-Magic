package com.example.mekanismmagic.integration.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Encodes only a visible, revalidated exact result. Reference icons are never outputs. */
abstract class Ae2ExactMachineJeiTransfer<R> implements IRecipeTransferHandler<PatternEncodingTermMenu, R> {
    private final IRecipeTransferHandlerHelper helper;

    Ae2ExactMachineJeiTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override public final Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override public final Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        return Optional.empty();
    }

    protected abstract List<ItemStack> displayedInputs(IRecipeSlotsView slots);
    protected abstract Optional<ItemStack> resolve(Player player, R recipe, List<ItemStack> inputs);

    @Override
    public final IRecipeTransferError transferRecipe(PatternEncodingTermMenu menu, R recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        List<ItemStack> inputs = displayedInputs(recipeSlots);
        var outputView = recipeSlots.findSlotByName("output")
                .filter(slot -> slot.getRole() == mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT);
        Optional<ItemStack> displayed = outputView.flatMap(slot -> slot.getDisplayedItemStack());
        if (inputs.isEmpty() || inputs.stream().anyMatch(ItemStack::isEmpty)
                || displayed.isEmpty() || displayed.get().isEmpty()) {
            return error("jei.mekanism_magic.dynamic_recipe.exact_input_required");
        }
        Optional<ItemStack> resolved;
        try {
            resolved = resolve(player, recipe, inputs);
        } catch (RuntimeException invalidRecipe) {
            return error("jei.mekanism_magic.dynamic_recipe.stale_output");
        }
        if (resolved.isEmpty() || resolved.get().isEmpty()
                || !ItemStack.matches(resolved.get(), displayed.get())) {
            return error("jei.mekanism_magic.dynamic_recipe.stale_output");
        }
        Map<AEItemKey, Long> grouped = new LinkedHashMap<>();
        for (ItemStack stack : inputs) {
            AEItemKey key = AEItemKey.of(stack);
            if (key == null || stack.getCount() <= 0) {
                return helper.createInternalError();
            }
            grouped.merge(key, (long) stack.getCount(), Math::addExact);
        }
        if (grouped.size() > menu.getProcessingInputSlots().length
                || menu.getProcessingOutputSlots().length < 1) {
            return error("jei.mekanism_magic.dynamic_recipe.terminal_too_small");
        }
        GenericStack output = GenericStack.fromItemStack(resolved.get());
        if (output == null) {
            return helper.createInternalError();
        }
        if (doTransfer) {
            // A singleton choice prevents EncodingHelper from substituting a
            // same-item/different-component ingredient behind this result.
            EncodingHelper.encodeProcessingRecipe(menu, grouped.entrySet().stream()
                    .map(entry -> List.of(new GenericStack(entry.getKey(), entry.getValue())))
                    .toList(), List.of(output));
        }
        return null;
    }

    private IRecipeTransferError error(String key) {
        return helper.createUserErrorWithTooltip(Component.translatable(key));
    }
}
