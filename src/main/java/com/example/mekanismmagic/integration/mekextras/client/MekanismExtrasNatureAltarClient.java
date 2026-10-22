package com.example.mekanismmagic.integration.mekextras.client;

import com.example.mekanismmagic.client.MagicMachineAnimationRenderer;
import com.example.mekanismmagic.integration.mekextras
        .MekanismExtrasNatureAltarFactories;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client registration isolated behind the optional Mekanism Extras module. */
public final class MekanismExtrasNatureAltarClient {
    private MekanismExtrasNatureAltarClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MekanismExtrasNatureAltarFactories
                        .NATURE_ALTAR_FACTORY_CONTAINER.get(),
                ExtraNatureAltarFactoryScreen::new);
    }

    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                MekanismExtrasNatureAltarFactories.ABSOLUTE_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.NATURE_ALTAR_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasNatureAltarFactories.SUPREME_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.NATURE_ALTAR_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasNatureAltarFactories.COSMIC_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.NATURE_ALTAR_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasNatureAltarFactories.INFINITE_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.NATURE_ALTAR_FACTORY));
    }
}
