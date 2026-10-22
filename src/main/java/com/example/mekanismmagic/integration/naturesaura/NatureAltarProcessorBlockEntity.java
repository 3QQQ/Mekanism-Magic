package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.blockentity.NativeMachineUpgradeData;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryWindowSlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/** Single-block processor for Nature's Aura altar recipes. */
public final class NatureAltarProcessorBlockEntity
        extends NatureAuraMachineBlockEntity
        implements NatureAltarAutomationHost {
    private static final int SECOND_CATALYST_SLOT = 38;
    private static final String LOCKED_RECIPE =
            "nature_altar_locked_recipe";
    private static final String LOCKED_RECIPE_SIGNATURE =
            "nature_altar_locked_recipe_signature";
    private static final String CONTEXT_REMAINING =
            "nature_altar_context_remaining";
    private static final String PREFERRED_RECIPE =
            "nature_altar_preferred_recipe";

    private BasicInventorySlot catalystSlot;
    private BasicInventorySlot secondCatalystSlot;
    private NatureAltarCatalystLibrary catalystLibrary;
    private ResourceLocation lockedRecipe;
    private String lockedRecipeSignature = "";
    private int contextRemaining;
    private ResourceLocation preferredRecipe;
    private int matchingRecipeCount;
    private ItemStack selectedRecipeOutput = ItemStack.EMPTY;

    public NatureAltarProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get()
                        .builtInRegistryHolder(), pos, state,
                NatureAuraMachineConfig.ALTAR_CAPACITY,
                NatureAuraMachineConfig.ALTAR_TRANSFER,
                AuraSideMode.INPUT);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, 0,
                InputInventorySlot.at(stack -> NatureAuraRecipeBridge
                        .isAltarInput(level, stack), listener, 46, 35));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        catalystSlot = registerLogicalSlot(helper, CONTAINMENT_SLOT,
                BasicInventorySlot.at(
                        (stack, automation) ->
                                automation == AutomationType.MANUAL,
                        (stack, automation) -> true,
                        stack -> NatureAuraRecipeBridge.isAltarCatalyst(
                                level, stack), listener, 64, 53));
        secondCatalystSlot = registerLogicalSlot(helper,
                SECOND_CATALYST_SLOT, BasicInventorySlot.at(
                        (stack, automation) ->
                                automation == AutomationType.MANUAL,
                        (stack, automation) -> true,
                        stack -> NatureAuraRecipeBridge.isAltarCatalyst(
                                level, stack), listener, 82, 53));
        catalystLibrary = new NatureAltarCatalystLibrary(
                this::getLevel, () -> 176, listener::onContentsChanged);
        for (int index = 0;
             index < catalystLibrary.windowSlots().size(); index++) {
            // The backing library is not a processing-inventory index.
            helper.addSlot(catalystLibrary.windowSlots().get(index));
        }
        var itemConfig = setupNativeItemIO(
                List.of(inputSlot), List.of(outputSlot), List.of());
        addNativeItemSlotInfo(itemConfig, DataType.EXTRA,
                true, false, List.of(catalystSlot, secondCatalystSlot));
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        ItemStack input = inventory.getStackInSlot(0);
        if (lockedRecipe != null && contextRemaining > 0) {
            ItemStack cleanInput = NatureAuraRecipeBridge
                    .recipeInputView(input);
            Optional<NatureAuraRecipeBridge.AltarRecipeView> locked =
                    level == null ? Optional.empty()
                            : NatureAuraRecipeScanner.find(
                            level.getRecipeManager(), lockedRecipe);
            String currentSignature = locked
                    .map(NatureAuraRecipeBridge::recipeSignature)
                    .orElse("");
            boolean inputMatches = locked
                    .filter(recipe -> recipe.input().test(cleanInput))
                    .isPresent();
            if (!NatureAltarLockedContextPolicy.remainsValid(
                    locked.isPresent(), inputMatches,
                    lockedRecipeSignature, currentSignature)) {
                // An already delivered AE batch belongs to the old signed
                // recipe. Keep it quarantined until its input is removed.
                return Optional.empty();
            }
        }
        boolean patternMarked = NatureAuraRecipeBridge
                .hasPatternRecipeMarker(input);
        ResourceLocation patternRecipe = patternMarked
                ? NatureAuraRecipeBridge.patternRecipe(input) : null;
        Optional<NatureAuraRecipeBridge.AltarRecipeView> found =
                patternMarked
                        ? findExactAutomationRecipe(
                        input, patternRecipe)
                        : NatureAuraRecipeBridge.findAltarRecipe(
                        level, input, getCatalystStacks(), lockedRecipe,
                        preferredRecipe);
        if (found.isEmpty()) {
            // A malformed/stale AE context must fail closed instead of
            // silently selecting another recipe with the same input.
            if (!patternMarked) {
                clearLockedRecipe();
            }
            return Optional.empty();
        }
        ResourceLocation selected = found.get().id();
        String selectedSignature = NatureAuraRecipeBridge
                .recipeSignature(found.get());
        boolean sameSelection = selected.equals(lockedRecipe)
                && (lockedRecipeSignature.isBlank()
                || lockedRecipeSignature.equals(selectedSignature));
        if (!sameSelection && lockedRecipe != null && progress > 0) {
            return Optional.empty();
        }
        if (!sameSelection || lockedRecipeSignature.isBlank()) {
            lockedRecipe = selected;
            lockedRecipeSignature = selectedSignature;
            setChanged();
        }
        if (patternMarked && inputSlot != null) {
            if (contextRemaining <= 0) {
                contextRemaining = Math.max(1, input.getCount());
            } else {
                contextRemaining = Math.max(
                        contextRemaining, input.getCount());
            }
            ItemStack clean = NatureAuraRecipeBridge
                    .clearPatternRecipeMarker(inputSlot.getStack());
            if (!ItemStack.isSameItemSameComponents(
                    clean, inputSlot.getStack())) {
                inputSlot.setStack(clean);
            }
        }
        return Optional.of(NatureAuraRecipeBridge.toMachineRecipe(
                found.get(), 0));
    }

    @Override
    protected long recipeLookupRevision() {
        long catalogVersion = level == null ? 0L
                : NatureAuraRecipeScanner.version(
                level.getRecipeManager());
        return 31L * catalogVersion + (preferredRecipe == null ? 0
                : preferredRecipe.hashCode());
    }

    @Override
    protected boolean onUpdateServer() {
        boolean cleared = catalystLibrary != null
                && catalystLibrary.clampPage();
        if (cleared) {
            setChanged();
        }
        if (lockedRecipe != null) {
            ItemStack input = inputSlot == null ? ItemStack.EMPTY
                    : NatureAuraRecipeBridge.recipeInputView(
                    inputSlot.getStack());
            Optional<NatureAuraRecipeBridge.AltarRecipeView> current =
                    level == null ? Optional.empty()
                            : NatureAuraRecipeScanner.find(
                            level.getRecipeManager(), lockedRecipe);
            boolean inputMatches = !input.isEmpty()
                    && current.filter(recipe -> recipe.input().test(input))
                    .isPresent();
            String currentSignature = current
                    .map(NatureAuraRecipeBridge::recipeSignature)
                    .orElse("");
            boolean valid = NatureAltarLockedContextPolicy.remainsValid(
                    current.isPresent(), inputMatches,
                    lockedRecipeSignature, currentSignature);
            if (!valid) {
                if (NatureAltarLockedContextPolicy.quarantineInvalid(
                        valid, contextRemaining, input.getCount())) {
                    // Do not reinterpret an in-flight exact AE batch as the
                    // replacement recipe that reused the same id.
                    if (progress != 0) {
                        progress = 0;
                        setChanged();
                        cleared = true;
                    }
                } else {
                    clearLockedRecipe();
                    cleared = true;
                }
            } else if (lockedRecipeSignature.isBlank()) {
                lockedRecipeSignature = NatureAuraRecipeBridge
                        .recipeSignature(current.orElseThrow());
                setChanged();
                cleared = true;
            }
            if (lockedRecipe != null && contextRemaining > 0) {
                int clamped = NatureAltarContextCountPolicy.clampToInput(
                        contextRemaining, input.getCount());
                if (clamped != contextRemaining) {
                    contextRemaining = clamped;
                    setChanged();
                    cleared = true;
                }
            }
        }
        boolean changed = super.onUpdateServer();
        return refreshRecipeChoiceDisplay() || changed || cleared;
    }

    @Override
    protected boolean hasAnyRecipeInput() {
        return inputSlot != null && !inputSlot.getStack().isEmpty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return NatureAuraMachineConfig.ALTAR_BASE_ENERGY_PER_TICK;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return true;
    }

    /**
     * Commits a complete AE/MekE batch and its virtual recipe context as one
     * transaction. The persistent catalyst is verified but never inserted or
     * consumed by this method.
     */
    @Override
    public boolean acceptNatureAltarPattern(
            ResourceLocation recipeId, ItemStack input) {
        if (level == null || inputSlot == null || recipeId == null
                || input == null || input.isEmpty() || progress > 0
                || !inputSlot.getStack().isEmpty()
                || findExactAutomationRecipe(input, recipeId).isEmpty()) {
            return false;
        }
        ItemStack cleanInput = NatureAuraRecipeBridge.recipeInputView(input);
        int newContextRemaining = NatureAltarContextCountPolicy
                .onAccepted(contextRemaining, cleanInput.getCount());
        if (newContextRemaining < 0) {
            return false;
        }
        ItemStack simulated = inputSlot.insertItem(cleanInput,
                Action.SIMULATE, AutomationType.EXTERNAL);
        if (!simulated.isEmpty()) {
            return false;
        }

        ItemStack previousInput = inputSlot.getStack().copy();
        ResourceLocation previousRecipe = lockedRecipe;
        String previousSignature = lockedRecipeSignature;
        int previousContextRemaining = contextRemaining;
        try {
            ItemStack remainder = inputSlot.insertItem(cleanInput,
                    Action.EXECUTE, AutomationType.EXTERNAL);
            if (!remainder.isEmpty()) {
                inputSlot.setStack(previousInput);
                lockedRecipe = previousRecipe;
                lockedRecipeSignature = previousSignature;
                contextRemaining = previousContextRemaining;
                setChanged();
                return false;
            }
            lockedRecipe = recipeId;
            lockedRecipeSignature = NatureAuraRecipeScanner.find(
                            level.getRecipeManager(), recipeId)
                    .map(NatureAuraRecipeBridge::recipeSignature)
                    .orElse("");
            contextRemaining = newContextRemaining;
            setChanged();
            return true;
        } catch (RuntimeException | Error failure) {
            inputSlot.setStack(previousInput);
            lockedRecipe = previousRecipe;
            lockedRecipeSignature = previousSignature;
            contextRemaining = previousContextRemaining;
            setChanged();
            throw failure;
        }
    }

    private Optional<NatureAuraRecipeBridge.AltarRecipeView>
    findExactAutomationRecipe(ItemStack input, ResourceLocation recipeId) {
        if (level == null || recipeId == null || input == null
                || input.isEmpty()) {
            return Optional.empty();
        }
        if (NatureAuraRecipeBridge.hasPatternRecipeMarker(input)
                && !NatureAuraRecipeBridge.patternMarkerMatchesRecipe(
                level, input, recipeId)) {
            return Optional.empty();
        }
        ItemStack cleanInput = NatureAuraRecipeBridge.recipeInputView(input);
        return NatureAuraRecipeScanner.find(level.getRecipeManager(), recipeId)
                .filter(recipe -> recipe.input().test(cleanInput))
                .filter(recipe -> !recipe.requiresCatalyst()
                        || NatureAuraRecipeBridge.hasMatchingCatalyst(
                        recipe, getCatalystStacks()));
    }

    @Override
    protected int energySlotX() {
        return 28;
    }

    @Override
    protected int energySlotY() {
        return 53;
    }

    @Override
    protected void onRecipeFinished(MachineRecipeResult recipe) {
        if (contextRemaining > 0) {
            contextRemaining = NatureAltarContextCountPolicy
                    .afterCompletion(contextRemaining);
            if (contextRemaining > 0) {
                setChanged();
                return;
            }
        }
        clearLockedRecipe();
    }

    @Override
    protected boolean updateRecipeItemDisplay(
            ItemStackHandler inventory, MachineRecipeResult recipe) {
        ItemStack input = RecipeItemDisplayState.displayCopy(
                inventory.getStackInSlot(0));
        ItemStack output = RecipeItemDisplayState.displayCopy(
                recipe.output());
        ItemStack catalyst = ItemStack.EMPTY;
        if (level != null) {
            var selected = NatureAuraRecipeScanner.find(
                    level.getRecipeManager(), recipe.id());
            if (selected.isPresent() && selected.get().requiresCatalyst()) {
                for (ItemStack retained : getCatalystStacks()) {
                    if (selected.get().catalyst().test(retained)) {
                        catalyst = RecipeItemDisplayState.displayCopy(
                                retained);
                        break;
                    }
                }
            }
        }
        java.util.ArrayList<RecipeItemDisplayState.Entry> entries =
                new java.util.ArrayList<>(2);
        if (!input.isEmpty() || !output.isEmpty()) {
            entries.add(new RecipeItemDisplayState.Entry(
                    0, input, output));
        }
        if (!catalyst.isEmpty()) {
            entries.add(new RecipeItemDisplayState.Entry(
                    1, catalyst, ItemStack.EMPTY));
        }
        return mekanismMagicRecipeItemDisplay().update(entries);
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
        if (level == null || level.isClientSide || inputSlot == null
                || progress > 0 || contextRemaining > 0) {
            return;
        }
        List<ItemStack> catalysts = getCatalystStacks();
        var choice = NatureAltarRecipeChoices.firstAmbiguous(level,
                List.of(inputSlot.getStack()), catalysts,
                lane -> contextRemaining == 0 && progress == 0);
        if (choice.isEmpty()) {
            return;
        }
        ResourceLocation next = choice.get().next(level, catalysts,
                preferredRecipe);
        if (next.equals(preferredRecipe)) {
            return;
        }
        preferredRecipe = next;
        if (lockedRecipe != null && !NatureAuraRecipeBridge
                .hasPatternRecipeMarker(inputSlot.getStack())) {
            clearLockedRecipe();
        }
        activeRecipe = "";
        refreshRecipeChoiceDisplay();
        setChanged();
        sendUpdatePacket();
    }

    private boolean refreshRecipeChoiceDisplay() {
        var choice = inputSlot == null ? Optional
                .<NatureAltarRecipeChoices.Choice>empty()
                : NatureAltarRecipeChoices.firstAmbiguous(level,
                List.of(inputSlot.getStack()), getCatalystStacks(),
                lane -> contextRemaining == 0 && progress == 0);
        int nextCount = choice.map(value -> value.recipes().size())
                .orElse(0);
        ItemStack nextOutput = choice.map(value -> value.selectedOutput(
                level, getCatalystStacks(), preferredRecipe))
                .orElse(ItemStack.EMPTY);
        if (nextCount == matchingRecipeCount
                && ItemStack.matches(nextOutput, selectedRecipeOutput)) {
            return false;
        }
        matchingRecipeCount = nextCount;
        selectedRecipeOutput = nextOutput;
        return true;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        if (catalystLibrary != null) {
            catalystLibrary.addContainerTrackers(container);
        }
        container.track(SyncableInt.create(this::getMatchingRecipeCount,
                value -> matchingRecipeCount = Math.max(0, value)));
        container.track(SyncableItemStack.create(
                this::getSelectedRecipeOutput,
                value -> selectedRecipeOutput = value == null
                        ? ItemStack.EMPTY : value.copy()));
    }

    public ResourceLocation getLockedRecipe() {
        return lockedRecipe;
    }

    @Override
    public NatureAltarFactoryUpgradeData getUpgradeData(
            HolderLookup.Provider registries) {
        return NativeMachineUpgradeData.capture(new NatureAltarFactoryUpgradeData(
                registries, redstone, getControlType(), energyContainer,
                new int[]{progress}, energySlot,
                inputSlot == null ? List.of() : List.of(inputSlot),
                outputSlot == null ? List.of() : List.of(outputSlot),
                false, getComponents(), snapshotNatureAuraForUpgrade(),
                getCatalystStacks(), preferredRecipe,
                new ResourceLocation[]{lockedRecipe},
                new String[]{lockedRecipeSignature},
                new int[]{contextRemaining},
                new int[]{Math.max(1, progressRequired)}), this, registries);
    }

    @Override
    public void parseUpgradeData(HolderLookup.Provider registries,
                                 IUpgradeData data) {
        super.parseUpgradeData(registries, data);
        if (data instanceof NatureAltarFactoryUpgradeData altarUpgrade) {
            restoreNatureAuraFromUpgrade(altarUpgrade.auraSnapshot());
            setSpiritSourceFromUpgrade(altarUpgrade.catalyst(0));
            if (secondCatalystSlot != null) {
                secondCatalystSlot.setStack(
                        altarUpgrade.catalyst(1));
            }
            if (catalystLibrary != null) {
                catalystLibrary.replaceOverflow(
                        altarUpgrade.catalysts());
            }
            preferredRecipe = altarUpgrade.preferredRecipe();
            lockedRecipe = altarUpgrade.lockedRecipe(0);
            lockedRecipeSignature = altarUpgrade.lockedRecipeSignature(0);
            contextRemaining = altarUpgrade.contextRemaining(0);
            progressRequired = altarUpgrade.requiredTicks(0);
        }
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
    public AttachedItems collectInventorySlots(
            DataComponentMap.Builder builder, List<IInventorySlot> slots) {
        return NatureAltarCatalystMigration.collectPhysicalItemSlots(
                builder, slots, catalystLibrary);
    }

    @Override
    public void applyInventorySlots(BlockEntity.DataComponentInput input,
                                    List<IInventorySlot> slots,
                                    AttachedItems attachedItems) {
        NatureAltarCatalystMigration.applyPhysicalItemSlots(slots,
                NatureAltarCatalystMigration.processorAttachment(
                        attachedItems, slots, secondCatalystSlot,
                        catalystLibrary.windowSlots().getFirst()),
                catalystLibrary);
    }

    @Override
    protected ItemStack getSpiritSourceForUpgrade() {
        return getCatalystStack();
    }

    @Override
    protected void setSpiritSourceFromUpgrade(ItemStack source) {
        if (catalystSlot != null) {
            catalystSlot.setStack(source.copy());
        }
    }

    @Override
    protected void saveNatureAuraMachineData(
            CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(NatureAltarCatalystMigration.SLOT_COUNT_NBT,
                NatureAltarCatalystLibrary.SAVED_CATALYST_SLOTS);
        if (catalystLibrary != null) {
            catalystLibrary.save(tag, registries);
        }
        if (preferredRecipe != null) {
            tag.putString(PREFERRED_RECIPE, preferredRecipe.toString());
        }
        if (lockedRecipe != null) {
            tag.putString(LOCKED_RECIPE, lockedRecipe.toString());
            tag.putString(LOCKED_RECIPE_SIGNATURE,
                    lockedRecipeSignature);
            tag.putInt(CONTEXT_REMAINING,
                    Math.max(0, contextRemaining));
        }
    }

    @Override
    protected void loadNatureAuraMachineData(
            CompoundTag tag, HolderLookup.Provider registries) {
        if (catalystLibrary != null) {
            catalystLibrary.load(tag, registries);
            NatureAltarCatalystMigration.restoreProcessorWorldTail(
                    tag, registries, getInventorySlots(null),
                    secondCatalystSlot,
                    catalystLibrary.windowSlots().getFirst());
        }
        preferredRecipe = ResourceLocation.tryParse(
                tag.getString(PREFERRED_RECIPE));
        String savedRecipe = tag.getString(LOCKED_RECIPE);
        ResourceLocation parsed = savedRecipe.isBlank() ? null
                : ResourceLocation.tryParse(savedRecipe);
        lockedRecipe = parsed == null || parsed.getPath().isEmpty()
                ? null : parsed;
        lockedRecipeSignature = lockedRecipe == null ? ""
                : tag.getString(LOCKED_RECIPE_SIGNATURE);
        contextRemaining = lockedRecipe == null ? 0
                : Math.max(0, tag.getInt(CONTEXT_REMAINING));
    }

    private void clearLockedRecipe() {
        if (lockedRecipe != null) {
            lockedRecipe = null;
            lockedRecipeSignature = "";
            contextRemaining = 0;
            progress = 0;
            setChanged();
        }
    }
}
