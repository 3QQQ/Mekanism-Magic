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
 * Mekanism implementation of Ars Nouveau imbuement recipes.
 */
public final class ImbuementProcessorBlockEntity
        extends ArsSourceMachineBlockEntity {
    public static final int REAGENT_SLOT = 0;
    public static final int PEDESTAL_SLOT_START = 1;
    public static final int PEDESTAL_SLOT_COUNT = 3;

    public ImbuementProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        List<IInventorySlot> inputs = new ArrayList<>();
        inputSlot = registerLogicalSlot(helper, REAGENT_SLOT,
                InputInventorySlot.at(listener, 62, 35));
        inputs.add(inputSlot);
        int[] xPositions = {44, 62, 80};
        for (int index = 0; index < PEDESTAL_SLOT_COUNT; index++) {
            inputs.add(registerLogicalSlot(helper,
                    PEDESTAL_SLOT_START + index,
                    InputInventorySlot.at(listener,
                            xPositions[index], 17)));
        }
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        IInventorySlot module = addSourceConversionModuleSlot(
                helper, listener, 28, 62);
        setupArsItemIO(inputs, List.of(outputSlot), List.of(module));
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return ArsNouveauRecipeBridge.findImbuementRecipe(
                level, inventory, REAGENT_SLOT,
                PEDESTAL_SLOT_START, PEDESTAL_SLOT_COUNT);
    }

    @Override
    protected int baseEnergyPerTick() {
        return 600;
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
