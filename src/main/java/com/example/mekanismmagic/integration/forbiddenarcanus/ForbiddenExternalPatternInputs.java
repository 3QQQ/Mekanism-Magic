package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Exact item-key checks shared by the Forbidden & Arcanus external AE routes. */
final class ForbiddenExternalPatternInputs {
    private ForbiddenExternalPatternInputs() {
    }

    static Optional<List<PatternStack>> normalize(List<PatternStack> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Optional.empty();
        }
        List<PatternStack> merged = new ArrayList<>();
        for (PatternStack input : inputs) {
            if (input == null || input.stack().isEmpty()
                    || input.amount() <= 0 || input.amount() > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            int matching = -1;
            for (int index = 0; index < merged.size(); index++) {
                if (ItemStack.isSameItemSameComponents(
                        merged.get(index).stack(), input.stack())) {
                    matching = index;
                    break;
                }
            }
            if (matching < 0) {
                merged.add(input);
            } else {
                PatternStack previous = merged.get(matching);
                long amount = previous.amount() + input.amount();
                if (amount > Integer.MAX_VALUE) {
                    return Optional.empty();
                }
                merged.set(matching, new PatternStack(previous.stack(), amount));
            }
        }
        return Optional.of(List.copyOf(merged));
    }

    static boolean matchesPrimaryOutput(List<PatternStack> outputs,
                                        ItemStack actual) {
        return outputs != null && outputs.size() == 1
                && outputs.getFirst() != null && !actual.isEmpty()
                && outputs.getFirst().amount() == actual.getCount()
                && ItemStack.isSameItemSameComponents(
                        outputs.getFirst().stack(), actual);
    }
}
