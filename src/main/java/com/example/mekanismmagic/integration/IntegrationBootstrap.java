package com.example.mekanismmagic.integration;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauIntegration;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.example.mekanismmagic.integration.occultism.OccultismContentModule;
import com.example.mekanismmagic.integration.occultism.OccultismEntityContainerAdapter;
import net.neoforged.bus.api.IEventBus;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers optional integrations through stable common extension points.
 */
public final class IntegrationBootstrap {
    private static final List<ContentIntegrationModule> BUILTIN_CONTENT_MODULES =
            List.of(OccultismContentModule.INSTANCE);
    private static boolean initialized;

    private IntegrationBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        if (ModCompatibility.occultismLoaded()) {
            EntityContainerRegistry.register(
                    OccultismEntityContainerAdapter.INSTANCE);
        }
        if (ModCompatibility.arsNouveauLoaded()) {
            EntityContainerRegistry.register(ArsNouveauIntegration.INSTANCE);
        }
        initialized = true;
    }

    public static void registerContent(IEventBus modBus) {
        List<ContentIntegrationModule> modules = new ArrayList<>();
        for (ContentIntegrationModule module : BUILTIN_CONTENT_MODULES) {
            if (module.isLoaded()) {
                modules.add(module);
            }
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            optionalModule("arsnouveau.ArsNouveauContentModule")
                    .filter(ContentIntegrationModule::isLoaded)
                    .ifPresent(modules::add);
        }
        if (modules.isEmpty()) {
            return;
        }
        com.example.mekanismmagic.MekanismMagic.CREATIVE_TABS.register(modBus);
        modules.forEach(module -> module.register(modBus));
    }

    private static java.util.Optional<ContentIntegrationModule> optionalModule(
            String className) {
        try {
            Class<?> moduleClass = Class.forName(
                    "com.example.mekanismmagic.integration." + className);
            Field instance = moduleClass.getField("INSTANCE");
            return java.util.Optional.of(
                    (ContentIntegrationModule) instance.get(null));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return java.util.Optional.empty();
        }
    }
}
