package com.example.mekanismmagic.integration.ae2;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import appeng.api.AECapabilities;

public final class Ae2ArsCompat {
    private Ae2ArsCompat() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Ae2ArsCompat::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_TILE.get(),
                (tile, side) -> Ae2ImbuementProvider.forTile(tile));
    }
}
