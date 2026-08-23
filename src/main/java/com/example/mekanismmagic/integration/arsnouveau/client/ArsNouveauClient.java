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
        event.register(ArsNouveauRegistries.SOURCE_AMPLIFIER_CONTAINER.get(),
                SourceAmplifierScreen::new);
        event.register(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_CONTAINER.get(),
                ImbuementProcessorScreen::new);
        event.register(
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_CONTAINER
                        .get(),
                EnchantingApparatusProcessorScreen::new);
        event.register(ArsNouveauRegistries.DRYGMY_SIMULATOR_CONTAINER.get(),
                DrygmySimulatorScreen::new);
    }
}
