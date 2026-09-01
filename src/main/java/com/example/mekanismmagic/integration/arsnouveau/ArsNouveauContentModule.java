package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.ContentIntegrationModule;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.MekanismMagic;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.lang.reflect.InvocationTargetException;

/**
 * Registers machines and items backed by Ars Nouveau.
 */
public final class ArsNouveauContentModule
        implements ContentIntegrationModule {
    public static final ArsNouveauContentModule INSTANCE =
            new ArsNouveauContentModule();

    private ArsNouveauContentModule() {
    }

    @Override
    public String modId() {
        return ModCompatibility.ARS_NOUVEAU;
    }

    @Override
    public void register(IEventBus modBus) {
        ArsNouveauRegistries.register(modBus);
        NeoForge.EVENT_BUS.addListener(
                SourceLinkToolItem::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ArsDevelopmentCommands::register);
        NeoForge.EVENT_BUS.addListener(
                (ServerStartedEvent event) ->
                        ArsNouveauRecipeScanner.scanAtStartup(
                                event.getServer(), MekanismMagic.LOGGER));
        NeoForge.EVENT_BUS.addListener(
                ArsNouveauContentModule::onDatapackSync);
        try {
            Class.forName("com.example.mekanismmagic.integration.ae2.Ae2ArsCompat")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // AE2 is optional; the Ars machines remain fully functional
            // without its virtual-context crafting provider.
        }
        if (ModCompatibility.mekanismExtrasLoaded()) {
            try {
                Class<?> factoryBridge = Class.forName(
                        "com.example.mekanismmagic.integration.mekextras."
                                + "MekanismExtrasImbuementFactories");
                try {
                    factoryBridge
                        .getMethod("register", IEventBus.class)
                        .invoke(null, modBus);
                } catch (InvocationTargetException failure) {
                    // Once registration has started, continuing after a
                    // failure could leave partially attached registries.
                    throw new IllegalStateException(
                            "Mekanism Extras imbuement factory registration "
                                    + "failed",
                            failure.getCause());
                }
            } catch (ClassNotFoundException missingBridge) {
                // A core-only distribution may intentionally omit this
                // binary-only bridge. The base Ars machines remain usable.
                MekanismMagic.LOGGER.warn(
                        "Mekanism Extras is installed, but this build "
                                + "does not contain its optional imbuement "
                                + "factory bridge; continuing without those "
                                + "factories");
            } catch (NoSuchMethodException | IllegalAccessException
                    | LinkageError failure) {
                MekanismMagic.LOGGER.warn(
                        "Skipping optional Mekanism Extras imbuement "
                                + "factories because their runtime API is "
                                + "incompatible",
                        failure);
            }
        }
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        if (!ArsNouveauRecipeScanner.refresh(
                server.getRecipeManager())) {
            return;
        }
        ArsNouveauRecipeScanner.scanAtStartup(
                server, MekanismMagic.LOGGER);
        try {
            Class.forName("com.example.mekanismmagic.integration.ae2."
                            + "Ae2ArsCompat")
                    .getMethod("refreshPatterns")
                    .invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // AE2 is optional; the dynamic physical and JEI catalogs still
            // refresh when no AE crafting provider is present.
        }
    }
}
