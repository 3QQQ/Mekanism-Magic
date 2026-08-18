package com.example.mekanismmagic.blockentity;

import mekanism.api.energy.IEnergyContainer;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.interfaces.IRedstoneControl;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import mekanism.api.inventory.IInventorySlot;

import java.util.List;

/**
 * Mekanism machine upgrade payload with the non-consumable spirit source
 * carried across a tier-installer conversion.
 */
public final class SpiritMachineUpgradeData extends MachineUpgradeData {
    public final ItemStack spiritSource;

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
        super(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components);
        this.spiritSource = spiritSource.copy();
    }
}
