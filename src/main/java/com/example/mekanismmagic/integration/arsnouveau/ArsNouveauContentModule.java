package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.ContentIntegrationModule;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.MekanismMagic;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Registers machines and items backed by Ars Nouveau.
 */
public final class ArsNouveauContentModule
        implements ContentIntegrationModule {
    public static final ArsNouveauContentModule INSTANCE =
            new ArsNouveauContentModule();

    private ArsNouveauContentModule() {
    }

    @Override
    public String modId() {
        return ModCompatibility.ARS_NOUVEAU;
    }

    @Override
    public void register(IEventBus modBus) {
        ArsNouveauRegistries.register(modBus);
        NeoForge.EVENT_BUS.addListener(ArsDevelopmentCommands::register);
        NeoForge.EVENT_BUS.addListener(
                (ServerStartedEvent event) ->
                        ArsNouveauRecipeScanner.scanAtStartup(
                                event.getServer(), MekanismMagic.LOGGER));
        try {
            Class.forName("com.example.mekanismmagic.integration.ae2.Ae2ArsCompat")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // AE2 is optional; the Ars machines remain fully functional
            // without its virtual-context crafting provider.
        }
        if (ModCompatibility.mekanismExtrasLoaded()) {
            try {
                Class.forName("com.example.mekanismmagic.integration.mekextras."
                                + "MekanismExtrasImbuementFactories")
                        .getMethod("register", IEventBus.class)
                        .invoke(null, modBus);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException(
                        "Failed to register Extras imbuement factories",
                        failure);
            }
        }
    }
}
