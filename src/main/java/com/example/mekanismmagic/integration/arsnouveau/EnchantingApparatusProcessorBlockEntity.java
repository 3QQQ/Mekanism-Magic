package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mekanism implementation of all Ars Nouveau IEnchantingRecipe types.
 */
public final class EnchantingApparatusProcessorBlockEntity
        extends ArsSourceMachineBlockEntity {
    public static final int REAGENT_SLOT = 0;
    public static final int PEDESTAL_SLOT_START = 1;
    public static final int PEDESTAL_SLOT_COUNT = 8;

    public EnchantingApparatusProcessorBlockEntity(
            BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        List<IInventorySlot> inputs = new ArrayList<>();
        int logical = PEDESTAL_SLOT_START;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (row == 1 && column == 1) {
                    inputSlot = registerLogicalSlot(helper, REAGENT_SLOT,
                            InputInventorySlot.at(listener, 62, 35));
                    inputs.add(inputSlot);
                } else {
                    inputs.add(registerLogicalSlot(helper, logical++,
                            InputInventorySlot.at(listener,
                                    44 + column * 18,
                                    17 + row * 18)));
                }
            }
        }
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        IInventorySlot module = addSourceConversionModuleSlot(
                helper, listener, 28, 71);
        setupArsItemIO(inputs, List.of(outputSlot), List.of(module));
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return ArsNouveauRecipeBridge.findApparatusRecipe(
                level, inventory, REAGENT_SLOT,
                PEDESTAL_SLOT_START, PEDESTAL_SLOT_COUNT);
    }

    @Override
    protected int baseEnergyPerTick() {
        return 1_200;
    }

    @Override
    protected int energySlotX() {
        return 28;
    }

    @Override
    protected int energySlotY() {
        return 17;
    }
}
