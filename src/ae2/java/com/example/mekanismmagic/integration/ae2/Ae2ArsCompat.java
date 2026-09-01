package com.example.mekanismmagic.integration.ae2;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import appeng.api.AECapabilities;

public final class Ae2ArsCompat {
    private Ae2ArsCompat() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Ae2ArsCompat::registerCapabilities);
    }

    public static void refreshPatterns() {
        Ae2ImbuementProvider.refreshAllPatterns();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_TILE.get(),
                (tile, side) -> Ae2ImbuementProvider.forTile(tile));
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ArsNouveauRegistries.DRYGMY_SIMULATOR_TILE.get(),
                (tile, side) -> Ae2DirectOutputProvider.forTile(tile));
        registerMissingSourceInterfaceAdapters(event);
    }

    private static void registerMissingSourceInterfaceAdapters(
            RegisterCapabilitiesEvent event) {
        if (!ModCompatibility.loaded(
                ModCompatibility.ARS_ENERGISTIQUE)) {
            return;
        }
        int registered = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            // Ars Energistique performs the same registry-wide scan, but
            // add-ons can register their generic inventories later in mod
            // order. Complete only the registrations that were missed. This
            // intentionally is not restricted to known interface IDs so new
            // AE interface add-ons inherit the fix automatically.
            if (!event.isBlockRegistered(
                    AECapabilities.GENERIC_INTERNAL_INV, block)) {
                continue;
            }
            event.registerBlock(
                    CapabilityRegistry.SOURCE_CAPABILITY,
                    (level, pos, state, blockEntity, side) ->
                            Ae2SourceInterfaceStorage.create(
                                    level, pos, state, blockEntity, side),
                    block);
            registered++;
        }
        if (registered > 0) {
            MekanismMagic.LOGGER.info(
                    "Registered {} late AE Source interface "
                            + "capability adapters",
                    registered);
        }
    }
}
