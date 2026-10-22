package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.api.IMekanismMagicAutomation.ItemInputGroup;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/**
 * Automates a whole offering-table trigger inside the machine.
 * One start item pays for the whole input stack; committed
 * output remains durable while either output slot is blocked.
 */
public final class OfferingProcessorBlockEntity
        extends NativeMagicMachineBlockEntity {
    public static final int BONUS_OUTPUT_SLOT = RITUAL_REMAINDER_SLOT;
    public static final int WITHER_ROSE_SLOT = SACRIFICE_SLOT;
    public static final int WITHER_ROSES_FOR_BONUS = 36;
    private static final String PENDING_OUTPUT = "offering_pending_output";
    private static final String PENDING_BONUS = "offering_pending_bonus";
    private static final String OUTPUT_UNIT = "offering_output_unit";

    private InputInventorySlot startItemSlot;
    private OutputInventorySlot bonusOutputSlot;
    private BasicInventorySlot witherRoseSlot;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private ItemStack pendingBonus = ItemStack.EMPTY;
    private int outputUnit = 1;

    public OfferingProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, 0,
                InputInventorySlot.at(stack -> OfferingRecipeBridge
                        .isInput(level, stack), listener, 46, 35));
        startItemSlot = registerLogicalSlot(helper, ACTIVATION_SLOT,
                InputInventorySlot.at(stack -> OfferingRecipeBridge
                        .isStartItem(level, stack), listener, 64, 35));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        bonusOutputSlot = registerLogicalSlot(helper, BONUS_OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 134, 53));
        // Append after all old slots so legacy physical inventory indices remain valid.
        witherRoseSlot = registerLogicalSlot(helper, WITHER_ROSE_SLOT,
                BasicInventorySlot.at((stack, automation) -> automation != AutomationType.EXTERNAL,
                        (stack, automation) -> true, stack -> stack.is(Items.WITHER_ROSE), listener, 82, 53));
        var itemConfig = setupNativeItemIO(List.of(inputSlot, startItemSlot),
                List.of(outputSlot, bonusOutputSlot), List.of(witherRoseSlot));
        addNativeItemSlotInfo(itemConfig, DataType.EXTRA,
                true, false, List.of(startItemSlot, witherRoseSlot));
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(ItemStackHandler inventory) {
        return OfferingRecipeBridge.findRecipe(level,
                inventory.getStackInSlot(0),
                inventory.getStackInSlot(ACTIVATION_SLOT))
                .flatMap(recipe -> OfferingRecipeBridge.toMachineRecipe(recipe,
                        inventory.getStackInSlot(0).getCount(),
                        0, ACTIVATION_SLOT));
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = nativeBaseUpdate();
        clearNativeRecipeWarnings();
        if (level == null || !canFunction()) {
            return finishServerUpdate(changed, false);
        }
        if (hasPendingOutput()) {
            if (level.getGameTime() % NatureAuraMachineConfig
                    .OFFERING_OUTPUT_INTERVAL == 0) {
                changed |= flushPendingOutput();
            }
            // Keep this separate from triggering: the last queued output
            // can never also cause a second start item to be spent this tick.
            return finishServerUpdate(changed, hasPendingOutput());
        }
        if (!hasAnyRecipeInput() || startItemSlot.getStack().isEmpty()) {
            changed |= resetWork();
            return finishServerUpdate(changed, false);
        }
        var selected = OfferingRecipeBridge.findRecipe(level,
                inputSlot.getStack(), startItemSlot.getStack());
        if (selected.isEmpty()) {
            changed |= resetWork();
            setInputDoesntProduceOutputWarning(true);
            return finishServerUpdate(changed, false);
        }
        OfferingRecipeBridge.OfferingRecipeView view = selected.get();
        var batch = OfferingRecipeBridge.toMachineRecipe(view,
                inputSlot.getCount(), 0, ACTIVATION_SLOT);
        if (batch.isEmpty()) {
            changed |= resetWork();
            setInputDoesntProduceOutputWarning(true);
            return finishServerUpdate(changed, false);
        }
        MachineRecipeResult recipe = batch.get();
        // Content signatures invalidate progress even when a data pack
        // replaces input/start ingredients without changing the recipe id.
        String selection = view.id() + "|" + view.signature()
                + "|" + recipe.inputs().getFirst().count();
        if (!selection.equals(activeRecipe)) {
            progress = 0;
            activeRecipe = selection;
            changed = true;
        }
        progressRequired = Math.max(1, MekanismUtils.getTicks(this,
                NatureAuraMachineConfig.OFFERING_TRIGGER_INTERVAL));
        long usage = energyUsagePerTick(recipe);
        if (!hasEnergyForRecipe(recipe, usage)) {
            setNotEnoughEnergyWarning(true);
            return finishServerUpdate(changed, false);
        }
        changed |= updateWorkDisplay(view);
        energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
        progress++;
        if (progress >= progressRequired) {
            ItemStack result = recipe.output().copy();
            ItemStack bonus = hasWitherRoseBonus()
                    ? new ItemStack(Items.BLACK_DYE, OfferingBatchPolicy
                    .witherRoseBonus(level.random.nextInt(5))) : ItemStack.EMPTY;
            int consumed = recipe.inputs().getFirst().count();
            // All validation is complete before any inventory mutation.
            // These are owned slots; the server-thread commit cannot be
            // interleaved with an item capability call.
            pendingOutput = result;
            pendingBonus = bonus;
            outputUnit = view.output().getCount();
            inputSlot.shrinkStack(consumed, Action.EXECUTE);
            startItemSlot.shrinkStack(1, Action.EXECUTE);
            progress = 0;
            activeRecipe = "";
            setChanged();
            changed = true;
        }
        return finishServerUpdate(changed, true);
    }

    private boolean updateWorkDisplay(OfferingRecipeBridge.OfferingRecipeView view) {
        return mekanismMagicRecipeItemDisplay().update(List.of(
                new RecipeItemDisplayState.Entry(0,
                        RecipeItemDisplayState.displayCopy(inputSlot.getStack()),
                        RecipeItemDisplayState.displayCopy(view.output())),
                new RecipeItemDisplayState.Entry(1,
                        RecipeItemDisplayState.displayCopy(startItemSlot.getStack()),
                        ItemStack.EMPTY)));
    }

    private boolean flushPendingOutput() {
        if (!pendingOutput.isEmpty()) {
            int moved = insertPending(outputSlot, pendingOutput, outputUnit);
            if (moved > 0) {
                pendingOutput.shrink(moved);
                setChanged();
                return true;
            }
        } else if (!pendingBonus.isEmpty()) {
            int moved = insertPending(bonusOutputSlot, pendingBonus, 1);
            if (moved > 0) {
                pendingBonus.shrink(moved);
                setChanged();
                return true;
            }
        }
        return false;
    }

    private static int insertPending(IInventorySlot slot, ItemStack pending,
                                     int perTickLimit) {
        ItemStack existing = slot.getStack();
        if (!existing.isEmpty()
                && !ItemStack.isSameItemSameComponents(existing, pending)) {
            return 0;
        }
        int moved = Math.min(Math.min(pending.getCount(),
                        Math.max(1, perTickLimit)),
                Math.max(0, pending.getMaxStackSize() - existing.getCount()));
        if (moved > 0) {
            slot.setStack(pending.copyWithCount(existing.getCount() + moved));
        }
        return moved;
    }

    private boolean resetWork() {
        boolean changed = progress != 0 || !activeRecipe.isEmpty();
        progress = 0;
        progressRequired = 1;
        activeRecipe = "";
        return mekanismMagicRecipeItemDisplay().clear() || changed;
    }

    public boolean hasPendingOutput() {
        return !pendingOutput.isEmpty() || !pendingBonus.isEmpty();
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        // A start item processes a variable batch and may add random dye.
        return false;
    }

    @Override
    public Optional<List<ItemInputGroup>>
    mekanismMagicPlanExternalItemInputs(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        Optional<List<PatternStack>> compacted =
                ExternalNaturePatternInputs.compact(inputs);
        if (level == null || compacted.isEmpty()
                || compacted.get().size() > 2 || inputSlot == null
                || startItemSlot == null) {
            return Optional.empty();
        }
        List<PatternStack> items = compacted.get();
        List<ItemInputGroup> selected = null;
        for (int mainIndex = 0; mainIndex < items.size(); mainIndex++) {
            for (int startIndex = 0; startIndex < items.size(); startIndex++) {
                PatternStack mainInput = items.get(mainIndex);
                PatternStack startInput = items.get(startIndex);
                long mainAmount = mainInput.amount()
                        - (mainIndex == startIndex ? 1 : 0);
                if ((mainIndex == startIndex && items.size() != 1)
                        || (mainIndex != startIndex && items.size() != 2)
                        || mainAmount <= 0 || mainAmount > Integer.MAX_VALUE
                        || mainIndex != startIndex
                        && startInput.amount() != 1) {
                    continue;
                }
                ItemStack main = mainInput.stack().copyWithCount(
                        (int) mainAmount);
                ItemStack start = startInput.stack();
                Optional<OfferingRecipeBridge.OfferingRecipeView> recipe =
                        OfferingRecipeBridge.findRecipe(level, main, start);
                if (recipe.isEmpty()) {
                    continue;
                }
                Optional<MachineRecipeResult> operation =
                        OfferingRecipeBridge.toMachineRecipe(recipe.get(),
                                (int) mainAmount, 0, ACTIVATION_SLOT);
                if (operation.isEmpty()
                        || operation.get().inputs().size() != 1
                        || operation.get().inputs().getFirst().count()
                        != mainAmount
                        || !ExternalNaturePatternInputs.matchesSingleOutput(
                        outputs, operation.get().output())) {
                    continue;
                }
                List<ItemInputGroup> candidate = List.of(
                        new ItemInputGroup(List.of(new PatternStack(
                                start, 1)), List.of(startItemSlot)),
                        new ItemInputGroup(List.of(new PatternStack(
                                main, mainAmount)), List.of(inputSlot)));
                // A recipe output is not enough to distinguish two opposite
                // roles when both input items match both global predicates.
                if (selected != null) {
                    return Optional.empty();
                }
                selected = candidate;
            }
        }
        return Optional.ofNullable(selected);
    }

    public boolean hasMissingFlowerRing() {
        return false;
    }

    public boolean hasWitherRoseBonus() {
        return witherRoseSlot != null && witherRoseSlot.getStack().is(Items.WITHER_ROSE)
                && witherRoseSlot.getCount() >= WITHER_ROSES_FOR_BONUS;
    }

    public IInventorySlot getWitherRoseSlot() { return witherRoseSlot; }

    public ItemStack getStartItemStack() {
        return startItemSlot == null ? ItemStack.EMPTY : startItemSlot.getStack();
    }

    public IInventorySlot getBonusOutputSlot() {
        return bonusOutputSlot;
    }

    /** Defensive pending snapshots for persistence/regression inspection. */
    public List<ItemStack> getPendingOutputs() {
        return List.of(pendingOutput.copy(), pendingBonus.copy());
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        return super.mekanismMagicIsBusy() || hasPendingOutput();
    }

    @Override
    protected boolean hasAnyRecipeInput() {
        return inputSlot != null && !inputSlot.getStack().isEmpty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return NatureAuraMachineConfig.OFFERING_BASE_ENERGY_PER_TICK;
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
    protected void saveNativeMachineData(CompoundTag tag,
                                         HolderLookup.Provider registries) {
        super.saveNativeMachineData(tag, registries);
        tag.put(PENDING_OUTPUT, saveCountedItemStack(pendingOutput, registries));
        tag.put(PENDING_BONUS, saveCountedItemStack(pendingBonus, registries));
        tag.putInt(OUTPUT_UNIT, outputUnit);
    }

    @Override
    protected void loadNativeMachineData(CompoundTag tag,
                                         HolderLookup.Provider registries) {
        super.loadNativeMachineData(tag, registries);
        pendingOutput = loadCountedItemStack(tag.getCompound(PENDING_OUTPUT), registries);
        pendingBonus = loadCountedItemStack(tag.getCompound(PENDING_BONUS), registries);
        outputUnit = Math.max(1, tag.getInt(OUTPUT_UNIT));
    }
}
