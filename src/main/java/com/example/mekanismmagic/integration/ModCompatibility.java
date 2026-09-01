package com.example.mekanismmagic.integration;

import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
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
    public static final String ARS_ENERGISTIQUE = "arseng";
    public static final String MEKANISM_EXTRAS = "mekanism_extras";
    public static final String MEKMM = "mekmm";
    public static final String MEK_ENERGISTICS = "mekenergistics";
    public static final String MEK_ENERGISTICS_AUTOMATION_VERSION = "3.0.6";
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
     * Ars Nouveau machine content is packaged by default so its registry IDs
     * remain stable across restarts. An explicit system-property override is
     * retained for isolated compatibility diagnostics only.
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

    /**
     * Returns the installed Mek Energistics version when the optional mod is
     * present. The bridge is compiled against 3.0.6, but older releases are
     * allowed to load and simply keep the ME automation feature disabled.
     */
    public static Optional<String> mekenergisticsVersion() {
        return ModList.get().getModContainerById(MEK_ENERGISTICS)
                .map(container -> container.getModInfo().getVersion().toString());
    }

    public static boolean mekenergisticsAutomationSupported() {
        return mekenergisticsVersion()
                .map(version -> versionAtLeast(version,
                        MEK_ENERGISTICS_AUTOMATION_VERSION))
                .orElse(false);
    }

    public static boolean loaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private static boolean versionAtLeast(String actual, String required) {
        String[] actualParts = actual.split("[^0-9]+");
        String[] requiredParts = required.split("[^0-9]+");
        int length = Math.max(actualParts.length, requiredParts.length);
        for (int index = 0; index < length; index++) {
            int actualPart = numericPart(actualParts, index);
            int requiredPart = numericPart(requiredParts, index);
            if (actualPart != requiredPart) {
                return actualPart > requiredPart;
            }
        }
        return true;
    }

    private static int numericPart(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean packagedArsMachineContent() {
        Properties properties = new Properties();
        try (InputStream stream = ModCompatibility.class.getClassLoader()
                .getResourceAsStream(
                        "META-INF/mekanism_magic_features.properties")) {
            if (stream == null) {
                return true;
            }
            properties.load(stream);
            return Boolean.parseBoolean(properties.getProperty(
                    "ars_nouveau_machine_content", "true"));
        } catch (IOException ignored) {
            return true;
        }
    }
}
