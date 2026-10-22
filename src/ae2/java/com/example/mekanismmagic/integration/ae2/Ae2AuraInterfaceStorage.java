package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** A view of the local Aura type in an AE interface, with no duplicate tank. */
public final class Ae2AuraInterfaceStorage implements IAuraContainer {
    private final GenericInternalInventory inventory;
    private final IAuraType auraType;
    private final AEKey key;
    private int insertSlotHint;
    private int extractSlotHint;

    private Ae2AuraInterfaceStorage(GenericInternalInventory inventory,
                                    IAuraType auraType, AEKey key) {
        this.inventory = inventory;
        this.auraType = auraType;
        this.key = key;
    }

    /** Invoked only after the common AE entry point confirms Nature is loaded. */
    public static void register(IEventBus modBus) {
        modBus.addListener(Ae2AuraInterfaceStorage::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        int registered = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            // Include interface add-ons and cable bus face parts, just like
            // the existing Source capability adapter.
            if (!event.isBlockRegistered(
                    AECapabilities.GENERIC_INTERNAL_INV, block)) {
                continue;
            }
            event.registerBlock(
                    NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                    Ae2AuraInterfaceStorage::create, block);
            registered++;
        }
        if (registered > 0) {
            MekanismMagic.LOGGER.info(
                    "Registered {} AE Aura interface capability adapters",
                    registered);
        }
    }

    static @Nullable IAuraContainer create(
            @Nullable GenericInternalInventory inventory,
            @Nullable IAuraType auraType) {
        if (inventory == null || auraType == null) {
            return null;
        }
        AEKey key = MagicResourceKeys.key(auraType.getName());
        return key == null ? null
                : new Ae2AuraInterfaceStorage(inventory, auraType, key);
    }

    static @Nullable IAuraContainer create(
            Level level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction side) {
        IAuraType auraType = IAuraType.forLevel(level);
        IAuraContainer direct = create(level.getCapability(
                AECapabilities.GENERIC_INTERNAL_INV,
                pos, state, blockEntity, side), auraType);
        if (direct != null || side != null) {
            return direct;
        }
        // A null cable-bus query reaches only the center cable. Unsided
        // callers can still address a viable face interface in stable order.
        IAuraContainer fallback = null;
        for (Direction candidate : Direction.values()) {
            IAuraContainer faced = create(level.getCapability(
                    AECapabilities.GENERIC_INTERNAL_INV,
                    pos, state, blockEntity, candidate), auraType);
            if (faced == null) {
                continue;
            }
            if (fallback == null) {
                fallback = faced;
            }
            if (faced.drainAura(1, true) > 0
                    || faced.storeAura(1, true) > 0) {
                return faced;
            }
        }
        return fallback;
    }

    @Override
    public int storeAura(int amount, boolean simulate) {
        return transfer(Math.max(0, amount), Actionable.ofSimulate(simulate),
                true);
    }

    @Override
    public int drainAura(int amount, boolean simulate) {
        return transfer(Math.max(0, amount), Actionable.ofSimulate(simulate),
                false);
    }

    @Override
    public int getStoredAura() {
        return drainAura(Integer.MAX_VALUE, true);
    }

    @Override
    public int getMaxAura() {
        long capacity = 0L;
        for (int slot = 0; slot < inventory.size(); slot++) {
            AEKey existing = inventory.getKey(slot);
            if ((existing == null || key.equals(existing))
                    && inventory.isAllowedIn(slot, key)) {
                long slotCapacity = Math.max(0L, inventory.getMaxAmount(key));
                // IAuraContainer exposes ints even when an AE add-on uses
                // long slot capacities. Stop before either sum can overflow.
                capacity += Math.min(Integer.MAX_VALUE - capacity,
                        slotCapacity);
                if (capacity == Integer.MAX_VALUE) {
                    break;
                }
            }
        }
        return (int) capacity;
    }

    @Override
    public int getAuraColor() {
        return auraType.getColor();
    }

    @Override
    public boolean isAcceptableType(IAuraType candidate) {
        return candidate != null && candidate.isSimilar(auraType);
    }

    private int transfer(int amount, Actionable mode, boolean insert) {
        if (amount <= 0 || inventory.size() <= 0) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            inventory.beginBatch();
            try {
                return transferAcrossSlots(amount, mode, insert);
            } finally {
                // Every multi-slot operation notifies AE exactly once.
                inventory.endBatch();
            }
        }
        return transferAcrossSlots(amount, mode, insert);
    }

    private int transferAcrossSlots(int amount, Actionable mode,
                                     boolean insert) {
        int moved = 0;
        int slots = inventory.size();
        int start = Math.floorMod(insert ? insertSlotHint : extractSlotHint,
                slots);
        int firstAcceptedSlot = -1;
        int lastAcceptedSlot = -1;
        for (int offset = 0; offset < slots && moved < amount; offset++) {
            int slot = (start + offset) % slots;
            long transferred = insert
                    ? inventory.insert(slot, key, amount - moved, mode)
                    : inventory.extract(slot, key, amount - moved, mode);
            int accepted = (int) Math.max(0L,
                    Math.min(amount - moved, transferred));
            if (accepted > 0) {
                if (firstAcceptedSlot < 0) {
                    firstAcceptedSlot = slot;
                }
                lastAcceptedSlot = slot;
                moved += accepted;
            }
        }
        if (firstAcceptedSlot >= 0) {
            // Execution starts at the first viable slot found by simulation;
            // subsequent operations can resume at the last accepting slot.
            int next = mode == Actionable.SIMULATE
                    ? firstAcceptedSlot : lastAcceptedSlot;
            if (insert) {
                insertSlotHint = next;
            } else {
                extractSlotHint = next;
            }
        }
        return moved;
    }
}
