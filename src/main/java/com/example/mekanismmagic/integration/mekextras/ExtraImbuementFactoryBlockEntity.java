package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IRecipeItemDisplay;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.blockentity.DefaultMachineSideConfig;
import com.example.mekanismmagic.inventory.CreativeMagicUpgradeSlot;
import com.example.mekanismmagic.upgrade.CreativeMagicUpgradeMigration;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauMachineConfig;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceInteraction;
import com.example.mekanismmagic.integration.arsnouveau.ArsFactoryEnergyFallback;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceModeHost;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceMachineBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.SourceModeCapability;
import com.example.mekanismmagic.integration.arsnouveau.SourceLinkHost;
import com.example.mekanismmagic.integration.arsnouveau.SourceLinkState;
import com.example.mekanismmagic.integration.arsnouveau.InputGatedSourceStorage;
import com.example.mekanismmagic.integration.arsnouveau.SourceDisplayHost;
import com.example.mekanismmagic.integration.arsnouveau.SourceDisplaySnapshot;
import com.example.mekanismmagic.integration.arsnouveau.SourceAwareImbuementCachedRecipe;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierItem;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierSelectionHost;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryUpgradeData;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryRecipe;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryLayout;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.api.source.SourceProvider;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import com.jerry.mekextras.common.tile.factory.TileEntityExtraItemToItemFactory;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Mekanism Extras 11/13/15/17-process imbuement factory.
 */
public final class ExtraImbuementFactoryBlockEntity
        extends TileEntityExtraItemToItemFactory<ImbuementFactoryRecipe>
        implements IMekanismMagicAutomation, ISourceTile, ArsSourceModeHost,
        CatalystIdentifierSelectionHost, IRecipeItemDisplay,
        SourceLinkHost, SourceDisplayHost {
    private BasicInventorySlot lockSlot;
    private BasicInventorySlot legacyCreativeMagicUpgradeSlot;
    private List<BasicInventorySlot> catalystLibrarySlots;
    private boolean catalystLibraryOpen;
    private int selectedCatalystIndex = -1;
    private int catalystPage;
    private boolean virtualCatalystSelected;
    private int virtualCatalystIndex = -1;
    private String virtualCatalystId = "";
    private ItemStack cachedVirtualCatalyst = ItemStack.EMPTY;
    private String cachedVirtualCatalystId = "";
    private int cachedVirtualCatalystIndex = Integer.MIN_VALUE;
    private long cachedCatalystCatalogVersion = Long.MIN_VALUE;
    private int[] requiredTicks;
    private SourceStorage sourceStorage;
    private SourceProvider sourceProvider;
    private final EnumMap<Direction, ArsSourceMachineBlockEntity.SourceMode>
            sourceModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, ISourceCap> sidedSourceCaps =
            new EnumMap<>(Direction.class);
    private final SourceLinkState sourceLinks = new SourceLinkState();
    private final SourceDisplaySnapshot sourceDisplay =
            new SourceDisplaySnapshot();
    private long nextNearbySourcePullGameTime = Long.MIN_VALUE;
    private final RecipeItemDisplayState recipeItemDisplay =
            new RecipeItemDisplayState();

    public ExtraImbuementFactoryBlockEntity(Holder<Block> block,
                                            BlockPos pos, BlockState state) {
        super(block, pos, state,
                List.of(CachedRecipe.OperationTracker.RecipeError
                                .NOT_ENOUGH_INPUT,
                        CachedRecipe.OperationTracker.RecipeError
                                .NOT_ENOUGH_OUTPUT_SPACE,
                        CachedRecipe.OperationTracker.RecipeError
                                .INPUT_DOESNT_PRODUCE_OUTPUT),
                Set.of(CachedRecipe.OperationTracker.RecipeError
                        .NOT_ENOUGH_ENERGY));
        requiredTicks = new int[tier.processes];
        for (Direction direction : Direction.values()) {
            sourceModes.put(direction,
                    ArsSourceMachineBlockEntity.SourceMode.INPUT_OUTPUT);
            sidedSourceCaps.put(direction, new SourceModeCapability(
                    this::getSourceStorage,
                    () -> getSourceMode(direction)));
        }
        DefaultMachineSideConfig.apply(configComponent);
    }

    @Override
    protected void addSlots(InventorySlotHelper helper,
                            mekanism.api.IContentsListener listener,
                            mekanism.api.IContentsListener recipeListener) {
        super.addSlots(helper, listener, recipeListener);
        // Serialized-slot compatibility only; installation now uses
        // Mekanism's native upgrade input and TileComponentUpgrade.
        legacyCreativeMagicUpgradeSlot =
                CreativeMagicUpgradeSlot.create(listener);
        helper.addSlot(legacyCreativeMagicUpgradeSlot);
        lockSlot = new RecipeLockSlot(recipeListener,
                ImbuementFactoryLayout.LOCK_SLOT_X,
                ImbuementFactoryLayout.LOCK_SLOT_Y);
        lockSlot.setSlotType(ContainerSlotType.EXTRA);
        catalystLibrarySlots = new ArrayList<>();
        IContentsListener libraryListener = () -> {
            refreshRecipeLock();
            recipeListener.onContentsChanged();
            setChanged();
        };
        int imageWidth = factoryImageWidth();
        int slotCount = com.example.mekanismmagic.blockentity
                .NativeMagicMachineBlockEntity.CATALYST_LIBRARY_SLOT_COUNT;
        for (int index = 0; index < slotCount; index++) {
            int pageSlot = index % CatalystLibraryLayout.PAGE_SIZE;
            BasicInventorySlot slot = new CatalystLibrarySlot(
                    this, index / CatalystLibraryLayout.PAGE_SIZE,
                    libraryListener,
                    CatalystLibraryLayout.slotX(imageWidth, pageSlot),
                    CatalystLibraryLayout.slotY(pageSlot));
            catalystLibrarySlots.add(slot);
            helper.addSlot(slot);
        }
        requiredTicks = new int[tier.processes];
    }

    private int factoryImageWidth() {
        return ImbuementFactoryLayout.extraImageWidth(tier.ordinal());
    }

    @Override
    protected IInventorySlot getExtraSlot() {
        return lockSlot;
    }

    @Override
    protected ImbuementFactoryRecipe findRecipe(
            int process, ItemStack input, IInventorySlot extra,
            IInventorySlot output) {
        if (input.isEmpty()) {
            return null;
        }
        ResourceLocation patternRecipe =
                ArsNouveauRecipeBridge.patternRecipe(input);
        if (patternRecipe != null) {
            selectCatalystIdentifierForRecipe(patternRecipe);
        }
        ItemStack identifier = selectedCatalystIdentifier();
        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, input.copy());
        return ArsNouveauRecipeBridge.findImbuementByIdentifier(
                        level, inventory, 0, identifier)
                .map(result -> new ImbuementFactoryRecipe(
                        input, identifier, result,
                        ArsNouveauRecipeBridge.requiresCatalystIdentifier(
                                level, result.id())))
                .orElse(null);
    }

    @Override
    protected ImbuementFactoryRecipe getRecipeForInput(
            int process, ItemStack input, IInventorySlot output,
            IInventorySlot secondaryOutput, boolean recheck) {
        return findRecipe(process, input, lockSlot, output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public IMekanismRecipeTypeProvider getRecipeType() {
        return (IMekanismRecipeTypeProvider) MekanismRecipeType.SMELTING;
    }

    @Override
    protected int getNeededInput(ImbuementFactoryRecipe recipe,
                                 ItemStack input) {
        return 1;
    }

    @Override
    protected boolean isCachedRecipeValid(
            CachedRecipe<ImbuementFactoryRecipe> cached, ItemStack input) {
        return cached != null && cached.getRecipe() != null
                && cached.getRecipe().test(input)
                && lockSlot != null
                && hasMatchingIdentifier(cached.getRecipe());
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
    public ImbuementFactoryRecipe getRecipe(int process) {
        return process < 0 || process >= inputSlots.size() ? null
                : findRecipe(process, inputSlots.get(process).getStack(),
                        lockSlot, outputSlots.get(process));
    }

    @Override
    public CachedRecipe<ImbuementFactoryRecipe> createNewCachedRecipe(
            ImbuementFactoryRecipe recipe, int process) {
        requiredTicks[process] = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        recipe.duration()));
        @SuppressWarnings({"rawtypes", "unchecked"})
        CachedRecipe<ImbuementFactoryRecipe> cached =
                (CachedRecipe) new SourceAwareImbuementCachedRecipe(recipe,
                                () -> true, inputHandlers[process],
                                outputHandlers[process],
                                () -> hasCreativeUpgrade()
                                        || recipe.sourceCost() <= 0
                                        || consumeSourceForRecipe(
                                        recipe.sourceCost()))
                        .setCanHolderFunction(() -> lockSlot != null
                                && hasMatchingIdentifier(recipe)
                                && (hasCreativeUpgrade()
                                || hasSourceForRecipe(
                                recipe.sourceCost())))
                        .setActive(active -> setActiveState(active, process))
                        .setEnergyRequirements(
                                () -> ArsFactoryEnergyFallback
                                        .energyRequirement(energyContainer,
                                                recipeEnergyUsage()),
                                energyContainer)
                        .setPostProcessOperations(tracker ->
                                ArsFactoryEnergyFallback.limitOperations(
                                        tracker, level, energyContainer,
                                        recipeEnergyUsage(),
                                        recipe.sourceCost(),
                                        () -> hasSourceForRecipe(
                                                recipe.sourceCost()),
                                        hasCreativeUpgrade()))
                        .setRequiredTicks(() -> requiredTicks[process])
                        .setOperatingTicksChanged(value ->
                                progress[process] = value)
                        .setBaselineMaxOperations(() -> 1);
        return cached;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sourceChanged = autoPullNearbySource();
        boolean changed = super.onUpdateServer();
        boolean hasInput = inputSlots != null && inputSlots.stream()
                .anyMatch(slot -> !slot.getStack().isEmpty());
        boolean displayChanged = hasInput
                ? recipeItemDisplay.updateFactory(inputSlots,
                selectedCatalystIdentifier(), process -> {
                    ImbuementFactoryRecipe recipe = getRecipe(process);
                    return recipe == null
                            || recipe.getOutputDefinition().isEmpty()
                            ? ItemStack.EMPTY
                            : recipe.getOutputDefinition().getFirst();
                }) : recipeItemDisplay.clear();
        return sourceChanged || displayChanged || changed;
    }

    private boolean autoPullNearbySource() {
        if (hasCreativeUpgrade()) {
            nextNearbySourcePullGameTime = Long.MIN_VALUE;
            return false;
        }
        if (level == null || level.isClientSide()
                || getSource() >= getMaxSource()) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (nextNearbySourcePullGameTime != Long.MIN_VALUE
                && gameTime < nextNearbySourcePullGameTime) {
            return false;
        }
        nextNearbySourcePullGameTime = gameTime
                + ArsNouveauMachineConfig.NEARBY_SOURCE_PULL_INTERVAL;
        int request = Math.min(Math.max(1, getTransferRate()),
                getMaxSource() - getSource());
        int moved = sourceLinks.pullInto(this, level, request);
        if (moved < request) {
            moved += ArsSourceInteraction.pullNearbySource(this, level,
                    worldPosition, ArsNouveauMachineConfig
                            .IMBUEMENT_SOURCE_INTERACTION_RADIUS,
                    request - moved);
        }
        return moved > 0;
    }

    @Override
    public boolean mekanismMagicLinkSourceJar(BlockPos sourceJar) {
        if (level == null || !(level.getBlockEntity(sourceJar)
                instanceof com.hollingsworth.arsnouveau.common.block.tile
                        .SourceJarTile)) {
            return false;
        }
        boolean linked = sourceLinks.link(level.dimension().location(),
                worldPosition, sourceJar);
        if (linked) {
            nextNearbySourcePullGameTime = Long.MIN_VALUE;
            setChanged();
        }
        return linked;
    }

    @Override
    public int mekanismMagicClearSourceJarLinks() {
        int removed = sourceLinks.clear();
        if (removed > 0) {
            setChanged();
        }
        return removed;
    }

    @Override
    public RecipeItemDisplayState mekanismMagicRecipeItemDisplay() {
        return recipeItemDisplay;
    }

    @Override
    public CompoundTag getReducedUpdateTag(
            net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = super.getReducedUpdateTag(provider);
        recipeItemDisplay.writeUpdateTag(tag, provider);
        return tag;
    }

    @Override
    public void handleUpdateTag(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        recipeItemDisplay.readUpdateTag(tag, provider);
    }

    private boolean hasMatchingIdentifier(ImbuementFactoryRecipe recipe) {
        if (!recipe.requiresIdentifier()) {
            return true;
        }
        ItemStack identifier = selectedCatalystIdentifier();
        return !identifier.isEmpty()
                && recipe.sameIdentifier(identifier)
                && ArsNouveauRecipeBridge.identifierMatchesRecipe(
                level, identifier, recipe.imbuementId());
    }

    public ItemStack selectedCatalystIdentifier() {
        if (virtualCatalystSelected && level != null) {
            long version = ArsNouveauRecipeScanner.version(
                    level.getRecipeManager());
            if (version != cachedCatalystCatalogVersion
                    || level.isClientSide()
                    && virtualCatalystIndex != cachedVirtualCatalystIndex
                    || !level.isClientSide()
                    && !virtualCatalystId.equals(cachedVirtualCatalystId)) {
                String requestedId = level.isClientSide()
                        ? catalystIdAtVirtualIndex() : virtualCatalystId;
                cachedVirtualCatalyst = ArsNouveauRecipeBridge
                        .catalystIdentifierJeiStack(level, requestedId);
                cachedVirtualCatalystId = requestedId;
                cachedVirtualCatalystIndex = virtualCatalystIndex;
                cachedCatalystCatalogVersion = version;
            }
            return cachedVirtualCatalyst;
        }
        if (catalystLibrarySlots == null
                || selectedCatalystIndex < 0
                || selectedCatalystIndex >= catalystLibrarySlots.size()) {
            return ItemStack.EMPTY;
        }
        return catalystLibrarySlots.get(selectedCatalystIndex).getStack();
    }

    public int catalystPage() {
        return catalystPage;
    }

    public int catalystPageCount() {
        int slotCount = com.example.mekanismmagic.blockentity
                .NativeMagicMachineBlockEntity.CATALYST_LIBRARY_SLOT_COUNT;
        return Math.max(1, Math.ceilDiv(
                slotCount, CatalystLibraryLayout.PAGE_SIZE));
    }

    public void cycleCatalystPage(int delta) {
        catalystPage = Math.floorMod(catalystPage + delta,
                catalystPageCount());
        setChanged();
    }

    public void selectCatalystIdentifier(int index) {
        if (catalystLibrarySlots == null || index < 0
                || index >= catalystLibrarySlots.size()
                || catalystLibrarySlots.get(index).getStack().isEmpty()) {
            return;
        }
        selectedCatalystIndex = index;
        virtualCatalystSelected = false;
        virtualCatalystIndex = -1;
        virtualCatalystId = "";
        cachedVirtualCatalyst = ItemStack.EMPTY;
        refreshRecipeLock();
        setChanged();
    }

    public boolean selectCatalystIdentifierId(String id) {
        int index = ArsNouveauRecipeBridge.catalystIdentifierJeiIndex(
                level, id);
        if (index < 0) {
            return false;
        }
        ItemStack canonical = ArsNouveauRecipeBridge
                .catalystIdentifierJeiStack(level, index);
        String canonicalId = canonical.isEmpty() ? id
                : CatalystIdentifierItem.catalystId(canonical).toString();
        if (virtualCatalystSelected
                && canonicalId.equals(virtualCatalystId)) {
            return true;
        }
        virtualCatalystSelected = true;
        virtualCatalystIndex = index;
        virtualCatalystId = canonicalId;
        cachedVirtualCatalyst = ItemStack.EMPTY;
        cachedCatalystCatalogVersion = Long.MIN_VALUE;
        refreshRecipeLock();
        setChanged();
        return true;
    }

    public void selectCatalystIdentifierRecipe(int index) {
        ItemStack identifier = ArsNouveauRecipeBridge
                .catalystIdentifierJeiStack(level, index);
        if (!identifier.isEmpty()) {
            selectCatalystIdentifierId(CatalystIdentifierItem
                    .catalystId(identifier).toString());
        }
    }

    public void clearCatalystIdentifierSelection() {
        virtualCatalystSelected = false;
        virtualCatalystIndex = -1;
        virtualCatalystId = "";
        selectedCatalystIndex = -1;
        cachedVirtualCatalyst = ItemStack.EMPTY;
        cachedCatalystCatalogVersion = Long.MIN_VALUE;
        refreshRecipeLock();
        setChanged();
    }

    public int catalystIdentifierRecipeCount() {
        return level == null ? 0 : ArsNouveauRecipeBridge
                .catalystIdentifierJeiRecipes(level).size();
    }

    /** Empty factory inputs and an empty lock are normal idle state. */
    public boolean hasFactoryWarningContext(int process) {
        return process >= 0 && process < inputSlots.size()
                && !inputSlots.get(process).getStack().isEmpty()
                && hasCatalystSelection();
    }

    private boolean hasAnyFactoryWarningContext() {
        if (!hasCatalystSelection()) {
            return false;
        }
        for (IInventorySlot slot : inputSlots) {
            if (!slot.getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** See the standard factory implementation for the UI hot-path reason. */
    private boolean hasCatalystSelection() {
        if (virtualCatalystSelected) {
            return level != null && (level.isClientSide()
                    ? virtualCatalystIndex >= 0
                    : !virtualCatalystId.isBlank());
        }
        return catalystLibrarySlots != null
                && selectedCatalystIndex >= 0
                && selectedCatalystIndex < catalystLibrarySlots.size()
                && !catalystLibrarySlots.get(selectedCatalystIndex)
                .getStack().isEmpty();
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

    public void setCatalystLibraryOpen(boolean open) {
        catalystLibraryOpen = open;
    }

    public boolean isCatalystLibrarySlotActive() {
        return level == null || !level.isClientSide() || catalystLibraryOpen;
    }

    private String catalystIdAtVirtualIndex() {
        return ArsNouveauRecipeBridge.catalystIdentifierJeiId(
                level, virtualCatalystIndex);
    }

    private int trackedVirtualCatalystIndex() {
        if (level != null && !level.isClientSide()
                && virtualCatalystSelected
                && !virtualCatalystId.isBlank()) {
            virtualCatalystIndex = ArsNouveauRecipeBridge
                    .catalystIdentifierJeiIndex(level, virtualCatalystId);
        }
        return virtualCatalystIndex;
    }

    private void refreshRecipeLock() {
        if (lockSlot == null) {
            return;
        }
        ItemStack selected = selectedCatalystIdentifier();
        ItemStack lock = selected.isEmpty()
                ? ItemStack.EMPTY : selected.copyWithCount(1);
        if (!ItemStack.matches(lockSlot.getStack(), lock)) {
            lockSlot.setStack(lock);
        }
    }

    private long recipeEnergyUsage() {
        return mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, ArsNouveauMachineConfig.IMBUEMENT_FACTORY_FE_PER_TICK);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        for (Direction direction : Direction.values()) {
            container.track(SyncableInt.create(
                    () -> getSourceMode(direction).ordinal(),
                    value -> sourceModes.put(direction,
                            ArsSourceMachineBlockEntity.SourceMode.values()[
                                    Math.max(0, Math.min(
                                            ArsSourceMachineBlockEntity.SourceMode
                                                    .values().length - 1,
                                            value))])));
        }
        for (int process = 0; process < tier.processes; process++) {
            int index = process;
            container.track(SyncableInt.create(
                    () -> index < requiredTicks.length
                            ? requiredTicks[index] : 0,
                    value -> {
                        if (index < requiredTicks.length) {
                            requiredTicks[index] = Math.max(1, value);
                        }
                    }));
        }
        container.track(SyncableInt.create(this::getSource, this::setSource));
        sourceDisplay.addTrackers(container, this::getMaxSource,
                this::hasCreativeUpgrade);
        container.track(SyncableInt.create(
                () -> selectedCatalystIndex,
                value -> selectedCatalystIndex = Math.max(-1, value)));
        container.track(SyncableInt.create(
                () -> catalystPage,
                value -> catalystPage = Math.max(0, Math.min(
                        catalystPageCount() - 1, value))));
        container.track(mekanism.common.inventory.container.sync.SyncableBoolean
                .create(() -> virtualCatalystSelected,
                        value -> virtualCatalystSelected = value));
        container.track(SyncableInt.create(
                this::trackedVirtualCatalystIndex,
                value -> virtualCatalystIndex = Math.max(-1, value)));
    }

    @Override
    public double getScaledProgress(int scale, int process) {
        return process < requiredTicks.length && requiredTicks[process] > 0
                ? progress[process] * (double) scale / requiredTicks[process]
                : 0;
    }

    @Override
    public ImbuementFactoryUpgradeData getUpgradeData(
            net.minecraft.core.HolderLookup.Provider registries) {
        EnergyInventorySlot currentEnergySlot = null;
        for (IInventorySlot slot : getInventorySlots(null)) {
            if (slot instanceof EnergyInventorySlot energy) {
                currentEnergySlot = energy;
                break;
            }
        }
        List<ItemStack> catalysts = catalystLibrarySlots == null
                ? List.of() : catalystLibrarySlots.stream()
                .map(IInventorySlot::getStack)
                .map(ItemStack::copy)
                .toList();
        return new ImbuementFactoryUpgradeData(registries, redstone,
                getControlType(), energyContainer, progress,
                currentEnergySlot, inputSlots, outputSlots, isSorting(),
                getComponents(), getSource(),
                lockSlot == null ? ItemStack.EMPTY : lockSlot.getStack(),
                catalysts,
                selectedCatalystIndex, virtualCatalystSelected,
                virtualCatalystId, catalystLibraryOpen, catalystPage,
                requiredTicks == null ? new int[0] : requiredTicks,
                sourceModes, sourceLinks.snapshot());
    }

    @Override
    public void parseUpgradeData(
            net.minecraft.core.HolderLookup.Provider registries,
            IUpgradeData data) {
        super.parseUpgradeData(registries, data);
        if (!(data instanceof ImbuementFactoryUpgradeData imbuement)) {
            return;
        }
        if (catalystLibrarySlots != null) {
            for (int index = 0; index < catalystLibrarySlots.size(); index++) {
                ItemStack stack = index < imbuement.catalystLibrary.size()
                        ? imbuement.catalystLibrary.get(index).copy()
                        : ItemStack.EMPTY;
                catalystLibrarySlots.get(index).setStack(stack);
            }
        }
        setSource(imbuement.source);
        if (lockSlot != null) {
            lockSlot.setStack(imbuement.recipeLock.copy());
        }
        int librarySize = catalystLibrarySlots == null
                ? 0 : catalystLibrarySlots.size();
        selectedCatalystIndex = Math.max(-1, Math.min(
                librarySize - 1, imbuement.selectedCatalystIndex));
        virtualCatalystSelected = imbuement.virtualCatalystSelected;
        virtualCatalystId = imbuement.virtualCatalystId;
        catalystLibraryOpen = imbuement.catalystLibraryOpen;
        catalystPage = Math.max(0, Math.min(
                catalystPageCount() - 1, imbuement.catalystPage));
        requiredTicks = Arrays.copyOf(
                imbuement.requiredTicks, tier.processes);
        for (Direction direction : Direction.values()) {
            sourceModes.put(direction, imbuement.sourceModes.getOrDefault(
                    direction,
                    ArsSourceMachineBlockEntity.SourceMode.INPUT_OUTPUT));
        }
        sourceLinks.replace(imbuement.sourceLinks, level == null ? null
                : level.dimension().location());
        nextNearbySourcePullGameTime = Long.MIN_VALUE;
        virtualCatalystIndex = -1;
        cachedVirtualCatalyst = ItemStack.EMPTY;
        cachedCatalystCatalogVersion = Long.MIN_VALUE;
        refreshRecipeLock();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        CreativeMagicUpgradeMigration.migrate(
                this, legacyCreativeMagicUpgradeSlot);
        if (level != null && !level.isClientSide() && sourceProvider == null) {
            sourceProvider = new SourceProvider(this, worldPosition) {
                @Override
                public boolean isValid() {
                    return !ExtraImbuementFactoryBlockEntity.this.isRemoved()
                            && ExtraImbuementFactoryBlockEntity.this.level != null
                            && ExtraImbuementFactoryBlockEntity.this.level
                            .getBlockEntity(worldPosition)
                            == ExtraImbuementFactoryBlockEntity.this;
                }
            };
            SourceManager.INSTANCE.addInterface(level, sourceProvider);
        }
    }

    private SourceStorage sourceStorage() {
        if (sourceStorage == null) {
            sourceStorage = new InputGatedSourceStorage(
                    ArsNouveauMachineConfig.SOURCE_CAPACITY,
                    ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE,
                    ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE,
                    () -> !hasCreativeUpgrade(), this::setChanged);
        }
        return sourceStorage;
    }

    public SourceStorage getSourceStorage() {
        return sourceStorage();
    }

    private boolean hasSourceForRecipe(int sourceCost) {
        return ArsSourceInteraction.hasSource(this, level, worldPosition,
                ArsNouveauMachineConfig
                        .IMBUEMENT_SOURCE_INTERACTION_RADIUS,
                sourceCost);
    }

    private boolean consumeSourceForRecipe(int sourceCost) {
        return ArsSourceInteraction.consumeSource(this, level,
                worldPosition, ArsNouveauMachineConfig
                        .IMBUEMENT_SOURCE_INTERACTION_RADIUS,
                sourceCost);
    }

    @Override
    public ArsSourceMachineBlockEntity.SourceMode getSourceMode(
            Direction side) {
        return sourceModes.getOrDefault(side,
                ArsSourceMachineBlockEntity.SourceMode.NONE);
    }

    @Override
    public void cycleSourceMode(int index, int delta) {
        Direction[] directions = Direction.values();
        if (index < 0 || index >= directions.length) {
            return;
        }
        Direction side = directions[index];
        sourceModes.put(side, getSourceMode(side).shift(delta));
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    public ISourceCap getSourceStorage(Direction side) {
        return getSourceMode(side)
                == ArsSourceMachineBlockEntity.SourceMode.NONE
                ? null : sidedSourceCaps.get(side);
    }

    private boolean hasCreativeUpgrade() {
        return getComponent() != null
                && getComponent().getUpgrades(
                MagicUpgrades.creativeMagic()) > 0;
    }

    @Override
    public int getTransferRate() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    @Override
    public boolean canAcceptSource() {
        return sourceStorage().canAcceptSource(1);
    }

    @Override
    public boolean canProvideSource() {
        return getSource() > 0;
    }

    @Override
    public int getSource() {
        return sourceStorage().getSource();
    }

    @Override
    public int getMaxSource() {
        return sourceStorage().getSourceCapacity();
    }

    @Override
    public boolean mekanismMagicCreativeSourceActive() {
        return sourceDisplay.creativeOr(hasCreativeUpgrade());
    }

    @Override
    public int mekanismMagicDisplayedSourceCapacity() {
        return sourceDisplay.capacityOr(getMaxSource());
    }

    @Override
    public int setSource(int amount) {
        sourceStorage().setSource(amount);
        return getSource();
    }

    @Override
    public int addSource(int amount) {
        if (amount > 0 && hasCreativeUpgrade()) {
            return getSource();
        }
        return setSource(getSource() + amount);
    }

    @Override
    public int addSource(int amount, boolean simulate) {
        return sourceStorage().receiveSource(amount, simulate);
    }

    @Override
    public int removeSource(int amount) {
        return setSource(getSource() - amount);
    }

    @Override
    public int removeSource(int amount, boolean simulate) {
        return sourceStorage().extractSource(amount, simulate);
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
        return catalystLibrarySlots == null
                ? List.of() : List.copyOf(catalystLibrarySlots);
    }

    @Override
    public List<IInventorySlot> mekanismMagicManualOnlySlots() {
        return catalystLibrarySlots == null
                ? List.of() : List.copyOf(catalystLibrarySlots);
    }

    @Override
    public mekanism.api.energy.IEnergyContainer
    mekanismMagicEnergyContainer() {
        return energyContainer;
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        for (int value : progress) {
            if (value > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void saveAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ars_source", getSource());
        CompoundTag modes = new CompoundTag();
        for (Direction direction : Direction.values()) {
            modes.putInt(direction.getName(), getSourceMode(direction).ordinal());
        }
        tag.put("ars_source_modes", modes);
        sourceLinks.save(tag);
        tag.putInt("catalyst_selected_index", selectedCatalystIndex);
        tag.putInt("catalyst_page", catalystPage);
        tag.putBoolean("virtual_catalyst_selected",
                virtualCatalystSelected);
        tag.putString("virtual_catalyst_id", virtualCatalystId);
    }

    @Override
    public void loadAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        setSource(tag.getInt("ars_source"));
        CompoundTag modes = tag.getCompound("ars_source_modes");
        for (Direction direction : Direction.values()) {
            if (modes.contains(direction.getName())) {
                int value = Math.max(0, Math.min(
                        ArsSourceMachineBlockEntity.SourceMode.values().length - 1,
                        modes.getInt(direction.getName())));
                sourceModes.put(direction,
                        ArsSourceMachineBlockEntity.SourceMode.values()[value]);
            }
        }
        sourceLinks.load(tag);
        nextNearbySourcePullGameTime = Long.MIN_VALUE;
        selectedCatalystIndex = tag.contains("catalyst_selected_index")
                ? Math.max(-1, tag.getInt("catalyst_selected_index")) : -1;
        if (!tag.contains("catalyst_selected_index")
                && catalystLibrarySlots != null) {
            for (int index = 0; index < catalystLibrarySlots.size(); index++) {
                if (!catalystLibrarySlots.get(index).getStack().isEmpty()) {
                    selectedCatalystIndex = index;
                    break;
                }
            }
        }
        catalystPage = Math.max(0, Math.min(
                catalystPageCount() - 1, tag.getInt("catalyst_page")));
        virtualCatalystSelected = tag.getBoolean(
                "virtual_catalyst_selected");
        virtualCatalystId = tag.getString("virtual_catalyst_id");
        if (virtualCatalystId.isBlank()) {
            virtualCatalystSelected = false;
        }
        virtualCatalystIndex = -1;
        cachedVirtualCatalyst = ItemStack.EMPTY;
        cachedCatalystCatalogVersion = Long.MIN_VALUE;
        refreshRecipeLock();
    }

    private static final class RecipeLockSlot extends BasicInventorySlot {
        private RecipeLockSlot(IContentsListener listener, int x, int y) {
            super((stack, automation) -> false,
                    (stack, automation) -> false,
                    stack -> stack.is(
                            ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get()),
                    listener, x, y);
        }
    }

    private static final class CatalystLibrarySlot extends BasicInventorySlot {
        private final ExtraImbuementFactoryBlockEntity tile;
        private final int page;
        private final int x;
        private final int y;

        private CatalystLibrarySlot(ExtraImbuementFactoryBlockEntity tile,
                                    int page, IContentsListener listener,
                                    int x, int y) {
            super((stack, automation) -> true,
                    (stack, automation) -> true,
                    stack -> stack.is(
                            ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get()),
                    listener, x, y);
            this.tile = tile;
            this.page = page;
            this.x = x;
            this.y = y;
            setSlotType(ContainerSlotType.INPUT);
        }

        @Override
        public InventoryContainerSlot createContainerSlot() {
            return new InventoryContainerSlot(this, x, y, getSlotType(),
                    getSlotOverlay(), warning -> {
                    }, this::setStackUnchecked) {
                @Override
                public boolean isActive() {
                    return tile.isCatalystLibrarySlotActive()
                            && tile.catalystPage() == page;
                }
            };
        }
    }
}
