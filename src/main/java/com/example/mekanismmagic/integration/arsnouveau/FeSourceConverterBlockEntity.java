package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * Converts Mekanism FE into Ars Nouveau Source and exposes the result to the
 * Ars Source network.
 */
public final class FeSourceConverterBlockEntity
        extends ArsSourceMachineBlockEntity {
    public FeSourceConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.SOURCE_CONVERTER_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        setupArsItemIO(java.util.List.of(), java.util.List.of(),
                java.util.List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return Optional.empty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return ArsNouveauMachineConfig.SOURCE_CONVERTER_FE_PER_TICK;
    }

    @Override
    protected int energySlotX() {
        return 28;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    @Override
    protected int sourceMaxReceive() {
        return 0;
    }

    @Override
    public boolean canAcceptSource() {
        return false;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = nativeBaseUpdate();
        setActive(false);
        progressRequired = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        ArsNouveauMachineConfig.SOURCE_CONVERTER_DURATION));
        if (getMaxSource() - getSource()
                < ArsNouveauMachineConfig.SOURCE_CONVERTER_SOURCE_PER_OPERATION) {
            progress = 0;
            return changed;
        }
        long usage = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
        if (energyContainer == null || energyContainer.getEnergy() < usage) {
            return changed;
        }
        setActive(true);
        energyContainer.extract(usage, Action.EXECUTE,
                AutomationType.INTERNAL);
        progress++;
        if (progress >= progressRequired) {
            addSource(ArsNouveauMachineConfig
                    .SOURCE_CONVERTER_SOURCE_PER_OPERATION);
            progress = 0;
            changed = true;
        }
        return changed;
    }
}
