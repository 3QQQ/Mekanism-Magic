package com.example.mekanismmagic.integration;

import net.minecraftforge.fml.ModList;

/**
 * Central optional-integration gates. Keep mod presence checks here so future
 * integrations do not spread loader-specific checks across registrations.
 */
public final class ModCompatibility {
    public static final String OCCULTISM = "occultism";
    public static final String ARS_NOUVEAU = "ars_nouveau";
    public static final String MEKANISM_EXTRAS = "mekanism_extras";
    public static final String MEKMM = "mekmm";

    private ModCompatibility() {
    }

    public static boolean occultismLoaded() {
        return loaded(OCCULTISM);
    }

    public static boolean loaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
