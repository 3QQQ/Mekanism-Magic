package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.NativeMekanismRegistries;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated adapter for Occultism's dimensional miner table. The miner item
 * remains in place and selects the matching weighted recipe family; each
 * completed cycle places one weighted result into one of twenty-seven output
 * slots.
 */
public final class NativeDimensionMinerBlockEntity
        extends NativeMagicMachineBlockEntity {
    public static final int MINER_INPUT_SLOT = 0;
    public static final int MINER_OUTPUT_START = 16;
    public static final int MINER_OUTPUT_COUNT = 27;

    private List<OutputInventorySlot> minerOutputs;
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private ItemStack pendingInput = ItemStack.EMPTY;

    public NativeDimensionMinerBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.DIMENSION_MINER_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        // TileEntityMekanism builds capabilities from its superclass
        // constructor, before subclass field initializers have run.
        minerOutputs = new ArrayList<>(MINER_OUTPUT_COUNT);
        inputSlot = registerLogicalSlot(helper, MINER_INPUT_SLOT,
                InputInventorySlot.at(OccultismRecipeBridge::isMinerItem,
                        listener, 20, 35));
        for (int index = 0; index < MINER_OUTPUT_COUNT; index++) {
            int column = index % 9;
            int row = index / 9;
            OutputInventorySlot slot = registerLogicalSlot(helper,
                    MINER_OUTPUT_START + index,
                    OutputInventorySlot.at(listener,
                            48 + column * 18, 8 + row * 18));
            minerOutputs.add(slot);
            if (index == 0) {
                outputSlot = slot;
            }
        }
        configComponent.setupItemIOConfig(List.of(inputSlot),
                List.copyOf(minerOutputs), energySlot, true);
    }

    @Override
    protected int energySlotX() {
        return 20;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    @Override
    protected int baseEnergyPerTick() {
        return 800;
    }

    @Override
    protected java.util.Optional<OccultismRecipeBridge.RecipeResult> findRecipe(
            ItemStackHandler inventory) {
        // The machine uses its own weighted multi-output loop.
        return java.util.Optional.empty();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = nativeBaseUpdate();
        if (level == null) {
            return changed;
        }
        ItemStack input = inputSlot == null ? ItemStack.EMPTY : inputSlot.getStack();
        if (!OccultismRecipeBridge.isMinerItem(input)) {
            resetPending();
            return changed;
        }
        if (pendingOutputs.isEmpty()
                || !ItemStack.isSameItemSameComponents(input, pendingInput)) {
            preparePendingOutputs(input);
        }
        if (pendingOutputs.isEmpty()) {
            progress = 0;
            return changed;
        }
        if (!canAccept(pendingOutputs)) {
            return changed;
        }
        long usage = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
        if (energyContainer == null || energyContainer.getEnergy() < usage) {
            return changed;
        }
        energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
        int efficiency = enchantmentLevel(input, Enchantments.EFFICIENCY);
        progress += 1 + minimumRandomBonus(efficiency, 2);
        if (progress >= progressRequired) {
            if (insertOutputs(pendingOutputs)) {
                progress = 0;
                pendingOutputs.clear();
                changed = true;
            }
        }
        return changed;
    }

    private void preparePendingOutputs(ItemStack input) {
        pendingInput = input.copy();
        pendingOutputs.clear();
        progress = 0;
        progressRequired = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        OccultismRecipeBridge.minerDuration(input)));

        int fortune = enchantmentLevel(input, Enchantments.FORTUNE);
        int silkTouch = enchantmentLevel(input, Enchantments.SILK_TOUCH);
        int rolls = OccultismRecipeBridge.minerRollsPerOperation(input)
                + minimumRandomBonus(fortune, 3);
        for (int roll = 0; roll < rolls; roll++) {
            OccultismRecipeBridge.findMinerOutput(level, input)
                    .map(OccultismRecipeBridge.MinerOutput::output)
                    .map(ItemStack::copy)
                    .ifPresent(output -> {
                        int multiplier = silkTouch <= 0 ? 1
                                : 1 + level.random.nextIntBetweenInclusive(
                                0, silkTouch);
                        output.setCount(output.getCount() * multiplier);
                        pendingOutputs.add(output);
                    });
        }
    }

    private int enchantmentLevel(ItemStack stack,
                                 ResourceKey<Enchantment> enchantment) {
        if (level == null || stack.isEmpty()) {
            return 0;
        }
        try {
            return stack.getEnchantmentLevel(level.holderOrThrow(enchantment));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private int minimumRandomBonus(int level, int samples) {
        if (level <= 0 || this.level == null) {
            return 0;
        }
        int result = level;
        for (int sample = 0; sample < samples; sample++) {
            result = Math.min(result,
                    this.level.random.nextIntBetweenInclusive(0, level));
        }
        return result;
    }

    private boolean canAccept(List<ItemStack> stacks) {
        List<ItemStack> simulated = new ArrayList<>(minerOutputs.size());
        for (OutputInventorySlot slot : minerOutputs) {
            simulated.add(slot.getStack().copy());
        }
        for (ItemStack stack : stacks) {
            if (!insertIntoStacks(simulated, stack)) {
                return false;
            }
        }
        return true;
    }

    private boolean insertOutputs(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!insertOutput(stack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean insertIntoStacks(List<ItemStack> targets,
                                            ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack existing : targets) {
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, stack)) {
                int moved = Math.min(remaining,
                        existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining -= moved;
                    if (remaining == 0) {
                        return true;
                    }
                }
            }
        }
        for (int index = 0; index < targets.size() && remaining > 0; index++) {
            if (targets.get(index).isEmpty()) {
                int moved = Math.min(remaining, stack.getMaxStackSize());
                targets.set(index, stack.copyWithCount(moved));
                remaining -= moved;
            }
        }
        return remaining == 0;
    }

    private boolean insertOutput(ItemStack stack) {
        int remaining = stack.getCount();
        for (OutputInventorySlot slot : minerOutputs) {
            ItemStack existing = slot.getStack();
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, stack)) {
                int moved = Math.min(remaining,
                        existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    slot.setStack(existing);
                    remaining -= moved;
                    if (remaining == 0) {
                        return true;
                    }
                }
            }
        }
        for (OutputInventorySlot slot : minerOutputs) {
            if (remaining <= 0) {
                break;
            }
            if (slot.getStack().isEmpty()) {
                int moved = Math.min(remaining, stack.getMaxStackSize());
                slot.setStack(stack.copyWithCount(moved));
                remaining -= moved;
            }
        }
        return remaining == 0;
    }

    private void resetPending() {
        pendingOutputs.clear();
        pendingInput = ItemStack.EMPTY;
        progress = 0;
        progressRequired = 1;
    }
}
