package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client registration loaded reflectively only when Ars Nouveau is present.
 */
public final class ArsNouveauClient {
    private ArsNouveauClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ArsNouveauRegistries.SOURCE_AMPLIFIER_CONTAINER.get(),
                SourceAmplifierScreen::new);
        event.register(ArsNouveauRegistries.SOURCE_CONVERTER_CONTAINER.get(),
                FeSourceConverterScreen::new);
        event.register(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_CONTAINER.get(),
                ImbuementProcessorScreen::new);
        event.register(
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_CONTAINER
                        .get(),
                EnchantingApparatusProcessorScreen::new);
        event.register(ArsNouveauRegistries.DRYGMY_SIMULATOR_CONTAINER.get(),
                DrygmySimulatorScreen::new);
        event.register(ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_CONTAINER
                        .get(),
                CatalystIdentifierAssemblerScreen::new);
        event.register(ArsNouveauRegistries.IMBUEMENT_FACTORY_CONTAINER.get(),
                ImbuementFactoryScreen::new);
    }

    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
    }
}
