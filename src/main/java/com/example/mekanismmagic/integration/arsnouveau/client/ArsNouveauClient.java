package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client registration loaded reflectively only when Ars Nouveau is present.
 */
public final class ArsNouveauClient {
    private ArsNouveauClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ArsNouveauRegistries.SOURCE_GENERATOR_CONTAINER.get(),
                SourceGeneratorScreen::new);
        event.register(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_CONTAINER.get(),
                ImbuementProcessorScreen::new);
        event.register(
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_CONTAINER
                        .get(),
                EnchantingApparatusProcessorScreen::new);
    }
}
