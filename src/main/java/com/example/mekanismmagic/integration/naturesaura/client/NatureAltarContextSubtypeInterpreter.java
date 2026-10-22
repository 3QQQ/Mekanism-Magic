package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.naturesaura
        .NatureAltarContextItem;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Keeps every recipe-bound natural altar context distinct in JEI. */
final class NatureAltarContextSubtypeInterpreter
        implements ISubtypeInterpreter<ItemStack> {
    static final NatureAltarContextSubtypeInterpreter INSTANCE =
            new NatureAltarContextSubtypeInterpreter();

    private NatureAltarContextSubtypeInterpreter() {
    }

    @Override
    public @Nullable Object getSubtypeData(
            ItemStack stack, UidContext context) {
        Subtype subtype = subtype(stack);
        return subtype.isEmpty() ? null : subtype;
    }

    @Override
    public String getLegacyStringSubtypeInfo(
            ItemStack stack, UidContext context) {
        return subtype(stack).serialized();
    }

    private static Subtype subtype(ItemStack stack) {
        var id = NatureAltarContextItem.recipeId(stack);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String signature = data == null ? "" : data.getUnsafe()
                .getString(NatureAltarContextItem.RECIPE_SIGNATURE_KEY);
        return id == null ? Subtype.EMPTY
                : new Subtype(id.toString(), signature);
    }

    private record Subtype(String recipeId, String signature) {
        private static final Subtype EMPTY = new Subtype("", "");

        private boolean isEmpty() {
            return recipeId.isBlank();
        }

        private String serialized() {
            return isEmpty() ? ""
                    : recipeId + "|signature=" + signature;
        }
    }
}
