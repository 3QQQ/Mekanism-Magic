package com.example.mekanismmagic.api;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Stable, dependency-free integration surface for external automation mods.
 *
 * <p>Mek Energistics and similar addons can detect this interface without
 * reflecting into machine internals. Returned lists are immutable snapshots
 * of live Mekanism inventory-slot references.</p>
 */
public interface IMekanismMagicAutomation {
    int API_VERSION = 1;

    default int mekanismMagicAutomationApiVersion() {
        return API_VERSION;
    }

    ResourceLocation mekanismMagicMachineId();

    List<IInventorySlot> mekanismMagicPatternInputs();

    List<IInventorySlot> mekanismMagicPatternOutputs();

    default List<IInventorySlot> mekanismMagicPersistentInputs() {
        return List.of();
    }

    default List<IInventorySlot> mekanismMagicManualOnlySlots() {
        return List.of();
    }

    IEnergyContainer mekanismMagicEnergyContainer();

    default boolean mekanismMagicSupportsPatternAutomation() {
        return true;
    }

    default boolean mekanismMagicIsBusy() {
        return false;
    }
}
