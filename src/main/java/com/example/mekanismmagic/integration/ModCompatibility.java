package com.example.mekanismmagic.integration;

import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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
    public static final String ARS_MACHINE_CONTENT_PROPERTY =
            "mekanism_magic.ars_machine_content";
    private static final boolean PACKAGED_ARS_MACHINE_CONTENT =
            packagedArsMachineContent();

    private ModCompatibility() {
    }

    public static boolean occultismLoaded() {
        return loaded(OCCULTISM);
    }

    public static boolean arsNouveauLoaded() {
        return loaded(ARS_NOUVEAU);
    }

    /**
     * Ars Nouveau machine content is opt-in while its full recipe and
     * multiplayer compatibility matrix is being completed. Development runs
     * enable it with -Pmekanism_magic.ars_machine_content=true; release builds
     * leave it disabled by default. The independent mob-jar adapter remains
     * available whenever Ars Nouveau is installed.
     */
    public static boolean arsNouveauMachineContentEnabled() {
        String override = System.getProperty(ARS_MACHINE_CONTENT_PROPERTY);
        boolean enabled = override == null
                ? PACKAGED_ARS_MACHINE_CONTENT
                : Boolean.parseBoolean(override);
        return enabled
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

    private static boolean packagedArsMachineContent() {
        Properties properties = new Properties();
        try (InputStream stream = ModCompatibility.class.getClassLoader()
                .getResourceAsStream(
                        "META-INF/mekanism_magic_features.properties")) {
            if (stream == null) {
                return false;
            }
            properties.load(stream);
            return Boolean.parseBoolean(properties.getProperty(
                    "ars_nouveau_machine_content", "false"));
        } catch (IOException ignored) {
            return false;
        }
    }
}
