package com.example.mekanismmagic.integration.jei;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Makes JEI distinguish every bound ritual and every pentacle selector.
 */
final class MiniRitualSubtypeInterpreter
        implements ISubtypeInterpreter<ItemStack> {
    static final MiniRitualSubtypeInterpreter INSTANCE =
            new MiniRitualSubtypeInterpreter();

    private MiniRitualSubtypeInterpreter() {
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
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return Subtype.EMPTY;
        }
        CompoundTag tag = data.copyTag();
        return new Subtype(tag.getString("ritual"),
                tag.getString("pentacle"));
    }

    private record Subtype(String ritual, String pentacle) {
        private static final Subtype EMPTY = new Subtype("", "");

        private boolean isEmpty() {
            return ritual.isBlank() && pentacle.isBlank();
        }

        private String serialized() {
            if (isEmpty()) {
                return "";
            }
            return "ritual=" + ritual + "|pentacle=" + pentacle;
        }
    }
}
