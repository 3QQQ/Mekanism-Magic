package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;
import com.example.mekanismmagic.integration.common.network.PatternAutomationRefreshHooks;

/**
 * Mekanism implementation of Ars Nouveau imbuement recipes.
 */
public final class ImbuementProcessorBlockEntity
        extends ArsSourceMachineBlockEntity
        implements CatalystIdentifierSelectionHost {
    public static final int REAGENT_SLOT = 0;
    private CatalystLibraryStorage catalystLibraryStorage;
    private List<CatalystLibraryWindowSlot> catalystLibrarySlots;
    private ItemStack syncedPhysicalCatalyst = ItemStack.EMPTY;
    private boolean catalystLibraryOpen;
    private int selectedCatalystIndex = -1;
    private int catalystPage;
    private int syncedCatalystVisibleSlotCount = 1;
    private boolean virtualCatalystSelected;
    private int virtualCatalystIndex = -1;
    private String virtualCatalystId = "";
    private ItemStack cachedVirtualCatalyst = ItemStack.EMPTY;
    private String cachedVirtualCatalystId = "";
    private int cachedVirtualCatalystIndex = Integer.MIN_VALUE;
    private long cachedCatalystCatalogVersion = Long.MIN_VALUE;
    private long observedRecipeCatalogVersion = Long.MIN_VALUE;

    public ImbuementProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected int sourceInteractionRadius() {
        return ArsNouveauMachineConfig
                .IMBUEMENT_SOURCE_INTERACTION_RADIUS;
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, REAGENT_SLOT,
                InputInventorySlot.at(listener, 64, 17));
        IContentsListener libraryListener = () -> {
            listener.onContentsChanged();
            clampCatalystPage();
            setChanged();
        };
        catalystLibraryStorage = new CatalystLibraryStorage(
                libraryListener::onContentsChanged);
        CatalystLibraryStorage.PageWindow window = catalystLibraryStorage
                .pageWindow(this::catalystPage,
                        this::catalystWindowRecipeCount);
        catalystLibrarySlots = CatalystLibraryWindowSlot.createManualWindow(
                window,
                () -> level != null && level.isClientSide(),
                () -> level == null || !level.isClientSide()
                        || catalystLibraryOpen,
                stack -> stack.is(
                        ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get()),
                pageSlot -> CatalystLibraryLayout.slotX(176, pageSlot),
                CatalystLibraryLayout::slotY);
        for (int index = 0; index < catalystLibrarySlots.size(); index++) {
            registerLogicalSlot(helper, CATALYST_LIBRARY_SLOT_START + index,
                    catalystLibrarySlots.get(index));
        }
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        var itemConfig = setupArsItemIO(
                List.of(inputSlot), List.of(outputSlot), List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        ResourceLocation patternRecipe = ArsNouveauRecipeBridge
                .patternRecipe(inventory.getStackInSlot(REAGENT_SLOT));
        if (patternRecipe != null
                && (!canAdoptMarkedPatternRecipe(patternRecipe)
                || !selectCatalystIdentifierForRecipe(patternRecipe))) {
            return Optional.empty();
        }
        if (patternRecipe != null) {
            inventory.setStackInSlot(REAGENT_SLOT,
                    ArsNouveauRecipeBridge.recipeInputView(
                            inventory.getStackInSlot(REAGENT_SLOT)));
        }
        ItemStack selected = selectedCatalystIdentifier();
        return ArsNouveauRecipeBridge.findImbuementByIdentifier(
                level, inventory, REAGENT_SLOT, selected);
    }

    @Override
    protected long recipeLookupRevision() {
        return level == null ? 0L : ArsNouveauRecipeScanner.version(
                level.getRecipeManager());
    }

    @Override
    protected boolean onUpdateServer() {
        boolean revisionChanged = refreshRecipeCatalogVersion();
        return super.onUpdateServer() || revisionChanged;
    }

    private boolean refreshRecipeCatalogVersion() {
        long revision = recipeLookupRevision();
        if (observedRecipeCatalogVersion == revision) {
            return false;
        }
        boolean initial = observedRecipeCatalogVersion == Long.MIN_VALUE;
        observedRecipeCatalogVersion = revision;
        invalidateVirtualCatalystCache();
        clampCatalystPage();
        if (!initial) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
        }
        PatternAutomationRefreshHooks.request(this);
        return true;
    }

    @Override
    protected int baseEnergyPerTick() {
        return 600;
    }

    @Override
    protected int energySlotX() {
        return 39;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    void seedDevelopmentTest(boolean electric) {
        inputSlot.setStack(new net.minecraft.world.item.ItemStack(
                Items.AMETHYST_SHARD));
        setSource(electric && hasCreativeSourceUpgrade() ? 0 : getMaxSource());
        if (energyContainer != null) {
            energyContainer.setEnergy(energyContainer.getMaxEnergy());
        }
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternInputs() {
        return inputSlot == null ? List.of() : List.of(inputSlot);
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
    public IUpgradeData getUpgradeData(
            net.minecraft.core.HolderLookup.Provider registries) {
        List<ItemStack> catalysts = catalystLibraryStorage == null
                ? List.of() : catalystLibraryStorage.snapshot();
        return new ImbuementFactoryUpgradeData(registries, redstone,
                getControlType(), energyContainer, new int[]{progress},
                energySlot,
                inputSlot == null ? List.of() : List.of(inputSlot),
                outputSlot == null ? List.of() : List.of(outputSlot),
                false, getComponents(), getSource(),
                selectedCatalystIdentifier(), catalysts,
                selectedCatalystIndex, virtualCatalystSelected,
                virtualCatalystId, catalystLibraryOpen, catalystPage,
                new int[]{Math.max(1, progressRequired)},
                sourceModesForUpgrade(), sourceLinksForUpgrade());
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
                cachedVirtualCatalyst =
                        ArsNouveauRecipeBridge.catalystIdentifierJeiStack(
                                level, requestedId);
                cachedVirtualCatalystId = requestedId;
                cachedVirtualCatalystIndex = virtualCatalystIndex;
                cachedCatalystCatalogVersion = version;
            }
            return cachedVirtualCatalyst;
        }
        if (level != null && level.isClientSide()) {
            return syncedPhysicalCatalyst;
        }
        return physicalSelectedCatalyst();
    }

    public int selectedCatalystIndex() {
        return selectedCatalystIndex;
    }

    public int catalystPage() {
        return catalystPage;
    }

    public int catalystPageCount() {
        if (level != null && level.isClientSide()) {
            return Math.max(1, Math.ceilDiv(
                    Math.max(1, syncedCatalystVisibleSlotCount),
                    CatalystLibraryStorage.PAGE_SIZE));
        }
        return catalystLibraryStorage == null ? 1
                : catalystLibraryStorage.pageCount(
                        catalystWindowRecipeCount());
    }

    public int catalystVisibleSlotCount() {
        if (level != null && level.isClientSide()) {
            return Math.max(1, syncedCatalystVisibleSlotCount);
        }
        return catalystLibraryStorage == null ? 1
                : catalystLibraryStorage.visibleSlotCount(
                        catalystIdentifierRecipeCount());
    }

    private int catalystWindowRecipeCount() {
        return level != null && level.isClientSide()
                ? Math.max(1, syncedCatalystVisibleSlotCount)
                : catalystIdentifierRecipeCount();
    }

    public ItemStack catalystPageStack(int pageSlot) {
        return catalystLibrarySlots != null && pageSlot >= 0
                && pageSlot < catalystLibrarySlots.size()
                ? catalystLibrarySlots.get(pageSlot).getStack()
                : ItemStack.EMPTY;
    }

    public void cycleCatalystPage(int delta) {
        catalystPage = Math.floorMod(catalystPage + delta,
                catalystPageCount());
        setChanged();
    }

    private void clampCatalystPage() {
        if (level != null && level.isClientSide()) {
            catalystPage = Math.max(0, Math.min(
                    catalystPageCount() - 1, catalystPage));
        } else {
            catalystPage = catalystLibraryStorage == null ? 0
                    : catalystLibraryStorage.clampPage(
                            catalystPage, catalystWindowRecipeCount());
        }
    }

    public void selectCatalystIdentifier(int index) {
        if (catalystLibraryStorage == null || index < 0
                || index >= catalystVisibleSlotCount()) {
            return;
        }
        if (!catalystLibraryStorage.get(index).isEmpty()) {
            selectedCatalystIndex = index;
            virtualCatalystSelected = false;
            virtualCatalystIndex = -1;
            virtualCatalystId = "";
            cachedVirtualCatalyst = ItemStack.EMPTY;
            setChanged();
        }
    }

    public boolean selectCatalystIdentifierId(String id) {
        VirtualCatalystSelection selection =
                resolveVirtualCatalystSelection(id);
        if (selection == null) {
            return false;
        }
        if (virtualCatalystSelected
                && selection.id().equals(virtualCatalystId)) {
            return true;
        }
        applyVirtualCatalystSelection(selection);
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
        return inputSlot != null && inputSlot.getStack().isEmpty()
                && !mekanismMagicIsBusy();
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
        ItemStack input = inputSlot == null
                ? ItemStack.EMPTY : inputSlot.getStack();
        return progress <= 0 && !input.isEmpty()
                && recipeId.equals(
                ArsNouveauRecipeBridge.patternRecipe(input))
                && !com.example.mekanismmagic.integration.common.network
                .PatternAutomationRefreshHooks.hasPendingPatternWork(this);
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
        applyClearedCatalystSelection();
    }

    /**
     * Atomically accepts one external processing-pattern input and its
     * non-physical catalyst context. The complete stack is simulated before
     * either the input slot or catalyst selection changes.
     */
    public boolean acceptAutomatedImbuementInput(
            ItemStack input, boolean requiresCatalyst, String catalystId) {
        if (inputSlot == null || input == null || input.isEmpty()
                || mekanismMagicIsBusy()
                || !inputSlot.getStack().isEmpty()) {
            return false;
        }
        VirtualCatalystSelection requestedSelection = requiresCatalyst
                ? resolveVirtualCatalystSelection(catalystId) : null;
        if (requiresCatalyst && requestedSelection == null) {
            return false;
        }
        ItemStack simulatedRemainder = inputSlot.insertItem(
                input, Action.SIMULATE, AutomationType.EXTERNAL);
        if (!simulatedRemainder.isEmpty()) {
            return false;
        }

        ItemStack previousInput = inputSlot.getStack().copy();
        CatalystSelectionState previousSelection =
                captureCatalystSelection();
        try {
            ItemStack remainder = inputSlot.insertItem(
                    input, Action.EXECUTE, AutomationType.EXTERNAL);
            if (!remainder.isEmpty()) {
                restoreAutomatedInputState(
                        previousInput, previousSelection);
                return false;
            }
            if (requestedSelection == null) {
                applyClearedCatalystSelection();
            } else {
                applyVirtualCatalystSelection(requestedSelection);
            }
            return true;
        } catch (RuntimeException | Error failure) {
            restoreAutomatedInputState(previousInput, previousSelection);
            throw failure;
        }
    }

    private VirtualCatalystSelection resolveVirtualCatalystSelection(
            String id) {
        if (level == null || id == null || id.isBlank()) {
            return null;
        }
        int index = ArsNouveauRecipeBridge.catalystIdentifierJeiIndex(
                level, id);
        if (index < 0) {
            return null;
        }
        ItemStack canonical = ArsNouveauRecipeBridge
                .catalystIdentifierJeiStack(level, index);
        String canonicalId = canonical.isEmpty() ? id
                : CatalystIdentifierItem.catalystId(canonical).toString();
        return new VirtualCatalystSelection(index, canonicalId);
    }

    private void applyVirtualCatalystSelection(
            VirtualCatalystSelection selection) {
        virtualCatalystSelected = true;
        virtualCatalystIndex = selection.index();
        virtualCatalystId = selection.id();
        invalidateVirtualCatalystCache();
        setChanged();
    }

    private void applyClearedCatalystSelection() {
        virtualCatalystSelected = false;
        virtualCatalystIndex = -1;
        virtualCatalystId = "";
        selectedCatalystIndex = -1;
        invalidateVirtualCatalystCache();
        setChanged();
    }

    private CatalystSelectionState captureCatalystSelection() {
        return new CatalystSelectionState(selectedCatalystIndex,
                virtualCatalystSelected, virtualCatalystIndex,
                virtualCatalystId);
    }

    private void restoreAutomatedInputState(
            ItemStack previousInput,
            CatalystSelectionState previousSelection) {
        inputSlot.setStack(previousInput);
        selectedCatalystIndex = previousSelection.selectedIndex();
        virtualCatalystSelected = previousSelection.virtualSelected();
        virtualCatalystIndex = previousSelection.virtualIndex();
        virtualCatalystId = previousSelection.virtualId();
        invalidateVirtualCatalystCache();
        setChanged();
    }

    private void invalidateVirtualCatalystCache() {
        cachedVirtualCatalyst = ItemStack.EMPTY;
        cachedVirtualCatalystId = "";
        cachedVirtualCatalystIndex = Integer.MIN_VALUE;
        cachedCatalystCatalogVersion = Long.MIN_VALUE;
    }

    public int catalystIdentifierRecipeCount() {
        return level == null ? 0 : ArsNouveauRecipeBridge
                .catalystIdentifierJeiRecipes(level).size();
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

    private record VirtualCatalystSelection(int index, String id) {
    }

    private record CatalystSelectionState(
            int selectedIndex,
            boolean virtualSelected,
            int virtualIndex,
            String virtualId) {
    }

    public int catalystIdentifierIndex(String id) {
        if (catalystLibraryStorage == null || id == null || id.isEmpty()) {
            return -1;
        }
        List<ItemStack> catalysts = catalystLibraryStorage.snapshot();
        for (int index = 0; index < catalysts.size(); index++) {
            var data = catalysts.get(index)
                    .get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null && id.equals(data.getUnsafe()
                    .getString("catalyst_id"))) {
                return index;
            }
        }
        return -1;
    }

    private ItemStack physicalSelectedCatalyst() {
        return catalystLibraryStorage == null || selectedCatalystIndex < 0
                ? ItemStack.EMPTY
                : catalystLibraryStorage.get(selectedCatalystIndex);
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
                            LEGACY_CATALYST_LIBRARY_SLOT_COUNT);
            super.applyInventorySlots(input, slots,
                    CatalystLibraryMigration.remapLegacyAttachment(
                            attached, slots.size(), firstWindow));
            catalystLibraryStorage.replace(legacy);
            return;
        }
        super.applyInventorySlots(input, slots, attached);
    }

    @Override
    public void addContainerTrackers(mekanism.common.inventory.container.MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(mekanism.common.inventory.container.sync.SyncableInt.create(
                () -> selectedCatalystIndex,
                value -> selectedCatalystIndex = Math.max(-1, value)));
        // This must precede the page tracker. During initial sync the client
        // needs the retained tail size before it clamps a saved tail page.
        container.track(mekanism.common.inventory.container.sync.SyncableInt.create(
                this::catalystVisibleSlotCount,
                value -> syncedCatalystVisibleSlotCount =
                        Math.max(1, value)));
        container.track(SyncableItemStack.create(
                this::physicalSelectedCatalyst,
                value -> syncedPhysicalCatalyst = value == null
                        ? ItemStack.EMPTY : value.copy()));
        container.track(mekanism.common.inventory.container.sync.SyncableInt.create(
                () -> catalystPage,
                value -> catalystPage = Math.max(0, Math.min(
                        catalystPageCount() - 1, value))));
        container.track(mekanism.common.inventory.container.sync.SyncableBoolean
                .create(() -> virtualCatalystSelected,
                        value -> virtualCatalystSelected = value));
        container.track(mekanism.common.inventory.container.sync.SyncableInt
                .create(this::trackedVirtualCatalystIndex,
                        value -> virtualCatalystIndex = Math.max(-1, value)));
    }

    @Override
    protected void saveArsMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        tag.putInt("catalyst_selected_index", selectedCatalystIndex);
        tag.putInt("catalyst_page", catalystPage);
        tag.putBoolean("virtual_catalyst_selected",
                virtualCatalystSelected);
        tag.putString("virtual_catalyst_id", virtualCatalystId);
        if (catalystLibraryStorage != null) {
            catalystLibraryStorage.save(tag,
                    CatalystLibraryMigration.STORAGE_NBT, registries);
        }
    }

    @Override
    protected void loadArsMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        if (catalystLibraryStorage != null
                && !catalystLibraryStorage.load(tag,
                CatalystLibraryMigration.STORAGE_NBT, registries)) {
            List<IInventorySlot> allSlots = getInventorySlots(null);
            int firstWindow = catalystLibrarySlots == null
                    || catalystLibrarySlots.isEmpty() ? -1
                    : allSlots.indexOf(catalystLibrarySlots.getFirst());
            if (firstWindow >= 0) {
                catalystLibraryStorage.replace(CatalystLibraryMigration
                        .migrateLegacyWorldInventory(tag, registries,
                                allSlots, firstWindow));
            }
        }
        selectedCatalystIndex = tag.contains("catalyst_selected_index")
                ? Math.max(-1, tag.getInt("catalyst_selected_index")) : -1;
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
    }
}
