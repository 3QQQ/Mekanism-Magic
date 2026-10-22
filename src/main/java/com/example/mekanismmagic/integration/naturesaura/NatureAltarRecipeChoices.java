package com.example.mekanismmagic.integration.naturesaura;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;

/** First ambiguous ordinary input in a machine's current work lanes. */
public final class NatureAltarRecipeChoices {
    private NatureAltarRecipeChoices() {
    }

    public static Optional<Choice> firstAmbiguous(Level level,
                                                  List<ItemStack> inputs,
                                                  List<ItemStack> catalysts,
                                                  IntPredicate eligibleLane) {
        if (level == null || inputs == null || eligibleLane == null) {
            return Optional.empty();
        }
        for (int lane = 0; lane < inputs.size(); lane++) {
            ItemStack input = inputs.get(lane);
            if (!eligibleLane.test(lane) || input == null || input.isEmpty()
                    || NatureAuraRecipeBridge.hasPatternRecipeMarker(input)) {
                continue;
            }
            List<NatureAuraRecipeBridge.AltarRecipeView> matches =
                    NatureAuraRecipeBridge.matchingAltarRecipes(
                            level, input, catalysts);
            if (matches.size() > 1) {
                return Optional.of(new Choice(input, matches));
            }
        }
        return Optional.empty();
    }

    public record Choice(ItemStack input,
                         List<NatureAuraRecipeBridge.AltarRecipeView> recipes) {
        public Choice {
            input = input.copy();
            recipes = List.copyOf(recipes);
        }

        public ResourceLocation next(Level level, List<ItemStack> catalysts,
                                     ResourceLocation preferred) {
            ResourceLocation current = NatureAuraRecipeBridge
                    .findAltarRecipe(level, input, catalysts, null, preferred)
                    .map(NatureAuraRecipeBridge.AltarRecipeView::id)
                    .orElse(null);
            int index = -1;
            for (int candidate = 0; candidate < recipes.size(); candidate++) {
                if (recipes.get(candidate).id().equals(current)) {
                    index = candidate;
                    break;
                }
            }
            return recipes.get((index + 1) % recipes.size()).id();
        }

        public ItemStack selectedOutput(Level level,
                                        List<ItemStack> catalysts,
                                        ResourceLocation preferred) {
            return NatureAuraRecipeBridge.findAltarRecipe(
                            level, input, catalysts, null, preferred)
                    .map(recipe -> recipe.output().copy())
                    .orElse(ItemStack.EMPTY);
        }
    }
}
