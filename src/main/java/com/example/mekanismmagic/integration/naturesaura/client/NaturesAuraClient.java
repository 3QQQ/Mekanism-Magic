package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.naturesaura
        .NaturesAuraRegistries;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.example.mekanismmagic.client.MagicMachineAnimationRenderer;

/** Client entry loaded reflectively only with Nature's Aura present. */
public final class NaturesAuraClient {
    private static boolean recipeListenerRegistered;

    private NaturesAuraClient() {
    }

    public static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? 0xFF95BC73 : 0xFF344F3A, NaturesAuraRegistries.ANIMAL_SPAWN_EGG.get());
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        NatureMachineSettingsSection.register();
        event.register(NaturesAuraRegistries.FE_AURA_CONVERTER_CONTAINER.get(), FeAuraConverterScreen::new);
        event.register(NaturesAuraRegistries.AURA_FUEL_GENERATOR_CONTAINER.get(), AuraFuelGeneratorScreen::new);
        event.register(NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_CONTAINER.get(), TreeRitualProcessorScreen::new);
        event.register(NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_CONTAINER.get(), AnimalSpawnerProcessorScreen::new);
        event.register(NaturesAuraRegistries.OFFERING_PROCESSOR_CONTAINER.get(), OfferingProcessorScreen::new);
        if (!recipeListenerRegistered) {
            NeoForge.EVENT_BUS.addListener(
                    NaturesAuraClient::onRecipesUpdated);
            recipeListenerRegistered = true;
        }
        event.register(NaturesAuraRegistries
                        .AURA_FIELD_INTERFACE_CONTAINER.get(),
                AuraFieldInterfaceScreen::new);
        event.register(NaturesAuraRegistries
                        .NATURE_ALTAR_PROCESSOR_CONTAINER.get(),
                NatureAltarProcessorScreen::new);
        event.register(NaturesAuraRegistries
                        .NATURE_ALTAR_FACTORY_CONTAINER.get(),
                NatureAltarFactoryScreen::new);
        invokeExtrasClient("registerScreens", RegisterMenuScreensEvent.class,
                event);
    }

    private static void onRecipesUpdated(RecipesUpdatedEvent event) {
        com.example.mekanismmagic.integration.naturesaura
                .NatureAuraRecipeScanner.refresh(event.getRecipeManager());
    }

    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context, MagicMachineAnimationRenderer.Kind.TREE_RITUAL));
        event.registerBlockEntityRenderer(NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context, MagicMachineAnimationRenderer.Kind.ANIMAL_SPAWNER));
        event.registerBlockEntityRenderer(NaturesAuraRegistries.OFFERING_PROCESSOR_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context, MagicMachineAnimationRenderer.Kind.OFFERING));




        event.registerBlockEntityRenderer(
                NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.NATURE_ALTAR));
        registerNatureAltarFactoryRenderers(event);
        invokeExtrasClient("registerRenderers",
                EntityRenderersEvent.RegisterRenderers.class, event);
    }

    private static void invokeExtrasClient(String method, Class<?> eventType,
                                          Object event) {
        if (!com.example.mekanismmagic.integration.ModCompatibility
                .mekanismExtrasLoaded()) {
            return;
        }
        try {
            Class.forName("com.example.mekanismmagic.integration.mekextras.client."
                            + "MekanismExtrasNatureAltarClient")
                    .getMethod(method, eventType).invoke(null, event);
        } catch (ClassNotFoundException missingBridge) {
            // Reduced distributions may omit the optional Extras bridge.
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register Mekanism Extras natural altar client", failure);
        }
    }

    public static void invalidateSpriteCaches() {

    }

    private static void registerNatureAltarFactoryRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                NaturesAuraRegistries.BASIC_NATURE_ALTAR_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind
                                .NATURE_ALTAR_FACTORY));
        event.registerBlockEntityRenderer(
                NaturesAuraRegistries.ADVANCED_NATURE_ALTAR_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind
                                .NATURE_ALTAR_FACTORY));
        event.registerBlockEntityRenderer(
                NaturesAuraRegistries.ELITE_NATURE_ALTAR_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind
                                .NATURE_ALTAR_FACTORY));
        event.registerBlockEntityRenderer(
                NaturesAuraRegistries.ULTIMATE_NATURE_ALTAR_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind
                                .NATURE_ALTAR_FACTORY));
    }
}
