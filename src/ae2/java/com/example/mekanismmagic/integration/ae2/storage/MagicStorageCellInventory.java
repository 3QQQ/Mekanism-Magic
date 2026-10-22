package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A shared byte pool for canonical magic keys, without item or fluid storage. */
public final class MagicStorageCellInventory implements StorageCell {
    private final MagicStorageCellItem item;
    private final ItemStack stack;
    @Nullable
    private final ISaveProvider container;
    private final Map<ResourceLocation, Long> amounts;
    private boolean dirty;

    public MagicStorageCellInventory(MagicStorageCellItem item, ItemStack stack,
                                     @Nullable ISaveProvider container) {
        if (stack.isEmpty() || stack.getItem() != item) {
            throw new IllegalArgumentException("The cell item does not match its stack");
        }
        this.item = item;
        this.stack = stack;
        this.container = container;
        this.amounts = new LinkedHashMap<>(MagicStorageCellItem.contents(stack).amounts());
    }

    public long capacityBytes() {
        return item.capacityBytes();
    }

    public long usedBytes() {
        long result = 0;
        int units = MagicResourceKeys.unitsPerByte();
        for (long amount : amounts.values()) {
            long bytes = (amount - 1) / units + 1;
            if (bytes > Long.MAX_VALUE - result) {
                return Long.MAX_VALUE;
            }
            result += bytes;
        }
        return result;
    }

    public long storedAmount(ResourceLocation id) {
        return amounts.getOrDefault(id, 0L);
    }

    public int storedTypes() {
        return amounts.size();
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode,
                       IActionSource source) {
        Objects.requireNonNull(mode, "mode");
        if (amount <= 0 || key == null) {
            return 0;
        }
        ResourceLocation id = MagicResourceKeys.resourceId(key);
        if (id == null || !matchesFilter(id)) {
            return 0;
        }
        long stored = storedAmount(id);
        if (stored == 0 && amounts.size() >= MagicCellContents.MAX_TYPES) {
            return 0;
        }
        long used = usedBytes();
        if (used > capacityBytes()) {
            return 0;
        }
        int units = MagicResourceKeys.unitsPerByte();
        long tailSpace = stored == 0 ? 0 : (units - stored % units) % units;
        long space = (capacityBytes() - used) * units + tailSpace;
        long inserted = Math.min(amount, Math.min(space, Long.MAX_VALUE - stored));
        if (inserted > 0 && mode == Actionable.MODULATE) {
            amounts.put(id, stored + inserted);
            saveChanges();
        }
        return inserted;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode,
                        IActionSource source) {
        Objects.requireNonNull(mode, "mode");
        if (amount <= 0 || key == null) {
            return 0;
        }
        ResourceLocation id = MagicResourceKeys.resourceId(key);
        if (id == null) {
            return 0;
        }
        long stored = storedAmount(id);
        long extracted = Math.min(amount, stored);
        if (extracted > 0 && mode == Actionable.MODULATE) {
            if (extracted == stored) {
                amounts.remove(id);
            } else {
                amounts.put(id, stored - extracted);
            }
            saveChanges();
        }
        return extracted;
    }

    private boolean matchesFilter(ResourceLocation id) {
        List<ResourceLocation> filter = MagicStorageCellItem.contents(stack).filter();
        return filter.isEmpty() || filter.contains(id);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        ResourceLocation id = key == null ? null : MagicResourceKeys.resourceId(key);
        return id != null && matchesFilter(id)
                && (amounts.containsKey(id)
                || !MagicStorageCellItem.contents(stack).filter().isEmpty());
    }

    @Override
    public void getAvailableStacks(KeyCounter output) {
        amounts.forEach((id, amount) -> {
            AEKey key = MagicResourceKeys.key(id);
            if (key != null) {
                output.add(key, amount);
            }
        });
    }

    @Override
    public CellState getStatus() {
        if (amounts.isEmpty()) {
            return CellState.EMPTY;
        }
        long used = usedBytes();
        if (used >= capacityBytes()) {
            if (used == capacityBytes()) {
                int units = MagicResourceKeys.unitsPerByte();
                for (var entry : amounts.entrySet()) {
                    if (entry.getValue() % units != 0
                            && matchesFilter(entry.getKey())
                            && MagicResourceKeys.key(entry.getKey()) != null) {
                        return CellState.TYPES_FULL;
                    }
                }
            }
            return CellState.FULL;
        }
        return amounts.size() >= MagicCellContents.MAX_TYPES
                ? CellState.TYPES_FULL : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return item.idleDrain();
    }

    @Override
    public boolean canFitInsideCell() {
        // Dormant resources still occupy the physical cell.
        return amounts.isEmpty();
    }

    private void saveChanges() {
        dirty = true;
        if (container != null) {
            container.saveChanges();
        } else {
            persist();
        }
    }

    @Override
    public void persist() {
        if (dirty) {
            MagicCellContents previous = MagicStorageCellItem.contents(stack);
            MagicStorageCellItem.setContents(stack, previous.withAmounts(amounts));
            dirty = false;
        }
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }
}
