package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
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
    private List<IInventorySlot> pedestalSlots;

    public EnchantingApparatusProcessorBlockEntity(
            BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        pedestalSlots = new ArrayList<>();
        int logical = PEDESTAL_SLOT_START;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (row == 1 && column == 1) {
                    inputSlot = registerLogicalSlot(helper, REAGENT_SLOT,
                            InputInventorySlot.at(listener,
                                    ArsThreeByThreeMachineLayout.slotX(column),
                                    ArsThreeByThreeMachineLayout.slotY(row)));
                } else {
                    pedestalSlots.add(registerLogicalSlot(helper, logical++,
                            InputInventorySlot.at(listener,
                                    ArsThreeByThreeMachineLayout.slotX(column),
                                    ArsThreeByThreeMachineLayout.slotY(row))));
                }
            }
        }
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener,
                        ArsThreeByThreeMachineLayout.SOURCE_SAFE_OUTPUT_X,
                        ArsThreeByThreeMachineLayout.OUTPUT_Y));
        // Keep the existing logical slot ids so old worlds load without moving
        // any stacks, but expose every cell as the same kind of input. The
        // recipe bridge now discovers which stack is the reagent instead of
        // permanently assigning that role to the centre cell.
        List<IInventorySlot> apparatusInputs =
                new ArrayList<>(PEDESTAL_SLOT_COUNT + 1);
        apparatusInputs.add(inputSlot);
        apparatusInputs.addAll(pedestalSlots);
        setupArsItemIO(apparatusInputs, List.of(outputSlot), List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return ArsNouveauRecipeBridge.findApparatusRecipe(
                level, inventory, REAGENT_SLOT,
                PEDESTAL_SLOT_COUNT + 1);
    }

    @Override
    protected int baseEnergyPerTick() {
        return 1_200;
    }

    @Override
    protected int energySlotX() {
        return ArsThreeByThreeMachineLayout.ENERGY_SLOT_X;
    }

    @Override
    protected int energySlotY() {
        return ArsThreeByThreeMachineLayout.ENERGY_SLOT_Y;
    }

    void seedDevelopmentTest() {
        // Intentionally seed an outer cell: this command is also the
        // regression check that the reagent is no longer tied to the centre.
        pedestalSlots.getFirst().setStack(
                new net.minecraft.world.item.ItemStack(Items.SCULK_SENSOR));
        setSource(getMaxSource());
        if (energyContainer != null) {
            energyContainer.setEnergy(energyContainer.getMaxEnergy());
        }
    }

}
