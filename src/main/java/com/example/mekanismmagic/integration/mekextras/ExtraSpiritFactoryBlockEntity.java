package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.SpiritFactoryRecipe;
import com.example.mekanismmagic.blockentity.SpiritMachineUpgradeData;
import com.jerry.mekanism_extras.common.tile.factory.TileEntityExtraItemToItemFactory;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
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
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Spirit recipe adapter for Mekanism Extras' 11/13/15/17-process factories.
 */
public final class ExtraSpiritFactoryBlockEntity
        extends TileEntityExtraItemToItemFactory<SpiritFactoryRecipe>
        implements IMekanismMagicAutomation {
    private BasicInventorySlot spiritSlot;
    private int[] processRequiredTicks;

    public ExtraSpiritFactoryBlockEntity(IBlockProvider block, BlockPos pos,
                                         BlockState state) {
        super(block, pos, state,
                List.of(CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_INPUT,
                        CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
                        CachedRecipe.OperationTracker.RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT),
                Set.of(CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_ENERGY));
        ejectorComponent.setCanEject(
                type -> level instanceof net.minecraft.server.level.ServerLevel);
        ensureProcessTickCapacity(tier.processes - 1);
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
    public net.minecraft.network.chat.Component getName() {
        return net.minecraft.network.chat.Component.translatable(
                getBlockState().getBlock().getDescriptionId());
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
    protected IInventorySlot getExtraSlot() {
        return spiritSlot;
    }

    @Override
    protected SpiritFactoryRecipe findRecipe(int process, ItemStack input,
                                             IInventorySlot output,
                                             IInventorySlot secondaryOutput) {
        if (input.isEmpty() || spiritSlot == null || spiritSlot.getStack().isEmpty()) {
            return null;
        }
        net.minecraftforge.items.ItemStackHandler inventory =
                new net.minecraftforge.items.ItemStackHandler(
                        NativeMagicMachineBlockEntity.MACHINE_INVENTORY_SIZE);
        inventory.setStackInSlot(0, input.copy());
        inventory.setStackInSlot(NativeMagicMachineBlockEntity.CONTAINMENT_SLOT,
                spiritSlot.getStack().copy());
        return OccultismRecipeBridge.findSpiritRecipe(level, inventory,
                        spiritSlot.getStack())
                .filter(result -> !result.output().isEmpty())
                .map(result -> new SpiritFactoryRecipe(
                        input, spiritSlot.getStack(), result))
                .orElse(null);
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
    public boolean isValidInputItem(ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public SpiritFactoryRecipe getRecipe(int process) {
        if (process < 0 || process >= inputSlots.size()) {
            return null;
        }
        return findRecipe(process, inputSlots.get(process).getStack(),
                outputSlots.get(process), null);
    }

    @Override
    public CachedRecipe<SpiritFactoryRecipe> createNewCachedRecipe(
            SpiritFactoryRecipe recipe, int process) {
        ensureProcessTickCapacity(process);
        processRequiredTicks[process] = Math.max(1,
                MekanismUtils.getTicks(this, recipe.duration()));
        @SuppressWarnings({"rawtypes", "unchecked"})
        CachedRecipe<SpiritFactoryRecipe> cached = (CachedRecipe)
                OneInputCachedRecipe.itemToItem(recipe,
                                recheckAllRecipeErrors[process],
                                inputHandlers[process], outputHandlers[process])
                        .setErrorsChanged(errors ->
                                errorTracker.onErrorsChanged(errors, process))
                        .setCanHolderFunction(() ->
                                MekanismUtils.canFunction(this)
                                && spiritSlot != null
                                && OccultismRecipeBridge.isSpiritSource(
                                        spiritSlot.getStack()))
                        .setActive(active -> setActiveState(active, process))
                        .setEnergyRequirements(
                                () -> MekanismUtils.getEnergyPerTick(
                                        this, FloatingLong.create(400)),
                                energyContainer)
                        .setRequiredTicks(() ->
                                MekanismUtils.getTicks(this, recipe.duration()))
                        .setOnFinish(this::markForSave)
                        .setBaselineMaxOperations(this::getBaselineMaxOperations)
                        .setOperatingTicksChanged(value -> progress[process] = value);
        return cached;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
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
    public SpiritMachineUpgradeData getUpgradeData() {
        EnergyInventorySlot currentEnergySlot = null;
        for (IInventorySlot slot : getInventorySlots(null)) {
            if (slot instanceof EnergyInventorySlot energy) {
                currentEnergySlot = energy;
                break;
            }
        }
        return new SpiritMachineUpgradeData(redstone, getControlType(),
                energyContainer, progress, currentEnergySlot,
                inputSlots, outputSlots, isSorting(), getComponents(),
                spiritSlot == null ? ItemStack.EMPTY : spiritSlot.getStack());
    }

    @Override
    public void parseUpgradeData(IUpgradeData data) {
        super.parseUpgradeData(data);
        if (data instanceof SpiritMachineUpgradeData spiritUpgrade
                && spiritSlot != null) {
            spiritSlot.setStack(spiritUpgrade.spiritSource.copy());
        }
    }

    @Override
    public net.minecraft.resources.ResourceLocation mekanismMagicMachineId() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                getBlockState().getBlock());
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternInputs() {
        return inputSlots == null ? List.of() : List.copyOf(inputSlots);
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternOutputs() {
        return outputSlots == null ? List.of() : List.copyOf(outputSlots);
    }

    @Override
    public List<IInventorySlot> mekanismMagicPersistentInputs() {
        return spiritSlot == null ? List.of() : List.of(spiritSlot);
    }

    @Override
    public mekanism.api.energy.IEnergyContainer
    mekanismMagicEnergyContainer() {
        return energyContainer;
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        if (progress != null) {
            for (int value : progress) {
                if (value > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
