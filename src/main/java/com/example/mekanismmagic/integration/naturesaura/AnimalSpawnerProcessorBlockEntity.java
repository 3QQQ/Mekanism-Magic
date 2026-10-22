package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.api.IMekanismMagicAutomation.ItemInputGroup;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Internal spawn-egg production. No entity construction, spawn event, world structure or collision search. */
public final class AnimalSpawnerProcessorBlockEntity extends NatureAuraMachineBlockEntity {
    public static final int MIN_SPAWN_INTERVAL = 20;
    public static final int BASE_ENERGY_PER_TICK = MagicMachineEnergyProfiles.MAGIC_REACTION;
    public static final long ENERGY_CAPACITY = 2_000_000L;
    public static final int AURA_CAPACITY = 500_000;
    public static final int AURA_TRANSFER = 5_000;
    private static final String SELECTED_RECIPE = "animal_selected_recipe";
    private static final String PENDING_EGG = "animal_pending_egg";

    private List<IInventorySlot> ingredientSlots;
    private String selectedRecipe = "";
    private List<ItemStack> cachedInputs = List.of();
    private List<AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView> matches = List.of();
    private long observedVersion = Long.MIN_VALUE;
    private AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView selected;
    private ItemStack selectedEgg = ItemStack.EMPTY;
    private ItemStack pendingEgg = ItemStack.EMPTY;
    private boolean outputBlocked;
    private boolean updating;
    private int matchingRecipeCount;
    private int outputRetry;

    public AnimalSpawnerProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK.get().builtInRegistryHolder(),
                pos, state, AURA_CAPACITY, AURA_TRANSFER, AuraSideMode.INPUT);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        ingredientSlots = new ArrayList<>();
        for (int index = 0; index < AnimalSpawnerRecipeBridge.INPUT_SLOT_COUNT; index++) {
            InputInventorySlot slot = registerLogicalSlot(helper, index,
                    InputInventorySlot.at(stack -> AnimalSpawnerRecipeBridge.isInput(level, stack),
                            listener, 38 + index % 3 * 18, 26 + index / 3 * 18));
            ingredientSlots.add(slot);
            if (index == 0) inputSlot = slot;
        }
        // Append the new output after the six existing inputs; saved input/energy indices stay unchanged.
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT, OutputInventorySlot.at(listener, 134, 44));
        setupNativeItemIO(ingredientSlots, List.of(outputSlot), List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(ItemStackHandler inventory) {
        if (level == null) return Optional.empty();
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < AnimalSpawnerRecipeBridge.INPUT_SLOT_COUNT; slot++) inputs.add(inventory.getStackInSlot(slot));
        var candidates = AnimalSpawnerRecipeBridge.matching(level.getRecipeManager(), inputs);
        return candidates.stream().filter(recipe -> recipe.id().toString().equals(selectedRecipe))
                .findFirst().or(() -> candidates.stream().findFirst())
                .flatMap(recipe -> recipe.match(inputs).map(recipe::toMachineRecipe));
    }

    @Override
    public Optional<List<ItemInputGroup>> mekanismMagicPlanExternalItemInputs(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        // Native animal recipes require one occupied physical slot for each
        // ingredient, even when two ingredients are the very same item key.
        // AE's complete delivery is a multiset, so split it into exact native
        // ingredient roles before the shared router commits any stack.
        Optional<List<PatternStack>> compacted =
                ExternalNaturePatternInputs.compact(inputs);
        if (level == null || compacted.isEmpty() || ingredientSlots == null
                || ingredientSlots.stream().anyMatch(slot -> !slot.isEmpty())
                || compacted.get().size() > ingredientSlots.size()) {
            return Optional.empty();
        }
        List<PatternStack> keys = compacted.get();
        long supplied = 0;
        for (PatternStack key : keys) {
            supplied += key.amount();
        }
        for (AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe
                : AnimalSpawnerRecipeBridge.scan(level.getRecipeManager())) {
            if (!ExternalNaturePatternInputs.matchesSingleOutput(
                    outputs, recipe.outputEgg())
                    || recipe.ingredients().size() > ingredientSlots.size()) {
                continue;
            }
            long required = 0;
            for (int amount : recipe.inputAmounts()) {
                required += amount;
            }
            if (supplied != required) {
                continue;
            }
            int[] remaining = new int[keys.size()];
            for (int index = 0; index < keys.size(); index++) {
                remaining[index] = (int) keys.get(index).amount();
            }
            int[] chosenKeys = new int[recipe.ingredients().size()];
            Optional<List<ItemInputGroup>> plan = assignAnimalInputs(
                    recipe, keys, remaining, chosenKeys, 0, outputs);
            if (plan.isPresent()) {
                return plan;
            }
        }
        return Optional.empty();
    }

    private Optional<List<ItemInputGroup>> assignAnimalInputs(
            AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe,
            List<PatternStack> keys, int[] remaining, int[] chosenKeys,
            int ingredient, List<PatternStack> outputs) {
        if (ingredient == chosenKeys.length) {
            for (int amount : remaining) {
                if (amount != 0) {
                    return Optional.empty();
                }
            }
            List<ItemStack> planned = new ArrayList<>(ingredientSlots.size());
            List<ItemInputGroup> groups = new ArrayList<>(chosenKeys.length);
            for (int index = 0; index < ingredientSlots.size(); index++) {
                planned.add(ItemStack.EMPTY);
            }
            for (int index = 0; index < chosenKeys.length; index++) {
                ItemStack stack = keys.get(chosenKeys[index]).stack();
                int amount = recipe.inputAmounts().get(index);
                planned.set(index, stack.copyWithCount(amount));
                groups.add(new ItemInputGroup(
                        List.of(new PatternStack(stack, amount)),
                        List.of(ingredientSlots.get(index))));
            }
            if (recipe.match(planned).isEmpty()) {
                return Optional.empty();
            }
            List<AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView> matches =
                    AnimalSpawnerRecipeBridge.matching(
                            level.getRecipeManager(), planned);
            AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView selectedRecipe =
                    matches.stream().filter(candidate -> candidate.id()
                                    .toString().equals(this.selectedRecipe))
                            .findFirst().orElseGet(() -> matches.isEmpty()
                                    ? null : matches.getFirst());
            return selectedRecipe != null
                    && ExternalNaturePatternInputs.matchesSingleOutput(
                    outputs, selectedRecipe.outputEgg())
                    ? Optional.of(List.copyOf(groups)) : Optional.empty();
        }
        int required = recipe.inputAmounts().get(ingredient);
        for (int key = 0; key < keys.size(); key++) {
            if (remaining[key] < required || !recipe.ingredients()
                    .get(ingredient).test(keys.get(key).stack())) {
                continue;
            }
            remaining[key] -= required;
            chosenKeys[ingredient] = key;
            Optional<List<ItemInputGroup>> result = assignAnimalInputs(
                    recipe, keys, remaining, chosenKeys,
                    ingredient + 1, outputs);
            remaining[key] += required;
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    @Override
    protected boolean onUpdateServer() {
        if (updating) return false;
        updating = true;
        try {
            return updateEggProduction();
        } finally {
            updating = false;
        }
    }

    private boolean updateEggProduction() {
        boolean changed = nativeBaseUpdate();
        clearNativeRecipeWarnings();
        beginNatureAuraUpdate();
        outputBlocked = false;
        if (!(level instanceof ServerLevel server) || !isLive(server)) return finishServerUpdate(changed, false);
        if (hasPendingOutput()) {
            if (outputRetry-- <= 0) {
                ItemStack remaining = outputSlot.insertItem(pendingEgg.copy(), Action.EXECUTE, AutomationType.INTERNAL);
                if (!ItemStack.matches(remaining, pendingEgg)) {
                    pendingEgg = remaining;
                    setChanged();
                    changed = true;
                }
                outputRetry = hasPendingOutput() ? 5 : 0;
            }
            outputBlocked = hasPendingOutput();
            return finishServerUpdate(changed, false);
        }
        if (!canFunction()) return finishServerUpdate(changed, false);
        changed |= refreshSelection();
        if (selected == null || selectedEgg.isEmpty()) {
            changed |= resetWork();
            setInputDoesntProduceOutputWarning(hasAnyRecipeInput());
            return finishServerUpdate(changed, false);
        }
        var inputs = selected.match(inputStacks());
        if (inputs.isEmpty()) return finishServerUpdate(resetWork() || changed, false);
        MachineRecipeResult recipe = selected.toMachineRecipe(inputs.get());
        String selection = selected.id() + "|" + selected.signature();
        if (!selection.equals(activeRecipe)) {
            progress = 0;
            activeRecipe = selection;
            changed = true;
        }
        progressRequired = Math.max(MIN_SPAWN_INTERVAL, MekanismUtils.getTicks(this, selected.time()));
        outputBlocked = !fitsOutput(selectedEgg);
        if (outputBlocked || !hasRecipeResources(recipe)) return finishServerUpdate(changed, false);
        changed |= updateDisplay();
        if (progress < progressRequired) {
            long usage = energyUsagePerTick(recipe);
            if (!hasEnergyForRecipe(recipe, usage)) {
                setNotEnoughEnergyWarning(true);
                return finishServerUpdate(changed, false);
            }
            energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
            progress++;
            changed = true;
        }
        if (progress >= progressRequired && isLive(server) && canFunction()
                && observedVersion == NatureAuraRecipeScanner.version(server.getRecipeManager())) {
            var currentInputs = selected.match(inputStacks());
            if (currentInputs.isPresent() && fitsOutput(selectedEgg)
                    && consumeRecipeResources(selected.toMachineRecipe(currentInputs.get()))) {
                for (InputUse use : currentInputs.get()) ingredientSlots.get(use.slot()).shrinkStack(use.count(), Action.EXECUTE);
                pendingEgg = selectedEgg.copy();
                progress = 0;
                activeRecipe = "";
                cachedInputs = List.of();
                outputRetry = 0;
                setChanged();
                changed = true;
            }
        }
        return finishServerUpdate(changed, true);
    }

    private boolean fitsOutput(ItemStack result) {
        ItemStack current = outputSlot.getStack();
        return !result.isEmpty() && (current.isEmpty() || ItemStack.isSameItemSameComponents(current, result))
                && (long) current.getCount() + result.getCount() <= Math.min(result.getMaxStackSize(), outputSlot.getLimit(result));
    }

    private boolean isLive(ServerLevel server) {
        return level == server && !isRemoved() && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
    }

    private boolean refreshSelection() {
        List<ItemStack> current = inputStacks();
        long version = NatureAuraRecipeScanner.version(level.getRecipeManager());
        boolean sameInputs = cachedInputs.size() == current.size();
        for (int slot = 0; sameInputs && slot < current.size(); slot++) sameInputs = ItemStack.matches(cachedInputs.get(slot), current.get(slot));
        if (version == observedVersion && sameInputs) return false;
        observedVersion = version;
        cachedInputs = current.stream().map(ItemStack::copy).toList();
        matches = AnimalSpawnerRecipeBridge.matching(level.getRecipeManager(), current);
        matchingRecipeCount = matches.size();
        selected = matches.stream().filter(recipe -> recipe.id().toString().equals(selectedRecipe))
                .findFirst().orElse(matches.isEmpty() ? null : matches.getFirst());
        selectedEgg = selected == null ? ItemStack.EMPTY : selected.outputEgg();
        if (selected != null) selectedRecipe = selected.id().toString();
        return true;
    }

    private List<ItemStack> inputStacks() { return ingredientSlots.stream().map(IInventorySlot::getStack).toList(); }

    private boolean updateDisplay() {
        boolean changed = mekanismMagicRecipeEntityDisplay().updateEntity(selected.entityId(), new CompoundTag());
        List<RecipeItemDisplayState.Entry> items = new ArrayList<>();
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            ItemStack input = ingredientSlots.get(slot).getStack();
            if (!input.isEmpty()) items.add(new RecipeItemDisplayState.Entry(slot,
                    RecipeItemDisplayState.displayCopy(input), slot == 0 ? RecipeItemDisplayState.displayCopy(selectedEgg) : ItemStack.EMPTY));
        }
        return mekanismMagicRecipeItemDisplay().update(items) || changed;
    }

    private boolean resetWork() {
        boolean changed = progress != 0 || !activeRecipe.isEmpty();
        progress = 0;
        progressRequired = 1;
        activeRecipe = "";
        return (mekanismMagicRecipeEntityDisplay().clear() | mekanismMagicRecipeItemDisplay().clear()) || changed;
    }

    public void cycleMatchingRecipe() {
        if (level == null || level.isClientSide || updating || hasPendingOutput()) return;
        refreshSelection();
        if (matches.size() > 1) {
            selected = matches.get((matches.indexOf(selected) + 1) % matches.size());
            selectedRecipe = selected.id().toString();
            selectedEgg = selected.outputEgg();
            progress = 0;
            activeRecipe = "";
            updateDisplay();
            setChanged();
            sendUpdatePacket();
        }
    }

    public ResourceLocation getSelectedEntityId() {
        if (selected != null) return selected.entityId();
        String id = mekanismMagicRecipeEntityDisplay().entityData().getString("id");
        return id.isBlank() ? null : ResourceLocation.tryParse(id);
    }
    public int getMatchingRecipeCount() { return matchingRecipeCount; }
    public boolean hasOutputBlocked() { return outputBlocked; }
    public boolean hasMissingStructure() { return false; }
    public boolean hasSpawnBlocked() { return outputBlocked; }
    public boolean hasEntityLimit() { return false; }
    public boolean hasPendingOutput() { return !pendingEgg.isEmpty(); }
    public List<ItemStack> getPendingOutputs() { return List.of(pendingEgg.copy()); }
    public List<IInventorySlot> getIngredientSlots() { return List.copyOf(ingredientSlots); }
    @Override public boolean mekanismMagicIsBusy() { return super.mekanismMagicIsBusy() || hasPendingOutput(); }
    @Override public boolean mekanismMagicSupportsPatternAutomation() { return false; }
    @Override protected boolean hasAnyRecipeInput() {
        return ingredientSlots != null && ingredientSlots.stream().anyMatch(slot -> !slot.isEmpty());
    }
    @Override protected int baseEnergyPerTick() { return BASE_ENERGY_PER_TICK; }

    @Override
    protected long energyUsagePerTick(MachineRecipeResult recipe) {
        double multiplier = MekanismConfig.general.maxUpgradeMultiplier.get();
        double speed = Math.pow(multiplier, MekanismUtils.fractionUpgrades(this, Upgrade.SPEED));
        if (!NatureMachineEnergyMath.isAnimalSpeedCapped(recipe.duration(), MIN_SPAWN_INTERVAL, speed)) {
            return MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
        }
        double energyReduction = Math.pow(multiplier, MekanismUtils.fractionUpgrades(this, Upgrade.ENERGY));
        return NatureMachineEnergyMath.cappedAnimalJoulesPerTick(BASE_ENERGY_PER_TICK,
                recipe.duration(), MIN_SPAWN_INTERVAL, energyReduction);
    }

    @Override protected int energySlotX() { return 20; }
    @Override protected int energySlotY() { return 62; }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::hasOutputBlocked, value -> outputBlocked = value));
        container.track(SyncableInt.create(() -> matchingRecipeCount, value -> matchingRecipeCount = value));
    }

    @Override
    protected void saveNatureAuraMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(SELECTED_RECIPE, selectedRecipe);
        tag.put(PENDING_EGG, saveCountedItemStack(pendingEgg, registries));
    }

    @Override
    protected void loadNatureAuraMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        selectedRecipe = tag.getString(SELECTED_RECIPE);
        pendingEgg = loadCountedItemStack(tag.getCompound(PENDING_EGG), registries);
        cachedInputs = List.of();
        selected = null;
        selectedEgg = ItemStack.EMPTY;
        observedVersion = Long.MIN_VALUE;
        outputRetry = 0;
        outputBlocked = false;
    }
}
