package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.api.functions.ConstantPredicates;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker;
import mekanism.api.recipes.ingredients.InputIngredient;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.outputs.IOutputHandler;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

/**
 * Commits Source before Mekanism consumes input and writes output.
 *
 * <p>Factory processes share one Source tank. Paying in the old onFinish
 * callback was too late because Mekanism invokes it after finishProcessing;
 * multiple lanes could therefore produce before a later payment failed.</p>
 */
public final class SourceAwareImbuementCachedRecipe extends
        OneInputCachedRecipe<@NotNull ItemStack, @NotNull ItemStack,
                ImbuementFactoryRecipe> {
    private final BooleanSupplier commitSource;
    private final BooleanSupplier recipeContextValid;
    private final BooleanSupplier hasSource;
    private final IInputHandler<@NotNull ItemStack> physicalInput;

    public SourceAwareImbuementCachedRecipe(
            ImbuementFactoryRecipe recipe,
            BooleanSupplier recheckAllErrors,
            IInputHandler<@NotNull ItemStack> inputHandler,
            IOutputHandler<@NotNull ItemStack> outputHandler,
            BooleanSupplier recipeContextValid,
            BooleanSupplier hasSource,
            BooleanSupplier commitSource) {
        super(recipe, recheckAllErrors, matchingView(inputHandler), outputHandler,
                recipe::getInput, recipe::getOutput,
                ConstantPredicates.ITEM_EMPTY,
                ConstantPredicates.ITEM_EMPTY);
        this.commitSource = commitSource;
        this.recipeContextValid = recipeContextValid;
        this.hasSource = hasSource;
        this.physicalInput = inputHandler;
    }

    @Override
    public boolean isInputValid() {
        // The recipe includes the optional exact-batch receipt requirement.
        // Applying that predicate to the clean matching view would erase its proof.
        // Mekanism retains a cache while this returns true, even after onChange.
        // The same reagent can feed several different catalyst-selected recipes.
        return recipeContextValid.getAsBoolean()
                && getRecipe().test(physicalInput.getInput());
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        // Check the original receipt before the clean Ingredient view is used.
        // Otherwise a previous lane cache could accept a newly marked, conflicting recipe.
        if (!isInputValid()) {
            tracker.mismatchedRecipe();
            return;
        }
        // Check Source after identity. An obsolete cache must reset its progress
        // even with an empty tank, rather than remaining paused as "busy" forever.
        if (!hasSource.getAsBoolean()) {
            tracker.updateOperations(0);
            return;
        }
        super.calculateOperationsThisTick(tracker);
    }

    private static IInputHandler<@NotNull ItemStack> matchingView(IInputHandler<@NotNull ItemStack> physical) {
        return new IInputHandler<>() {
            @Override
            public ItemStack getInput() {
                return ArsNouveauRecipeBridge.recipeInputView(physical.getInput());
            }

            @Override
            public ItemStack getRecipeInput(InputIngredient<@NotNull ItemStack> ingredient) {
                ItemStack clean = getInput();
                return clean.isEmpty() ? ItemStack.EMPTY : ingredient.getMatchingInstance(clean);
            }

            @Override
            public void use(ItemStack recipeInput, int operations) {
                // Native item handlers consume the count from their real slot; remaining
                // item components (including the exact-recipe receipt) are retained.
                physical.use(recipeInput, operations);
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, ItemStack recipeInput, int usageMultiplier) {
                physical.calculateOperationsCanSupport(tracker, recipeInput, usageMultiplier);
            }
        };
    }

    @Override
    protected void finishProcessing(int operations) {
        if (operations > 0 && commitSource.getAsBoolean()) {
            super.finishProcessing(operations);
        }
    }
}
