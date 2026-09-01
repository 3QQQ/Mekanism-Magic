package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.MagicMachineAnimationRenderer;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.ModCompatibility;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client registration loaded reflectively only when Ars Nouveau is present.
 */
public final class ArsNouveauClient {
    private static boolean recipeListenerRegistered;

    private ArsNouveauClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        if (!recipeListenerRegistered) {
            NeoForge.EVENT_BUS.addListener(
                    ArsNouveauClient::onRecipesUpdated);
            recipeListenerRegistered = true;
        }
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
        if (ModCompatibility.mekanismExtrasLoaded()) {
            registerExtrasScreens(event);
        }
    }

    private static void onRecipesUpdated(RecipesUpdatedEvent event) {
        ArsNouveauRecipeScanner.refresh(event.getRecipeManager());
    }

    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.ADVANCED_MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.ELITE_MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.ULTIMATE_MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.ABSOLUTE_MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.SUPREME_MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.COSMIC_MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.INFINITE_MAGIC_SOURCE_PIPE_TILE.get(),
                MagicSourcePipeRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.DRYGMY_SIMULATOR_TILE.get(),
                DrygmySimulatorRenderer::new);
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.SOURCE_AMPLIFIER_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SOURCE));
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.SOURCE_CONVERTER_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SOURCE_CONVERTER));
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.CATALYST_SCANNER));
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT));
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.ENCHANTING));
        registerImbuementFactoryRenderers(event);
        if (ModCompatibility.mekanismExtrasLoaded()) {
            registerExtrasRenderers(event);
        }
    }

    private static void registerImbuementFactoryRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT_FACTORY));
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.ADVANCED_IMBUEMENT_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT_FACTORY));
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.ELITE_IMBUEMENT_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT_FACTORY));
        event.registerBlockEntityRenderer(
                ArsNouveauRegistries.ULTIMATE_IMBUEMENT_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT_FACTORY));
    }

    private static void registerExtrasScreens(
            RegisterMenuScreensEvent event) {
        try {
            Class.forName("com.example.mekanismmagic.integration.mekextras."
                            + "client.MekanismExtrasImbuementClient")
                    .getMethod("registerScreens",
                            RegisterMenuScreensEvent.class)
                    .invoke(null, event);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register Mekanism Extras imbuement screen",
                    failure);
        }
    }

    private static void registerExtrasRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        try {
            Class.forName("com.example.mekanismmagic.integration.mekextras."
                            + "client.MekanismExtrasImbuementClient")
                    .getMethod("registerRenderers",
                            EntityRenderersEvent.RegisterRenderers.class)
                    .invoke(null, event);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register Mekanism Extras imbuement renderers",
                    failure);
        }
    }
}
