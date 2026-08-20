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
    private static final boolean ARS_NOUVEAU_MACHINE_CONTENT_ENABLED = false;

    private ModCompatibility() {
    }

    public static boolean occultismLoaded() {
        return loaded(OCCULTISM);
    }

    public static boolean arsNouveauLoaded() {
        return loaded(ARS_NOUVEAU);
    }

    /**
     * Ars Nouveau machine content remains disabled in release builds until
     * its full recipe and multiplayer compatibility matrix is complete.
     * The independent mob-jar adapter is still enabled when Ars Nouveau is
     * installed.
     */
    public static boolean arsNouveauMachineContentEnabled() {
        return ARS_NOUVEAU_MACHINE_CONTENT_ENABLED
                && arsNouveauLoaded();
    }

    public static boolean mekanismExtrasLoaded() {
        return loaded(MEKANISM_EXTRAS);
    }

    public static boolean mekmmLoaded() {
        return loaded(MEKMM);
    }

    public static boolean loaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
