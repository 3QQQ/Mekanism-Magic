package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ContentIntegrationModule;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Exact-version content, data and captured-container integration. */
public final class ForbiddenArcanusContentModule implements ContentIntegrationModule {
    public static final ForbiddenArcanusContentModule INSTANCE = new ForbiddenArcanusContentModule();
    private boolean registered;

    private ForbiddenArcanusContentModule() {
    }

    @Override
    public String modId() {
        return ModCompatibility.FORBIDDEN_ARCANUS;
    }

    @Override
    public boolean isLoaded() {
        return ModCompatibility.forbiddenArcanusIntegrationEnabled();
    }

    @Override
    public boolean registersGameContent() {
        return true;
    }

    @Override
    public boolean registersPluginItems() {
        return true;
    }

    @Override
    public synchronized void register(IEventBus modBus) {
        if (registered || !isLoaded()) {
            return;
        }
        EntityContainerRegistry.register(ForbiddenArcanusEntityContainerAdapter.INSTANCE);
        ForbiddenArcanusRegistries.register(modBus);
        com.example.mekanismmagic.integration.forbiddenarcanus.pipe.MagicPipeEssenceIntegration.register();
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(
                    com.example.mekanismmagic.integration.common.resource.EssenceResourceCapabilities.ESSENCE,
                    ForbiddenArcanusRegistries.ESSENCE_CONDENSER_TILE.get(), (tile, side) -> tile);
            event.registerBlockEntity(
                    com.example.mekanismmagic.integration.common.resource.EssenceResourceCapabilities.ESSENCE,
                    ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_TILE.get(), (tile, side) -> tile);
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> refresh(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ForbiddenArcanusContentModule::onDatapackSync);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ForbiddenArcanusRecipeCatalog.clear());
        registered = true;
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            refresh(event.getPlayerList().getServer());
        }
    }

    private static void refresh(MinecraftServer server) {
        var snapshot = ForbiddenArcanusRecipeCatalog.refresh(server);
        MekanismMagic.LOGGER.info("Forbidden & Arcanus 2.6.1 catalog revision {}: {}",
                snapshot.revision(), snapshot.counts());
    }
}
