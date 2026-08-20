package com.example.mekanismmagic.integration;

import net.neoforged.bus.api.IEventBus;

/**
 * Registration boundary for content owned by one optional integration.
 */
public interface ContentIntegrationModule {
    String modId();

    void register(IEventBus modBus);

    default boolean isLoaded() {
        return ModCompatibility.loaded(modId());
    }
}
