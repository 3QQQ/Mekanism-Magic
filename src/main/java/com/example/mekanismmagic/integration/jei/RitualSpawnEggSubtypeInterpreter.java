package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.item.RitualSpawnEggIdentity;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The shared egg item must not collapse all ritual entities/jobs into one JEI output. */
final class RitualSpawnEggSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
    static final RitualSpawnEggSubtypeInterpreter INSTANCE = new RitualSpawnEggSubtypeInterpreter();
    private RitualSpawnEggSubtypeInterpreter() { }
    @Override public @Nullable Object getSubtypeData(ItemStack stack, UidContext context) {
        String key = RitualSpawnEggIdentity.variantKey(stack);
        return key.isEmpty() ? null : key;
    }
    @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
        return RitualSpawnEggIdentity.variantKey(stack);
    }
}
