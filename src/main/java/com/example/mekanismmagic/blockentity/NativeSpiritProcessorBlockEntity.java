package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.NativeMekanismRegistries;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

public final class NativeSpiritProcessorBlockEntity extends NativeMagicMachineBlockEntity {
    public NativeSpiritProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.SPIRIT_BLOCK.get().builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, 0, InputInventorySlot.at(listener, 64, 17));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT, OutputInventorySlot.at(listener, 116, 35));
        containmentSlot = registerLogicalSlot(helper, CONTAINMENT_SLOT,
                BasicInventorySlot.at(OccultismRecipeBridge::isSpiritSource, listener, 64, 53));
        configComponent.setupItemIOExtraConfig(inputSlot, outputSlot, containmentSlot, energySlot);
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
}
