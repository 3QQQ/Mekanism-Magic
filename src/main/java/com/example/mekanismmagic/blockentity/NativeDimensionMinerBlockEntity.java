package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.integration.common.network.MachineDirectOutputHooks;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.pipez.PipezItemHandlerCompat;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
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
    private static final String PENDING_OUTPUTS_NBT =
            "miner_pending_outputs";
    private static final String PENDING_INPUT_NBT = "miner_pending_input";
    private static final String PENDING_MULTIPLIER_NBT =
            "miner_pending_operation_multiplier";
    private static final String PENDING_ENERGY_NBT =
            "miner_pending_energy_per_tick";

    private List<BasicInventorySlot> minerOutputs;
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private final List<BufferedOutput> bufferedOutputs = new ArrayList<>();
    private ItemStack pendingInput = ItemStack.EMPTY;
    private int pendingOperationMultiplier = 1;
    private long pendingEnergyPerTick;
    private int ejectCooldown;
    private boolean ejectRetryPending;
    private boolean outputTransferDirty;
    private long nextOutputTransferGameTime;
    private long nextAeBatchGameTime = Long.MIN_VALUE;
    private boolean aeBackpressured;
    private boolean lastAeOnline;
    private long nextNativeEjectGameTime;
    private int directPushCallBudget =
            INITIAL_DIRECT_PUSH_CALLS_PER_TICK;
    private int directPushCursor;

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
        IContentsListener outputListener = () -> {
            // External pipes extract from the visible output slots, while
            // overflow lives in our long buffer. Wake the buffer transfer as
            // soon as a pipe frees space instead of waiting for the active
            // ejector's retry interval.
            if (bufferedOutputs != null && !bufferedOutputs.isEmpty()) {
                outputTransferDirty = true;
            }
            listener.onContentsChanged();
        };
        inputSlot = registerLogicalSlot(helper, MINER_INPUT_SLOT,
                InputInventorySlot.at(OccultismRecipeBridge::isMinerItem,
                        listener, 96, 16));
        for (int index = 0; index < MINER_OUTPUT_COUNT; index++) {
            int column = index % 9;
            int row = index / 9;
            BasicInventorySlot slot = registerLogicalSlot(helper,
                    MINER_OUTPUT_START + index,
                    new MinerOutputInventorySlot(outputListener,
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

    public boolean isMinerOutputSlot(
            mekanism.api.inventory.IInventorySlot inventorySlot) {
        return inventorySlot != null
                && minerOutputs != null
                && minerOutputs.contains(inventorySlot);
    }

    @Override
    protected boolean isLongBufferedOutputSlot(IInventorySlot slot) {
        return isMinerOutputSlot(slot);
    }

    @Override
    protected ItemStack extractLongBufferedOutput(
            IInventorySlot inventorySlot, int amount, Action action) {
        if (amount <= 0 || minerOutputs == null) {
            return ItemStack.EMPTY;
        }
        int outputIndex = minerOutputs.indexOf(inventorySlot);
        if (outputIndex < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack visible = inventorySlot.getStack();
        ItemStack template;
        if (!visible.isEmpty()) {
            template = visible.copyWithCount(1);
        } else if (outputIndex < bufferedOutputs.size()
                && bufferedOutputs.get(outputIndex).count > 0) {
            template = bufferedOutputs.get(outputIndex).stack;
        } else {
            return ItemStack.EMPTY;
        }

        long available = visible.isEmpty() ? 0 : visible.getCount();
        for (BufferedOutput buffered : bufferedOutputs) {
            if (buffered.count > 0
                    && ItemStack.isSameItemSameComponents(
                    buffered.stack, template)) {
                available = saturatingAdd(available, buffered.count);
            }
        }
        int requested = PipezItemHandlerCompat.boundedExtractionAmount(
                        amount, available);
        if (requested <= 0 || !action.execute()) {
            return requested <= 0 ? ItemStack.EMPTY
                    : template.copyWithCount(requested);
        }

        int remaining = requested;
        int removed = 0;
        if (!visible.isEmpty()
                && ItemStack.isSameItemSameComponents(visible, template)) {
            int fromVisible = inventorySlot.shrinkStack(
                    Math.min(remaining, visible.getCount()),
                    Action.EXECUTE);
            remaining -= fromVisible;
            removed += fromVisible;
        }
        for (BufferedOutput buffered : bufferedOutputs) {
            if (remaining <= 0) {
                break;
            }
            if (buffered.count <= 0
                    || !ItemStack.isSameItemSameComponents(
                    buffered.stack, template)) {
                continue;
            }
            int fromBuffer = (int) Math.min(remaining, buffered.count);
            buffered.count -= fromBuffer;
            remaining -= fromBuffer;
            removed += fromBuffer;
        }
        if (removed > 0) {
            outputTransferDirty = true;
            setChanged();
        }
        return removed <= 0 ? ItemStack.EMPTY
                : template.copyWithCount(removed);
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    @Override
    public boolean mekanismMagicSupportsDirectNetworkOutput() {
        return true;
    }

    @Override
    protected boolean onUpdateServer() {
        clearNativeRecipeWarnings();
        if (ejectCooldown > 0) {
            ejectCooldown--;
        }
        boolean bufferedReady = attemptOutputTransfer();
        boolean changed = nativeBaseUpdate();
        if (level == null) {
            return finishServerUpdate(changed, false);
        }
        ItemStack input = inputSlot == null ? ItemStack.EMPTY : inputSlot.getStack();
        if (!OccultismRecipeBridge.isMinerItem(input)) {
            resetPending();
            return finishServerUpdate(
                    mekanismMagicRecipeItemDisplay().clear() || changed,
                    false);
        }
        if (pendingOutputs.isEmpty()
                || !ItemStack.isSameItemSameComponents(input, pendingInput)) {
            preparePendingOutputs(input);
        }
        if (pendingOutputs.isEmpty()) {
            progress = 0;
            setInputDoesntProduceOutputWarning(true);
            return finishServerUpdate(
                    mekanismMagicRecipeItemDisplay().clear() || changed,
                    false);
        }
        changed |= mekanismMagicRecipeItemDisplay().update(List.of(
                new RecipeItemDisplayState.Entry(0,
                        pendingInput, ItemStack.EMPTY)));
        if (!bufferedReady) {
            return finishServerUpdate(changed, false);
        }
        long usage = Math.max(1L, pendingEnergyPerTick);
        if (energyContainer == null || energyContainer.getEnergy() < usage) {
            setNotEnoughEnergyWarning(true);
            return finishServerUpdate(changed, false);
        }
        setActive(true);
        energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
        int efficiency = enchantmentLevel(input, Enchantments.EFFICIENCY);
        progress += 1 + minimumRandomBonus(efficiency, 2);
        if (progress >= progressRequired) {
            progress = 0;
            mergeBufferedOutputs(pendingOutputs);
            pendingOutputs.clear();
            pendingOperationMultiplier = 1;
            pendingEnergyPerTick = 0L;
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
        pendingOperationMultiplier = stackOperationMultiplier();
        pendingEnergyPerTick = stackScaledEnergyUsage(
                pendingOperationMultiplier);
        for (OccultismRecipeBridge.MinerOutput rolled
                : OccultismRecipeBridge.rollMinerOutputs(
                level, input, rolls)) {
            ItemStack output = rolled.output();
            int multiplier = silkTouch <= 0 ? 1
                    : 1 + level.random.nextIntBetweenInclusive(
                    0, silkTouch);
            scaleOutputCount(output, multiplier,
                    pendingOperationMultiplier);
            addPendingOutput(output);
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
            mergeBufferedOutput(output, output.getCount());
        }
    }

    private void mergeBufferedOutput(ItemStack template, long amount) {
        if (template.isEmpty() || amount <= 0) {
            return;
        }
        for (BufferedOutput candidate : bufferedOutputs) {
            if (ItemStack.isSameItemSameComponents(
                    candidate.stack, template)) {
                candidate.count = saturatingAdd(candidate.count, amount);
                outputTransferDirty = true;
                return;
            }
        }
        bufferedOutputs.add(new BufferedOutput(
                template.copyWithCount(1), amount));
        outputTransferDirty = true;
    }

    private boolean attemptOutputTransfer() {
        if (level == null) {
            return bufferedOutputs.isEmpty();
        }
        long gameTime = level.getGameTime();
        boolean aeOnline = MachineDirectOutputHooks.status(this).connected();
        if (bufferedOutputs.isEmpty()) {
            lastAeOnline = aeOnline;
            if (!aeOnline) {
                aeBackpressured = false;
                nextAeBatchGameTime = Long.MIN_VALUE;
            }
            return true;
        }
        if (aeOnline) {
            if (!lastAeOnline) {
                aeBackpressured = true;
                nextAeBatchGameTime = gameTime;
            }
            lastAeOnline = true;
            return attemptAeBatch(gameTime);
        }
        lastAeOnline = false;
        aeBackpressured = false;
        nextAeBatchGameTime = Long.MIN_VALUE;
        if (!outputTransferDirty && gameTime < nextOutputTransferGameTime) {
            return bufferedOutputs.isEmpty();
        }
        boolean directBudgetExhausted = pushBufferedOutputsPhysically();
        boolean ready = flushBufferedOutputs();
        outputTransferDirty = directBudgetExhausted && !ready;
        nextOutputTransferGameTime = gameTime + (outputTransferDirty
                ? 1 : EJECT_INTERVAL_TICKS);
        return ready;
    }

    private boolean attemptAeBatch(long gameTime) {
        if (nextAeBatchGameTime == Long.MIN_VALUE) {
            nextAeBatchGameTime = aeBackpressured
                    ? gameTime
                    : gameTime + DIRECT_NETWORK_BATCH_INTERVAL_TICKS;
        }
        if (gameTime < nextAeBatchGameTime) {
            return !aeBackpressured;
        }
        AeBatchResult result = pushBufferedOutputsToNetwork();
        if (!result.online()) {
            aeBackpressured = false;
            nextAeBatchGameTime = Long.MIN_VALUE;
            boolean physicalBudgetExhausted =
                    pushBufferedOutputsPhysically();
            boolean ready = flushBufferedOutputs();
            outputTransferDirty = physicalBudgetExhausted && !ready;
            nextOutputTransferGameTime = gameTime
                    + (outputTransferDirty ? 1 : EJECT_INTERVAL_TICKS);
            return ready;
        }
        if (bufferedOutputs.isEmpty()) {
            aeBackpressured = false;
            outputTransferDirty = false;
            nextAeBatchGameTime = gameTime
                    + DIRECT_NETWORK_BATCH_INTERVAL_TICKS;
            return true;
        }
        aeBackpressured = true;
        long retryAt = result.retryAtGameTime() == Long.MAX_VALUE
                ? gameTime + DIRECT_NETWORK_BATCH_INTERVAL_TICKS
                : Math.max(gameTime + 1, result.retryAtGameTime());
        nextAeBatchGameTime = retryAt;
        return false;
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

    private AeBatchResult pushBufferedOutputsToNetwork() {
        boolean online = true;
        boolean changed = false;
        long retryAt = Long.MAX_VALUE;
        for (BufferedOutput buffered : bufferedOutputs) {
            if (buffered.count <= 0) {
                continue;
            }
            MachineDirectOutputHooks.DirectInsertResult result =
                    MachineDirectOutputHooks.insertDetailed(
                            this, buffered.stack, buffered.count);
            if (!result.online()) {
                online = false;
                break;
            }
            if (result.accepted() > 0) {
                buffered.count -= result.accepted();
                changed = true;
            }
            if (buffered.count > 0) {
                retryAt = Math.min(retryAt,
                        result.retryAtGameTime());
            }
        }
        bufferedOutputs.removeIf(output -> output.count <= 0);
        if (changed) {
            setChanged();
        }
        return new AeBatchResult(online, retryAt);
    }

    private boolean pushBufferedOutputsPhysically() {
        // Zero-count placeholders are deliberately retained during one
        // Pipez pass so later virtual slot mappings do not shift between its
        // simulate and execute calls. It is safe to compact them now.
        bufferedOutputs.removeIf(output -> output.count <= 0);
        if (bufferedOutputs.isEmpty()) {
            directPushCursor = 0;
            return false;
        }
        int budget = Math.max(MIN_DIRECT_PUSH_CALLS_PER_TICK,
                Math.min(MAX_DIRECT_PUSH_CALLS_PER_TICK,
                        directPushCallBudget));
        int calls = 0;
        long totalMoved = 0;
        boolean bufferChanged = false;
        int noProgress = 0;
        int entries = bufferedOutputs.size();
        if (directPushCursor < 0 || directPushCursor >= entries) {
            directPushCursor = 0;
        }
        while (calls < budget && noProgress < entries) {
            BufferedOutput buffered = bufferedOutputs.get(directPushCursor);
            directPushCursor = (directPushCursor + 1) % entries;
            if (buffered.count <= 0) {
                noProgress++;
                continue;
            }
            int chunk = (int) Math.min(Integer.MAX_VALUE,
                    buffered.count);
            ItemStack attempt = buffered.stack.copyWithCount(chunk);
            ItemStack remainder = pushDirectlyToTargets(attempt);
            calls++;
            long moved = chunk - remainder.getCount();
            if (moved > 0) {
                buffered.count -= moved;
                totalMoved = saturatingAdd(totalMoved, moved);
                bufferChanged = true;
                noProgress = 0;
            } else {
                noProgress++;
            }
        }
        bufferedOutputs.removeIf(output -> output.count <= 0);
        if (bufferedOutputs.isEmpty()) {
            directPushCursor = 0;
        } else {
            directPushCursor %= bufferedOutputs.size();
        }
        if (bufferChanged) {
            setChanged();
        }
        boolean remaining = !bufferedOutputs.isEmpty();
        directPushCallBudget = adaptDirectPushCallBudget(
                budget, calls, totalMoved, remaining);
        return calls >= budget && remaining;
    }

    private record AeBatchResult(
            boolean online, long retryAtGameTime) {
    }

    private long insertLongIntoOutputs(ItemStack template, long amount) {
        long remaining = amount;
        int limit = currentOutputLimit(template);
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
            this.stack = stack.copyWithCount(1);
            this.count = count;
        }
    }

    @Override
    protected void saveNativeMachineData(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveNativeMachineData(tag, registries);
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
        net.minecraft.nbt.ListTag pending = new net.minecraft.nbt.ListTag();
        for (ItemStack output : pendingOutputs) {
            if (!output.isEmpty()) {
                pending.add(saveCountedItemStack(output, registries));
            }
        }
        if (!pending.isEmpty()) {
            tag.put(PENDING_OUTPUTS_NBT, pending);
            tag.putInt(PENDING_MULTIPLIER_NBT,
                    pendingOperationMultiplier);
            tag.putLong(PENDING_ENERGY_NBT,
                    Math.max(1L, pendingEnergyPerTick));
        }
        if (!pendingInput.isEmpty()) {
            tag.put(PENDING_INPUT_NBT, pendingInput.save(registries));
        }
    }

    @Override
    protected void loadNativeMachineData(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.loadNativeMachineData(tag, registries);
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
        pendingOutputs.clear();
        net.minecraft.nbt.ListTag pending = tag.getList(
                PENDING_OUTPUTS_NBT, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < pending.size(); index++) {
            ItemStack stack = loadCountedItemStack(
                    pending.getCompound(index), registries);
            if (!stack.isEmpty()) {
                pendingOutputs.add(stack);
            }
        }
        pendingInput = ItemStack.parseOptional(registries,
                tag.getCompound(PENDING_INPUT_NBT));
        pendingOperationMultiplier = tag.contains(
                PENDING_MULTIPLIER_NBT, net.minecraft.nbt.Tag.TAG_INT)
                ? Math.max(1, tag.getInt(PENDING_MULTIPLIER_NBT))
                : stackOperationMultiplier();
        pendingEnergyPerTick = tag.contains(
                PENDING_ENERGY_NBT, net.minecraft.nbt.Tag.TAG_LONG)
                ? Math.max(1L, tag.getLong(PENDING_ENERGY_NBT))
                : stackScaledEnergyUsage(pendingOperationMultiplier);
        // Treat incomplete/corrupt pending state as absent. Keeping only the
        // rolled output or only its input signature would either duplicate a
        // result or bind it to a different miner tool on the next tick.
        if (pendingOutputs.isEmpty() || pendingInput.isEmpty()) {
            pendingOutputs.clear();
            pendingInput = ItemStack.EMPTY;
            pendingOperationMultiplier = 1;
            pendingEnergyPerTick = 0L;
            progress = 0;
        }
        normalizeVisibleOutputSlots();
        outputTransferDirty = !bufferedOutputs.isEmpty();
        aeBackpressured = !bufferedOutputs.isEmpty();
        nextAeBatchGameTime = Long.MIN_VALUE;
    }

    @Override
    protected void onNativeUpgradeChanged(mekanism.api.Upgrade upgrade) {
        mekanism.api.Upgrade stackUpgrade =
                NativeMekanismRegistries.dimensionMinerStackUpgrade();
        if (!pendingOutputs.isEmpty()
                && (upgrade == mekanism.api.Upgrade.SPEED
                || upgrade == mekanism.api.Upgrade.ENERGY
                || stackUpgrade != null && upgrade == stackUpgrade)) {
            rebasePendingWorkForUpgrade();
        }
    }

    private void rebasePendingWorkForUpgrade() {
        int oldRequired = Math.max(1, progressRequired);
        int newRequired = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        OccultismRecipeBridge.minerDuration(pendingInput)));
        progress = (int) Math.min(newRequired - 1L,
                (long) Math.max(0, progress) * newRequired / oldRequired);
        progressRequired = newRequired;
        // The rolled output multiplier remains fixed, while ordinary speed
        // and energy upgrades immediately rebalance duration and FE/t.
        pendingEnergyPerTick = stackScaledEnergyUsage(
                pendingOperationMultiplier);
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

    private static int currentOutputLimit(ItemStack stack) {
        return stack.isEmpty() ? 64 : Math.max(1, stack.getMaxStackSize());
    }

    private void normalizeVisibleOutputSlots() {
        for (BasicInventorySlot slot : minerOutputs) {
            ItemStack stack = slot.getStack();
            int limit = currentOutputLimit(stack);
            if (stack.isEmpty() || stack.getCount() <= limit) {
                continue;
            }
            long excess = (long) stack.getCount() - limit;
            ItemStack normalized = stack.copyWithCount(limit);
            slot.setStack(normalized);
            mergeBufferedOutput(stack, excess);
        }
    }

    private long stackScaledEnergyUsage(int operationMultiplier) {
        long base = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
        int multiplier = Math.max(1, operationMultiplier);
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
        private MinerOutputInventorySlot(
                IContentsListener listener, int x, int y) {
            super(BASE_MINER_OUTPUT_LIMIT,
                    (stack, automation) -> true,
                    (stack, automation) -> automation
                            == mekanism.api.AutomationType.INTERNAL
                            || automation
                            == mekanism.api.AutomationType.EXTERNAL,
                    stack -> true, listener, x, y);
            setSlotType(ContainerSlotType.OUTPUT);
        }

        @Override
        public int getLimit(ItemStack stack) {
            return currentOutputLimit(stack);
        }
    }

    /** Package-private hooks used only by the isolated runtime GameTests. */
    void seedBufferedOutputDevelopment(ItemStack stack, long count) {
        mergeBufferedOutput(stack, count);
    }

    long developmentTotalOutput(ItemStack template) {
        long total = 0;
        for (BasicInventorySlot slot : minerOutputs) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, template)) {
                total = saturatingAdd(total, stack.getCount());
            }
        }
        for (BufferedOutput buffered : bufferedOutputs) {
            if (buffered.count > 0
                    && ItemStack.isSameItemSameComponents(
                    buffered.stack, template)) {
                total = saturatingAdd(total, buffered.count);
            }
        }
        return total;
    }

    int seedDevelopmentTest(ItemStack miner) {
        if (level == null || !OccultismRecipeBridge.isMinerItem(miner)) {
            return 0;
        }
        inputSlot.setStack(miner.copy());
        preparePendingOutputs(inputSlot.getStack());
        if (pendingOutputs.isEmpty()) {
            return 0;
        }
        progress = Math.max(0, progressRequired - 1);
        return pendingOutputs.stream().mapToInt(ItemStack::getCount).sum();
    }

    List<ItemStack> developmentPendingOutputs() {
        return pendingOutputs.stream().map(ItemStack::copy).toList();
    }

    ItemStack developmentPendingInput() {
        return pendingInput.copy();
    }

    int developmentPendingOperationMultiplier() {
        return pendingOperationMultiplier;
    }

    long developmentPendingEnergyPerTick() {
        return pendingEnergyPerTick;
    }

    private void resetPending() {
        pendingOutputs.clear();
        pendingInput = ItemStack.EMPTY;
        pendingOperationMultiplier = 1;
        pendingEnergyPerTick = 0L;
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
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()
                    && slot.getCount() >= currentOutputLimit(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onFastEjectFinished(boolean outputRemaining,
                                       boolean continueImmediately) {
        // Continue at full speed only while a target accepted something.
        // A full/rejecting target is retried on the normal interval instead
        // of rescanning every output slot on every server tick.
        ejectRetryPending = outputRemaining && continueImmediately;
        nextNativeEjectGameTime = (level == null ? 0 : level.getGameTime())
                + EJECT_INTERVAL_TICKS;
    }

}
