package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IRecipeItemDisplay;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.blockentity.DefaultMachineSideConfig;
import com.example.mekanismmagic.blockentity.PersistentInputMutationGuard;
import com.example.mekanismmagic.inventory.CreativeMagicUpgradeSlot;
import com.example.mekanismmagic.upgrade.CreativeMagicUpgradeMigration;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.example.mekanismmagic.integration.common.network.PatternAutomationRefreshHooks;
import mekanism.api.IContentsListener;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.api.source.SourceProvider;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
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
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.tile.factory.TileEntityItemToItemFactory;
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

import java.util.List;
import java.util.Set;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

public final class ImbuementFactoryBlockEntity
        extends TileEntityItemToItemFactory<ImbuementFactoryRecipe>
        implements IMekanismMagicAutomation, ISourceTile, ArsSourceModeHost,
        CatalystIdentifierSelectionHost, IRecipeItemDisplay,
        SourceLinkHost, SourceDisplayHost {
    private BasicInventorySlot lockSlot;
    private BasicInventorySlot legacyCreativeMagicUpgradeSlot;
    private CatalystLibraryStorage catalystLibrary;
    private List<CatalystLibraryWindowSlot> catalystLibrarySlots;
    private ItemStack syncedPhysicalCatalyst = ItemStack.EMPTY;
    private int syncedCatalystVisibleSlotCount = 1;
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
    private long observedRecipeCatalogVersion = Long.MIN_VALUE;
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

    public ImbuementFactoryBlockEntity(Holder<Block> block, BlockPos pos,
                                       BlockState state) {
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
        catalystLibrary = new CatalystLibraryStorage(() -> {
            clampCatalystPage();
            refreshRecipeLock();
            recipeListener.onContentsChanged();
            setChanged();
        });
        CatalystLibraryStorage.PageWindow window = catalystLibrary.pageWindow(
                this::catalystPage, this::windowRecipeCount);
        int imageWidth = factoryImageWidth();
        catalystLibrarySlots = CatalystLibraryWindowSlot.createManualWindow(
                window,
                () -> level != null && level.isClientSide(),
                () -> level == null || !level.isClientSide()
                        || catalystLibraryOpen,
                stack -> stack.is(
                        ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get()),
                pageSlot -> CatalystLibraryLayout.slotX(
                        imageWidth, pageSlot),
                CatalystLibraryLayout::slotY);
        for (CatalystLibraryWindowSlot slot : catalystLibrarySlots) {
            helper.addSlot(slot);
        }
        requiredTicks = new int[tier.processes];
    }

    private int factoryImageWidth() {
        return ImbuementFactoryLayout.standardImageWidth(tier);
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
        if (patternRecipe != null
                && (!canAdoptMarkedPatternRecipe(patternRecipe)
                || !selectCatalystIdentifierForRecipe(patternRecipe))) {
            return null;
        }
        if (patternRecipe != null) {
            input = ArsNouveauRecipeBridge.recipeInputView(input);
            if (process >= 0 && process < inputSlots.size()
                    && !ItemStack.matches(
                    inputSlots.get(process).getStack(), input)) {
                inputSlots.get(process).setStack(input.copy());
            }
        }
        ItemStack identifier = selectedCatalystIdentifier();
        ItemStackHandler inventory = new ItemStackHandler(23);
        inventory.setStackInSlot(0, input.copy());
        return ArsNouveauRecipeBridge.findImbuementByIdentifier(
                        level, inventory, 0, identifier)
                .map(result -> {
                    var holder = level == null ? null
                            : ArsNouveauRecipeScanner.find(
                            level.getRecipeManager(), result.id());
                    return holder == null ? null
                            : new ImbuementFactoryRecipe(
                            holder, identifier, result,
                            ArsNouveauRecipeBridge
                                    .requiresCatalystIdentifier(
                                            level, result.id()),
                            ArsNouveauRecipeScanner.version(
                                    level.getRecipeManager()));
                })
                .orElse(null);
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
            moved += ArsSourceInteraction.pullConnectedNetworkSource(
                    this, level, worldPosition, request - moved);
        }
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
        if (level == null
                || !SourceLinkState.isSourceEndpoint(level, sourceJar)) {
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
    public void onLoad() {
        super.onLoad();
        CreativeMagicUpgradeMigration.migrate(
                this, legacyCreativeMagicUpgradeSlot);
        if (level != null && !level.isClientSide()) {
            if (sourceProvider == null) {
                sourceProvider = new SourceProvider(this, worldPosition) {
                    @Override
                    public boolean isValid() {
                        return !ImbuementFactoryBlockEntity.this.isRemoved()
                                && ImbuementFactoryBlockEntity.this.level != null
                                && ImbuementFactoryBlockEntity.this.level
                                .getBlockEntity(worldPosition)
                                == ImbuementFactoryBlockEntity.this;
                    }
                };
            }
            SourceManager.INSTANCE.addInterface(level, sourceProvider);
        }
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
    public FactoryType getFactoryType() {
        return FactoryType.SMELTING;
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
                && cached.getRecipe().matchesCurrentRecipe(level)
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
                                && recipe.matchesCurrentRecipe(level)
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
        boolean revisionChanged = refreshRecipeCatalogVersion();
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
        return revisionChanged || sourceChanged || displayChanged || changed;
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
        if (!recipe.matchesCurrentRecipe(level)) {
            return false;
        }
        if (!recipe.requiresIdentifier()) {
            return true;
        }
        ItemStack identifier = selectedCatalystIdentifier();
        return !identifier.isEmpty()
                && recipe.sameIdentifier(identifier)
                && ArsNouveauRecipeBridge.identifierMatchesRecipe(
                level, identifier, recipe.imbuementId());
    }

    private boolean refreshRecipeCatalogVersion() {
        long revision = level == null ? 0L
                : ArsNouveauRecipeScanner.version(level.getRecipeManager());
        if (observedRecipeCatalogVersion == revision) {
            return false;
        }
        boolean initial = observedRecipeCatalogVersion == Long.MIN_VALUE;
        observedRecipeCatalogVersion = revision;
        cachedCatalystCatalogVersion = Long.MIN_VALUE;
        cachedVirtualCatalyst = ItemStack.EMPTY;
        catalystPage = Math.max(0, Math.min(
                catalystPageCount() - 1, catalystPage));
        if (!initial) {
            Arrays.fill(progress, 0);
            Arrays.fill(requiredTicks, 0);
        }
        if (recipeCacheLookupMonitors != null) {
            for (var monitor : recipeCacheLookupMonitors) {
                if (monitor != null) {
                    monitor.onChange();
                }
            }
        }
        refreshRecipeLock();
        PatternAutomationRefreshHooks.request(this);
        return true;
    }

    @Override
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
        if (level != null && level.isClientSide()) {
            return syncedPhysicalCatalyst;
        }
        return catalystLibrary == null || selectedCatalystIndex < 0
                ? ItemStack.EMPTY
                : catalystLibrary.get(selectedCatalystIndex);
    }

    public int catalystPage() {
        return catalystPage;
    }

    public int catalystPageCount() {
        return catalystLibrary == null ? 1 : catalystLibrary.pageCount(
                windowRecipeCount());
    }

    public int catalystVisibleSlotCount() {
        if (level != null && level.isClientSide()) {
            return Math.max(1, syncedCatalystVisibleSlotCount);
        }
        return catalystLibrary == null ? 1
                : catalystLibrary.visibleSlotCount(
                catalystIdentifierRecipeCount());
    }

    public void cycleCatalystPage(int delta) {
        catalystPage = Math.floorMod(catalystPage + delta,
                catalystPageCount());
        setChanged();
    }

    private int windowRecipeCount() {
        return level != null && level.isClientSide()
                ? Math.max(1, syncedCatalystVisibleSlotCount)
                : catalystIdentifierRecipeCount();
    }

    private void clampCatalystPage() {
        catalystPage = catalystLibrary == null ? 0
                : catalystLibrary.clampPage(
                catalystPage, windowRecipeCount());
    }

    public void selectCatalystIdentifier(int index) {
        if (catalystLibrary == null || index < 0
                || index >= catalystVisibleSlotCount()
                || catalystLibrary.get(index).isEmpty()) {
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

    @Override
    public boolean canSelectCatalystIdentifierId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        ItemStack selected = selectedCatalystIdentifier();
        if (!selected.isEmpty() && id.equals(
                CatalystIdentifierItem.catalystId(selected).toString())) {
            return true;
        }
        if (inputSlots != null && inputSlots.stream()
                .anyMatch(slot -> !slot.getStack().isEmpty())) {
            return false;
        }
        if (progress != null && Arrays.stream(progress)
                .anyMatch(value -> value > 0)) {
            return false;
        }
        return !com.example.mekanismmagic.integration.common.network
                .PatternAutomationRefreshHooks.hasPendingPatternWork(this);
    }

    private boolean canAdoptMarkedPatternRecipe(
            ResourceLocation recipeId) {
        String desired = catalystIdentifierIdForRecipe(recipeId);
        if (desired.isEmpty()) {
            return false;
        }
        ItemStack selected = selectedCatalystIdentifier();
        if (!selected.isEmpty() && desired.equals(
                CatalystIdentifierItem.catalystId(selected).toString())) {
            return true;
        }
        if (progress != null && Arrays.stream(progress)
                .anyMatch(value -> value > 0)
                || com.example.mekanismmagic.integration.common.network
                .PatternAutomationRefreshHooks.hasPendingPatternWork(this)) {
            return false;
        }
        return inputSlots != null && inputSlots.stream()
                .anyMatch(slot -> !slot.getStack().isEmpty())
                && inputSlots.stream().filter(
                        slot -> !slot.getStack().isEmpty())
                .allMatch(slot -> recipeId.equals(
                        ArsNouveauRecipeBridge.patternRecipe(
                                slot.getStack())));
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack, Direction side) {
        IInventorySlot target = getInventorySlot(slot, side);
        if (target != null && catalystLibrarySlots != null
                && catalystLibrarySlots.contains(target)
                && !PersistentInputMutationGuard.permits(target, stack)) {
            return;
        }
        super.setStackInSlot(slot, stack, side);
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

    private int catalystLibraryIndex(String catalystId) {
        if (catalystLibrary == null || catalystId == null
                || catalystId.isBlank()) {
            return -1;
        }
        for (int index = 0;
             index < catalystLibrary.retainedSlotCount(); index++) {
            ItemStack stack = catalystLibrary.get(index);
            if (!stack.isEmpty() && catalystId.equals(
                    CatalystIdentifierItem.catalystId(
                            stack).toString())) {
                return index;
            }
        }
        return -1;
    }

    private ItemStack physicalSelectedCatalyst() {
        return catalystLibrary == null || selectedCatalystIndex < 0
                ? ItemStack.EMPTY
                : catalystLibrary.get(selectedCatalystIndex);
    }

    /**
     * Factory warning indicators are meaningful only once an actual process
     * has been selected. Empty inputs and an empty lock are normal idle state.
     */
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

    /**
     * Warning widgets poll this path many times per rendered frame. Checking
     * the synchronized selection fields avoids rebuilding or resolving the
     * virtual identifier stack merely to answer a boolean question.
     */
    private boolean hasCatalystSelection() {
        if (virtualCatalystSelected) {
            return level != null && (level.isClientSide()
                    ? virtualCatalystIndex >= 0
                    : !virtualCatalystId.isBlank());
        }
        return catalystLibrary != null
                && selectedCatalystIndex >= 0
                && !catalystLibrary.get(selectedCatalystIndex).isEmpty();
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

    @Override
    public void applyInventorySlots(
            net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput input,
            List<IInventorySlot> slots, AttachedItems attached) {
        int firstWindow = catalystLibrarySlots == null
                || catalystLibrarySlots.isEmpty() ? -1
                : slots.indexOf(catalystLibrarySlots.getFirst());
        if (firstWindow >= 0 && CatalystLibraryMigration
                .isLegacyAttachment(attached, slots.size())) {
            List<ItemStack> legacy = CatalystLibraryMigration
                    .copyAttachedRange(attached, firstWindow,
                            com.example.mekanismmagic.blockentity
                                    .NativeMagicMachineBlockEntity
                                    .LEGACY_CATALYST_LIBRARY_SLOT_COUNT);
            super.applyInventorySlots(input, slots,
                    CatalystLibraryMigration.remapLegacyAttachment(
                            attached, slots.size(), firstWindow));
            catalystLibrary.replace(legacy);
            validateSelectedCatalystIndex();
            refreshRecipeLock();
            return;
        }
        super.applyInventorySlots(input, slots, attached);
        validateSelectedCatalystIndex();
        refreshRecipeLock();
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
        List<ItemStack> catalysts = catalystLibrary == null
                ? List.of() : catalystLibrary.snapshot();
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
        if (catalystLibrary != null) {
            catalystLibrary.replace(imbuement.catalystLibrary);
        }
        setSource(imbuement.source);
        if (lockSlot != null) {
            lockSlot.setStack(imbuement.recipeLock.copy());
        }
        selectedCatalystIndex = catalystLibrary == null
                || imbuement.selectedCatalystIndex < 0
                || catalystLibrary.get(
                imbuement.selectedCatalystIndex).isEmpty()
                ? -1 : imbuement.selectedCatalystIndex;
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
    public boolean mekanismMagicGroupParallelItemInputs() {
        return true;
    }

    @Override
    public List<IInventorySlot> mekanismMagicManualOnlySlots() {
        return catalystLibrarySlots == null
                ? List.of() : List.copyOf(catalystLibrarySlots);
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
    public mekanism.api.energy.IEnergyContainer
    mekanismMagicEnergyContainer() {
        return energyContainer;
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
                this::catalystVisibleSlotCount,
                value -> syncedCatalystVisibleSlotCount = Math.max(1,
                        value)));
        container.track(SyncableItemStack.create(
                this::physicalSelectedCatalyst,
                value -> syncedPhysicalCatalyst = value == null
                        ? ItemStack.EMPTY : value.copy()));
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
        if (catalystLibrary != null) {
            catalystLibrary.save(tag, CatalystLibraryMigration.STORAGE_NBT,
                    registries);
            ItemStack selected = catalystLibrary.get(
                    selectedCatalystIndex);
            if (!selected.isEmpty()) {
                tag.putString("catalyst_selected_id",
                        CatalystIdentifierItem.catalystId(
                                selected).toString());
            }
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean loadedDynamicLibrary = catalystLibrary != null
                && catalystLibrary.load(tag,
                CatalystLibraryMigration.STORAGE_NBT, registries);
        if (!loadedDynamicLibrary && catalystLibrary != null
                && catalystLibrarySlots != null
                && !catalystLibrarySlots.isEmpty()) {
            List<IInventorySlot> allSlots = getInventorySlots(null);
            int firstWindow = allSlots.indexOf(
                    catalystLibrarySlots.getFirst());
            if (firstWindow >= 0) {
                catalystLibrary.replace(CatalystLibraryMigration
                        .migrateLegacyWorldInventory(tag, registries,
                                allSlots, firstWindow));
            }
        }
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
        String selectedCatalystId = tag.getString(
                "catalyst_selected_id");
        if (!selectedCatalystId.isBlank()) {
            int resolvedIndex = catalystLibraryIndex(selectedCatalystId);
            if (resolvedIndex >= 0) {
                selectedCatalystIndex = resolvedIndex;
            }
        }
        if (!tag.contains("catalyst_selected_index")
                && selectedCatalystId.isBlank()
                && catalystLibrary != null) {
            for (int index = 0;
                 index < catalystLibrary.retainedSlotCount(); index++) {
                if (!catalystLibrary.get(index).isEmpty()) {
                    selectedCatalystIndex = index;
                    break;
                }
            }
        }
        if (loadedDynamicLibrary || catalystLibrary != null
                && catalystLibrary.retainedSlotCount() > 0) {
            validateSelectedCatalystIndex();
        }
        catalystPage = Math.max(0, tag.getInt("catalyst_page"));
        clampCatalystPage();
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

    private void validateSelectedCatalystIndex() {
        if (selectedCatalystIndex >= 0
                && (catalystLibrary == null
                || catalystLibrary.get(selectedCatalystIndex).isEmpty())) {
            selectedCatalystIndex = -1;
        }
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

}
