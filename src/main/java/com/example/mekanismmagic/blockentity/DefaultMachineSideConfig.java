package com.example.mekanismmagic.blockentity;

import mekanism.api.RelativeSide;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Shared default side layout for every Mekanism Magic machine family. */
public final class DefaultMachineSideConfig {
    private DefaultMachineSideConfig() {
    }

    public static void apply(TileComponentConfig configComponent) {
        if (configComponent == null) {
            return;
        }
        ConfigInfo energyConfig = configComponent.getConfig(
                TransmissionType.ENERGY);
        if (energyConfig != null) {
            for (RelativeSide side : RelativeSide.values()) {
                energyConfig.setDataType(DataType.INPUT, side);
            }
        }

        ConfigInfo itemConfig = configComponent.getConfig(
                TransmissionType.ITEM);
        if (itemConfig == null) {
            return;
        }
        // INPUT_OUTPUT normally contains only a factory's primary processing
        // slots. Merge all exposed item categories so the single top port can
        // also handle catalysts, persistent extras and the energy item slot.
        Set<IInventorySlot> topSlots = new LinkedHashSet<>();
        for (DataType type : DataType.values()) {
            if (itemConfig.getSlotInfo(type)
                    instanceof InventorySlotInfo slotInfo) {
                topSlots.addAll(slotInfo.getSlots());
            }
        }
        if (!topSlots.isEmpty()) {
            itemConfig.addSlotInfo(DataType.INPUT_OUTPUT,
                    new InventorySlotInfo(true, true,
                            new ArrayList<>(topSlots)));
        }
        for (RelativeSide side : RelativeSide.values()) {
            itemConfig.setDataType(DataType.NONE, side);
        }
        itemConfig.setDataType(DataType.INPUT_OUTPUT, RelativeSide.TOP);
        itemConfig.setEjecting(true);
    }
}
