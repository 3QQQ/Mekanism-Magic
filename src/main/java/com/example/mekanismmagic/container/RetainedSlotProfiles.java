package com.example.mekanismmagic.container;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
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
            case "nature_altar_processor" -> List.of(
                    spec(17, "catalyst"), spec(38, "catalyst"));
            case "offering_processor" -> List.of(spec(20, "wither_roses"));
            case "clibano_processor" -> List.of(spec(11, "enhancer"));
            case "hephaestus_forge_processor" -> List.of(
                    spec(24, "enhancer"), spec(25, "enhancer"),
                    spec(26, "enhancer"), spec(27, "enhancer"));
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
        if (tile instanceof IMekanismMagicAutomation automation) {
            String role = id.getPath().endsWith("_nature_altar_factory") ? "catalyst" : null;
            // The first two are the stable physical work-screen slots.
            // Paged overflow entries are storage views, not menu slots;
            // their presentation belongs to Nature's catalyst popup.
            if (role != null) return automation.mekanismMagicPersistentInputs().stream()
                    .limit(2)
                    .map(slot -> new Entry(slot, "gui.mekanism_magic.retained." + role)).toList();
        }
        return List.of();
    }
}
