package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.interfaces.IRedstoneControl;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** Carries the native AE ledger and supply/node state across tile replacement. */
public abstract class NativeMachineUpgradeData extends MachineUpgradeData {
    private CompoundTag nativeAeState;

    protected NativeMachineUpgradeData(HolderLookup.Provider registries,
                                       boolean redstone,
                                       IRedstoneControl.RedstoneControl controlType,
                                       IEnergyContainer energyContainer,
                                       int[] progress,
                                       EnergyInventorySlot energySlot,
                                       List<IInventorySlot> inputSlots,
                                       List<IInventorySlot> outputSlots,
                                       boolean sorting,
                                       List<ITileComponent> components) {
        super(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components);
    }

    public static <T extends NativeMachineUpgradeData> T capture(
            T upgrade, BlockEntity source, HolderLookup.Provider registries) {
        CompoundTag saved = new CompoundTag();
        MachineAeResourceHooks.save(source, saved, registries);
        NativeMachineUpgradeData payload = upgrade;
        payload.nativeAeState = saved.copy();
        return upgrade;
    }

    public static void restore(IUpgradeData upgrade, BlockEntity target,
                               HolderLookup.Provider registries) {
        if (upgrade instanceof NativeMachineUpgradeData payload
                && payload.nativeAeState != null) {
            // Each tier owns its own snapshot; repeated parse must replace,
            // never merge, a pending output or refund ledger.
            MachineAeResourceHooks.load(target,
                    payload.nativeAeState.copy(), registries);
        }
    }
}
