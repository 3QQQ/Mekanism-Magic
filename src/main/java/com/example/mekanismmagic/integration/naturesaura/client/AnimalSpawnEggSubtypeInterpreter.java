package com.example.mekanismmagic.integration.naturesaura.client;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Fixed encoded targets must remain distinct in JEI even when no native spawn-egg item exists. */
final class AnimalSpawnEggSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
    static final AnimalSpawnEggSubtypeInterpreter INSTANCE = new AnimalSpawnEggSubtypeInterpreter();
    private AnimalSpawnEggSubtypeInterpreter() { }

    private static @Nullable ResourceLocation target(ItemStack stack) {
        var data = stack.get(DataComponents.ENTITY_DATA);
        String id = data == null ? "" : data.getUnsafe().getString("id");
        return id.isBlank() ? null : ResourceLocation.tryParse(id);
    }

    @Override public @Nullable Object getSubtypeData(ItemStack stack, UidContext context) { return target(stack); }
    @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
        var id = target(stack);
        return id == null ? "" : id.toString();
    }
}
