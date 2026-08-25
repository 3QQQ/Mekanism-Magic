package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;

import com.example.mekanismmagic.NativeMekanismRegistries;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

public final class NativeSpiritProcessorBlockEntity extends NativeMagicMachineBlockEntity {
    public NativeSpiritProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.SPIRIT_BLOCK, pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, 0, InputInventorySlot.at(listener, 64, 17));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        containmentSlot = registerLogicalSlot(helper, CONTAINMENT_SLOT,
                BasicInventorySlot.at(OccultismRecipeBridge::isSpiritSource, listener, 64, 53));
        var itemConfig = setupNativeItemIO(
                List.of(inputSlot), List.of(outputSlot), List.of());
        addNativeItemSlotInfo(itemConfig, DataType.EXTRA,
                true, true, List.of(containmentSlot));
    }

    @Override
    protected Optional<OccultismRecipeBridge.RecipeResult> findRecipe(ItemStackHandler inventory) {
        return OccultismRecipeBridge.findSpiritRecipe(level, inventory, inventory.getStackInSlot(CONTAINMENT_SLOT));
    }

    @Override
    protected int baseEnergyPerTick() {
        return 400;
    }

    @Override
    protected int energySlotX() {
        return 39;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    @Override
    protected ItemStack getSpiritSourceForUpgrade() {
        return containmentSlot == null ? ItemStack.EMPTY : containmentSlot.getStack();
    }

    @Override
    protected void setSpiritSourceFromUpgrade(ItemStack source) {
        if (containmentSlot != null) {
            containmentSlot.setStack(source.copy());
        }
    }

    @Override
    public List<mekanism.api.inventory.IInventorySlot>
    mekanismMagicPersistentInputs() {
        return containmentSlot == null ? List.of()
                : List.of(containmentSlot);
    }
}

