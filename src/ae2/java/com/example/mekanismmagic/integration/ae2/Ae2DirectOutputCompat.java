package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.common.network
        .MachineDirectOutputHooks;
import com.example.mekanismmagic.integration.common.network
        .MagicSourceExternalEndpointHooks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Registers native AE nodes without requiring Mek Energistics upgrades. */
public final class Ae2DirectOutputCompat {
    private static boolean registered;

    private Ae2DirectOutputCompat() {
    }

    public static synchronized void register(IEventBus modBus) {
        if (registered) {
            return;
        }
        registered = true;
        MachineDirectOutputHooks.register(Ae2DirectOutputProvider.HOOK);
        if (ModCompatibility.loaded(
                ModCompatibility.ARS_ENERGISTIQUE)) {
            MagicSourceExternalEndpointHooks.register(
                    (level, pos, side) -> level.getCapability(
                            AECapabilities.GENERIC_INTERNAL_INV,
                            pos, side) != null);
        }
        modBus.addListener(Ae2DirectOutputCompat::registerCapabilities);
    }

    private static void registerCapabilities(
            RegisterCapabilitiesEvent event) {
        if (ModCompatibility.occultismLoaded()) {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    NativeMekanismRegistries.DIMENSION_MINER_TILE.get(),
                    (tile, side) -> Ae2DirectOutputProvider.forTile(tile));
        }
    }
}
