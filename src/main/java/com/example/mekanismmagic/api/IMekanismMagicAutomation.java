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
    int API_VERSION = 2;

    default int mekanismMagicAutomationApiVersion() {
        return API_VERSION;
    }

    ResourceLocation mekanismMagicMachineId();

    /**
     * Inputs that are consumed by a processing recipe and should be encoded
     * in an automated crafting pattern.
     */
    List<IInventorySlot> mekanismMagicPatternInputs();

    /**
     * Machine outputs that may be returned to an external storage network.
     */
    List<IInventorySlot> mekanismMagicPatternOutputs();

    /**
     * Required but normally non-consumed inputs, such as a spirit source or
     * dimensional miner. These should not be encoded into every pattern.
     */
    default List<IInventorySlot> mekanismMagicPersistentInputs() {
        return List.of();
    }

    /**
     * Slots intentionally excluded from external insertion and pattern
     * encoding, such as chalk, ritual selectors, and the Dictionary of
     * Spirits.
     */
    default List<IInventorySlot> mekanismMagicManualOnlySlots() {
        return List.of();
    }

    IEnergyContainer mekanismMagicEnergyContainer();

    default boolean mekanismMagicSupportsPatternAutomation() {
        return true;
    }

    /**
     * Whether an optional storage-network bridge may attach a native node and
     * return this machine's output slots without an installed machine upgrade.
     */
    default boolean mekanismMagicSupportsDirectNetworkOutput() {
        return false;
    }

    default boolean mekanismMagicIsBusy() {
        return false;
    }
}
