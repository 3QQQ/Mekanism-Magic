package com.example.mekanismmagic.api;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Stable, dependency-free integration surface for external automation mods.
 *
 * <p>Mek Energistics and similar addons can detect this interface without
 * reflecting into machine internals. Returned lists are immutable snapshots
 * of live Mekanism inventory-slot references.</p>
 */
public interface IMekanismMagicAutomation {
    int API_VERSION = 5;

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
     * Dynamic gate for advertising and accepting installed patterns. Unlike
     * the static support flag, this may depend on a persistent catalyst or on
     * whether the selected recipe has a deterministic output.
     */
    default boolean mekanismMagicCanAdvertisePatterns() {
        return mekanismMagicSupportsPatternAutomation();
    }

    /**
     * Whether every installed processing pattern must be checked against the
     * machine's current persistent recipe context before it is advertised or
     * accepted. This is intentionally separate from static pattern support:
     * changing a catalyst must not destroy the optional network node that
     * also returns machine outputs.
     */
    default boolean mekanismMagicUsesContextualPatternValidation() {
        return false;
    }

    /**
     * Validates one complete processing-pattern declaration using only
     * dependency-free item snapshots. Contextual machines override this and
     * fail closed when their catalyst/job makes the result non-deterministic.
     */
    default boolean mekanismMagicMatchesPattern(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        return !mekanismMagicUsesContextualPatternValidation();
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

    /** Whether equivalent factory input lanes may share one routing port. */
    default boolean mekanismMagicGroupParallelItemInputs() {
        return false;
    }

    /** Immutable-count item description used at optional automation edges. */
    record PatternStack(ItemStack stack, long amount) {
        public PatternStack {
            stack = stack == null || stack.isEmpty()
                    ? ItemStack.EMPTY : stack.copyWithCount(1);
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }
}
