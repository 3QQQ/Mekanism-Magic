package com.example.mekanismmagic.integration.arsnouveau.client;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Makes every scanned catalyst combination a distinct JEI ingredient. */
final class CatalystIdentifierSubtypeInterpreter
        implements ISubtypeInterpreter<ItemStack> {
    static final CatalystIdentifierSubtypeInterpreter INSTANCE =
            new CatalystIdentifierSubtypeInterpreter();

    private CatalystIdentifierSubtypeInterpreter() {
    }

    @Override
    public @Nullable Object getSubtypeData(
            ItemStack stack, UidContext context) {
        String id = catalystId(stack);
        return id.isBlank() ? null : id;
    }

    @Override
    public String getLegacyStringSubtypeInfo(
            ItemStack stack, UidContext context) {
        return catalystId(stack);
    }

    private static String catalystId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? ""
                : data.copyTag().getString("catalyst_id");
    }
}
