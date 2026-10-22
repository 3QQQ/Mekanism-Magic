package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceCapabilities;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceHost;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import mekanism.api.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** Four typed F&A point views over a native AE interface's existing inventory. */
public final class Ae2EssenceInterfaceStorage implements EssenceResourceHost {
    private final GenericInternalInventory inventory;
    private final int[] insertSlotHints = new int[EssenceResources.CHANNELS];
    private final int[] extractSlotHints = new int[EssenceResources.CHANNELS];

    private Ae2EssenceInterfaceStorage(GenericInternalInventory inventory) {
        this.inventory = inventory;
    }

    static @Nullable EssenceResourceHost create(
            @Nullable GenericInternalInventory inventory) {
        return inventory == null ? null
                : new Ae2EssenceInterfaceStorage(inventory);
    }

    static @Nullable EssenceResourceHost create(Level level, BlockPos pos,
                                                 BlockState state,
                                                 @Nullable BlockEntity tile,
                                                 @Nullable Direction side) {
        EssenceResourceHost direct = create(level.getCapability(
                AECapabilities.GENERIC_INTERNAL_INV,
                pos, state, tile, side));
        if (direct != null || side != null) return direct;
        EssenceResourceHost fallback = null;
        for (Direction candidate : Direction.values()) {
            EssenceResourceHost faced = create(level.getCapability(
                    AECapabilities.GENERIC_INTERNAL_INV,
                    pos, state, tile, candidate));
            if (faced == null) continue;
            if (fallback == null) fallback = faced;
            for (int channel = 0; channel < EssenceResources.CHANNELS;
                 channel++) {
                if (faced.getStoredEssence(channel) > 0
                        || faced.getEssenceCapacity(channel) > 0) {
                    return faced;
                }
            }
        }
        return fallback;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Ae2EssenceInterfaceStorage::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        int registered = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id != null && MekanismMagic.MOD_ID.equals(id.getNamespace())) {
                // The two native machines already expose their actual ledger.
                continue;
            }
            if (!event.isBlockRegistered(
                    AECapabilities.GENERIC_INTERNAL_INV, block)) continue;
            event.registerBlock(EssenceResourceCapabilities.ESSENCE,
                    Ae2EssenceInterfaceStorage::create, block);
            registered++;
        }
        if (registered > 0) {
            MekanismMagic.LOGGER.info(
                    "Registered {} AE interface Essence capability adapters",
                    registered);
        }
    }

    @Override
    public int getStoredEssence(int channel) {
        return transfer(channel, Integer.MAX_VALUE,
                Actionable.SIMULATE, false);
    }

    @Override
    public int getEssenceCapacity(int channel) {
        AEKey key = MagicResourceKeys.essence(channel);
        if (key == null) return 0;
        long capacity = 0L;
        for (int slot = 0; slot < inventory.size(); slot++) {
            AEKey existing = inventory.getKey(slot);
            if ((existing == null || key.equals(existing))
                    && inventory.isAllowedIn(slot, key)) {
                long size = Math.max(0L, inventory.getMaxAmount(key));
                capacity += Math.min(Integer.MAX_VALUE - capacity, size);
                if (capacity == Integer.MAX_VALUE) break;
            }
        }
        return (int) capacity;
    }

    @Override
    public int insertEssence(int channel, int requested, Action action) {
        return transfer(channel, requested, mode(action), true);
    }

    @Override
    public int extractEssence(int channel, int requested, Action action) {
        return transfer(channel, requested, mode(action), false);
    }

    @Override
    public boolean canInputEssence(int channel) {
        return EssenceResources.validChannel(channel);
    }

    @Override
    public boolean canOutputEssence(int channel) {
        return EssenceResources.validChannel(channel);
    }

    private static @Nullable Actionable mode(@Nullable Action action) {
        return action == null ? null
                : action.execute() ? Actionable.MODULATE
                : Actionable.SIMULATE;
    }

    private int transfer(int channel, int requested,
                         @Nullable Actionable mode, boolean insert) {
        AEKey key = MagicResourceKeys.essence(channel);
        if (key == null || mode == null || requested <= 0
                || inventory.size() <= 0) return 0;
        if (mode == Actionable.MODULATE) {
            inventory.beginBatch();
            try {
                return transferAcrossSlots(channel, key, requested,
                        mode, insert);
            } finally {
                inventory.endBatch();
            }
        }
        return transferAcrossSlots(channel, key, requested, mode, insert);
    }

    private int transferAcrossSlots(int channel, AEKey key, int requested,
                                    Actionable mode, boolean insert) {
        int moved = 0;
        int count = inventory.size();
        int[] hints = insert ? insertSlotHints : extractSlotHints;
        int start = Math.floorMod(hints[channel], count);
        int first = -1;
        int last = -1;
        for (int offset = 0; offset < count && moved < requested;
             offset++) {
            int slot = (start + offset) % count;
            long amount = insert
                    ? inventory.insert(slot, key, requested - moved, mode)
                    : inventory.extract(slot, key, requested - moved, mode);
            int accepted = (int) Math.max(0L,
                    Math.min(requested - moved, amount));
            if (accepted <= 0) continue;
            if (first < 0) first = slot;
            last = slot;
            moved += accepted;
        }
        if (first >= 0) hints[channel] = mode == Actionable.SIMULATE
                ? first : last;
        return moved;
    }
}
