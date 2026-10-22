package com.example.mekanismmagic.integration.jade;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceHost;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.factory.TileEntityFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Read-only, bounded public status; never serializes machine inventories or save data. */
public final class MachineJadeData {
    public static final String KEY = "mekanism_magic_machine";

    private MachineJadeData() {
    }

    public static CompoundTag capture(BlockEntity tile) {
        CompoundTag data = new CompoundTag();
        if (!(tile instanceof IMekanismMagicAutomation)
                || !(tile instanceof TileEntityMekanism machine)
                || tile.isRemoved()) {
            return data;
        }
        data.putBoolean("active", machine.getActive());
        if (tile instanceof NativeMagicMachineBlockEntity ordinary) {
            data.putInt("progress", percent(ordinary.getProgress(),
                    ordinary.getProgressRequired()));
        } else if (tile instanceof TileEntityFactory<?> factory) {
            appendFactoryProgress(data, factory.progress, factory.getTicksRequired());
        } else if (ModCompatibility.mekanismExtrasLoaded()) {
            ExtrasReadout.append(data, tile);
        }
        if (ModCompatibility.arsNouveauLoaded()) {
            SourceReadout.append(data, tile);
        }
        if (ModCompatibility.naturesAuraLoaded()) {
            AuraReadout.append(data, tile);
        }
        if (tile instanceof EssenceResourceHost essences) {
            for (int channel = 0; channel < EssenceResources.CHANNELS; channel++) {
                data.putInt("essence" + channel, Math.max(0, essences.getStoredEssence(channel)));
                data.putInt("essenceCapacity" + channel, Math.max(0, essences.getEssenceCapacity(channel)));
            }
        }
        var network = MachineAeResourceHooks.snapshot(tile);
        if (network.supported()) {
            data.putBoolean("ae", true);
            data.putBoolean("connected", network.connected());
            data.putBoolean("online", network.active());
            data.putBoolean("supply", network.enabled());
        }
        return data;
    }

    private static int percent(int progress, int required) {
        return (int) Math.min(100L, Math.max(0L, progress)
                * 100L / Math.max(1, required));
    }

    private static void appendFactoryProgress(CompoundTag data,
                                               int[] progress, int required) {
        int working = 0;
        int furthest = 0;
        for (int ticks : progress) {
            if (ticks > 0) working++;
            furthest = Math.max(furthest, percent(ticks, required));
        }
        data.putInt("progress", furthest);
        data.putInt("workingLanes", working);
        data.putInt("lanes", progress.length);
    }

    private static final class ExtrasReadout {
        private static void append(CompoundTag data, BlockEntity tile) {
            // Extras is a local compile-only integration, not a public core
            // build dependency. Read its stable public progress surface
            // without linking the Jade plugin to that optional binary.
            try {
                Object progress = tile.getClass().getField("progress").get(tile);
                Object required = tile.getClass().getMethod("getTicksRequired").invoke(tile);
                if (progress instanceof int[] lanes && required instanceof Integer ticks) {
                    appendFactoryProgress(data, lanes, ticks);
                }
            } catch (ReflectiveOperationException ignored) {
                // Non-factory machines still retain their active/idle readout.
            }
        }
    }

    // Keep optional API types outside the common snapshot's verification/JIT boundary.
    private static final class SourceReadout {
        private static void append(CompoundTag data, BlockEntity tile) {
            if (tile instanceof com.hollingsworth.arsnouveau.api.source.ISourceTile source) {
                data.putInt("source", Math.max(0, source.getSource()));
                data.putInt("sourceCapacity", Math.max(0, source.getMaxSource()));
            }
        }
    }

    private static final class AuraReadout {
        private static void append(CompoundTag data, BlockEntity tile) {
            if (tile instanceof com.example.mekanismmagic.integration.naturesaura.AuraSideModeHost aura) {
                data.putInt("aura", Math.max(0, aura.getStoredAura()));
                data.putInt("auraCapacity", Math.max(0, aura.getAuraCapacity()));
                data.putBoolean("creativeAura", aura.isCreativeAuraActive());
            }
        }
    }
}
