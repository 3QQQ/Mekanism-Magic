package com.example.mekanismmagic.integration.mekextras.client;

import com.example.mekanismmagic.client.MagicMachineAnimationRenderer;
import com.example.mekanismmagic.integration.mekextras
        .MekanismExtrasImbuementFactories;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client registration isolated behind the optional Mekanism Extras module. */
public final class MekanismExtrasImbuementClient {
    private MekanismExtrasImbuementClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MekanismExtrasImbuementFactories
                        .IMBUEMENT_FACTORY_CONTAINER.get(),
                ExtraImbuementFactoryScreen::new);
    }

    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                MekanismExtrasImbuementFactories.ABSOLUTE_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasImbuementFactories.SUPREME_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasImbuementFactories.COSMIC_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT_FACTORY));
        event.registerBlockEntityRenderer(
                MekanismExtrasImbuementFactories.INFINITE_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.IMBUEMENT_FACTORY));
    }
}
