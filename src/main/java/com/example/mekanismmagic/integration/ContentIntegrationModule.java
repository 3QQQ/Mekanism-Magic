package com.example.mekanismmagic.integration;

import net.neoforged.bus.api.IEventBus;

/**
 * Registration boundary for content owned by one optional integration.
 */
public interface ContentIntegrationModule {
    String modId();

    void register(IEventBus modBus);

    /** Whether this module requires the addon's shared items and creative tab. */
    default boolean registersGameContent() {
        return true;
    }

    /** Whether this module needs optional shared plugin items. */
    default boolean registersPluginItems() {
        return true;
    }

    default boolean isLoaded() {
        return ModCompatibility.loaded(modId());
    }
}
