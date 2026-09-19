package com.example.mekanismmagic.integration.ae2;

import appeng.api.stacks.GenericStack;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge.IdentifierImbuementJeiData;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Pure encoding shape, usable by server tests without loading JEI/terminal client helpers. */
public final class Ae2ImbuementPatternEncoding {
    private Ae2ImbuementPatternEncoding() { }

    public static List<List<GenericStack>> encodingInputs(IdentifierImbuementJeiData recipe) {
        List<GenericStack> reagents = Arrays.stream(recipe.input().getItems())
                .map(stack -> stack.copyWithCount(1)).map(GenericStack::fromItemStack)
                .filter(Objects::nonNull).toList();
        if (reagents.isEmpty()) return List.of();
        if (recipe.identifier().isEmpty()) return List.of(reagents);
        GenericStack identifier = GenericStack.fromItemStack(recipe.identifier().copyWithCount(1));
        return identifier == null ? List.of() : List.of(reagents, List.of(identifier));
    }
}
