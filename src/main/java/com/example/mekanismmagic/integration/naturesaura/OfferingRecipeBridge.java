package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import de.ellpeck.naturesaura.Helper;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import de.ellpeck.naturesaura.recipes.OfferingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Nature's Aura 41.9 offering boundary. These recipes have no Aura/time/dimension fields. */
public final class OfferingRecipeBridge {
    private OfferingRecipeBridge() {
    }

    static List<OfferingRecipeView> readRecipes(RecipeManager manager) {
        if (manager == null) {
            return List.of();
        }
        List<RecipeHolder<OfferingRecipe>> recipes = new ArrayList<>(
                manager.getAllRecipesFor(ModRecipes.OFFERING_TYPE));
        recipes.sort(Comparator.comparing(holder -> holder.id().toString()));
        List<OfferingRecipeView> views = new ArrayList<>(recipes.size());
        for (RecipeHolder<OfferingRecipe> holder : recipes) {
            OfferingRecipe recipe = holder.value();
            if (recipe.input == null || recipe.input.isEmpty()
                    || recipe.startItem == null || recipe.startItem.isEmpty()
                    || recipe.output == null || recipe.output.isEmpty()) {
                continue;
            }
            int amount = Helper.getIngredientAmount(recipe.input);
            if (amount > 0) {
                views.add(new OfferingRecipeView(holder.id(), recipe.input,
                        amount, recipe.startItem, recipe.output));
            }
        }
        return List.copyOf(views);
    }

    public static Optional<OfferingRecipeView> findRecipe(
            Level level, ItemStack input, ItemStack startItem) {
        if (level == null || input == null || input.isEmpty()
                || startItem == null || startItem.isEmpty()) {
            return Optional.empty();
        }
        // The original table chooses by the offered input first, then
        // validates the thrown start item against that selected recipe.
        return NatureAuraRecipeScanner.scanOfferings(level.getRecipeManager())
                .stream().filter(recipe -> recipe.input().test(input))
                .findFirst().filter(recipe -> recipe.matches(input, startItem));
    }

    static boolean isInput(Level level, ItemStack stack) {
        return stack != null && !stack.isEmpty() && (level == null
                || NatureAuraRecipeScanner.scanOfferings(level.getRecipeManager())
                .stream().anyMatch(recipe -> recipe.input().test(stack)));
    }

    static boolean isStartItem(Level level, ItemStack stack) {
        return stack != null && !stack.isEmpty() && (level == null
                || NatureAuraRecipeScanner.scanOfferings(level.getRecipeManager())
                .stream().anyMatch(recipe -> recipe.startItem().test(stack)));
    }

    /**
     * A machine cycle describes one full trigger, not one input item. The
     * activation item is consumed exactly once even for a 64-item offering.
     * Twenty ticks is the original trigger interval, not a recipe duration.
     */
    static Optional<MachineRecipeResult> toMachineRecipe(
            OfferingRecipeView recipe, int inputCount,
            int inputSlot, int startItemSlot) {
        if (recipe == null || inputSlot == startItemSlot) {
            return Optional.empty();
        }
        int operations = OfferingBatchPolicy.operations(
                inputCount, recipe.inputAmount());
        int outputCount = OfferingBatchPolicy.outputCount(
                operations, recipe.output().getCount());
        if (outputCount <= 0) {
            return Optional.empty();
        }
        return Optional.of(new MachineRecipeResult(recipe.id(),
                recipe.output().copyWithCount(outputCount),
                NatureAuraMachineConfig.OFFERING_TRIGGER_INTERVAL,
                List.of(new InputUse(inputSlot, OfferingBatchPolicy
                        .consumedInput(inputCount, recipe.inputAmount()))),
                startItemSlot));
    }

    public record OfferingRecipeView(ResourceLocation id, Ingredient input,
                                     int inputAmount, Ingredient startItem,
                                     ItemStack output, String signature) {
        public OfferingRecipeView(ResourceLocation id, Ingredient input,
                                  int inputAmount, Ingredient startItem,
                                  ItemStack output) {
            this(id, input, inputAmount, startItem, output, "");
        }

        public OfferingRecipeView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(startItem, "startItem");
            Objects.requireNonNull(output, "output");
            if (input.isEmpty() || startItem.isEmpty()
                    || output.isEmpty() || inputAmount <= 0) {
                throw new IllegalArgumentException("Invalid offering recipe");
            }
            output = output.copy();
            signature = signatureOf(input, inputAmount, startItem, output);
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        public boolean matches(ItemStack inputStack, ItemStack startStack) {
            return inputStack != null && startStack != null
                    && inputStack.getCount() >= inputAmount
                    && !startStack.isEmpty() && input.test(inputStack)
                    && startItem.test(startStack);
        }
    }

    private static String signatureOf(Ingredient input, int inputAmount,
                                      Ingredient startItem, ItemStack output) {
        String value = ingredientSignature(input) + "@" + inputAmount
                + "|start=" + ingredientSignature(startItem)
                + "|output=" + stackSignature(output);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String ingredientSignature(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .map(OfferingRecipeBridge::stackSignature).sorted()
                .collect(Collectors.joining(","));
    }

    private static String stackSignature(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "@"
                + stack.getCount() + "|" + stack.getComponentsPatch();
    }
}
