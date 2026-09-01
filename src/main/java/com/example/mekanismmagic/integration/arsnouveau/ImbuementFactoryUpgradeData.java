package com.example.mekanismmagic.integration.arsnouveau;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.interfaces.IRedstoneControl;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Preserves the Source and catalyst library during factory tier upgrades. */
public final class ImbuementFactoryUpgradeData extends MachineUpgradeData {
    public final int source;
    public final ItemStack recipeLock;
    public final List<ItemStack> catalystLibrary;
    public final int selectedCatalystIndex;
    public final boolean virtualCatalystSelected;
    public final String virtualCatalystId;
    public final boolean catalystLibraryOpen;
    public final int catalystPage;
    public final int[] requiredTicks;
    public final EnumMap<Direction,
            ArsSourceMachineBlockEntity.SourceMode> sourceModes;
    public final List<BlockPos> sourceLinks;

    public ImbuementFactoryUpgradeData(
            HolderLookup.Provider registries, boolean redstone,
            IRedstoneControl.RedstoneControl controlType,
            IEnergyContainer energyContainer, int[] progress,
            EnergyInventorySlot energySlot,
            List<IInventorySlot> inputSlots,
            List<IInventorySlot> outputSlots,
            boolean sorting, List<ITileComponent> components,
            int source, ItemStack recipeLock,
            List<ItemStack> catalystLibrary,
            int selectedCatalystIndex,
            boolean virtualCatalystSelected,
            String virtualCatalystId,
            boolean catalystLibraryOpen, int catalystPage,
            int[] requiredTicks,
            Map<Direction, ArsSourceMachineBlockEntity.SourceMode>
                    sourceModes) {
        this(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components,
                source, recipeLock, catalystLibrary, selectedCatalystIndex,
                virtualCatalystSelected, virtualCatalystId,
                catalystLibraryOpen, catalystPage, requiredTicks,
                sourceModes, List.of());
    }

    public ImbuementFactoryUpgradeData(
            HolderLookup.Provider registries, boolean redstone,
            IRedstoneControl.RedstoneControl controlType,
            IEnergyContainer energyContainer, int[] progress,
            EnergyInventorySlot energySlot,
            List<IInventorySlot> inputSlots,
            List<IInventorySlot> outputSlots,
            boolean sorting, List<ITileComponent> components,
            int source, ItemStack recipeLock,
            List<ItemStack> catalystLibrary,
            int selectedCatalystIndex,
            boolean virtualCatalystSelected,
            String virtualCatalystId,
            boolean catalystLibraryOpen, int catalystPage,
            int[] requiredTicks,
            Map<Direction, ArsSourceMachineBlockEntity.SourceMode>
                    sourceModes,
            List<BlockPos> sourceLinks) {
        super(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components);
        this.source = source;
        this.recipeLock = recipeLock.copy();
        this.catalystLibrary = catalystLibrary.stream()
                .map(ItemStack::copy).toList();
        this.selectedCatalystIndex = selectedCatalystIndex;
        this.virtualCatalystSelected = virtualCatalystSelected;
        this.virtualCatalystId = virtualCatalystId;
        this.catalystLibraryOpen = catalystLibraryOpen;
        this.catalystPage = catalystPage;
        this.requiredTicks = Arrays.copyOf(requiredTicks,
                requiredTicks.length);
        this.sourceModes = new EnumMap<>(Direction.class);
        this.sourceModes.putAll(sourceModes);
        this.sourceLinks = sourceLinks.stream()
                .map(BlockPos::immutable).toList();
    }
}
