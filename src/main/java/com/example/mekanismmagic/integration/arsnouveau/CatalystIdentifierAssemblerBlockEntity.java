package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

public final class CatalystIdentifierAssemblerBlockEntity
        extends com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity {
    private static final int INPUT_START = 0;
    private static final int INPUT_COUNT = 9;

    public CatalystIdentifierAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_BLOCK
                .get().builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        java.util.ArrayList<mekanism.api.inventory.IInventorySlot> inputs =
                new java.util.ArrayList<>();
        for (int index = 0; index < INPUT_COUNT; index++) {
            int row = index / 3;
            int column = index % 3;
            inputs.add(registerLogicalSlot(helper, INPUT_START + index,
                    InputInventorySlot.at(listener,
                            ArsThreeByThreeMachineLayout.slotX(column),
                            ArsThreeByThreeMachineLayout.slotY(row))));
        }
        inputSlot = (InputInventorySlot) inputs.getFirst();
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener,
                        ArsThreeByThreeMachineLayout.STANDARD_OUTPUT_X,
                        ArsThreeByThreeMachineLayout.OUTPUT_Y));
        setupNativeItemIO(inputs, List.of(outputSlot), List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return ArsNouveauRecipeBridge.findCatalystIdentifier(
                level, inventory, INPUT_START, INPUT_COUNT);
    }

    @Override
    public Optional<List<ItemInputGroup>> mekanismMagicPlanExternalItemInputs(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        if (level == null || inputs == null || inputs.isEmpty()
                || outputs == null || outputs.size() != 1) return Optional.empty();
        // Each occupied cell is one catalyst sample. AE groups identical
        // keys, so split repeated samples back into distinct physical cells.
        ItemStackHandler staged = new ItemStackHandler(INPUT_COUNT);
        List<ItemInputGroup> groups = new java.util.ArrayList<>();
        List<mekanism.api.inventory.IInventorySlot> ports = mekanismMagicPatternInputs();
        int target = 0;
        for (PatternStack input : inputs) {
            if (input == null || input.stack().isEmpty() || input.amount() <= 0
                    || input.amount() > INPUT_COUNT - target) return Optional.empty();
            for (int count = 0; count < input.amount(); count++) {
                staged.setStackInSlot(target, input.stack());
                groups.add(new ItemInputGroup(List.of(new PatternStack(input.stack(), 1)),
                        List.of(ports.get(target))));
                target++;
            }
        }
        Optional<MachineRecipeResult> selected = ArsNouveauRecipeBridge.findCatalystIdentifier(
                level, staged, INPUT_START, INPUT_COUNT);
        PatternStack declared = outputs.getFirst();
        if (selected.isEmpty() || declared == null
                || declared.amount() != selected.get().output().getCount()
                || !ItemStack.isSameItemSameComponents(declared.stack(), selected.get().output())) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(groups));
    }

    @Override
    protected int baseEnergyPerTick() {
        return MagicMachineEnergyProfiles.ASSEMBLY;
    }

    @Override
    protected int energySlotX() {
        return ArsThreeByThreeMachineLayout.ENERGY_SLOT_X;
    }

    @Override
    protected int energySlotY() {
        return ArsThreeByThreeMachineLayout.ENERGY_SLOT_Y;
    }

    /**
     * Identifier outputs carry recipe component data. Use the bounded direct
     * item-handler path for ordinary inventories and third-party interfaces;
     * Mekanism logistical transporters are detected by the shared base and
     * still fall back to Mekanism's native transit request.
     */
    @Override
    protected boolean useFastEjectPath() {
        return true;
    }

    int seedDevelopmentTest(ResourceLocation recipeId) {
        if (level == null) {
            return 0;
        }
        var holder = level.getRecipeManager().getAllRecipesFor(
                        RecipeRegistry.IMBUEMENT_TYPE.get()).stream()
                .filter(candidate -> candidate.id().equals(recipeId))
                .findFirst().orElse(null);
        if (holder == null) {
            return 0;
        }
        List<net.minecraft.world.item.crafting.Ingredient> ingredients =
                holder.value().getPedestalItems();
        if (ingredients.isEmpty() || ingredients.size() > INPUT_COUNT) {
            return 0;
        }
        for (int index = 0; index < INPUT_COUNT; index++) {
            ItemStack sample = ItemStack.EMPTY;
            if (index < ingredients.size()) {
                sample = ArsNouveauRecipeBridge.representativeChoice(
                        ingredients.get(index));
                if (sample.isEmpty()) {
                    return 0;
                }
            }
            logicalSlots().get(INPUT_START + index).setStack(sample);
        }
        if (energyContainer != null) {
            energyContainer.setEnergy(energyContainer.getMaxEnergy());
        }
        return 1;
    }
}
