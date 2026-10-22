package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.api.IMekanismMagicAutomation.ItemInputGroup;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.recipe.BoundedSlotMatcher;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import de.ellpeck.naturesaura.blocks.ModBlocks;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Processes tree rituals entirely inside the machine, without planting, scanning or changing world blocks. */
public final class TreeRitualProcessorBlockEntity extends NativeMagicMachineBlockEntity {
    public static final int SAPLING_SLOT = ACTIVATION_SLOT;
    public static final int GOLD_POWDER_SLOT = SACRIFICE_SLOT;
    private static final String PENDING_OUTPUT = "tree_ritual_pending_output";
    private static final String LEGACY_SETUP = "tree_ritual_legacy_external_setup";
    // Existing physical slot order is deliberately unchanged for saved inventories.
    private List<IInventorySlot> materialSlots;
    private InputInventorySlot saplingSlot;
    private InputInventorySlot goldPowderSlot;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private CompoundTag legacyExternalSetup = new CompoundTag();
    private boolean missingGoldPowder;
    private boolean outputBlocked;
    private boolean updating;
    private int outputRetry;
    private long cachedVersion = Long.MIN_VALUE;
    private List<ItemStack> cachedInputs = List.of();
    private TreeRitualRecipeBridge.Match cachedMatch;
    private boolean syncedLegacySetup;

    public TreeRitualProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_BLOCK.get().builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        materialSlots = new ArrayList<>();
        for (int i = 0; i < TreeRitualRecipeBridge.MATERIAL_SLOTS; i++) {
            InputInventorySlot slot = registerLogicalSlot(helper, i,
                    InputInventorySlot.at(stack -> TreeRitualRecipeBridge.isMaterial(level, stack),
                            listener, NatureAuraScreenLayout.TREE_MATERIAL_X + i % 4 * 18,
                            NatureAuraScreenLayout.TREE_MATERIAL_Y + i / 4 * 18));
            materialSlots.add(slot);
            if (i == 0) inputSlot = slot;
        }
        saplingSlot = registerLogicalSlot(helper, SAPLING_SLOT,
                InputInventorySlot.at(stack -> TreeRitualRecipeBridge.isSapling(level, stack), listener,
                        NatureAuraScreenLayout.TREE_SAPLING_X, NatureAuraScreenLayout.TREE_MATERIAL_Y));
        goldPowderSlot = registerLogicalSlot(helper, GOLD_POWDER_SLOT,
                InputInventorySlot.at(stack -> stack.is(ModBlocks.GOLD_POWDER.asItem()), listener,
                        NatureAuraScreenLayout.TREE_GOLD_X, NatureAuraScreenLayout.TREE_MATERIAL_Y));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT, OutputInventorySlot.at(listener,
                NatureAuraScreenLayout.TREE_OUTPUT_X, NatureAuraScreenLayout.TREE_OUTPUT_Y));
        List<IInventorySlot> inputs = new ArrayList<>(materialSlots);
        inputs.add(saplingSlot);
        inputs.add(goldPowderSlot);
        var config = setupNativeItemIO(inputs, List.of(outputSlot), List.of());
        addNativeItemSlotInfo(config, DataType.EXTRA, true, false, List.of(saplingSlot, goldPowderSlot));
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(ItemStackHandler inventory) {
        if (!hasGoldPowder(inventory.getStackInSlot(GOLD_POWDER_SLOT))) return Optional.empty();
        List<ItemStack> materials = new ArrayList<>();
        for (int i = 0; i < TreeRitualRecipeBridge.MATERIAL_SLOTS; i++) materials.add(inventory.getStackInSlot(i));
        return TreeRitualRecipeBridge.findRecipe(level, materials, inventory.getStackInSlot(SAPLING_SLOT))
                .map(TreeRitualRecipeBridge.Match::machineRecipe);
    }

    @Override
    public Optional<List<ItemInputGroup>>
    mekanismMagicPlanExternalItemInputs(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        Optional<List<PatternStack>> compacted =
                ExternalNaturePatternInputs.compact(inputs);
        if (level == null || compacted.isEmpty() || saplingSlot == null
                || goldPowderSlot == null || materialSlots == null) {
            return Optional.empty();
        }
        List<PatternStack> items = compacted.get();
        List<ItemInputGroup> selected = null;
        for (TreeRitualRecipeBridge.TreeRitualRecipeView recipe :
                TreeRitualRecipeBridge.scan(level.getRecipeManager())) {
            if (!ExternalNaturePatternInputs.matchesSingleOutput(
                    outputs, recipe.output())) {
                continue;
            }
            for (int saplingIndex = 0; saplingIndex < items.size();
                 saplingIndex++) {
                if (!recipe.sapling().test(items.get(saplingIndex).stack())) {
                    continue;
                }
                for (int goldIndex = 0; goldIndex < items.size();
                     goldIndex++) {
                    if (!items.get(goldIndex).stack().is(
                            ModBlocks.GOLD_POWDER.asItem())) {
                        continue;
                    }
                    int[] remaining = new int[items.size()];
                    long materialCount = 0;
                    for (int index = 0; index < items.size(); index++) {
                        remaining[index] = (int) items.get(index).amount();
                        materialCount += remaining[index];
                    }
                    remaining[saplingIndex]--;
                    remaining[goldIndex] -= TreeRitualRecipeBridge
                            .GOLD_POWDER_PER_OPERATION;
                    materialCount -= 1 + TreeRitualRecipeBridge
                            .GOLD_POWDER_PER_OPERATION;
                    if (remaining[saplingIndex] < 0
                            || remaining[goldIndex] < 0
                            || materialCount != recipe.ingredients().size()) {
                        continue;
                    }
                    boolean[][] compatible = new boolean[
                            recipe.ingredients().size()][items.size()];
                    for (int ingredient = 0;
                         ingredient < recipe.ingredients().size();
                         ingredient++) {
                        for (int index = 0; index < items.size(); index++) {
                            compatible[ingredient][index] =
                                    recipe.ingredients().get(ingredient)
                                            .test(items.get(index).stack());
                        }
                    }
                    if (BoundedSlotMatcher.match(compatible, remaining)
                            .isEmpty()) {
                        continue;
                    }
                    List<PatternStack> materials = new ArrayList<>();
                    for (int index = 0; index < items.size(); index++) {
                        if (remaining[index] > 0) {
                            materials.add(new PatternStack(
                                    items.get(index).stack(),
                                    remaining[index]));
                        }
                    }
                    List<ItemInputGroup> candidate = List.of(
                            new ItemInputGroup(List.of(new PatternStack(
                                    items.get(saplingIndex).stack(), 1)),
                                    List.of(saplingSlot)),
                            new ItemInputGroup(List.of(new PatternStack(
                                    items.get(goldIndex).stack(),
                                    TreeRitualRecipeBridge
                                            .GOLD_POWDER_PER_OPERATION)),
                                    List.of(goldPowderSlot)),
                            new ItemInputGroup(materials, materialSlots));
                    if (selected != null) {
                        // A pattern with multiple valid physical role splits
                        // must not be assigned arbitrarily.
                        return Optional.empty();
                    }
                    selected = candidate;
                }
            }
        }
        return Optional.ofNullable(selected);
    }

    @Override
    protected boolean onUpdateServer() {
        if (updating) return false;
        updating = true;
        try {
            return updateRitualServer();
        } finally {
            updating = false;
        }
    }

    private boolean updateRitualServer() {
        boolean changed = nativeBaseUpdate();
        clearNativeRecipeWarnings();
        missingGoldPowder = false;
        outputBlocked = false;
        if (!(level instanceof ServerLevel server) || !isLive(server)) return finishServerUpdate(changed, false);
        if (hasPendingOutput()) {
            if (outputRetry-- <= 0) {
                ItemStack remaining = outputSlot.insertItem(pendingOutput.copy(), Action.EXECUTE, AutomationType.INTERNAL);
                if (!ItemStack.matches(remaining, pendingOutput)) {
                    pendingOutput = remaining;
                    setChanged();
                    changed = true;
                }
                outputRetry = hasPendingOutput() ? 5 : 0;
            }
            outputBlocked = hasPendingOutput();
            return finishServerUpdate(changed, false);
        }
        if (!canFunction()) return finishServerUpdate(changed, false);
        var match = selectRecipe();
        if (match == null) {
            changed |= resetWork();
            setInputDoesntProduceOutputWarning(hasAnyRecipeInput());
            return finishServerUpdate(changed, false);
        }
        String selection = match.recipe().id() + "|" + match.recipe().signature();
        if (!selection.equals(activeRecipe)) {
            activeRecipe = selection;
            progress = 0;
            changed = true;
        }
        var recipe = match.machineRecipe();
        progressRequired = Math.max(1, MekanismUtils.getTicks(this, recipe.duration()));
        missingGoldPowder = !hasGoldPowder(goldPowderSlot.getStack());
        outputBlocked = !fitsOutput(recipe.output());
        if (missingGoldPowder || outputBlocked) return finishServerUpdate(changed, false);
        long usage = energyUsagePerTick(recipe);
        if (progress < progressRequired) {
            if (!hasEnergyForRecipe(recipe, usage)) {
                setNotEnoughEnergyWarning(true);
                return finishServerUpdate(changed, false);
            }
            changed |= mekanismMagicRecipeItemDisplay().update(List.of(new RecipeItemDisplayState.Entry(0,
                    RecipeItemDisplayState.displayCopy(saplingSlot.getStack()),
                    RecipeItemDisplayState.displayCopy(recipe.output()))));
            energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
            progress++;
            changed = true;
        }
        if (progress >= progressRequired && inputsStillMatch(match) && isLive(server) && canFunction()) {
            // The server owns all slots. Commit each actual input once; no world callbacks are involved.
            for (var use : match.inputs()) materialSlots.get(use.slot()).shrinkStack(use.count(), Action.EXECUTE);
            saplingSlot.shrinkStack(1, Action.EXECUTE);
            goldPowderSlot.shrinkStack(TreeRitualRecipeBridge.GOLD_POWDER_PER_OPERATION, Action.EXECUTE);
            pendingOutput = recipe.output().copy();
            progress = 0;
            activeRecipe = "";
            cachedInputs = List.of();
            outputRetry = 0;
            setChanged();
            changed = true;
        }
        return finishServerUpdate(changed, true);
    }

    private TreeRitualRecipeBridge.Match selectRecipe() {
        List<ItemStack> current = new ArrayList<>(materialSlots.size() + 1);
        for (var slot : materialSlots) current.add(slot.getStack());
        current.add(saplingSlot.getStack());
        long version = NatureAuraRecipeScanner.version(level.getRecipeManager());
        boolean unchanged = version == cachedVersion && current.size() == cachedInputs.size();
        for (int i = 0; unchanged && i < current.size(); i++) unchanged = ItemStack.matches(current.get(i), cachedInputs.get(i));
        if (!unchanged) {
            cachedVersion = version;
            cachedInputs = current.stream().map(ItemStack::copy).toList();
            cachedMatch = TreeRitualRecipeBridge.findRecipe(level, current.subList(0, materialSlots.size()), current.getLast()).orElse(null);
        }
        return cachedMatch;
    }

    private boolean inputsStillMatch(TreeRitualRecipeBridge.Match match) {
        return cachedVersion == NatureAuraRecipeScanner.version(level.getRecipeManager())
                && hasGoldPowder(goldPowderSlot.getStack())
                && match.recipe().matchMaterials(materialSlots.stream().map(IInventorySlot::getStack).toList(), saplingSlot.getStack())
                .filter(match.inputs()::equals).isPresent();
    }

    private static boolean hasGoldPowder(ItemStack stack) {
        return stack.is(ModBlocks.GOLD_POWDER.asItem()) && stack.getCount() >= TreeRitualRecipeBridge.GOLD_POWDER_PER_OPERATION;
    }

    private boolean fitsOutput(ItemStack result) {
        ItemStack current = outputSlot.getStack();
        return !result.isEmpty() && (current.isEmpty() || ItemStack.isSameItemSameComponents(current, result))
                && (long) current.getCount() + result.getCount() <= Math.min(result.getMaxStackSize(), outputSlot.getLimit(result));
    }

    private boolean isLive(ServerLevel server) {
        return level == server && !isRemoved() && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
    }

    private boolean resetWork() {
        boolean changed = progress != 0 || !activeRecipe.isEmpty();
        progress = 0;
        progressRequired = 1;
        activeRecipe = "";
        return mekanismMagicRecipeItemDisplay().clear() || changed;
    }

    /** Informational legacy location only. Processing never reads or changes the external setup. */
    public BlockPos getRitualCenter() { return worldPosition.relative(getDirection().getOpposite(), 4); }
    public List<IInventorySlot> getMaterialSlots() { return List.copyOf(materialSlots); }
    public ItemStack getSaplingStack() { return saplingSlot.getStack(); }
    public IInventorySlot getSaplingSlot() { return saplingSlot; }
    public IInventorySlot getGoldPowderSlot() { return goldPowderSlot; }
    public ItemStack getGoldPowderStack() { return goldPowderSlot.getStack(); }
    public boolean hasMissingGoldPowder() { return missingGoldPowder; }
    public boolean hasMissingRitual() { return missingGoldPowder; }
    public boolean isWaitingForTree() { return false; }
    public boolean hasOutputBlocked() { return outputBlocked; }
    public List<ItemStack> getPendingOutputs() { return List.of(pendingOutput.copy()); }
    public boolean hasPendingOutput() { return !pendingOutput.isEmpty(); }
    public boolean hasLegacyExternalSetup() {
        return level != null && level.isClientSide ? syncedLegacySetup : !legacyExternalSetup.isEmpty();
    }
    @Override public boolean mekanismMagicIsBusy() { return super.mekanismMagicIsBusy() || hasPendingOutput(); }
    @Override public boolean mekanismMagicSupportsPatternAutomation() { return false; }
    @Override protected int baseEnergyPerTick() { return NatureAuraMachineConfig.TREE_RITUAL_BASE_ENERGY_PER_TICK; }
    @Override protected int energySlotX() { return 108; }
    @Override protected int energySlotY() { return 71; }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::hasMissingGoldPowder, value -> missingGoldPowder = value));
        container.track(SyncableBoolean.create(this::hasOutputBlocked, value -> outputBlocked = value));
        container.track(SyncableBoolean.create(this::hasLegacyExternalSetup, value -> syncedLegacySetup = value));
    }

    @Override
    protected void saveNativeMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(PENDING_OUTPUT, saveCountedItemStack(pendingOutput, registries));
        if (!legacyExternalSetup.isEmpty()) tag.put(LEGACY_SETUP, legacyExternalSetup.copy());
    }

    @Override
    protected void loadNativeMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        pendingOutput = loadCountedItemStack(tag.getCompound(PENDING_OUTPUT), registries);
        legacyExternalSetup = tag.getCompound(LEGACY_SETUP).copy();
        // An old planted item already exists outside. Never credit it again or remove the old tree/ring.
        if (!tag.getCompound("tree_ritual_planted_sapling").isEmpty()) {
            for (String key : List.of("tree_ritual_planted_sapling", "tree_ritual_planted_center", "tree_ritual_planted_dimension")) {
                if (tag.contains(key)) legacyExternalSetup.put(key, tag.get(key).copy());
            }
        }
        cachedInputs = List.of();
        cachedMatch = null;
        cachedVersion = Long.MIN_VALUE;
        outputRetry = 0;
        missingGoldPowder = false;
        outputBlocked = false;
    }
}
