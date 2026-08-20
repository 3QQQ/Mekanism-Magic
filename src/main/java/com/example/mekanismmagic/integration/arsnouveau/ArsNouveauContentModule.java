package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.ContentIntegrationModule;
import com.example.mekanismmagic.integration.ModCompatibility;
import net.neoforged.bus.api.IEventBus;

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
    }
}
