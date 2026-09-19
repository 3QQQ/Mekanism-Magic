package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import net.minecraft.resources.ResourceLocation;

/** A machine recipe identity, deliberately without a cached static output. */
public record ArsApparatusJeiData(ResourceLocation id,
                                  IEnchantingRecipe recipe) {
}
