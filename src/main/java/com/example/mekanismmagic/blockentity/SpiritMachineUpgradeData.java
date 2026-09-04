package com.example.mekanismmagic.blockentity;

import mekanism.api.energy.IEnergyContainer;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.interfaces.IRedstoneControl;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import mekanism.api.inventory.IInventorySlot;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mekanism machine upgrade payload with the non-consumable spirit source
 * carried across a tier-installer conversion.
 */
public final class SpiritMachineUpgradeData extends MachineUpgradeData {
    public final ItemStack spiritSource;
    public final int[] requiredTicks;
    public final long[] spiritTradeNonces;
    /** First-lane compatibility view retained for older single-machine code. */
    @Deprecated
    public final long spiritTradeNonce;
    public final long spiritTradeSalt;
    /** Logical-index snapshot for custom/extra/manual slots. */
    public final Map<Integer, ItemStack> logicalSlots;

    /**
     * Binary-compatible constructor retained for integrations compiled
     * against the original upgrade payload.
     */
    public SpiritMachineUpgradeData(HolderLookup.Provider registries,
                                    boolean redstone,
                                    IRedstoneControl.RedstoneControl controlType,
                                    IEnergyContainer energyContainer,
                                    int[] progress,
                                    EnergyInventorySlot energySlot,
                                    List<IInventorySlot> inputSlots,
                                    List<IInventorySlot> outputSlots,
                                    boolean sorting,
                                    List<ITileComponent> components,
                                    ItemStack spiritSource) {
        this(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components,
                spiritSource, new int[0], Map.of(), new long[0]);
    }

    public SpiritMachineUpgradeData(HolderLookup.Provider registries,
                                    boolean redstone,
                                    IRedstoneControl.RedstoneControl controlType,
                                    IEnergyContainer energyContainer,
                                    int[] progress,
                                    EnergyInventorySlot energySlot,
                                    List<IInventorySlot> inputSlots,
                                    List<IInventorySlot> outputSlots,
                                    boolean sorting,
                                    List<ITileComponent> components,
                                    ItemStack spiritSource,
                                    int[] requiredTicks) {
        this(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components,
                spiritSource, requiredTicks, Map.of(), new long[0]);
    }

    public SpiritMachineUpgradeData(HolderLookup.Provider registries,
                                    boolean redstone,
                                    IRedstoneControl.RedstoneControl controlType,
                                    IEnergyContainer energyContainer,
                                    int[] progress,
                                    EnergyInventorySlot energySlot,
                                    List<IInventorySlot> inputSlots,
                                    List<IInventorySlot> outputSlots,
                                    boolean sorting,
                                    List<ITileComponent> components,
                                     ItemStack spiritSource,
                                     int[] requiredTicks,
                                     Map<Integer, ItemStack> logicalSlots) {
        this(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components,
                spiritSource, requiredTicks, logicalSlots, new long[0]);
    }

    public SpiritMachineUpgradeData(HolderLookup.Provider registries,
                                    boolean redstone,
                                    IRedstoneControl.RedstoneControl controlType,
                                    IEnergyContainer energyContainer,
                                    int[] progress,
                                    EnergyInventorySlot energySlot,
                                    List<IInventorySlot> inputSlots,
                                    List<IInventorySlot> outputSlots,
                                    boolean sorting,
                                    List<ITileComponent> components,
                                    ItemStack spiritSource,
                                     int[] requiredTicks,
                                     Map<Integer, ItemStack> logicalSlots,
                                     long spiritTradeNonce) {
        this(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components,
                spiritSource, requiredTicks, logicalSlots,
                new long[]{spiritTradeNonce});
    }

    public SpiritMachineUpgradeData(HolderLookup.Provider registries,
                                    boolean redstone,
                                    IRedstoneControl.RedstoneControl controlType,
                                    IEnergyContainer energyContainer,
                                    int[] progress,
                                    EnergyInventorySlot energySlot,
                                    List<IInventorySlot> inputSlots,
                                    List<IInventorySlot> outputSlots,
                                    boolean sorting,
                                    List<ITileComponent> components,
                                    ItemStack spiritSource,
                                    int[] requiredTicks,
                                    Map<Integer, ItemStack> logicalSlots,
                                    long[] spiritTradeNonces) {
        this(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components,
                spiritSource, requiredTicks, logicalSlots,
                spiritTradeNonces, 0L);
    }

    public SpiritMachineUpgradeData(HolderLookup.Provider registries,
                                    boolean redstone,
                                    IRedstoneControl.RedstoneControl controlType,
                                    IEnergyContainer energyContainer,
                                    int[] progress,
                                    EnergyInventorySlot energySlot,
                                    List<IInventorySlot> inputSlots,
                                    List<IInventorySlot> outputSlots,
                                    boolean sorting,
                                    List<ITileComponent> components,
                                    ItemStack spiritSource,
                                    int[] requiredTicks,
                                    Map<Integer, ItemStack> logicalSlots,
                                    long[] spiritTradeNonces,
                                    long spiritTradeSalt) {
        super(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components);
        this.spiritSource = spiritSource.copy();
        this.requiredTicks = Arrays.copyOf(requiredTicks,
                requiredTicks.length);
        Map<Integer, ItemStack> slotCopies = new LinkedHashMap<>();
        logicalSlots.forEach((index, stack) ->
                slotCopies.put(index, stack.copy()));
        this.logicalSlots = Collections.unmodifiableMap(slotCopies);
        this.spiritTradeNonces = Arrays.copyOf(spiritTradeNonces,
                spiritTradeNonces.length);
        this.spiritTradeNonce = this.spiritTradeNonces.length == 0
                ? 0L : this.spiritTradeNonces[0];
        this.spiritTradeSalt = spiritTradeSalt;
    }
}
