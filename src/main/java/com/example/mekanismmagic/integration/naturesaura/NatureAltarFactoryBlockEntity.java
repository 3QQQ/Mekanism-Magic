package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.blockentity.NativeMachineUpgradeData;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IRecipeItemDisplay;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryWindowSlot;
import com.example.mekanismmagic.blockentity.DefaultMachineSideConfig;
import com.example.mekanismmagic.blockentity.PersistentInputMutationGuard;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.AutomationType;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.tile.factory.TileEntityItemToItemFactory;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Parallel compact altars backed by Mekanism's native factory scheduler.
 *
 * <p>All lanes share retained catalysts and one Aura buffer, but own independent
 * recipe locks and progress. Aura is charged atomically immediately before a
 * lane commits its input/output transaction.</p>
 */
public final class NatureAltarFactoryBlockEntity
        extends TileEntityItemToItemFactory<NatureAltarFactoryRecipe>
        implements IMekanismMagicAutomation, IRecipeItemDisplay,
        NatureAltarAutomationHost, AuraSideModeHost {
    public static final CachedRecipe.OperationTracker.RecipeError
            NOT_ENOUGH_AURA = AuraAwareNatureAltarCachedRecipe
            .NOT_ENOUGH_AURA;

    private static final String AURA_DATA = "nature_altar_factory_aura";
    private static final String LOCK_DATA = "nature_altar_factory_locks";
    private static final String LOCK_PREFIX = "lane_";
    private static final String SIGNATURE_PREFIX = "signature_";
    private static final String CONTEXT_REMAINING =
            "nature_altar_factory_context_remaining";
    private static final String REQUIRED_TICKS =
            "nature_altar_factory_required_ticks";
    private static final String PREFERRED_RECIPE =
            "nature_altar_preferred_recipe";
    private static final String CATALYST_SLOT_COUNT =
            "nature_altar_catalyst_slots";

    private BasicInventorySlot catalystSlot;
    private BasicInventorySlot secondCatalystSlot;
    private NatureAltarCatalystLibrary catalystLibrary;
    private IContentsListener catalystListener;
    private NatureAuraStorageComponent auraStorage;
    private ResourceLocation[] lockedRecipes;
    private String[] lockedRecipeSignatures;
    private int[] contextRemaining;
    private int[] requiredTicks;
    private ResourceLocation preferredRecipe;
    private int matchingRecipeCount;
    private ItemStack selectedRecipeOutput = ItemStack.EMPTY;
    private boolean suppressNativeSorting;
    private long observedCatalogVersion = Long.MIN_VALUE;
    private long lastDisplayCatalogVersion = Long.MIN_VALUE;
    private final RecipeItemDisplayState recipeItemDisplay =
            new RecipeItemDisplayState();

    public NatureAltarFactoryBlockEntity(Holder<Block> block, BlockPos pos,
                                         BlockState state) {
        super(block, pos, state,
                List.of(CachedRecipe.OperationTracker.RecipeError
                                .NOT_ENOUGH_INPUT,
                        CachedRecipe.OperationTracker.RecipeError
                                .NOT_ENOUGH_OUTPUT_SPACE,
                        CachedRecipe.OperationTracker.RecipeError
                                .INPUT_DOESNT_PRODUCE_OUTPUT,
                        NOT_ENOUGH_AURA),
                Set.of(CachedRecipe.OperationTracker.RecipeError
                                .NOT_ENOUGH_ENERGY,
                        NOT_ENOUGH_AURA));
        ensureProcessState();
        auraStorage = new NatureAuraStorageComponent(
                this::getLevel,
                this::hasCreativeAuraUpgrade,
                this::setChanged,
                () -> {
                    if (level != null) {
                        level.invalidateCapabilities(worldPosition);
                    }
                },
                this::wakeAuraRecipes,
                NatureAuraMachineConfig.altarFactoryCapacity(tier),
                NatureAuraMachineConfig.altarFactoryTransfer(tier),
                NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        var energyConfig = configComponent.getConfig(TransmissionType.ENERGY);
        if (energyConfig != null) {
            for (mekanism.api.RelativeSide side
                    : mekanism.api.RelativeSide.values()) {
                energyConfig.setDataType(
                        mekanism.common.tile.component.config.DataType.INPUT,
                        side);
            }
        }
        var itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null && secondCatalystSlot != null) {
            itemConfig.addSlotInfo(DataType.EXTRA,
                    new InventorySlotInfo(true, true,
                            List.of(catalystSlot, secondCatalystSlot)));
        }
        DefaultMachineSideConfig.apply(configComponent);
    }

    @Override
    protected void addSlots(InventorySlotHelper helper,
                            IContentsListener listener,
                            IContentsListener recipeListener) {
        super.addSlots(helper, listener, recipeListener);
        catalystListener = markAllMonitorsChanged(recipeListener);
        catalystSlot = BasicInventorySlot.at(
                (stack, automation) -> automation == AutomationType.MANUAL,
                (stack, automation) -> true,
                stack -> NatureAuraRecipeBridge.isAltarCatalyst(
                        level, stack), catalystListener,
                NatureAltarFactoryLayout.CATALYST_SLOT_X,
                NatureAltarFactoryLayout.CATALYST_SLOT_Y);
        helper.addSlot(catalystSlot);
        secondCatalystSlot = BasicInventorySlot.at(
                (stack, automation) -> automation == AutomationType.MANUAL,
                (stack, automation) -> true,
                stack -> NatureAuraRecipeBridge.isAltarCatalyst(
                        level, stack), catalystListener,
                NatureAltarFactoryLayout.SECOND_CATALYST_SLOT_X,
                NatureAltarFactoryLayout.SECOND_CATALYST_SLOT_Y);
        helper.addSlot(secondCatalystSlot);
        catalystLibrary = new NatureAltarCatalystLibrary(
                this::getLevel,
                () -> NatureAltarFactoryLayout.imageWidth(tier),
                catalystListener::onContentsChanged);
        for (CatalystLibraryWindowSlot slot
                : catalystLibrary.windowSlots()) {
            helper.addSlot(slot);
        }
        ensureProcessState();
    }

    @Override
    protected IInventorySlot getExtraSlot() {
        return catalystSlot;
    }

    @Override
    protected NatureAltarFactoryRecipe findRecipe(
            int process, ItemStack input, IInventorySlot extra,
            IInventorySlot output) {
        ensureProcessState();
        if (process < 0 || process >= tier.processes || input.isEmpty()) {
            return null;
        }
        ResourceLocation previous = lockedRecipes[process];
        if (previous != null && contextRemaining[process] > 0) {
            ItemStack cleanInput = NatureAuraRecipeBridge
                    .recipeInputView(input);
            var locked = level == null ? java.util.Optional
                    .<NatureAuraRecipeBridge.AltarRecipeView>empty()
                    : NatureAuraRecipeScanner.find(
                    level.getRecipeManager(), previous);
            String currentSignature = locked
                    .map(NatureAuraRecipeBridge::recipeSignature)
                    .orElse("");
            boolean inputMatches = locked
                    .filter(recipe -> recipe.input().test(cleanInput))
                    .isPresent();
            if (!NatureAltarLockedContextPolicy.remainsValid(
                    locked.isPresent(), inputMatches,
                    lockedRecipeSignatures[process], currentSignature)) {
                return null;
            }
        }
        boolean patternMarked = NatureAuraRecipeBridge
                .hasPatternRecipeMarker(input);
        ResourceLocation patternRecipe = patternMarked
                ? NatureAuraRecipeBridge.patternRecipe(input) : null;
        var selected = patternMarked
                ? findExactAutomationRecipe(input, patternRecipe)
                : NatureAuraRecipeBridge.findAltarRecipe(
                level, input, getCatalystStacks(), previous,
                preferredRecipe);
        if (selected.isEmpty()) {
            if (!patternMarked && previous != null) {
                clearLaneLock(process);
            }
            return null;
        }
        ItemStack existingOutput = output == null
                ? ItemStack.EMPTY : output.getStack();
        ItemStack recipeOutput = selected.get().output();
        if (!existingOutput.isEmpty()
                && (!ItemStack.isSameItemSameComponents(
                existingOutput, recipeOutput)
                || existingOutput.getCount() + recipeOutput.getCount()
                > existingOutput.getMaxStackSize())) {
            return null;
        }
        ResourceLocation selectedId = selected.get().id();
        String selectedSignature = NatureAuraRecipeBridge
                .recipeSignature(selected.get());
        boolean sameSelection = selectedId.equals(previous)
                && (lockedRecipeSignatures[process].isBlank()
                || lockedRecipeSignatures[process]
                .equals(selectedSignature));
        if (!sameSelection && previous != null && progress[process] > 0) {
            return null;
        }
        if (!sameSelection) {
            var lane = NatureAltarFactoryLanePolicy.reconcile(
                    previous, selectedId, progress[process]);
            lockedRecipes[process] = lane.lockedRecipe();
            progress[process] = lane.progress();
            lockedRecipeSignatures[process] = selectedSignature;
            markForSave();
        } else if (lockedRecipeSignatures[process].isBlank()) {
            lockedRecipeSignatures[process] = selectedSignature;
            markForSave();
        }
        if (patternMarked) {
            if (contextRemaining[process] <= 0) {
                contextRemaining[process] = Math.max(1, input.getCount());
            } else {
                contextRemaining[process] = Math.max(
                        contextRemaining[process], input.getCount());
            }
            ItemStack clean = NatureAuraRecipeBridge
                    .clearPatternRecipeMarker(
                            inputSlots.get(process).getStack());
            if (!ItemStack.isSameItemSameComponents(
                    clean, inputSlots.get(process).getStack())) {
                inputSlots.get(process).setStack(clean);
            }
        }
        return new NatureAltarFactoryRecipe(selected.get(),
                level == null ? 0L : NatureAuraRecipeScanner.version(
                        level.getRecipeManager()));
    }

    @Override
    protected NatureAltarFactoryRecipe getRecipeForInput(
            int process, ItemStack input, IInventorySlot output,
            IInventorySlot secondaryOutput, boolean recheck) {
        return findRecipe(process, input, catalystSlot, output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public IMekanismRecipeTypeProvider getRecipeType() {
        return (IMekanismRecipeTypeProvider) MekanismRecipeType.SMELTING;
    }

    /** Prevents integrations from treating this as a vanilla smelting factory. */
    @Override
    public FactoryType getFactoryType() {
        return null;
    }

    @Override
    public boolean isSorting() {
        return !suppressNativeSorting && super.isSorting();
    }

    @Override
    protected int getNeededInput(NatureAltarFactoryRecipe recipe,
                                 ItemStack input) {
        return 1;
    }

    @Override
    protected boolean isCachedRecipeValid(
            CachedRecipe<NatureAltarFactoryRecipe> cached,
            ItemStack input) {
        return cached != null && cached.getRecipe() != null
                && cached.getRecipe().test(NatureAuraRecipeBridge
                .recipeInputView(input))
                && level != null
                && cached.getRecipe().catalogVersion()
                == NatureAuraRecipeScanner.version(
                level.getRecipeManager())
                && NatureAuraRecipeScanner.find(level.getRecipeManager(),
                cached.getRecipe().altarRecipeId()).isPresent();
    }

    @Override
    public boolean isItemValidForSlot(ItemStack stack) {
        return isValidAltarInput(stack);
    }

    @Override
    public boolean isValidInputItem(ItemStack stack) {
        return isValidAltarInput(stack);
    }

    @Override
    public NatureAltarFactoryRecipe getRecipe(int process) {
        return process < 0 || process >= inputSlots.size() ? null
                : findRecipe(process, inputSlots.get(process).getStack(),
                catalystSlot, outputSlots.get(process));
    }

    @Override
    public CachedRecipe<NatureAltarFactoryRecipe> createNewCachedRecipe(
            NatureAltarFactoryRecipe recipe, int process) {
        ensureProcessState();
        requiredTicks[process] = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(
                        this, recipe.duration()));
        @SuppressWarnings({"rawtypes", "unchecked"})
        CachedRecipe<NatureAltarFactoryRecipe> cached =
                (CachedRecipe) new AuraAwareNatureAltarCachedRecipe(
                        recipe, recheckAllRecipeErrors[process],
                        inputHandlers[process], outputHandlers[process],
                        () -> laneLockMatches(process,
                                recipe.altarRecipeId(),
                                recipe.recipeSignature())
                                && level != null
                                && recipe.catalogVersion()
                                == NatureAuraRecipeScanner.version(
                                level.getRecipeManager()),
                        this::hasCreativeAuraUpgrade,
                        () -> {
                            boolean available = recipe.auraCost() <= 0
                                    || auraStorage.drainExact(
                                    recipe.auraCost(), true);
                            if (!available) {
                                auraStorage.setNotEnoughAura(true);
                            }
                            return available;
                        },
                        () -> hasCreativeAuraUpgrade()
                                || recipe.auraCost() <= 0
                                || auraStorage.drainExact(
                                recipe.auraCost(), false),
                        () -> onLaneRecipeFinished(process))
                        .setCanHolderFunction(() -> canFunction()
                                && laneLockMatches(process,
                                recipe.altarRecipeId(),
                                recipe.recipeSignature()))
                        .setActive(active -> setActiveState(active, process))
                        .setEnergyRequirements(
                                () -> mekanism.common.util.MekanismUtils
                                        .getEnergyPerTick(this,
                                                NatureAuraMachineConfig
                                                        .ALTAR_BASE_ENERGY_PER_TICK),
                                energyContainer)
                        .setRequiredTicks(() -> requiredTicks[process])
                        .setOperatingTicksChanged(value ->
                                progress[process] = value)
                        .setErrorsChanged(errors -> errorTracker
                                .onErrorsChanged(errors, process))
                        .setOnFinish(this::markForSave)
                        .setBaselineMaxOperations(() -> 1);
        return cached;
    }

    @Override
    protected boolean onUpdateServer() {
        ensureProcessState();
        auraStorage.refreshTypeState();
        long catalogVersion = level == null ? 0L
                : NatureAuraRecipeScanner.version(
                level.getRecipeManager());
        boolean catalogChanged = catalogVersion != observedCatalogVersion;
        if (catalogChanged) {
            observedCatalogVersion = catalogVersion;
            if (catalystLibrary != null
                    && catalystLibrary.clampPage()) {
                markForSave();
            }
            if (recipeCacheLookupMonitors != null) {
                for (var monitor : recipeCacheLookupMonitors) {
                    if (monitor != null) {
                        // Wake both a cached recipe and Mekanism's
                        // hasNoRecipe negative cache after /reload.
                        monitor.onChange();
                    }
                }
            }
        }
        for (int process = 0; process < tier.processes; process++) {
            ItemStack input = NatureAuraRecipeBridge.recipeInputView(
                    inputSlots.get(process).getStack());
            ResourceLocation locked = lockedRecipes[process];
            if (locked == null) {
                continue;
            }
            var current = level == null ? java.util.Optional
                    .<NatureAuraRecipeBridge.AltarRecipeView>empty()
                    : NatureAuraRecipeScanner.find(
                    level.getRecipeManager(), locked);
            boolean inputMatches = !input.isEmpty()
                    && current.filter(recipe -> NatureAuraRecipeBridge
                    .matchesLockedRecipe(recipe, input)).isPresent();
            String currentSignature = current
                    .map(NatureAuraRecipeBridge::recipeSignature)
                    .orElse("");
            boolean lockValid = NatureAltarLockedContextPolicy
                    .remainsValid(current.isPresent(), inputMatches,
                            lockedRecipeSignatures[process],
                            currentSignature);
            if (!lockValid) {
                if (NatureAltarLockedContextPolicy.quarantineInvalid(
                        lockValid, contextRemaining[process],
                        input.getCount())) {
                    // Quarantine already delivered exact-pattern inputs.
                    // Clearing the lock here would silently run them as the
                    // new recipe that reused this id after /reload.
                    if (progress[process] != 0) {
                        progress[process] = 0;
                        markForSave();
                    }
                } else {
                    clearLaneLock(process);
                }
            } else if (lockedRecipeSignatures[process].isBlank()) {
                lockedRecipeSignatures[process] = currentSignature;
                markForSave();
            }
            if (lockedRecipes[process] != null
                    && contextRemaining[process] > 0) {
                int clamped = NatureAltarContextCountPolicy.clampToInput(
                        contextRemaining[process], input.getCount());
                if (clamped != contextRemaining[process]) {
                    contextRemaining[process] = clamped;
                    markForSave();
                }
            }
        }
        boolean splitChanged = super.isSorting()
                && NatureAltarFactoryAutoSplit.balance(
                level, inputSlots, outputSlots, getCatalystStacks(),
                preferredRecipe,
                lockedRecipes, lockedRecipeSignatures, contextRemaining,
                progress);
        if (splitChanged) {
            markForSave();
        }
        // Mekanism's private sorter only moves stacks, not the recipe context
        // bound to each lane. Keep its persisted toggle but use our guarded
        // sorter above for this altar instead.
        boolean changed;
        suppressNativeSorting = true;
        try {
            changed = super.onUpdateServer();
        } finally {
            suppressNativeSorting = false;
        }
        boolean hasInput = inputSlots.stream()
                .anyMatch(slot -> !slot.getStack().isEmpty());
        if (!hasInput) {
            auraStorage.setNotEnoughAura(false);
        }
        boolean displayChanged = false;
        if (catalogVersion != lastDisplayCatalogVersion) {
            lastDisplayCatalogVersion = catalogVersion;
            displayChanged = recipeItemDisplay.clear();
        }
        long displayContextRevision = 31L * catalogVersion
                + ItemStack.hashStackList(getCatalystStacks());
        displayContextRevision = 31L * displayContextRevision
                + (preferredRecipe == null ? 0 : preferredRecipe.hashCode());
        displayChanged |= hasInput
                ? recipeItemDisplay.updateFactory(inputSlots,
                getCatalystStack(), displayContextRevision,
                process -> true, process -> {
                    NatureAltarFactoryRecipe recipe = getRecipe(process);
                    return recipe == null
                            || recipe.getOutputDefinition().isEmpty()
                            ? ItemStack.EMPTY
                            : recipe.getOutputDefinition().getFirst();
                }) : recipeItemDisplay.clear();
        boolean choiceChanged = refreshRecipeChoiceDisplay();
        return catalogChanged || splitChanged || changed || displayChanged
                || choiceChanged;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack, Direction side) {
        IInventorySlot target = getInventorySlot(slot, side);
        if ((target == catalystSlot || target == secondCatalystSlot
                || catalystLibrary != null
                && catalystLibrary.isWindowSlot(target))
                && !PersistentInputMutationGuard.permits(target, stack)) {
            return;
        }
        super.setStackInSlot(slot, stack, side);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        auraStorage.addContainerTrackers(container);
        if (catalystLibrary != null) {
            catalystLibrary.addContainerTrackers(container);
        }
        for (int process = 0; process < tier.processes; process++) {
            int index = process;
            container.track(mekanism.common.inventory.container.sync
                    .SyncableInt.create(
                            () -> requiredTicks[index],
                            value -> requiredTicks[index] =
                                    Math.max(1, value)));
        }
        container.track(SyncableInt.create(this::getMatchingRecipeCount,
                value -> matchingRecipeCount = Math.max(0, value)));
        container.track(SyncableItemStack.create(
                this::getSelectedRecipeOutput,
                value -> selectedRecipeOutput = value == null
                        ? ItemStack.EMPTY : value.copy()));
    }

    @Override
    public double getScaledProgress(int scale, int process) {
        ensureProcessState();
        return process < 0 || process >= progress.length
                || requiredTicks[process] <= 0 ? 0
                : progress[process] * (double) scale
                / requiredTicks[process];
    }

    public ItemStack getCatalystStack() {
        return catalystSlot == null ? ItemStack.EMPTY
                : catalystSlot.getStack();
    }

    /** Physical catalyst order, with the old single slot always first. */
    public List<ItemStack> getCatalystStacks() {
        return catalystLibrary != null
                ? catalystLibrary.stacks(getCatalystStack(),
                secondCatalystSlot == null ? ItemStack.EMPTY
                        : secondCatalystSlot.getStack())
                : secondCatalystSlot == null
                ? List.of(getCatalystStack())
                : List.of(getCatalystStack(),
                secondCatalystSlot.getStack());
    }

    public int catalystPage() {
        return catalystLibrary == null ? 0 : catalystLibrary.page();
    }

    public int catalystPageCount() {
        return catalystLibrary == null ? 1 : catalystLibrary.pageCount();
    }

    public int catalystVisibleSlotCount() {
        return catalystLibrary == null ? 0
                : catalystLibrary.visibleSlotCount();
    }

    public void cycleCatalystPage(int delta) {
        if (catalystLibrary != null) {
            catalystLibrary.cyclePage(delta);
        }
    }

    public void setCatalystLibraryOpen(boolean open) {
        if (catalystLibrary != null) {
            catalystLibrary.setOpen(open);
        }
    }

    public ItemStack catalystPageStack(int pageSlot) {
        return catalystLibrary == null ? ItemStack.EMPTY
                : catalystLibrary.pageStack(pageSlot);
    }

    public List<CatalystLibraryWindowSlot> catalystLibraryWindowSlots() {
        return catalystLibrary == null ? List.of()
                : catalystLibrary.windowSlots();
    }

    public int getMatchingRecipeCount() {
        return matchingRecipeCount;
    }

    public ItemStack getSelectedRecipeOutput() {
        return selectedRecipeOutput.copy();
    }

    public void cycleMatchingRecipe() {
        if (level == null || level.isClientSide || inputSlots == null) {
            return;
        }
        ensureProcessState();
        List<ItemStack> inputs = inputSlots.stream()
                .map(IInventorySlot::getStack).toList();
        List<ItemStack> catalysts = getCatalystStacks();
        var choice = NatureAltarRecipeChoices.firstAmbiguous(level,
                inputs, catalysts, lane -> contextRemaining[lane] == 0
                        && progress[lane] == 0);
        if (choice.isEmpty()) {
            return;
        }
        ResourceLocation next = choice.get().next(level, catalysts,
                preferredRecipe);
        if (next.equals(preferredRecipe)) {
            return;
        }
        preferredRecipe = next;
        for (int lane = 0; lane < tier.processes; lane++) {
            if (lockedRecipes[lane] != null && contextRemaining[lane] == 0
                    && progress[lane] == 0
                    && !NatureAuraRecipeBridge.hasPatternRecipeMarker(
                    inputSlots.get(lane).getStack())) {
                clearLaneLock(lane);
            }
        }
        if (recipeCacheLookupMonitors != null) {
            for (var monitor : recipeCacheLookupMonitors) {
                if (monitor != null) {
                    monitor.onChange();
                }
            }
        }
        refreshRecipeChoiceDisplay();
        markForSave();
        sendUpdatePacket();
    }

    private boolean refreshRecipeChoiceDisplay() {
        if (inputSlots == null) {
            return false;
        }
        ensureProcessState();
        List<ItemStack> inputs = inputSlots.stream()
                .map(IInventorySlot::getStack).toList();
        List<ItemStack> catalysts = getCatalystStacks();
        var choice = NatureAltarRecipeChoices.firstAmbiguous(level,
                inputs, catalysts, lane -> contextRemaining[lane] == 0
                        && progress[lane] == 0);
        int nextCount = choice.map(value -> value.recipes().size())
                .orElse(0);
        ItemStack nextOutput = choice.map(value -> value.selectedOutput(
                level, catalysts, preferredRecipe))
                .orElse(ItemStack.EMPTY);
        if (nextCount == matchingRecipeCount
                && ItemStack.matches(nextOutput, selectedRecipeOutput)) {
            return false;
        }
        matchingRecipeCount = nextCount;
        selectedRecipeOutput = nextOutput;
        return true;
    }

    public int getStoredAura() {
        return auraStorage.getStoredAura();
    }

    public int getAuraCapacity() {
        return auraStorage.getAuraCapacity();
    }

    public int getAuraColor() {
        return auraStorage.getAuraColor();
    }

    public boolean hasAuraTypeMismatch() {
        return auraStorage.hasAuraTypeMismatch();
    }

    public boolean hasNotEnoughAura() {
        return hasAnyFactoryWarningContext()
                && super.getWarningCheck(NOT_ENOUGH_AURA, 0)
                .getAsBoolean();
    }

    public @Nullable IAuraContainer getAuraContainer(
            @Nullable Direction side) {
        return auraStorage.getAuraContainer(side);
    }

    public NatureAuraMachineBlockEntity.AuraSideMode getAuraSideMode(
            Direction side) {
        return auraStorage.getAuraSideMode(side);
    }

    public void cycleAuraSideMode(int directionIndex) {
        auraStorage.cycleAuraSideMode(directionIndex);
    }

    @Override
    public void setAuraSideMode(Direction side,
                               NatureAuraMachineBlockEntity.AuraSideMode mode) {
        auraStorage.setAuraSideMode(side, mode);
    }

    @Override
    public void setAuraSideModes(java.util.Map<Direction,
            NatureAuraMachineBlockEntity.AuraSideMode> modes) {
        auraStorage.setAuraSideModes(modes);
    }

    @Override
    public boolean isCreativeAuraActive() {
        return auraStorage.isCreativeAuraActive();
    }

    private boolean hasCreativeAuraUpgrade() {
        return getComponent() != null && getComponent().getUpgrades(
                MagicUpgrades.creativeMagic()) > 0;
    }

    private boolean isValidAltarInput(ItemStack stack) {
        return NatureAuraRecipeBridge.isAltarInput(level, stack);
    }

    /** Aura-only state changes must wake caches paused on the shared error. */
    private void wakeAuraRecipes() {
        if (auraStorage != null) {
            auraStorage.setNotEnoughAura(false);
        }
        if (recipeCacheLookupMonitors != null) {
            for (var monitor : recipeCacheLookupMonitors) {
                if (monitor != null) {
                    monitor.unpause();
                }
            }
        }
    }

    private boolean laneLockMatches(int process, ResourceLocation id,
                                    String signature) {
        ensureProcessState();
        return process >= 0 && process < lockedRecipes.length
                && id.equals(lockedRecipes[process])
                && signature != null
                && signature.equals(lockedRecipeSignatures[process]);
    }

    private void clearLaneLock(int process) {
        if (process < 0 || process >= lockedRecipes.length) {
            return;
        }
        var lane = NatureAltarFactoryLanePolicy
                .<ResourceLocation>completed();
        lockedRecipes[process] = lane.lockedRecipe();
        lockedRecipeSignatures[process] = "";
        contextRemaining[process] = 0;
        progress[process] = lane.progress();
        markForSave();
    }

    private void onLaneRecipeFinished(int process) {
        if (process < 0 || process >= contextRemaining.length) {
            return;
        }
        if (contextRemaining[process] > 0) {
            contextRemaining[process] = NatureAltarContextCountPolicy
                    .afterCompletion(contextRemaining[process]);
            if (contextRemaining[process] > 0) {
                markForSave();
                return;
            }
        }
        clearLaneLock(process);
    }

    private void ensureProcessState() {
        int processes = tier == null ? 1 : tier.processes;
        if (lockedRecipes == null || lockedRecipes.length != processes) {
            lockedRecipes = lockedRecipes == null
                    ? new ResourceLocation[processes]
                    : Arrays.copyOf(lockedRecipes, processes);
        }
        if (lockedRecipeSignatures == null
                || lockedRecipeSignatures.length != processes) {
            lockedRecipeSignatures = lockedRecipeSignatures == null
                    ? new String[processes]
                    : Arrays.copyOf(lockedRecipeSignatures, processes);
            for (int index = 0; index < lockedRecipeSignatures.length;
                    index++) {
                if (lockedRecipeSignatures[index] == null) {
                    lockedRecipeSignatures[index] = "";
                }
            }
        }
        if (contextRemaining == null
                || contextRemaining.length != processes) {
            contextRemaining = contextRemaining == null
                    ? new int[processes]
                    : Arrays.copyOf(contextRemaining, processes);
            for (int index = 0; index < contextRemaining.length; index++) {
                contextRemaining[index] = Math.max(0,
                        contextRemaining[index]);
            }
        }
        if (requiredTicks == null || requiredTicks.length != processes) {
            requiredTicks = requiredTicks == null
                    ? new int[processes]
                    : Arrays.copyOf(requiredTicks, processes);
            for (int index = 0; index < requiredTicks.length; index++) {
                requiredTicks[index] = Math.max(1,
                        requiredTicks[index]);
            }
        }
    }

    @Override
    public NatureAltarFactoryUpgradeData getUpgradeData(
            HolderLookup.Provider registries) {
        ensureProcessState();
        EnergyInventorySlot currentEnergySlot = null;
        for (IInventorySlot slot : getInventorySlots(null)) {
            if (slot instanceof EnergyInventorySlot energy) {
                currentEnergySlot = energy;
                break;
            }
        }
        return NativeMachineUpgradeData.capture(new NatureAltarFactoryUpgradeData(registries, redstone,
                getControlType(), energyContainer, progress,
                currentEnergySlot, inputSlots, outputSlots, isSorting(),
                getComponents(), auraStorage.snapshot(),
                getCatalystStacks(), preferredRecipe, lockedRecipes,
                lockedRecipeSignatures, contextRemaining, requiredTicks), this, registries);
    }

    @Override
    public void parseUpgradeData(HolderLookup.Provider registries,
                                 IUpgradeData data) {
        long retainedEnergy = data instanceof NatureAltarFactoryUpgradeData altar
                ? altar.energyContainer.getEnergy() : 0L;
        super.parseUpgradeData(registries, data);
        NativeMachineUpgradeData.restore(data, this, registries);
        if (!(data instanceof NatureAltarFactoryUpgradeData altar)) {
            return;
        }
        // Restore again after ENERGY upgrades have expanded the new capacity.
        // Mekanism's parent loads energy before those components and can clamp it early.
        energyContainer.setEnergy(retainedEnergy);
        ensureProcessState();
        auraStorage.restore(altar.aura());
        if (catalystSlot != null) {
            catalystSlot.setStack(altar.catalyst(0));
        }
        if (secondCatalystSlot != null) {
            secondCatalystSlot.setStack(altar.catalyst(1));
        }
        if (catalystLibrary != null) {
            catalystLibrary.replaceOverflow(altar.catalysts());
        }
        preferredRecipe = altar.preferredRecipe();
        int locks = Math.min(lockedRecipes.length,
                altar.lockedRecipes().length);
        System.arraycopy(altar.lockedRecipes(), 0,
                lockedRecipes, 0, locks);
        int signatures = Math.min(lockedRecipeSignatures.length,
                altar.lockedRecipeSignatures().length);
        System.arraycopy(altar.lockedRecipeSignatures(), 0,
                lockedRecipeSignatures, 0, signatures);
        int contexts = Math.min(contextRemaining.length,
                altar.contextRemaining().length);
        System.arraycopy(altar.contextRemaining(), 0,
                contextRemaining, 0, contexts);
        int ticks = Math.min(requiredTicks.length,
                altar.requiredTicks().length);
        System.arraycopy(altar.requiredTicks(), 0,
                requiredTicks, 0, ticks);
        for (int index = 0; index < requiredTicks.length; index++) {
            requiredTicks[index] = Math.max(1, requiredTicks[index]);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag,
                               HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ensureProcessState();
        auraStorage.save(tag, AURA_DATA, registries);
        CompoundTag locks = new CompoundTag();
        for (int process = 0; process < lockedRecipes.length; process++) {
            if (lockedRecipes[process] != null) {
                locks.putString(LOCK_PREFIX + process,
                        lockedRecipes[process].toString());
                locks.putString(SIGNATURE_PREFIX + process,
                        lockedRecipeSignatures[process]);
            }
        }
        tag.put(LOCK_DATA, locks);
        tag.putIntArray(CONTEXT_REMAINING, contextRemaining);
        tag.putIntArray(REQUIRED_TICKS, requiredTicks);
        if (preferredRecipe != null) {
            tag.putString(PREFERRED_RECIPE, preferredRecipe.toString());
        }
        tag.putInt(CATALYST_SLOT_COUNT,
                NatureAltarCatalystLibrary.SAVED_CATALYST_SLOTS);
        if (catalystLibrary != null) {
            catalystLibrary.save(tag, registries);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag,
                               HolderLookup.Provider registries) {
        super.loadAdditional(migrateLegacyInventoryTag(tag), registries);
        if (catalystLibrary != null) {
            catalystLibrary.load(tag, registries);
        }
        ensureProcessState();
        auraStorage.load(tag, AURA_DATA, registries);
        preferredRecipe = ResourceLocation.tryParse(
                tag.getString(PREFERRED_RECIPE));
        Arrays.fill(lockedRecipes, null);
        Arrays.fill(lockedRecipeSignatures, "");
        Arrays.fill(contextRemaining, 0);
        CompoundTag locks = tag.getCompound(LOCK_DATA);
        for (int process = 0; process < lockedRecipes.length; process++) {
            String savedLock = locks.getString(LOCK_PREFIX + process);
            ResourceLocation parsed = savedLock.isBlank() ? null
                    : ResourceLocation.tryParse(savedLock);
            lockedRecipes[process] = parsed == null
                    || parsed.getPath().isBlank() ? null : parsed;
            if (lockedRecipes[process] != null) {
                lockedRecipeSignatures[process] = locks.getString(
                        SIGNATURE_PREFIX + process);
            }
        }
        int[] savedContexts = tag.getIntArray(CONTEXT_REMAINING);
        System.arraycopy(savedContexts, 0, contextRemaining, 0,
                Math.min(savedContexts.length, contextRemaining.length));
        for (int index = 0; index < contextRemaining.length; index++) {
            contextRemaining[index] = Math.max(0,
                    contextRemaining[index]);
        }
        int[] savedTicks = tag.getIntArray(REQUIRED_TICKS);
        System.arraycopy(savedTicks, 0, requiredTicks, 0,
                Math.min(savedTicks.length, requiredTicks.length));
        for (int index = 0; index < requiredTicks.length; index++) {
            requiredTicks[index] = Math.max(1, requiredTicks[index]);
        }
    }

    private CompoundTag migrateLegacyInventoryTag(CompoundTag tag) {
        return catalystLibrary == null ? tag
                : NatureAltarCatalystMigration.factoryWorldTag(tag,
                getInventorySlots(null), secondCatalystSlot,
                catalystLibrary.windowSlots().getFirst());
    }

    @Override
    public void applyInventorySlots(BlockEntity.DataComponentInput input,
                                    List<IInventorySlot> slots,
                                    AttachedItems attachedItems) {
        NatureAltarCatalystMigration.applyPhysicalItemSlots(slots,
                NatureAltarCatalystMigration.factoryAttachment(
                        attachedItems, slots, secondCatalystSlot,
                        catalystLibrary.windowSlots().getFirst()),
                catalystLibrary);
    }

    @Override
    public AttachedItems collectInventorySlots(
            DataComponentMap.Builder builder, List<IInventorySlot> slots) {
        return NatureAltarCatalystMigration.collectPhysicalItemSlots(
                builder, slots, catalystLibrary);
    }

    @Override
    public CompoundTag getReducedUpdateTag(
            HolderLookup.Provider registries) {
        CompoundTag tag = super.getReducedUpdateTag(registries);
        recipeItemDisplay.writeUpdateTag(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag,
                                HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        recipeItemDisplay.readUpdateTag(tag, registries);
    }

    @Override
    public RecipeItemDisplayState mekanismMagicRecipeItemDisplay() {
        return recipeItemDisplay;
    }

    @Override
    public ResourceLocation mekanismMagicMachineId() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(getBlockState().getBlock());
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
        return catalystLibrary != null
                ? catalystLibrary.persistentInputs(
                catalystSlot, secondCatalystSlot)
                : catalystSlot == null ? List.of()
                : secondCatalystSlot == null
                ? List.of(catalystSlot)
                : List.of(catalystSlot, secondCatalystSlot);
    }

    @Override
    public boolean mekanismMagicGroupParallelItemInputs() {
        return true;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return true;
    }

    /**
     * Atomically distributes an entire multiplied pattern batch over lanes
     * that are empty or already bound to the same recipe. A failed commit
     * restores every touched slot and lock.
     */
    @Override
    public boolean acceptNatureAltarPattern(
            ResourceLocation recipeId, ItemStack input) {
        ensureProcessState();
        if (level == null || recipeId == null || input == null
                || input.isEmpty() || inputSlots == null) {
            return false;
        }
        var selectedRecipe = findExactAutomationRecipe(
                input, recipeId);
        if (selectedRecipe.isEmpty()) {
            return false;
        }
        String selectedSignature = NatureAuraRecipeBridge
                .recipeSignature(selectedRecipe.get());
        ItemStack cleanInput = NatureAuraRecipeBridge.recipeInputView(input);
        int requested = cleanInput.getCount();
        int[] capacities = new int[tier.processes];
        for (int process = 0; process < tier.processes; process++) {
            ResourceLocation locked = lockedRecipes[process];
            ItemStack current = inputSlots.get(process).getStack();
            boolean sameContext = recipeId.equals(locked)
                    && selectedSignature.equals(
                    lockedRecipeSignatures[process]);
            if (progress[process] > 0 && !sameContext) {
                continue;
            }
            if (!current.isEmpty() && (!sameContext
                    || contextRemaining[process] <= 0)) {
                continue;
            }
            ItemStack offered = cleanInput.copyWithCount(requested);
            ItemStack remainder = inputSlots.get(process).insertItem(
                    offered, Action.SIMULATE, AutomationType.EXTERNAL);
            capacities[process] = requested - remainder.getCount();
        }
        int[] accepted = NatureAltarAutomationBatchPolicy.plan(
                requested, capacities);
        if (accepted == null) {
            return false;
        }

        ItemStack[] previousInputs = new ItemStack[tier.processes];
        ResourceLocation[] previousLocks = Arrays.copyOf(
                lockedRecipes, lockedRecipes.length);
        String[] previousSignatures = Arrays.copyOf(
                lockedRecipeSignatures,
                lockedRecipeSignatures.length);
        int[] previousContextRemaining = Arrays.copyOf(
                contextRemaining, contextRemaining.length);
        for (int process = 0; process < tier.processes; process++) {
            previousInputs[process] = inputSlots.get(process)
                    .getStack().copy();
        }
        try {
            for (int process = 0; process < tier.processes; process++) {
                int amount = accepted[process];
                if (amount <= 0) {
                    continue;
                }
                ItemStack remainder = inputSlots.get(process).insertItem(
                        cleanInput.copyWithCount(amount), Action.EXECUTE,
                        AutomationType.EXTERNAL);
                if (!remainder.isEmpty()) {
                    restoreAutomatedInputState(
                            previousInputs, previousLocks,
                            previousSignatures,
                            previousContextRemaining);
                    return false;
                }
                int updatedContext = NatureAltarContextCountPolicy
                        .onAccepted(contextRemaining[process], amount);
                if (updatedContext < 0) {
                    restoreAutomatedInputState(
                            previousInputs, previousLocks,
                            previousSignatures,
                            previousContextRemaining);
                    return false;
                }
                lockedRecipes[process] = recipeId;
                lockedRecipeSignatures[process] = selectedSignature;
                contextRemaining[process] = updatedContext;
                if (recipeCacheLookupMonitors != null
                        && process < recipeCacheLookupMonitors.length
                        && recipeCacheLookupMonitors[process] != null) {
                    recipeCacheLookupMonitors[process].unpause();
                }
            }
            markForSave();
            return true;
        } catch (RuntimeException | Error failure) {
            restoreAutomatedInputState(previousInputs, previousLocks,
                    previousSignatures, previousContextRemaining);
            throw failure;
        }
    }

    private void restoreAutomatedInputState(
            ItemStack[] previousInputs,
            ResourceLocation[] previousLocks,
            String[] previousSignatures,
            int[] previousContextRemaining) {
        for (int process = 0; process < tier.processes; process++) {
            inputSlots.get(process).setStack(previousInputs[process]);
        }
        System.arraycopy(previousLocks, 0, lockedRecipes, 0,
                Math.min(previousLocks.length, lockedRecipes.length));
        System.arraycopy(previousSignatures, 0, lockedRecipeSignatures, 0,
                Math.min(previousSignatures.length,
                        lockedRecipeSignatures.length));
        System.arraycopy(previousContextRemaining, 0, contextRemaining, 0,
                Math.min(previousContextRemaining.length,
                        contextRemaining.length));
        markForSave();
    }

    private java.util.Optional<NatureAuraRecipeBridge.AltarRecipeView>
    findExactAutomationRecipe(ItemStack input, ResourceLocation recipeId) {
        if (level == null || recipeId == null || input == null
                || input.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (NatureAuraRecipeBridge.hasPatternRecipeMarker(input)
                && !NatureAuraRecipeBridge.patternMarkerMatchesRecipe(
                level, input, recipeId)) {
            return java.util.Optional.empty();
        }
        ItemStack cleanInput = NatureAuraRecipeBridge.recipeInputView(input);
        return NatureAuraRecipeScanner.find(level.getRecipeManager(), recipeId)
                .filter(recipe -> recipe.input().test(cleanInput))
                .filter(recipe -> !recipe.requiresCatalyst()
                        || NatureAuraRecipeBridge.hasMatchingCatalyst(
                        recipe, getCatalystStacks()));
    }

    @Override
    public mekanism.api.energy.IEnergyContainer
    mekanismMagicEnergyContainer() {
        return energyContainer;
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        if (inputSlots == null || inputSlots.isEmpty()) {
            return true;
        }
        // Factory lanes are independent. Reporting busy because one lane is
        // active prevents AE from ever filling the remaining lanes; the
        // recipe-aware transaction performs the precise capacity check.
        return inputSlots.stream().allMatch(slot -> {
            ItemStack stack = slot.getStack();
            return !stack.isEmpty()
                    && stack.getCount() >= stack.getMaxStackSize();
        });
    }

    public boolean hasFactoryWarningContext(int process) {
        return process >= 0 && process < inputSlots.size()
                && !inputSlots.get(process).getStack().isEmpty();
    }

    private boolean hasAnyFactoryWarningContext() {
        return inputSlots.stream()
                .anyMatch(slot -> !slot.getStack().isEmpty());
    }

    @Override
    public BooleanSupplier getWarningCheck(
            CachedRecipe.OperationTracker.RecipeError error,
            int processIndex) {
        BooleanSupplier warning = super.getWarningCheck(
                error, processIndex);
        if (error == NOT_ENOUGH_AURA) {
            return () -> hasAnyFactoryWarningContext()
                    && warning.getAsBoolean();
        }
        return error == CachedRecipe.OperationTracker.RecipeError
                .NOT_ENOUGH_ENERGY
                ? () -> hasAnyFactoryWarningContext()
                && warning.getAsBoolean()
                : () -> hasFactoryWarningContext(processIndex)
                && warning.getAsBoolean();
    }

    @Override
    public void recalculateUpgrades(Upgrade upgrade) {
        super.recalculateUpgrades(upgrade);
        if (upgrade == Upgrade.SPEED && auraStorage != null
                && requiredTicks != null && inputSlots != null) {
            ensureProcessState();
            for (int process = 0; process < tier.processes; process++) {
                ResourceLocation locked = lockedRecipes[process];
                var recipe = level == null || locked == null
                        ? java.util.Optional
                        .<NatureAuraRecipeBridge.AltarRecipeView>empty()
                        : NatureAuraRecipeScanner.find(
                        level.getRecipeManager(), locked);
                requiredTicks[process] = recipe.isEmpty() ? 1
                        : Math.max(1, mekanism.common.util.MekanismUtils
                        .getTicks(this, recipe.get().duration()));
            }
            wakeAuraRecipes();
            markForSave();
        }
        if (upgrade == MagicUpgrades.creativeMagic()
                && auraStorage != null) {
            wakeAuraRecipes();
        }
    }
}
