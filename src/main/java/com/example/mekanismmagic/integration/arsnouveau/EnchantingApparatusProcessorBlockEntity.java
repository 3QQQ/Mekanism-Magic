package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.tile.component.config.DataType;
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
                            InputInventorySlot.at(listener, 62, 35));
                } else {
                    pedestalSlots.add(registerLogicalSlot(helper, logical++,
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
        var itemConfig = setupArsItemIO(
                List.of(inputSlot), List.of(outputSlot), List.of());
        addNativeItemSlotInfo(itemConfig, DataType.INPUT_2,
                true, false, pedestalSlots);
        addNativeItemSlotInfo(itemConfig, DataType.EXTRA,
                true, true, List.of(module));
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

    void seedDevelopmentTest() {
        inputSlot.setStack(new net.minecraft.world.item.ItemStack(
                Items.SCULK_SENSOR));
        setSource(getMaxSource());
        if (energyContainer != null) {
            energyContainer.setEnergy(energyContainer.getMaxEnergy());
        }
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternInputs() {
        List<IInventorySlot> slots =
                new ArrayList<>(super.mekanismMagicPatternInputs());
        if (pedestalSlots != null) {
            slots.addAll(pedestalSlots);
        }
        return List.copyOf(slots);
    }
}
