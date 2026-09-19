package com.example.mekanismmagic.container;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

/** Explicit optional sidebar roles. Miner, Drygmy samples and resident spirits remain visible in their main work areas. */
public final class RetainedSlotProfiles {
    private RetainedSlotProfiles() { }
    public record Spec(int logicalSlot, String tooltipKey) { }
    public record Entry(IInventorySlot inventory, String tooltipKey) { }

    public static List<Spec> specs(String id) {
        return switch (id) {
            case "ritual_engine" -> List.of(spec(18, "ritual"));
            default -> List.of();
        };
    }

    private static Spec spec(int index, String name) { return new Spec(index, "gui.mekanism_magic.retained." + name); }

    public static List<Entry> entries(TileEntityMekanism tile) {
        var id = BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock());
        if (id == null || !id.getNamespace().equals("mekanism_magic")) return List.of();
        if (tile instanceof NativeMagicMachineBlockEntity nativeTile) {
            List<Entry> result = new ArrayList<>();
            for (Spec spec : specs(id.getPath())) {
                IInventorySlot inventory = nativeTile.getLogicalSlot(spec.logicalSlot());
                if (inventory == null) throw new IllegalStateException("Missing retained slot " + id + "/" + spec.logicalSlot());
                result.add(new Entry(inventory, spec.tooltipKey()));
            }
            return List.copyOf(result);
        }
        return List.of();
    }
}
