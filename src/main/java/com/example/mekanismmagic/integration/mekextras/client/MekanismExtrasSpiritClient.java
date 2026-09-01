package com.example.mekanismmagic.integration.mekextras.client;

import com.example.mekanismmagic.client.MagicMachineAnimationRenderer;
import com.example.mekanismmagic.integration.mekextras
        .MekanismExtrasSpiritFactories;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Registers the themed screens for extended Occultism spirit factories. */
public final class MekanismExtrasSpiritClient {
    private MekanismExtrasSpiritClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MekanismExtrasSpiritFactories
                        .SPIRIT_FACTORY_CONTAINER.get(),
                ExtraSpiritFactoryScreen::new);
    }

    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                MekanismExtrasSpiritFactories.ABSOLUTE_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SPIRIT_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasSpiritFactories.SUPREME_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SPIRIT_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasSpiritFactories.COSMIC_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SPIRIT_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasSpiritFactories.INFINITE_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SPIRIT_FACTORY));
    }
}
