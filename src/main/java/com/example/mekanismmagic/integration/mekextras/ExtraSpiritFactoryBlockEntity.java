package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.SpiritFactoryRecipe;
import com.example.mekanismmagic.blockentity.SpiritMachineUpgradeData;
import com.jerry.mekextras.common.tile.factory.TileEntityExtraItemToItemFactory;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Spirit recipe adapter for Mekanism Extras' 11/13/15/17-process factories.
 */
public final class ExtraSpiritFactoryBlockEntity
        extends TileEntityExtraItemToItemFactory<SpiritFactoryRecipe> {
    private BasicInventorySlot spiritSlot;
    private int[] processRequiredTicks;

    public ExtraSpiritFactoryBlockEntity(Holder<Block> block, BlockPos pos,
                                         BlockState state) {
        super(block, pos, state,
                List.of(CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_INPUT,
                        CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
                        CachedRecipe.OperationTracker.RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT),
                Set.of(CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_ENERGY));
        ensureProcessTickCapacity(tier.processes - 1);
    }

    @Override
    protected void addSlots(InventorySlotHelper helper,
                            mekanism.api.IContentsListener listener,
                            mekanism.api.IContentsListener recipeCacheListener) {
        super.addSlots(helper, listener, recipeCacheListener);
        spiritSlot = BasicInventorySlot.at(OccultismRecipeBridge::isSpiritSource,
                recipeCacheListener, 7, 57);
        helper.addSlot(spiritSlot);
        processRequiredTicks = new int[tier.processes];
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        for (int process = 0; process < tier.processes; process++) {
            int index = process;
            container.track(SyncableInt.create(
                    () -> processRequiredTicks == null
                            || index >= processRequiredTicks.length
                            ? 0 : processRequiredTicks[index],
                    value -> {
                        if (processRequiredTicks != null
                                && index < processRequiredTicks.length) {
                            processRequiredTicks[index] = Math.max(1, value);
                        }
                    }));
        }
    }

    @Override
    protected IInventorySlot getExtraSlot() {
        return spiritSlot;
    }

    @Override
    protected SpiritFactoryRecipe findRecipe(int process, ItemStack input,
                                             IInventorySlot extra,
                                             IInventorySlot output) {
        if (input.isEmpty() || extra == null || extra.getStack().isEmpty()) {
            return null;
        }
        net.neoforged.neoforge.items.ItemStackHandler inventory =
                new net.neoforged.neoforge.items.ItemStackHandler(
                        NativeMagicMachineBlockEntity.MACHINE_INVENTORY_SIZE);
        inventory.setStackInSlot(0, input.copy());
        inventory.setStackInSlot(NativeMagicMachineBlockEntity.CONTAINMENT_SLOT,
                extra.getStack().copy());
        return OccultismRecipeBridge.findSpiritRecipe(level, inventory,
                        extra.getStack())
                .filter(result -> !result.output().isEmpty())
                .map(result -> new SpiritFactoryRecipe(input, extra.getStack(), result))
                .orElse(null);
    }

    @Override
    protected SpiritFactoryRecipe getRecipeForInput(int process, ItemStack input,
                                                    IInventorySlot extra,
                                                    IInventorySlot output,
                                                    boolean recheck) {
        return findRecipe(process, input, extra, output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public IMekanismRecipeTypeProvider getRecipeType() {
        return (IMekanismRecipeTypeProvider) MekanismRecipeType.CRUSHING;
    }

    @Override
    protected int getNeededInput(SpiritFactoryRecipe recipe, ItemStack input) {
        return recipe.inputCount();
    }

    @Override
    protected boolean isCachedRecipeValid(CachedRecipe<SpiritFactoryRecipe> cached,
                                          ItemStack input) {
        return cached != null && cached.getRecipe() != null
                && cached.getRecipe().test(input)
                && spiritSlot != null
                && cached.getRecipe().sameSource(spiritSlot.getStack());
    }

    @Override
    public boolean isItemValidForSlot(ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public boolean isValidInputItem(ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public SpiritFactoryRecipe getRecipe(int process) {
        if (process < 0 || process >= inputSlots.size()) {
            return null;
        }
        return findRecipe(process, inputSlots.get(process).getStack(),
                spiritSlot, outputSlots.get(process));
    }

    @Override
    public CachedRecipe<SpiritFactoryRecipe> createNewCachedRecipe(
            SpiritFactoryRecipe recipe, int process) {
        ensureProcessTickCapacity(process);
        processRequiredTicks[process] = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this, recipe.duration()));
        @SuppressWarnings({"rawtypes", "unchecked"})
        CachedRecipe<SpiritFactoryRecipe> cached = (CachedRecipe)
                OneInputCachedRecipe.itemToItem(recipe, () -> true,
                                inputHandlers[process], outputHandlers[process])
                        .setActive(active -> setActiveState(active, process))
                        .setEnergyRequirements(
                                () -> mekanism.common.util.MekanismUtils
                                        .getEnergyPerTick(this, 400),
                                energyContainer)
                        .setRequiredTicks(() -> mekanism.common.util.MekanismUtils
                                .getTicks(this, recipe.duration()))
                        .setOperatingTicksChanged(value -> progress[process] = value)
                        .setBaselineMaxOperations(this::getOperationsPerTick);
        return cached;
    }

    @Override
    public double getScaledProgress(int scale, int process) {
        if (process < 0 || process >= progress.length) {
            return 0;
        }
        int required = process < processRequiredTicks.length
                ? processRequiredTicks[process] : 0;
        return required <= 0 ? 0 : progress[process] * (double) scale / required;
    }

    private void ensureProcessTickCapacity(int process) {
        int requiredLength = Math.max(tier.processes, process + 1);
        if (processRequiredTicks == null) {
            processRequiredTicks = new int[requiredLength];
        } else if (processRequiredTicks.length < requiredLength) {
            processRequiredTicks = Arrays.copyOf(processRequiredTicks, requiredLength);
        }
    }

    @Override
    public SpiritMachineUpgradeData getUpgradeData(
            net.minecraft.core.HolderLookup.Provider registries) {
        EnergyInventorySlot currentEnergySlot = null;
        for (IInventorySlot slot : getInventorySlots(null)) {
            if (slot instanceof EnergyInventorySlot energy) {
                currentEnergySlot = energy;
                break;
            }
        }
        return new SpiritMachineUpgradeData(registries, redstone, getControlType(),
                energyContainer, progress, currentEnergySlot, inputSlots, outputSlots,
                isSorting(), getComponents(),
                spiritSlot == null ? ItemStack.EMPTY : spiritSlot.getStack());
    }

    @Override
    public void parseUpgradeData(net.minecraft.core.HolderLookup.Provider registries,
                                 IUpgradeData data) {
        super.parseUpgradeData(registries, data);
        if (data instanceof SpiritMachineUpgradeData spiritUpgrade
                && spiritSlot != null) {
            spiritSlot.setStack(spiritUpgrade.spiritSource.copy());
        }
    }
}
