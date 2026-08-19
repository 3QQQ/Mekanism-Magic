package com.example.mekanismmagic.integration;

import net.neoforged.fml.ModList;

/**
 * Single source of truth for optional integration gates.
 *
 * The addon intentionally has no registered content when its primary
 * integration target is absent. Future integrations should add their mod id
 * here instead of scattering ModList checks through registration code.
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
