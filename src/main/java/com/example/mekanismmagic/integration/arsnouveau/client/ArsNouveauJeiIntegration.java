package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.hollingsworth.arsnouveau.client.jei.JEIArsNouveauPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;

/**
 * Reuses Ars Nouveau's existing JEI categories for the equivalent machines.
 */
public final class ArsNouveauJeiIntegration {
    private ArsNouveauJeiIntegration() {
    }

    public static void registerCatalysts(
            IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.asItem(),
                JEIArsNouveauPlugin.IMBUEMENT_RECIPE_TYPE.get());
        registration.addRecipeCatalyst(
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK
                        .asItem(),
                JEIArsNouveauPlugin.ENCHANTING_APP_RECIPE_TYPE.get(),
                JEIArsNouveauPlugin.ENCHANTING_RECIPE_TYPE.get(),
                JEIArsNouveauPlugin.ARMOR_RECIPE_TYPE.get());
    }
}
