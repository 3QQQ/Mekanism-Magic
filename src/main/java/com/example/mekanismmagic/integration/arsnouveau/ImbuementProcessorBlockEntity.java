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
 * Mekanism implementation of Ars Nouveau imbuement recipes.
 */
public final class ImbuementProcessorBlockEntity
        extends ArsSourceMachineBlockEntity {
    public static final int REAGENT_SLOT = 0;
    public static final int PEDESTAL_SLOT_START = 1;
    public static final int PEDESTAL_SLOT_COUNT = 3;
    private List<IInventorySlot> pedestalSlots;

    public ImbuementProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, REAGENT_SLOT,
                InputInventorySlot.at(listener, 62, 35));
        pedestalSlots = new ArrayList<>();
        int[] xPositions = {44, 62, 80};
        for (int index = 0; index < PEDESTAL_SLOT_COUNT; index++) {
            pedestalSlots.add(registerLogicalSlot(helper,
                    PEDESTAL_SLOT_START + index,
                    InputInventorySlot.at(listener,
                            xPositions[index], 17)));
        }
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        IInventorySlot module = addSourceConversionModuleSlot(
                helper, listener, 28, 62);
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

    void seedDevelopmentTest(boolean electric) {
        inputSlot.setStack(new net.minecraft.world.item.ItemStack(
                Items.AMETHYST_SHARD));
        setSource(electric ? 0 : getMaxSource());
        if (electric && sourceConversionModuleSlot != null) {
            sourceConversionModuleSlot.setStack(
                    new net.minecraft.world.item.ItemStack(
                            ArsNouveauRegistries
                                    .SOURCE_CONVERSION_MODULE.get()));
        }
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
