package com.example.mekanismmagic.client;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.client.screen.NativeRitualScreen;
import com.example.mekanismmagic.client.screen.NativeMiniRitualAssemblerScreen;
import com.example.mekanismmagic.client.screen.NativeSpiritFactoryScreen;
import com.example.mekanismmagic.client.screen.NativeSpiritScreen;
import com.example.mekanismmagic.client.screen.NativeDimensionMinerScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.common.NeoForge;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = "mekanism_magic", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeMagicClient {
    private static boolean occultismRecipeListenerRegistered;

    private NativeMagicClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        if (ModCompatibility.occultismLoaded()) {
            if (!occultismRecipeListenerRegistered) {
                NeoForge.EVENT_BUS.addListener(
                        NativeMagicClient::onOccultismRecipesUpdated);
                occultismRecipeListenerRegistered = true;
            }
            event.register(NativeMekanismRegistries.SPIRIT_CONTAINER.get(),
                    NativeSpiritScreen::new);
            event.register(NativeMekanismRegistries.DIMENSION_MINER_CONTAINER.get(),
                    NativeDimensionMinerScreen::new);
            event.register(NativeMekanismRegistries.RITUAL_CONTAINER.get(),
                    NativeRitualScreen::new);
            event.register(
                    NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_CONTAINER.get(),
                    NativeMiniRitualAssemblerScreen::new);
            event.register(NativeMekanismRegistries.SPIRIT_FACTORY_CONTAINER.get(),
                    NativeSpiritFactoryScreen::new);
            if (ModCompatibility.mekanismExtrasLoaded()) {
                registerOptionalScreens(event,
                        "mekextras.client.MekanismExtrasSpiritClient");
            }
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            registerOptionalScreens(event,
                    "arsnouveau.client.ArsNouveauClient");
        }
    }

    private static void onOccultismRecipesUpdated(
            RecipesUpdatedEvent event) {
        OccultismRecipeBridge.invalidateRecipeCaches();
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        if (ModCompatibility.occultismLoaded()) {
            event.registerBlockEntityRenderer(
                    NativeMekanismRegistries.SPIRIT_TILE.get(),
                    context -> new MagicMachineAnimationRenderer<>(
                            context,
                            MagicMachineAnimationRenderer.Kind.SPIRIT));
            event.registerBlockEntityRenderer(
                    NativeMekanismRegistries.DIMENSION_MINER_TILE.get(),
                    context -> new MagicMachineAnimationRenderer<>(
                            context,
                            MagicMachineAnimationRenderer.Kind.DIMENSION));
            event.registerBlockEntityRenderer(
                    NativeMekanismRegistries.RITUAL_TILE.get(),
                    context -> new MagicMachineAnimationRenderer<>(
                            context,
                            MagicMachineAnimationRenderer.Kind.RITUAL));
            event.registerBlockEntityRenderer(
                    NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_TILE.get(),
                    context -> new MagicMachineAnimationRenderer<>(
                            context,
                            MagicMachineAnimationRenderer.Kind.SCRIBING));
            registerSpiritFactoryRenderers(event);
            if (ModCompatibility.mekanismExtrasLoaded()) {
                registerOptionalRenderers(event,
                        "mekextras.client.MekanismExtrasSpiritClient");
            }
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            try {
                Class.forName("com.example.mekanismmagic.integration."
                                + "arsnouveau.client.ArsNouveauClient")
                        .getMethod("registerRenderers",
                                EntityRenderersEvent.RegisterRenderers.class)
                        .invoke(null, event);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException(
                        "Failed to register optional Ars renderers",
                        failure);
            }
        }
    }

    private static void registerSpiritFactoryRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SPIRIT_FACTORY));
        event.registerBlockEntityRenderer(
                NativeMekanismRegistries.ADVANCED_SPIRIT_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SPIRIT_FACTORY));
        event.registerBlockEntityRenderer(
                NativeMekanismRegistries.ELITE_SPIRIT_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SPIRIT_FACTORY));
        event.registerBlockEntityRenderer(
                NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_TILE.get(),
                context -> new MagicMachineAnimationRenderer<>(context,
                        MagicMachineAnimationRenderer.Kind.SPIRIT_FACTORY));
    }

    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        if (!ModCompatibility.occultismLoaded()) {
            return;
        }
        event.enqueueWork(() -> ItemProperties.register(
                MekanismMagic.MINI_RITUAL.get(),
                ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "ritual"),
                (stack, level, entity, seed) ->
                        OccultismRecipeBridge.miniRitualModelData(stack)));
    }

    @SubscribeEvent
    public static void invalidateAnimationSprites(
            TextureAtlasStitchedEvent event) {
        if (TextureAtlas.LOCATION_BLOCKS.equals(
                event.getAtlas().location())) {
            MagicMachineAnimationRenderer.invalidateSpriteCache();
            if (ModCompatibility.arsNouveauMachineContentEnabled()) {
                invokeOptionalClientHook(
                        "arsnouveau.client.ArsNouveauClient",
                        "invalidateSpriteCaches");
            }
        }
    }

    private static void invokeOptionalClientHook(String className,
                                                 String methodName) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + className)
                    .getMethod(methodName)
                    .invoke(null);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to invoke optional client hook "
                            + className + "#" + methodName,
                    failure);
        }
    }

    private static void registerOptionalScreens(
            RegisterMenuScreensEvent event, String className) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + className)
                    .getMethod("registerScreens",
                            RegisterMenuScreensEvent.class)
                    .invoke(null, event);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional integration screens",
                    failure);
        }
    }

    private static void registerOptionalRenderers(
            EntityRenderersEvent.RegisterRenderers event,
            String className) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + className)
                    .getMethod("registerRenderers",
                            EntityRenderersEvent.RegisterRenderers.class)
                    .invoke(null, event);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional integration renderers",
                    failure);
        }
    }
}
