package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ContentIntegrationModule;
import com.example.mekanismmagic.integration.ModCompatibility;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Optional Nature's Aura 41.9 integration entry point. */
public final class NaturesAuraContentModule
        implements ContentIntegrationModule {
    public static final NaturesAuraContentModule INSTANCE =
            new NaturesAuraContentModule();

    private NaturesAuraContentModule() {
    }

    @Override
    public String modId() {
        return ModCompatibility.NATURES_AURA;
    }

    @Override
    public void register(IEventBus modBus) {
        MagicPipeAuraIntegration.register();
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            for (var type : com.example.mekanismmagic.integration.common.network
                    .MagicPipeRegistries.allTiles()) {
                event.registerBlockEntity(
                        de.ellpeck.naturesaura.api.NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                        type.get(), (tile, side) -> MagicPipeAuraIntegration.capability(tile.getTransmitter(), side));
            }
        });
        NaturesAuraRegistries.register(modBus);
        registerOptionalExtrasFactories(modBus);
        NeoForge.EVENT_BUS.addListener(
                (ServerStartedEvent event) ->
                        logCatalogs(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                NaturesAuraContentModule::onDatapackSync);
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        // A player login also emits this event, but cannot change recipes.
        // Rebuild only for the global synchronization that follows /reload.
        if (event.getPlayer() != null) {
            return;
        }
        var server = event.getPlayerList().getServer();
        NatureAuraRecipeScanner.refresh(server.getRecipeManager());
        logCatalogs(server);
        refreshOptionalPatternCaches(
                "com.example.mekanismmagic.integration.ae2."
                        + "Ae2NaturePatternCacheRefresher");
        refreshOptionalPatternCaches(
                "com.example.mekanismmagic.integration.mekenergistics."
                        + "MekEnergisticsNaturePatternCacheRefresher");
    }

    private static void registerOptionalExtrasFactories(IEventBus modBus) {
        if (!ModCompatibility.mekanismExtrasLoaded()) {
            return;
        }
        try {
            Class.forName("com.example.mekanismmagic.integration.mekextras."
                            + "MekanismExtrasNatureAltarFactories")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            // Registration may already have attached deferred registers.
            throw new IllegalStateException(
                    "Mekanism Extras natural altar factory registration failed",
                    failure.getCause());
        } catch (ClassNotFoundException missingBridge) {
            MekanismMagic.LOGGER.warn(
                    "Mekanism Extras is present but this build omits its optional natural altar factories");
        } catch (ReflectiveOperationException | LinkageError failure) {
            MekanismMagic.LOGGER.warn(
                    "Skipping incompatible optional Mekanism Extras natural altar factories",
                    failure);
        }
    }

    private static void refreshOptionalPatternCaches(String className) {
        try {
            Class.forName(className).getMethod("refreshAll").invoke(null);
        } catch (ClassNotFoundException | LinkageError ignored) {
            // AE2 and Mek Energistics are independent optional integrations.
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "Failed to refresh Nature altar pattern caches via "
                            + className, failure);
        }
    }

    private static void logCatalogs(net.minecraft.server.MinecraftServer server) {
        NatureAuraRecipeScanner.logCatalog(server, MekanismMagic.LOGGER);
        MekanismMagic.LOGGER.info("Nature's Aura additional recipes: {} tree rituals, {} animal spawns",
                TreeRitualRecipeBridge.scan(server.getRecipeManager()).size(),
                AnimalSpawnerRecipeBridge.scan(server.getRecipeManager()).size());
    }
}
