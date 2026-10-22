package com.example.mekanismmagic.integration.naturesaura;

import mekanism.api.functions.ConstantPredicates;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.outputs.IOutputHandler;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Factory cache that commits a recipe's shared Aura cost before mutating its
 * process input and output slots.
 *
 * <p>Mekanism processes factory lanes serially on the server thread. Each
 * lane therefore rechecks the shared store and performs one exact commit at
 * the completion boundary; a later lane in the same tick observes the
 * already reduced balance and cannot overdraw it.</p>
 */
public final class AuraAwareNatureAltarCachedRecipe extends
        OneInputCachedRecipe<@NotNull ItemStack, @NotNull ItemStack,
                NatureAltarFactoryRecipe> {
    /** Shared factory warning used when an otherwise valid lane lacks Aura. */
    public static final CachedRecipe.OperationTracker.RecipeError
            NOT_ENOUGH_AURA =
            CachedRecipe.OperationTracker.RecipeError.create();

    private final BooleanSupplier selectionValid;
    private final BooleanSupplier creativeAura;
    private final BooleanSupplier auraAvailable;
    private final BooleanSupplier commitAura;
    private final Runnable onCommitted;

    /**
     * @param selectionValid whether this cache still represents the lane's
     *                       locked recipe and current catalog version
     * @param creativeAura   whether recipe Aura consumption is bypassed
     * @param auraAvailable  exact-cost simulation against the shared store
     * @param commitAura     exact-cost execution against the shared store
     * @param onCommitted    invoked only after input/output were committed
     */
    public AuraAwareNatureAltarCachedRecipe(
            NatureAltarFactoryRecipe recipe,
            BooleanSupplier recheckAllErrors,
            IInputHandler<@NotNull ItemStack> inputHandler,
            IOutputHandler<@NotNull ItemStack> outputHandler,
            BooleanSupplier selectionValid,
            BooleanSupplier creativeAura,
            BooleanSupplier auraAvailable,
            BooleanSupplier commitAura,
            Runnable onCommitted) {
        super(recipe, recheckAllErrors, inputHandler, outputHandler,
                recipe::getInput, recipe::getOutput,
                ConstantPredicates.ITEM_EMPTY,
                ConstantPredicates.ITEM_EMPTY);
        this.selectionValid = Objects.requireNonNull(
                selectionValid, "selectionValid");
        this.creativeAura = Objects.requireNonNull(
                creativeAura, "creativeAura");
        this.auraAvailable = Objects.requireNonNull(
                auraAvailable, "auraAvailable");
        this.commitAura = Objects.requireNonNull(commitAura, "commitAura");
        this.onCommitted = Objects.requireNonNull(
                onCommitted, "onCommitted");
    }

    @Override
    public boolean isInputValid() {
        return selectionValid.getAsBoolean() && super.isInputValid();
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()
                && !creativeAura.getAsBoolean()
                && !auraAvailable.getAsBoolean()) {
            tracker.updateOperations(0);
            tracker.addError(NOT_ENOUGH_AURA);
        }
    }

    @Override
    protected void finishProcessing(int operations) {
        if (operations <= 0) {
            return;
        }
        // Nature altar factories deliberately cap every lane at one operation
        // per completion. Failing closed prevents a future configuration
        // change from producing several outputs for one Aura payment.
        if (operations != 1) {
            return;
        }
        if (!creativeAura.getAsBoolean() && !commitAura.getAsBoolean()) {
            return;
        }
        super.finishProcessing(operations);
        onCommitted.run();
    }
}
