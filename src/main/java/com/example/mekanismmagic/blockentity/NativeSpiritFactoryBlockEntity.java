package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.SpiritFactoryRecipe;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import mekanism.common.tile.factory.TileEntityItemToItemFactory;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tier.FactoryTier;
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
 * Mekanism factory-backed spirit processor. The factory cache/parallel process
 * pipeline is native Mekanism; only recipe discovery is delegated to
 * OccultismRecipeBridge.
 */
public final class NativeSpiritFactoryBlockEntity
        extends TileEntityItemToItemFactory<SpiritFactoryRecipe>
        implements IMekanismMagicAutomation {
    private static final int EJECTOR_CALLS_PER_TICK = 11;
    private BasicInventorySlot spiritSlot;
    private int[] processRequiredTicks;

    public NativeSpiritFactoryBlockEntity(Holder<Block> block, BlockPos pos,
                                          BlockState state) {
        super(block, pos, state,
                List.of(CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_INPUT,
                        CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
                        CachedRecipe.OperationTracker.RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT),
                Set.of(CachedRecipe.OperationTracker.RecipeError.NOT_ENOUGH_ENERGY));
        ejectorComponent.setCanEject(
                type -> level instanceof net.minecraft.server.level.ServerLevel);
        var energyConfig = configComponent.getConfig(TransmissionType.ENERGY);
        if (energyConfig != null) {
            for (mekanism.api.RelativeSide side :
                    mekanism.api.RelativeSide.values()) {
                energyConfig.setDataType(
                        mekanism.common.tile.component.config.DataType.INPUT,
                        side);
            }
        }
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
                new net.neoforged.neoforge.items.ItemStackHandler(23);
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
                                                    IInventorySlot output,
                                                    IInventorySlot secondaryOutput,
                                                    boolean recheck) {
        // TileEntityFactory calls this overload from its auto-sort path with
        // output and secondary-output slots. The spirit source is this
        // factory's shared extra slot, not either of those output slots.
        return findRecipe(process, input, spiritSlot, output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public IMekanismRecipeTypeProvider getRecipeType() {
        return (IMekanismRecipeTypeProvider) MekanismRecipeType.CRUSHING;
    }

    /**
     * Spirit factories reuse Mekanism's item-to-item factory pipeline, so the
     * internal recipe cache is exposed as CRUSHING above. They are not,
     * however, Mekanism crushing factories. Returning a null factory type
     * keeps external integrations such as Mek Energistics from replacing the
     * machine identity with the Crusher profile when an ME upgrade is added.
     */
    @Override
    public FactoryType getFactoryType() {
        return null;
    }

    @Override
    protected int getNeededInput(SpiritFactoryRecipe recipe, ItemStack input) {
        return recipe.inputCount();
    }

    @Override
    protected boolean isCachedRecipeValid(CachedRecipe<SpiritFactoryRecipe> cached,
                                          ItemStack input) {
        if (cached == null || cached.getRecipe() == null) {
            return false;
        }
        return cached.getRecipe().test(input)
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
        CachedRecipe<SpiritFactoryRecipe> cached = (CachedRecipe) OneInputCachedRecipe.itemToItem(recipe, () -> true,
                        inputHandlers[process], outputHandlers[process])
                .setCanHolderFunction(() -> spiritSlot != null
                        && OccultismRecipeBridge.isSpiritSource(
                                spiritSlot.getStack()))
                .setActive(active -> setActiveState(active, process))
                .setEnergyRequirements(
                        () -> mekanism.common.util.MekanismUtils.getEnergyPerTick(this, 400),
                        energyContainer)
                .setRequiredTicks(
                        () -> mekanism.common.util.MekanismUtils.getTicks(this, recipe.duration()))
                .setOperatingTicksChanged(value -> progress[process] = value)
                .setBaselineMaxOperations(this::getOperationsPerTick);
        return cached;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = super.onUpdateServer();
        if (level instanceof net.minecraft.server.level.ServerLevel
                && hasStoredOutput()) {
            for (int call = 0; call < EJECTOR_CALLS_PER_TICK; call++) {
                ejectorComponent.tickServer();
            }
        }
        return changed;
    }

    private boolean hasStoredOutput() {
        for (IInventorySlot slot : outputSlots) {
            if (!slot.getStack().isEmpty()) {
                return true;
            }
        }
        return false;
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

    @Override
    public void recalculateUpgrades(Upgrade upgrade) {
        super.recalculateUpgrades(upgrade);
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
