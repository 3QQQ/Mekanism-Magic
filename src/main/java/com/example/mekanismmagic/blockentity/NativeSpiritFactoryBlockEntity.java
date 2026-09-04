package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.api.IRecipeEntityDisplay;
import com.example.mekanismmagic.api.IRecipeItemDisplay;
import com.example.mekanismmagic.api.RecipeEntityDisplayState;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.OccultismSpiritPatternValidator;
import com.example.mekanismmagic.integration.occultism.SpiritFactoryRecipe;
import mekanism.api.AutomationType;
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
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Mekanism factory-backed spirit processor. The factory cache/parallel process
 * pipeline is native Mekanism; only recipe discovery is delegated to
 * OccultismRecipeBridge.
 */
public final class NativeSpiritFactoryBlockEntity
        extends TileEntityItemToItemFactory<SpiritFactoryRecipe>
        implements IMekanismMagicAutomation, IRecipeEntityDisplay,
        IRecipeItemDisplay {
    private static final String TRADE_NONCES = "spirit_trade_nonces";
    private static final String TRADE_SALT = "spirit_trade_salt";
    private BasicInventorySlot spiritSlot;
    private int[] processRequiredTicks;
    private long[] spiritTradeNonces;
    private long spiritTradeSalt;
    private long observedSpiritProcessingRevision = Long.MIN_VALUE;
    private final RecipeEntityDisplayState recipeEntityDisplay =
            new RecipeEntityDisplayState();
    private final RecipeItemDisplayState recipeItemDisplay =
            new RecipeItemDisplayState();

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
        DefaultMachineSideConfig.apply(configComponent);
        ensureProcessTickCapacity(tier.processes - 1);
    }

    @Override
    protected void addSlots(InventorySlotHelper helper,
                            mekanism.api.IContentsListener listener,
                            mekanism.api.IContentsListener recipeCacheListener) {
        super.addSlots(helper, listener, recipeCacheListener);
        mekanism.api.IContentsListener spiritListener = () -> {
            recipeCacheListener.onContentsChanged();
            com.example.mekanismmagic.integration.common.network
                    .PatternAutomationRefreshHooks.request(this);
        };
        spiritSlot = BasicInventorySlot.at(
                (stack, automation) -> automation == AutomationType.MANUAL
                        && canRemoveSpiritSource(),
                (stack, automation) -> true,
                OccultismRecipeBridge::isSpiritSource,
                spiritListener, 7, 57);
        helper.addSlot(spiritSlot);
        processRequiredTicks = new int[tier.processes];
        ensureTradeNonceCapacity(tier.processes - 1);
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
        long nonce = spiritTradeNonce(process);
        return OccultismRecipeBridge.findSpiritMachineRecipe(level, inventory,
                        extra.getStack(), spiritSelectionSeed(process, nonce))
                .filter(result -> !result.recipe().output().isEmpty())
                .map(result -> new SpiritFactoryRecipe(input,
                        extra.getStack(), result.recipe(), nonce,
                        result.randomTrade(),
                        () -> spiritSlot == null ? ItemStack.EMPTY
                                : spiritSlot.getStack(),
                        () -> spiritTradeNonce(process)))
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
                && cached.getRecipe().sameInput(input)
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

    /** Empty process inputs or an empty shared spirit slot are idle state. */
    public boolean hasFactoryWarningContext(int process) {
        return process >= 0 && process < inputSlots.size()
                && !inputSlots.get(process).getStack().isEmpty()
                && spiritSlot != null && !spiritSlot.getStack().isEmpty();
    }

    private boolean hasAnyFactoryWarningContext() {
        if (spiritSlot == null || spiritSlot.getStack().isEmpty()) {
            return false;
        }
        return inputSlots.stream().anyMatch(
                slot -> !slot.getStack().isEmpty());
    }

    @Override
    public BooleanSupplier getWarningCheck(
            CachedRecipe.OperationTracker.RecipeError error,
            int processIndex) {
        BooleanSupplier warning = super.getWarningCheck(error, processIndex);
        return error == CachedRecipe.OperationTracker.RecipeError
                .NOT_ENOUGH_ENERGY
                ? () -> hasAnyFactoryWarningContext()
                && warning.getAsBoolean()
                : () -> hasFactoryWarningContext(processIndex)
                && warning.getAsBoolean();
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
                                spiritSlot.getStack())
                        && recipe.sameSource(spiritSlot.getStack())
                        && recipe.sameSelectionNonce(
                        spiritTradeNonce(process))
                        && recipe.sameProcessingRevision())
                .setActive(active -> setActiveState(active, process))
                .setEnergyRequirements(
                        () -> mekanism.common.util.MekanismUtils.getEnergyPerTick(this, 400),
                        energyContainer)
                .setRequiredTicks(
                        () -> mekanism.common.util.MekanismUtils.getTicks(this, recipe.duration()))
                .setOperatingTicksChanged(value -> progress[process] = value)
                .setOnFinish(() -> finishSpiritRecipe(process, recipe))
                .setBaselineMaxOperations(recipe.randomTrade()
                        ? () -> 1 : this::getOperationsPerTick);
        return cached;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean revisionChanged = refreshSpiritProcessingRevision();
        boolean changed = super.onUpdateServer();
        boolean displayChanged = hasAnyFactoryWarningContext()
                ? recipeEntityDisplay.update(spiritSlot.getStack())
                : recipeEntityDisplay.clear();
        boolean itemDisplayChanged = hasAnyFactoryWarningContext()
                ? recipeItemDisplay.updateFactory(inputSlots,
                spiritSlot.getStack(),
                OccultismRecipeBridge.spiritProcessingRevision(),
                process -> progress != null && process >= 0
                        && process < progress.length
                        && progress[process] > 0,
                process -> {
                    SpiritFactoryRecipe recipe = getRecipe(process);
                    return recipe == null
                            || recipe.randomTrade()
                            || recipe.getOutputDefinition().isEmpty()
                            ? ItemStack.EMPTY
                            : recipe.getOutputDefinition().getFirst();
                }) : recipeItemDisplay.clear();
        return revisionChanged || itemDisplayChanged
                || displayChanged || changed;
    }

    @Override
    public RecipeEntityDisplayState mekanismMagicRecipeEntityDisplay() {
        return recipeEntityDisplay;
    }

    @Override
    public RecipeItemDisplayState mekanismMagicRecipeItemDisplay() {
        return recipeItemDisplay;
    }

    @Override
    public net.minecraft.nbt.CompoundTag getReducedUpdateTag(
            net.minecraft.core.HolderLookup.Provider provider) {
        net.minecraft.nbt.CompoundTag tag =
                super.getReducedUpdateTag(provider);
        recipeEntityDisplay.writeUpdateTag(tag);
        recipeItemDisplay.writeUpdateTag(tag, provider);
        return tag;
    }

    @Override
    public void handleUpdateTag(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        recipeEntityDisplay.readUpdateTag(tag);
        recipeItemDisplay.readUpdateTag(tag, provider);
    }

    @Override
    public void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLongArray(TRADE_NONCES,
                spiritTradeNonces == null ? new long[0]
                        : spiritTradeNonces);
        tag.putLong(TRADE_SALT, spiritTradeSalt);
    }

    @Override
    public void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ensureTradeNonceCapacity(tier.processes - 1);
        Arrays.fill(spiritTradeNonces, 0L);
        long[] saved = tag.getLongArray(TRADE_NONCES);
        System.arraycopy(saved, 0, spiritTradeNonces, 0,
                Math.min(saved.length, spiritTradeNonces.length));
        if (saved.length == 0 && tag.contains("spirit_trade_nonce")) {
            Arrays.fill(spiritTradeNonces,
                    tag.getLong("spirit_trade_nonce"));
        }
        spiritTradeSalt = tag.getLong(TRADE_SALT);
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

    private long spiritSelectionSeed(int process, long nonce) {
        return ensureSpiritTradeSalt()
                ^ Long.rotateLeft(nonce, 29)
                ^ (long) process * 0x9E3779B97F4A7C15L;
    }

    private long ensureSpiritTradeSalt() {
        if (spiritTradeSalt != 0L) {
            return spiritTradeSalt;
        }
        spiritTradeSalt = OccultismRecipeBridge.createSpiritTradeSalt();
        setChanged();
        return spiritTradeSalt;
    }

    private void finishSpiritRecipe(int process,
                                    SpiritFactoryRecipe recipe) {
        if (!recipe.randomTrade()) {
            return;
        }
        ensureTradeNonceCapacity(process);
        spiritTradeNonces[process] = spiritTradeNonces[process]
                == Long.MAX_VALUE ? 0L : spiritTradeNonces[process] + 1L;
        if (recipeCacheLookupMonitors != null && process >= 0
                && process < recipeCacheLookupMonitors.length
                && recipeCacheLookupMonitors[process] != null) {
            recipeCacheLookupMonitors[process].onChange();
        }
        setChanged();
    }

    private void ensureTradeNonceCapacity(int process) {
        int requiredLength = Math.max(tier.processes, process + 1);
        if (spiritTradeNonces == null) {
            spiritTradeNonces = new long[requiredLength];
        } else if (spiritTradeNonces.length < requiredLength) {
            spiritTradeNonces = Arrays.copyOf(
                    spiritTradeNonces, requiredLength);
        }
    }

    private long spiritTradeNonce(int process) {
        ensureTradeNonceCapacity(process);
        return process < 0 || process >= spiritTradeNonces.length
                ? 0L : spiritTradeNonces[process];
    }

    private boolean refreshSpiritProcessingRevision() {
        long revision = OccultismRecipeBridge.spiritProcessingRevision();
        if (observedSpiritProcessingRevision == revision) {
            return false;
        }
        boolean initialObservation = observedSpiritProcessingRevision
                == Long.MIN_VALUE;
        observedSpiritProcessingRevision = revision;
        if (progress != null) {
            if (initialObservation) {
                for (int process = 0; process < progress.length; process++) {
                    if (progress[process] > 0 && getRecipe(process) == null) {
                        progress[process] = 0;
                        if (processRequiredTicks != null
                                && process < processRequiredTicks.length) {
                            processRequiredTicks[process] = 0;
                        }
                    }
                }
            } else {
                Arrays.fill(progress, 0);
            }
        }
        if (!initialObservation && processRequiredTicks != null) {
            Arrays.fill(processRequiredTicks, 0);
        }
        if (recipeCacheLookupMonitors != null) {
            for (var monitor : recipeCacheLookupMonitors) {
                if (monitor != null) {
                    monitor.onChange();
                }
            }
        }
        com.example.mekanismmagic.integration.common.network
                .PatternAutomationRefreshHooks.request(this);
        return true;
    }

    private boolean canRemoveSpiritSource() {
        if (progress != null) {
            for (int value : progress) {
                if (value > 0) {
                    return false;
                }
            }
        }
        return (inputSlots == null || inputSlots.stream()
                .allMatch(slot -> slot.getStack().isEmpty()))
                && !com.example.mekanismmagic.integration.common.network
                .PatternAutomationRefreshHooks.hasPendingPatternWork(this);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack, Direction side) {
        IInventorySlot target = getInventorySlot(slot, side);
        if (target == null || target == spiritSlot
                && !PersistentInputMutationGuard.permits(target, stack)) {
            return;
        }
        // Preserve Mekanism's default behavior for every non-context slot;
        // internal GUI/NBT/upgrade paths write the slot directly.
        target.setStack(stack);
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
                spiritSlot == null ? ItemStack.EMPTY : spiritSlot.getStack(),
                processRequiredTicks == null
                        ? new int[0] : processRequiredTicks,
                java.util.Map.of(), spiritTradeNonces == null
                        ? new long[0] : spiritTradeNonces,
                spiritTradeSalt);
    }

    @Override
    public void parseUpgradeData(net.minecraft.core.HolderLookup.Provider registries,
                                 IUpgradeData data) {
        super.parseUpgradeData(registries, data);
        if (data instanceof SpiritMachineUpgradeData spiritUpgrade) {
            if (spiritSlot != null) {
                spiritSlot.setStack(spiritUpgrade.spiritSource.copy());
            }
            processRequiredTicks = Arrays.copyOf(
                    spiritUpgrade.requiredTicks, tier.processes);
            ensureTradeNonceCapacity(tier.processes - 1);
            Arrays.fill(spiritTradeNonces, 0L);
            System.arraycopy(spiritUpgrade.spiritTradeNonces, 0,
                    spiritTradeNonces, 0, Math.min(
                            spiritUpgrade.spiritTradeNonces.length,
                            spiritTradeNonces.length));
            spiritTradeSalt = spiritUpgrade.spiritTradeSalt;
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
    public boolean mekanismMagicGroupParallelItemInputs() {
        return true;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return true;
    }

    @Override
    public boolean mekanismMagicCanAdvertisePatterns() {
        return spiritSlot != null && !spiritSlot.getStack().isEmpty();
    }

    @Override
    public boolean mekanismMagicUsesContextualPatternValidation() {
        return true;
    }

    @Override
    public boolean mekanismMagicMatchesPattern(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        return spiritSlot != null
                && OccultismSpiritPatternValidator.matches(level,
                spiritSlot.getStack(), inputs, outputs);
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
