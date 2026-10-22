package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.core.localization.Tooltips;
import appeng.items.storage.StorageCellTooltipComponent;
import appeng.util.ConfigInventory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** ME drive/chest cell with resource-stable Cell Workbench partitioning. */
public final class MagicStorageCellItem extends Item implements ICellWorkbenchItem {
    private static final String TOOLTIP = "tooltip.mekanism_magic.magic_storage_cell.";
    private final long capacityBytes;
    private final double idleDrain;

    public MagicStorageCellItem(Properties properties, int kiloBytes) {
        super(properties.stacksTo(1));
        if (kiloBytes <= 0) {
            throw new IllegalArgumentException("Cell size must be positive");
        }
        capacityBytes = (long) kiloBytes * 1024;
        idleDrain = 0.5 + Math.log(kiloBytes) / Math.log(4) * 0.5;
    }

    public long capacityBytes() {
        return capacityBytes;
    }

    public double idleDrain() {
        return idleDrain;
    }

    public static MagicCellContents contents(ItemStack stack) {
        return stack.getOrDefault(MagicStorageCells.CONTENTS, MagicCellContents.EMPTY);
    }

    static void setContents(ItemStack stack, MagicCellContents contents) {
        if (contents.equals(MagicCellContents.EMPTY)) {
            stack.remove(MagicStorageCells.CONTENTS);
        } else {
            stack.set(MagicStorageCells.CONTENTS, contents);
        }
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return new PartitionInventory(stack).inventory;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        // Magic resources have no secondary components or fuzzy variants.
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        MagicCellContents data = contents(stack);
        MagicStorageCellInventory inventory = new MagicStorageCellInventory(this, stack, null);
        tooltip.add(Component.translatable(TOOLTIP + "supported")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Tooltips.bytesUsed(inventory.usedBytes(), capacityBytes));
        tooltip.add(Tooltips.typesUsed(data.amounts().size(), MagicCellContents.MAX_TYPES));
        tooltip.add(Component.translatable(TOOLTIP + "units_per_byte",
                        NumberFormat.getIntegerInstance().format(MagicResourceKeys.unitsPerByte()))
                .withStyle(ChatFormatting.GRAY));
        if (!data.filter().isEmpty()) {
            tooltip.add(Component.translatable(TOOLTIP + "partitioned", data.filter().size())
                    .withStyle(ChatFormatting.AQUA));
        }
        for (var entry : data.amounts().entrySet()) {
            AEKey key = MagicResourceKeys.key(entry.getKey());
            tooltip.add(key == null
                    ? Component.translatable(TOOLTIP + "unavailable", entry.getKey().toString(),
                                    NumberFormat.getIntegerInstance().format(entry.getValue()))
                            .withStyle(ChatFormatting.GOLD)
                    : Component.translatable(TOOLTIP + "amount", key.getDisplayName(),
                                    key.formatAmount(entry.getValue(), AmountFormat.FULL))
                            .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        List<GenericStack> visible = new ArrayList<>();
        contents(stack).amounts().forEach((id, amount) -> {
            AEKey key = MagicResourceKeys.key(id);
            if (key != null) {
                visible.add(new GenericStack(key, amount));
            }
        });
        if (visible.isEmpty()) {
            return Optional.empty();
        }
        int shown = Math.min(visible.size(), 8);
        return Optional.of(new StorageCellTooltipComponent(List.of(),
                List.copyOf(visible.subList(0, shown)), visible.size() > shown, true));
    }

    private static final class PartitionInventory {
        private final ItemStack stack;
        private final ConfigInventory inventory;
        private boolean ready;

        private PartitionInventory(ItemStack stack) {
            this.stack = stack;
            long dormantFilters = contents(stack).filter().stream()
                    .filter(id -> MagicResourceKeys.key(id) == null).count();
            inventory = ConfigInventory.configTypes(MagicCellContents.MAX_TYPES)
                    .supportedTypes(MagicResourceKeys.keyTypes())
                    .slotFilter((slot, key) -> slot < MagicCellContents.MAX_TYPES - dormantFilters
                            && MagicResourceKeys.resourceId(key) != null)
                    .changeListener(this::save)
                    .build();
            int slot = 0;
            for (ResourceLocation id : contents(stack).filter()) {
                AEKey key = MagicResourceKeys.key(id);
                if (key != null) {
                    inventory.setStack(slot++, new GenericStack(key, 1));
                }
            }
            ready = true;
        }

        private void save() {
            if (!ready) {
                return;
            }
            MagicCellContents previous = contents(stack);
            Set<ResourceLocation> selected = new LinkedHashSet<>();
            // Reserve missing resources before applying the editable slots.
            for (ResourceLocation id : previous.filter()) {
                if (MagicResourceKeys.key(id) == null) {
                    selected.add(id);
                }
            }
            for (int slot = 0; slot < inventory.size(); slot++) {
                AEKey key = inventory.getKey(slot);
                ResourceLocation id = key == null ? null : MagicResourceKeys.resourceId(key);
                if (id != null && selected.size() < MagicCellContents.MAX_TYPES) {
                    selected.add(id);
                }
            }
            setContents(stack, previous.withFilter(List.copyOf(selected)));
        }
    }
}
