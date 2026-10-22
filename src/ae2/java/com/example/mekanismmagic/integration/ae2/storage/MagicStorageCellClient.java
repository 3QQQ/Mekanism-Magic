package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.client.AEKeyRendering;
import appeng.api.client.StorageCellModels;
import appeng.items.storage.BasicStorageCell;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

final class MagicStorageCellClient {
    private MagicStorageCellClient() { }
    static void register(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            AEKeyRendering.register(MagicResourceKeys.TYPE, MagicResourceKey.class, new MagicResourceKeyRenderHandler());
            MagicStorageCells.CELLS.forEach((size, cell) -> StorageCellModels.registerModel(cell.get(),
                    ResourceLocation.parse("mekanism_magic:block/drive/magic_storage_cell_" + size + "k")));
        }));
        modBus.addListener((RegisterColorHandlersEvent.Item event) -> {
            for (var cell : MagicStorageCells.CELLS.values()) {
                event.register((stack, tint) -> 0xFF000000
                        | (tint == 0 ? 0xC6B9DF : BasicStorageCell.getColor(stack, tint)), cell.get());
            }
        });
    }
}
