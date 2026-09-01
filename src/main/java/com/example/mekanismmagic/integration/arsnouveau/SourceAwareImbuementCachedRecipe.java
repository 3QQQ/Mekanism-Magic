package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.api.functions.ConstantPredicates;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
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

    public SourceAwareImbuementCachedRecipe(
            ImbuementFactoryRecipe recipe,
            BooleanSupplier recheckAllErrors,
            IInputHandler<@NotNull ItemStack> inputHandler,
            IOutputHandler<@NotNull ItemStack> outputHandler,
            BooleanSupplier commitSource) {
        super(recipe, recheckAllErrors, inputHandler, outputHandler,
                recipe::getInput, recipe::getOutput,
                ConstantPredicates.ITEM_EMPTY,
                ConstantPredicates.ITEM_EMPTY);
        this.commitSource = commitSource;
    }

    @Override
    protected void finishProcessing(int operations) {
        if (operations > 0 && commitSource.getAsBoolean()) {
            super.finishProcessing(operations);
        }
    }
}
