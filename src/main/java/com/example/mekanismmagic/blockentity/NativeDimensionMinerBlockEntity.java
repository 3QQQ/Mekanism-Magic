package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
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
    private static final int BASE_MINER_OUTPUT_LIMIT = Integer.MAX_VALUE;
    private static final int EJECT_INTERVAL_TICKS = 20;

    private List<BasicInventorySlot> minerOutputs;
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private final List<BufferedOutput> bufferedOutputs = new ArrayList<>();
    private ItemStack pendingInput = ItemStack.EMPTY;
    private int ejectCooldown;
    private boolean ejectRetryPending;
    private boolean outputTransferDirty;
    private long nextOutputTransferGameTime;
    private long nextNativeEjectGameTime;

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
                        listener, 96, 16));
        for (int index = 0; index < MINER_OUTPUT_COUNT; index++) {
            int column = index % 9;
            int row = index / 9;
            BasicInventorySlot slot = registerLogicalSlot(helper,
                    MINER_OUTPUT_START + index,
                    new MinerOutputInventorySlot(this, listener,
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
    protected java.util.Optional<MachineRecipeResult> findRecipe(
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

    @Override
    protected boolean onUpdateServer() {
        if (ejectCooldown > 0) {
            ejectCooldown--;
        }
        boolean bufferedReady = attemptOutputTransfer();
        boolean changed = nativeBaseUpdate();
        setActive(false);
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
        if (!bufferedReady) {
            return changed;
        }
        long usage = stackScaledEnergyUsage();
        if (energyContainer == null || energyContainer.getEnergy() < usage) {
            return changed;
        }
        setActive(true);
        energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
        int efficiency = enchantmentLevel(input, Enchantments.EFFICIENCY);
        progress += 1 + minimumRandomBonus(efficiency, 2);
        if (progress >= progressRequired) {
            progress = 0;
            mergeBufferedOutputs(pendingOutputs);
            pendingOutputs.clear();
            outputTransferDirty = true;
            attemptOutputTransfer();
            changed = true;
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
        int stackMultiplier = stackOperationMultiplier();
        for (int roll = 0; roll < rolls; roll++) {
            OccultismRecipeBridge.findMinerOutput(level, input)
                    .map(OccultismRecipeBridge.MinerOutput::output)
                    .map(ItemStack::copy)
                    .ifPresent(output -> {
                        int multiplier = silkTouch <= 0 ? 1
                                : 1 + level.random.nextIntBetweenInclusive(
                                0, silkTouch);
                        scaleOutputCount(output, multiplier, stackMultiplier);
                        addPendingOutput(output);
                    });
        }
    }

    private static void scaleOutputCount(ItemStack output,
                                         int silkMultiplier,
                                         int stackMultiplier) {
        long count = (long) output.getCount()
                * Math.max(1, silkMultiplier)
                * Math.max(1, stackMultiplier);
        output.setCount((int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, count)));
    }

    private void addPendingOutput(ItemStack output) {
        if (output.isEmpty()) {
            return;
        }
        for (ItemStack existing : pendingOutputs) {
            if (!ItemStack.isSameItemSameComponents(existing, output)) {
                continue;
            }
            long combined = (long) existing.getCount() + output.getCount();
            existing.setCount((int) Math.min(Integer.MAX_VALUE, combined));
            return;
        }
        pendingOutputs.add(output);
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
                if (ItemStack.isSameItemSameComponents(
                        candidate.stack, output)) {
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
                ItemStack attempt = buffered.stack.copyWithCount(chunk);
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
        int limit = currentOutputLimit();
        for (BasicInventorySlot slot : minerOutputs) {
            if (remaining <= 0) {
                break;
            }
            ItemStack existing = slot.getStack();
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing,
                    template)) {
                continue;
            }
            int moved = (int) Math.min(remaining,
                    Math.max(0, limit - existing.getCount()));
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
            int moved = (int) Math.min(remaining, limit);
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

    @Override
    public void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.nbt.ListTag outputs = new net.minecraft.nbt.ListTag();
        for (BufferedOutput buffered : bufferedOutputs) {
            net.minecraft.nbt.CompoundTag entry =
                    new net.minecraft.nbt.CompoundTag();
            entry.put("stack", buffered.stack.save(registries));
            entry.putLong("count", buffered.count);
            outputs.add(entry);
        }
        if (!outputs.isEmpty()) {
            tag.put("miner_long_buffer", outputs);
        }
    }

    @Override
    public void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bufferedOutputs.clear();
        net.minecraft.nbt.ListTag outputs = tag.getList(
                "miner_long_buffer", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < outputs.size(); index++) {
            net.minecraft.nbt.CompoundTag entry = outputs.getCompound(index);
            ItemStack stack = ItemStack.parseOptional(registries,
                    entry.getCompound("stack"));
            long count = entry.getLong("count");
            if (!stack.isEmpty() && count > 0) {
                bufferedOutputs.add(new BufferedOutput(stack, count));
            }
        }
        outputTransferDirty = !bufferedOutputs.isEmpty();
    }

    @Override
    protected void onNativeUpgradeChanged(mekanism.api.Upgrade upgrade) {
        mekanism.api.Upgrade stackUpgrade =
                NativeMekanismRegistries.dimensionMinerStackUpgrade();
        if (stackUpgrade != null && upgrade == stackUpgrade) {
            resetPending();
        }
    }

    private int stackOperationMultiplier() {
        mekanism.api.Upgrade stackUpgrade =
                NativeMekanismRegistries.dimensionMinerStackUpgrade();
        if (stackUpgrade == null || upgradeComponent == null) {
            return 1;
        }
        int upgrades = Math.max(0,
                upgradeComponent.getUpgrades(stackUpgrade));
        return 1 << Math.min(upgrades, 8);
    }

    private int currentOutputLimit() {
        long required = BASE_MINER_OUTPUT_LIMIT;
        for (ItemStack pending : pendingOutputs) {
            required = Math.max(required, pending.getCount());
        }
        return (int) Math.min(Integer.MAX_VALUE, required);
    }

    private long stackScaledEnergyUsage() {
        long base = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
        int multiplier = stackOperationMultiplier();
        long scaled = base > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : base * multiplier;
        // Never require more energy per tick than the machine can store.
        // Otherwise a heavily stacked miner can permanently wait for an
        // impossible charge level and appear to stop working.
        return energyContainer == null
                ? scaled : Math.min(scaled, energyContainer.getMaxEnergy());
    }

    private static final class MinerOutputInventorySlot
            extends BasicInventorySlot {
        private final NativeDimensionMinerBlockEntity tile;

        private MinerOutputInventorySlot(
                NativeDimensionMinerBlockEntity tile,
                IContentsListener listener, int x, int y) {
            super(BASE_MINER_OUTPUT_LIMIT,
                    (stack, automation) -> true,
                    (stack, automation) -> automation
                            == mekanism.api.AutomationType.INTERNAL
                            || automation
                            == mekanism.api.AutomationType.EXTERNAL,
                    stack -> true, listener, x, y);
            this.tile = tile;
            obeyStackLimit = false;
            setSlotType(ContainerSlotType.OUTPUT);
        }

        @Override
        public int getLimit(ItemStack stack) {
            return tile.currentOutputLimit();
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

    private boolean outputBufferNeedsEject() {
        int limit = currentOutputLimit();
        for (BasicInventorySlot slot : minerOutputs) {
            if (slot.getCount() >= limit) {
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

}
