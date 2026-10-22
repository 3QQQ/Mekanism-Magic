package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Reflective client-only entry, using the same exact gate as content registration. */
public final class ForbiddenArcanusClient {
    private static boolean recipeListenersRegistered;

    private ForbiddenArcanusClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        if (!recipeListenersRegistered) {
            NeoForge.EVENT_BUS.addListener(ForbiddenArcanusClient::onRecipesUpdated);
            NeoForge.EVENT_BUS.addListener(ForbiddenArcanusClient::onClientDisconnected);
            recipeListenersRegistered = true;
        }
        event.register(ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_CONTAINER.get(), ClibanoProcessorScreen::new);
        event.register(ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_CONTAINER.get(), HephaestusForgeProcessorScreen::new);
        event.register(ForbiddenArcanusRegistries.ESSENCE_CONDENSER_CONTAINER.get(), EssenceCondenserScreen::new);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_TILE.get(),
                HephaestusForgeRenderer::new);
    }

    private static void onRecipesUpdated(RecipesUpdatedEvent event) {
        invokeOptionalJei("onRecipesUpdated", RecipeManager.class, event.getRecipeManager());
    }

    private static void onClientDisconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        invokeOptionalJei("onClientDisconnected", null, null);
    }

    /** Machines and their screens also work without JEI; keep its API out of this class. */
    private static void invokeOptionalJei(String method, Class<?> parameterType, Object argument) {
        if (!ModCompatibility.forbiddenArcanusIntegrationEnabled() || !ModCompatibility.loaded("jei")) return;
        try {
            Class<?> integration = Class.forName("com.example.mekanismmagic.integration."
                    + "forbiddenarcanus.client.ForbiddenArcanusJeiIntegration");
            if (parameterType == null) integration.getMethod(method).invoke(null);
            else integration.getMethod(method, parameterType).invoke(null, argument);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Failed optional Forbidden & Arcanus JEI lifecycle: " + method, failure);
        }
    }
}
