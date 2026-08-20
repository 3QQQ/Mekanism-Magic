package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Direct recipe bridge for Ars Nouveau's physical crafting machines.
 */
public final class ArsNouveauRecipeBridge {
    private ArsNouveauRecipeBridge() {
    }

    public static Optional<MachineRecipeResult> findImbuementRecipe(
            Level level, ItemStackHandler inventory, int inputSlot,
            int pedestalStart, int pedestalCount) {
        if (level == null) {
            return Optional.empty();
        }
        ItemStack reagent = inventory.getStackInSlot(inputSlot);
        if (reagent.isEmpty()) {
            return Optional.empty();
        }
        for (RecipeHolder<ImbuementRecipe> holder :
                level.getRecipeManager().getAllRecipesFor(
                        RecipeRegistry.IMBUEMENT_TYPE.get())) {
            ImbuementRecipe recipe = holder.value();
            if (!recipe.getInput().test(reagent)) {
                continue;
            }
            Optional<List<InputUse>> pedestalUses = matchIngredients(
                    recipe.getPedestalItems(), inventory,
                    pedestalStart, pedestalCount);
            if (pedestalUses.isEmpty()) {
                continue;
            }
            List<InputUse> inputs = new ArrayList<>();
            inputs.add(new InputUse(inputSlot, 1));
            inputs.addAll(pedestalUses.get());
            ItemStack output =
                    recipe.getResultItem(level.registryAccess()).copy();
            if (output.isEmpty()) {
                continue;
            }
            return Optional.of(new MachineRecipeResult(
                    holder.id(), output,
                    ArsNouveauMachineConfig.IMBUEMENT_DURATION,
                    inputs, -1, -1, null, null,
                    Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                            Math.max(0, recipe.getSource()))));
        }
        return Optional.empty();
    }

    public static Optional<MachineRecipeResult> findApparatusRecipe(
            Level level, ItemStackHandler inventory, int reagentSlot,
            int pedestalStart, int pedestalCount) {
        if (level == null) {
            return Optional.empty();
        }
        ItemStack reagent = inventory.getStackInSlot(reagentSlot);
        if (reagent.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> pedestalStacks = new ArrayList<>();
        for (int index = 0; index < pedestalCount; index++) {
            ItemStack stack = inventory.getStackInSlot(pedestalStart + index);
            if (!stack.isEmpty()) {
                pedestalStacks.add(stack.copyWithCount(1));
            }
        }
        ApparatusRecipeInput recipeInput =
                new ApparatusRecipeInput(reagent.copyWithCount(1),
                        pedestalStacks, null);
        for (RecipeHolder<? extends IEnchantingRecipe> holder :
                ArsNouveauAPI.getInstance()
                        .getEnchantingApparatusRecipes(level)) {
            IEnchantingRecipe recipe = holder.value();
            if (!recipe.matches(recipeInput, level)) {
                continue;
            }
            List<Ingredient> pedestalIngredients =
                    recipe instanceof EnchantingApparatusRecipe apparatus
                            ? apparatus.pedestalItems()
                            : List.copyOf(recipe.getIngredients());
            Optional<List<InputUse>> pedestalUses = matchIngredients(
                    pedestalIngredients, inventory,
                    pedestalStart, pedestalCount);
            if (pedestalUses.isEmpty()) {
                continue;
            }
            ItemStack output =
                    recipe.assemble(recipeInput, level.registryAccess()).copy();
            if (output.isEmpty()) {
                continue;
            }
            List<InputUse> inputs = new ArrayList<>();
            inputs.add(new InputUse(reagentSlot, 1));
            inputs.addAll(pedestalUses.get());
            return Optional.of(new MachineRecipeResult(
                    holder.id(), output,
                    ArsNouveauMachineConfig.APPARATUS_DURATION,
                    inputs, -1, -1, null, null,
                    Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                            Math.max(0, recipe.sourceCost()))));
        }
        return Optional.empty();
    }

    private static Optional<List<InputUse>> matchIngredients(
            List<Ingredient> required, ItemStackHandler inventory,
            int startSlot, int slotCount) {
        int[] remaining = new int[slotCount];
        int[] used = new int[slotCount];
        for (int index = 0; index < slotCount; index++) {
            remaining[index] =
                    inventory.getStackInSlot(startSlot + index).getCount();
        }
        if (!matchIngredients(required, 0, inventory, startSlot,
                remaining, used)) {
            return Optional.empty();
        }
        List<InputUse> result = new ArrayList<>();
        for (int index = 0; index < used.length; index++) {
            if (used[index] > 0) {
                result.add(new InputUse(startSlot + index, used[index]));
            }
        }
        return Optional.of(result);
    }

    private static boolean matchIngredients(
            List<Ingredient> required, int ingredientIndex,
            ItemStackHandler inventory, int startSlot,
            int[] remaining, int[] used) {
        if (ingredientIndex >= required.size()) {
            return true;
        }
        Ingredient ingredient = required.get(ingredientIndex);
        for (int index = 0; index < remaining.length; index++) {
            if (remaining[index] <= 0
                    || !ingredient.test(
                    inventory.getStackInSlot(startSlot + index))) {
                continue;
            }
            remaining[index]--;
            used[index]++;
            if (matchIngredients(required, ingredientIndex + 1,
                    inventory, startSlot, remaining, used)) {
                return true;
            }
            remaining[index]++;
            used[index]--;
        }
        return false;
    }
}
