package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ae2.Ae2MagicInterfaceCompat;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** AE-only entry point. Never reached or linked from dependency-minimal builds. */
public final class MagicStorageCells {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekanismMagic.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MekanismMagic.MOD_ID);
    private static final DeferredRegister<AEKeyType> KEY_TYPES =
            DeferredRegister.create(AEKeyType.REGISTRY_KEY, MekanismMagic.MOD_ID);
    public static final DataComponentType<MagicCellContents> CONTENTS = DataComponentType.<MagicCellContents>builder()
            .persistent(MagicCellContents.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(MagicCellContents.CODEC))
            .cacheEncoding().build();
    public static final Map<Integer, DeferredHolder<Item, MagicStorageCellItem>> CELLS = new LinkedHashMap<>();
    public static final DeferredHolder<Item, MagicResourceMarkerItem> MARKER = ITEMS.register(
            "magic_resource_marker", () -> new MagicResourceMarkerItem(new Item.Properties().stacksTo(1)));
    private static boolean registered;

    static {
        COMPONENTS.register("magic_cell_contents", () -> CONTENTS);
        KEY_TYPES.register("magic", () -> MagicResourceKeys.TYPE);
        for (int size : new int[]{1, 4, 16, 64, 256}) {
            CELLS.put(size, ITEMS.register("magic_storage_cell_" + size + "k",
                    () -> new MagicStorageCellItem(new Item.Properties().stacksTo(1), size)));
        }
    }

    private MagicStorageCells() { }

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        COMPONENTS.register(modBus);
        KEY_TYPES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            StorageCells.addCellHandler(new ICellHandler() {
                @Override public boolean isCell(ItemStack stack) { return stack.getItem() instanceof MagicStorageCellItem; }
                @Override public StorageCell getCellInventory(ItemStack stack, ISaveProvider saveProvider) {
                    return stack.getItem() instanceof MagicStorageCellItem item
                            ? new MagicStorageCellInventory(item, stack, saveProvider) : null;
                }
            });
            GenericSlotCapacities.register(MagicResourceKeys.TYPE, 256_000L);
        }));
        Ae2MagicInterfaceCompat.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) MagicStorageCellClient.register(modBus);
    }
}
