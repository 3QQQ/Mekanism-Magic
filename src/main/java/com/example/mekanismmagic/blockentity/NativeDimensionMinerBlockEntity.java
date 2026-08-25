package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;

import com.example.mekanismmagic.NativeMekanismRegistries;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

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
    private static final int MINER_OUTPUT_LIMIT = Integer.MAX_VALUE;

    private List<BasicInventorySlot> minerOutputs;
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private final List<BufferedOutput> bufferedOutputs = new ArrayList<>();
    private ItemStack pendingInput = ItemStack.EMPTY;
    private static final int EJECT_INTERVAL_TICKS = 20;
    private int ejectCooldown;
    private boolean outputTransferDirty;
    private long nextOutputTransferGameTime;
    private long nextNativeEjectGameTime;
    private boolean ejectRetryPending;

    public NativeDimensionMinerBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.DIMENSION_MINER_BLOCK, pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        // TileEntityMekanism builds capabilities from its superclass
        // constructor, before subclass field initializers have run.
        minerOutputs = new ArrayList<>(MINER_OUTPUT_COUNT);
        inputSlot = registerLogicalSlot(helper, MINER_INPUT_SLOT,
                InputInventorySlot.at(OccultismRecipeBridge::isMinerItem,
                        listener, 96, 16));
        for (int index = 0; index < MINER_OUTPUT_COUNT; index++) {
            int column = index % 9;
            int row = index / 9;
            BasicInventorySlot slot = registerLogicalSlot(helper,
                    MINER_OUTPUT_START + index,
                    new MinerOutputInventorySlot(listener,
                            24 + column * 18, 40 + row * 18));
            minerOutputs.add(slot);
            if (index == 0) {
                outputSlot = slot;
            }
        }
        setupNativeItemIO(List.of(inputSlot), minerOutputs, List.of());
    }

    @Override
    protected int energySlotX() {
        return 20;
    }

    @Override
    protected int energySlotY() {
        return 16;
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
    public List<mekanism.api.inventory.IInventorySlot>
    mekanismMagicPatternInputs() {
        return List.of();
    }

    @Override
    public List<mekanism.api.inventory.IInventorySlot>
    mekanismMagicPersistentInputs() {
        return inputSlot == null ? List.of() : List.of(inputSlot);
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    public boolean isMinerOutputSlot(
            mekanism.api.inventory.IInventorySlot inventorySlot) {
        return inventorySlot != null
                && minerOutputs != null
                && minerOutputs.contains(inventorySlot);
    }

    @Override
    protected void onUpdateServer() {
        if (ejectCooldown > 0) {
            ejectCooldown--;
        }
        boolean bufferedReady = attemptOutputTransfer();
        nativeBaseUpdate();
        setActive(false);
        if (level == null) {
            return;
        }
        ItemStack input = inputSlot == null ? ItemStack.EMPTY : inputSlot.getStack();
        if (!OccultismRecipeBridge.isMinerItem(input)) {
            resetPending();
            return;
        }
        if (pendingOutputs.isEmpty()
                || !ItemStack.isSameItemSameTags(input, pendingInput)) {
            preparePendingOutputs(input);
        }
        if (pendingOutputs.isEmpty()) {
            progress = 0;
            return;
        }
        if (!bufferedReady) {
            return;
        }
        FloatingLong usage = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, FloatingLong.create(baseEnergyPerTick()));
        if (energyContainer == null || energyContainer.getEnergy().smallerThan(usage)) {
            return;
        }
        setActive(true);
        energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
        int efficiency = enchantmentLevel(input, Enchantments.BLOCK_EFFICIENCY);
        progress += 1 + minimumRandomBonus(efficiency, 2);
        if (progress >= progressRequired) {
            progress = 0;
            mergeBufferedOutputs(pendingOutputs);
            pendingOutputs.clear();
            outputTransferDirty = true;
            attemptOutputTransfer();
            setChanged();
        }
    }

    private void preparePendingOutputs(ItemStack input) {
        pendingInput = input.copy();
        pendingOutputs.clear();
        progress = 0;
        progressRequired = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        OccultismRecipeBridge.minerDuration(input)));

        int fortune = enchantmentLevel(input, Enchantments.BLOCK_FORTUNE);
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

    private int enchantmentLevel(ItemStack stack, Enchantment enchantment) {
        if (stack.isEmpty()) {
            return 0;
        }
        return stack.getEnchantmentLevel(enchantment);
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

    private boolean hasStoredOutput() {
        if (minerOutputs == null) {
            return false;
        }
        for (BasicInventorySlot slot : minerOutputs) {
            if (!slot.getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void mergeBufferedOutputs(List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            if (output.isEmpty()) {
                continue;
            }
            BufferedOutput match = null;
            for (BufferedOutput candidate : bufferedOutputs) {
                if (ItemStack.isSameItemSameTags(candidate.stack, output)) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                bufferedOutputs.add(new BufferedOutput(output.copy(),
                        output.getCount()));
            } else {
                match.count = saturatingAdd(match.count, output.getCount());
            }
            outputTransferDirty = true;
        }
    }

    private boolean attemptOutputTransfer() {
        if (level == null || bufferedOutputs.isEmpty()) {
            return bufferedOutputs.isEmpty();
        }
        long gameTime = level.getGameTime();
        if (!outputTransferDirty && gameTime < nextOutputTransferGameTime) {
            return bufferedOutputs.isEmpty();
        }
        pushBufferedOutputsDirectly();
        boolean ready = flushBufferedOutputs();
        outputTransferDirty = false;
        nextOutputTransferGameTime = gameTime + EJECT_INTERVAL_TICKS;
        return ready;
    }

    private boolean flushBufferedOutputs() {
        for (int index = 0; index < bufferedOutputs.size();) {
            BufferedOutput buffered = bufferedOutputs.get(index);
            long moved = insertLongIntoOutputs(buffered.stack,
                    buffered.count);
            buffered.count -= moved;
            if (buffered.count <= 0) {
                bufferedOutputs.remove(index);
            } else {
                index++;
            }
        }
        return bufferedOutputs.isEmpty();
    }

    private void pushBufferedOutputsDirectly() {
        if (bufferedOutputs.isEmpty()) {
            return;
        }
        boolean remaining = false;
        for (BufferedOutput buffered : bufferedOutputs) {
            while (buffered.count > 0) {
                int chunk = (int) Math.min(Integer.MAX_VALUE,
                        buffered.count);
                ItemStack attempt = buffered.stack.copy();
                attempt.setCount(chunk);
                ItemStack remainder = pushDirectlyToTargets(attempt);
                long moved = chunk - remainder.getCount();
                if (moved <= 0) {
                    remaining = true;
                    break;
                }
                buffered.count -= moved;
            }
        }
        bufferedOutputs.removeIf(output -> output.count <= 0);
    }

    private long insertLongIntoOutputs(ItemStack template, long amount) {
        long remaining = amount;
        for (BasicInventorySlot slot : minerOutputs) {
            if (remaining <= 0) {
                break;
            }
            ItemStack existing = slot.getStack();
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameTags(existing, template)) {
                continue;
            }
            int moved = (int) Math.min(remaining,
                    Math.max(0, MINER_OUTPUT_LIMIT - existing.getCount()));
            if (moved > 0) {
                existing.grow(moved);
                slot.setStack(existing);
                remaining -= moved;
            }
        }
        for (BasicInventorySlot slot : minerOutputs) {
            if (remaining <= 0) {
                break;
            }
            if (!slot.getStack().isEmpty()) {
                continue;
            }
            int moved = (int) Math.min(remaining, MINER_OUTPUT_LIMIT);
            ItemStack stack = template.copy();
            stack.setCount(moved);
            slot.setStack(stack);
            remaining -= moved;
        }
        return amount - remaining;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    private static final class BufferedOutput {
        private final ItemStack stack;
        private long count;

        private BufferedOutput(ItemStack stack, long count) {
            this.stack = stack;
            this.count = count;
        }
    }

    private static boolean insertIntoStacks(List<ItemStack> targets,
                                            ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack existing : targets) {
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameTags(existing, stack)) {
                int moved = Math.min(remaining,
                        MINER_OUTPUT_LIMIT - existing.getCount());
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
                int moved = Math.min(remaining, MINER_OUTPUT_LIMIT);
                ItemStack movedStack = stack.copy();
                movedStack.setCount(moved);
                targets.set(index, movedStack);
                remaining -= moved;
            }
        }
        return remaining == 0;
    }

    private boolean insertOutput(ItemStack stack) {
        int remaining = stack.getCount();
        for (BasicInventorySlot slot : minerOutputs) {
            ItemStack existing = slot.getStack();
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameTags(existing, stack)) {
                int moved = Math.min(remaining,
                        MINER_OUTPUT_LIMIT - existing.getCount());
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
        for (BasicInventorySlot slot : minerOutputs) {
            if (remaining <= 0) {
                break;
            }
            if (slot.getStack().isEmpty()) {
                int moved = Math.min(remaining, MINER_OUTPUT_LIMIT);
                ItemStack movedStack = stack.copy();
                movedStack.setCount(moved);
                slot.setStack(movedStack);
                remaining -= moved;
            }
        }
        return remaining == 0;
    }

    private static final class MinerOutputInventorySlot
            extends BasicInventorySlot {
        private MinerOutputInventorySlot(IContentsListener listener, int x, int y) {
            super(MINER_OUTPUT_LIMIT,
                    BasicInventorySlot.alwaysTrueBi,
                    (stack, automation) ->
                            automation == AutomationType.INTERNAL
                                    || automation
                                    == AutomationType.EXTERNAL,
                    BasicInventorySlot.alwaysTrue,
                    listener, x, y);
            obeyStackLimit = false;
            setSlotType(ContainerSlotType.OUTPUT);
        }
    }

    private void resetPending() {
        pendingOutputs.clear();
        pendingInput = ItemStack.EMPTY;
        progress = 0;
        progressRequired = 1;
    }

    @Override
    protected boolean shouldEjectOutputs() {
        long gameTime = level == null ? Long.MAX_VALUE : level.getGameTime();
        return ejectRetryPending || gameTime >= nextNativeEjectGameTime
                || outputBufferNeedsEject();
    }

    @Override
    protected boolean useFastEjectPath() {
        return true;
    }

    private boolean outputBufferNeedsEject() {
        for (BasicInventorySlot slot : minerOutputs) {
            if (slot.getCount() >= MINER_OUTPUT_LIMIT) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onFastEjectFinished(boolean outputRemaining,
                                       boolean nativeFallbackRequired) {
        if (outputRemaining) {
            ejectRetryPending = true;
            nextNativeEjectGameTime = (level == null ? 0 : level.getGameTime())
                    + EJECT_INTERVAL_TICKS;
        } else {
            ejectRetryPending = false;
            nextNativeEjectGameTime = (level == null ? 0 : level.getGameTime())
                    + EJECT_INTERVAL_TICKS;
        }
    }

    @Override
    public void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        if (!pendingInput.isEmpty()) {
            tag.put("miner_pending_input",
                    pendingInput.save(new net.minecraft.nbt.CompoundTag()));
        }
        net.minecraft.nbt.ListTag outputs = new net.minecraft.nbt.ListTag();
        for (ItemStack output : pendingOutputs) {
            outputs.add(output.save(new net.minecraft.nbt.CompoundTag()));
        }
        if (!outputs.isEmpty()) {
            tag.put("miner_pending_outputs", outputs);
        }
        net.minecraft.nbt.ListTag buffered = new net.minecraft.nbt.ListTag();
        for (BufferedOutput output : bufferedOutputs) {
            net.minecraft.nbt.CompoundTag entry =
                    new net.minecraft.nbt.CompoundTag();
            entry.put("stack", output.stack.save(new net.minecraft.nbt.CompoundTag()));
            entry.putLong("count", output.count);
            buffered.add(entry);
        }
        if (!buffered.isEmpty()) {
            tag.put("miner_long_buffer", buffered);
        }
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        pendingInput = tag.contains("miner_pending_input",
                net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound("miner_pending_input"))
                : ItemStack.EMPTY;
        pendingOutputs.clear();
        net.minecraft.nbt.ListTag outputs = tag.getList(
                "miner_pending_outputs",
                net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < outputs.size(); index++) {
            ItemStack output = ItemStack.of(outputs.getCompound(index));
            if (!output.isEmpty()) {
                pendingOutputs.add(output);
            }
        }
        bufferedOutputs.clear();
        net.minecraft.nbt.ListTag buffered = tag.getList(
                "miner_long_buffer", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < buffered.size(); index++) {
            net.minecraft.nbt.CompoundTag entry = buffered.getCompound(index);
            ItemStack stack = ItemStack.of(entry.getCompound("stack"));
            long count = entry.getLong("count");
            if (!stack.isEmpty() && count > 0) {
                bufferedOutputs.add(new BufferedOutput(stack, count));
            }
        }
        outputTransferDirty = !bufferedOutputs.isEmpty();
        if (pendingOutputs.isEmpty()) {
            pendingInput = ItemStack.EMPTY;
            progress = 0;
            progressRequired = 1;
        }
    }
}

