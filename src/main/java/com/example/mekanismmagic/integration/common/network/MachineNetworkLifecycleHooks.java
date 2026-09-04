package com.example.mekanismmagic.integration.common.network;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dependency-free lifecycle bridge for optional machine network nodes.
 * Implementations live in optional source sets, so the common machine layer
 * never links against a specific storage-network API.
 */
public final class MachineNetworkLifecycleHooks {
    private static final List<Handler> HANDLERS =
            new CopyOnWriteArrayList<>();

    private MachineNetworkLifecycleHooks() {
    }

    public static void register(Handler handler) {
        if (handler == null) {
            return;
        }
        for (Handler existing : HANDLERS) {
            if (existing.getClass() == handler.getClass()) {
                return;
            }
        }
        HANDLERS.add(handler);
    }

    public static void onLoad(NativeMagicMachineBlockEntity tile) {
        HANDLERS.forEach(handler -> handler.onLoad(tile));
    }

    public static void onRemoved(NativeMagicMachineBlockEntity tile) {
        HANDLERS.forEach(handler -> handler.onRemoved(tile));
    }

    public static void onRevived(NativeMagicMachineBlockEntity tile) {
        HANDLERS.forEach(handler -> handler.onRevived(tile));
    }

    public static void onChunkUnloaded(NativeMagicMachineBlockEntity tile) {
        HANDLERS.forEach(handler -> handler.onChunkUnloaded(tile));
    }

    public static void onBlockRemoved(NativeMagicMachineBlockEntity tile) {
        HANDLERS.forEach(handler -> handler.onBlockRemoved(tile));
    }

    public static void save(NativeMagicMachineBlockEntity tile,
                            CompoundTag tag,
                            HolderLookup.Provider registries) {
        HANDLERS.forEach(handler -> handler.save(tile, tag, registries));
    }

    public static void load(NativeMagicMachineBlockEntity tile,
                            CompoundTag tag,
                            HolderLookup.Provider registries) {
        HANDLERS.forEach(handler -> handler.load(tile, tag, registries));
    }

    public interface Handler {
        default void onLoad(NativeMagicMachineBlockEntity tile) {
        }

        default void onRemoved(NativeMagicMachineBlockEntity tile) {
        }

        default void onRevived(NativeMagicMachineBlockEntity tile) {
        }

        default void onChunkUnloaded(NativeMagicMachineBlockEntity tile) {
        }

        default void onBlockRemoved(NativeMagicMachineBlockEntity tile) {
        }

        default void save(NativeMagicMachineBlockEntity tile,
                          CompoundTag tag,
                          HolderLookup.Provider registries) {
        }

        default void load(NativeMagicMachineBlockEntity tile,
                          CompoundTag tag,
                          HolderLookup.Provider registries) {
        }
    }
}
