package com.example.mekanismmagic.integration.naturesaura;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Dependency-light transaction boundary used by optional pattern providers.
 *
 * <p>The recipe id is non-physical context. Implementations must either
 * accept the complete item stack and bind every affected lane to that exact
 * current recipe, or leave all inventory and recipe state unchanged.</p>
 */
public interface NatureAltarAutomationHost {
    boolean acceptNatureAltarPattern(ResourceLocation recipeId,
                                     ItemStack input);
}
