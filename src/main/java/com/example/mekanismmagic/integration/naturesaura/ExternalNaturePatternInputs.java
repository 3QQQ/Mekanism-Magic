package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Exact item-only views of one external processing-pattern delivery. */
final class ExternalNaturePatternInputs {
    private ExternalNaturePatternInputs() {
    }

    static Optional<List<PatternStack>> compact(List<PatternStack> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Optional.empty();
        }
        List<PatternStack> grouped = new ArrayList<>();
        for (PatternStack input : inputs) {
            if (input == null || input.stack().isEmpty()
                    || input.amount() <= 0
                    || input.amount() > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            ItemStack stack = input.stack();
            int existing = -1;
            for (int index = 0; index < grouped.size(); index++) {
                if (ItemStack.isSameItemSameComponents(
                        grouped.get(index).stack(), stack)) {
                    existing = index;
                    break;
                }
            }
            if (existing < 0) {
                grouped.add(input);
            } else {
                long amount = grouped.get(existing).amount() + input.amount();
                if (amount > Integer.MAX_VALUE) {
                    return Optional.empty();
                }
                grouped.set(existing, new PatternStack(stack, amount));
            }
        }
        return Optional.of(List.copyOf(grouped));
    }

    static boolean matchesSingleOutput(
            List<PatternStack> outputs, ItemStack expected) {
        if (outputs == null || outputs.size() != 1 || expected.isEmpty()) {
            return false;
        }
        PatternStack declared = outputs.getFirst();
        return declared != null && !declared.stack().isEmpty()
                && declared.amount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(
                declared.stack(), expected);
    }
}
