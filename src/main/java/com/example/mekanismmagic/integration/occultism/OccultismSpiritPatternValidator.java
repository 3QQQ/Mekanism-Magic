package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Exact, dependency-free contract used for Spirit processing patterns. */
public final class OccultismSpiritPatternValidator {
    private OccultismSpiritPatternValidator() {
    }

    public static boolean matches(Level level, ItemStack spiritSource,
                                  List<PatternStack> inputs,
                                  List<PatternStack> outputs) {
        if (level == null || spiritSource == null
                || !OccultismRecipeBridge.isSpiritSource(spiritSource)) {
            return false;
        }
        return matchesResolved(inputs, outputs, input -> {
            ItemStackHandler inventory = new ItemStackHandler(
                    NativeMagicMachineBlockEntity.MACHINE_INVENTORY_SIZE);
            inventory.setStackInSlot(0, input.copy());
            inventory.setStackInSlot(
                    NativeMagicMachineBlockEntity.CONTAINMENT_SLOT,
                    spiritSource.copyWithCount(1));
            return OccultismRecipeBridge.findSpiritMachineRecipe(
                            level, inventory, spiritSource, 0L)
                    .filter(result -> !result.randomTrade())
                    .map(OccultismRecipeBridge.SpiritMachineRecipe::recipe);
        });
    }

    /** Package-visible seam for deterministic offline contract tests. */
    static boolean matchesResolved(
            List<PatternStack> inputs, List<PatternStack> outputs,
            Function<ItemStack, Optional<MachineRecipeResult>> resolver) {
        ItemStack input = singleInput(inputs);
        if (input.isEmpty() || outputs == null || outputs.size() != 1
                || resolver == null) {
            return false;
        }
        PatternStack declaredOutput = outputs.getFirst();
        ItemStack output = declaredOutput == null
                ? ItemStack.EMPTY : declaredOutput.stack();
        if (output.isEmpty() || declaredOutput.amount() <= 0
                || declaredOutput.amount() > Integer.MAX_VALUE) {
            return false;
        }
        Optional<MachineRecipeResult> resolved;
        try {
            resolved = resolver.apply(input.copy());
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
        if (resolved == null || resolved.isEmpty()) {
            return false;
        }
        MachineRecipeResult recipe = resolved.get();
        List<InputUse> consumed = recipe.inputs();
        return consumed.size() == 1
                && consumed.getFirst().slot() == 0
                && consumed.getFirst().count() == input.getCount()
                && ItemStack.isSameItemSameComponents(
                recipe.output(), output)
                && recipe.output().getCount() == declaredOutput.amount();
    }

    private static ItemStack singleInput(List<PatternStack> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack template = ItemStack.EMPTY;
        long amount = 0;
        try {
            for (PatternStack entry : inputs) {
                if (entry == null || entry.amount() <= 0
                        || entry.amount() > Integer.MAX_VALUE) {
                    return ItemStack.EMPTY;
                }
                ItemStack stack = entry.stack();
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                if (template.isEmpty()) {
                    template = stack.copyWithCount(1);
                } else if (!ItemStack.isSameItemSameComponents(
                        template, stack)) {
                    return ItemStack.EMPTY;
                }
                amount = Math.addExact(amount, entry.amount());
                if (amount > Integer.MAX_VALUE) {
                    return ItemStack.EMPTY;
                }
            }
        } catch (ArithmeticException overflow) {
            return ItemStack.EMPTY;
        }
        return template.isEmpty() ? ItemStack.EMPTY
                : template.copyWithCount((int) amount);
    }
}
