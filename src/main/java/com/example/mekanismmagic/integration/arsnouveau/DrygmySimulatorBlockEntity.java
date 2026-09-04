package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.example.mekanismmagic.integration.common.network.MachineDirectOutputHooks;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.pipez.PipezItemHandlerCompat;
import com.hollingsworth.arsnouveau.api.ANFakePlayer;
import com.hollingsworth.arsnouveau.common.lib.EntityTags;
import com.hollingsworth.arsnouveau.setup.config.Config;
import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Simulates Drygmy loot production from captured entities without placing
 * those entities into the world.
 */
public final class DrygmySimulatorBlockEntity
        extends ArsSourceMachineBlockEntity {
    public static final int JAR_SLOT_START = 0;
    public static final int JAR_SLOT_COUNT = 9;
    public static final int OUTPUT_SLOT_START = 9;
    public static final int OUTPUT_SLOT_COUNT = 27;
    private static final int BASE_OUTPUT_LIMIT = Integer.MAX_VALUE;
    private static final int EJECT_INTERVAL_TICKS = 20;
    private static final int VISUAL_STALL_GRACE_TICKS =
            EJECT_INTERVAL_TICKS * 2;
    private static final int EMPTY_LOOT_RETRY_TICKS = 200;
    private static final int MAX_LOOT_SELECTION_ROLLS = 4_096;
    private static final int MAX_STACK_OPERATION_MULTIPLIER = 1 << 8;
    private static final String VISUAL_PROCESSING_NBT =
            "drygmy_visual_processing";
    private static final String PENDING_OUTPUTS_NBT =
            "drygmy_pending_outputs";
    private static final String PENDING_SIGNATURE_NBT =
            "drygmy_pending_signature";
    private static final String PENDING_MULTIPLIER_NBT =
            "drygmy_pending_operation_multiplier";
    private static final String PENDING_SOURCE_COST_NBT =
            "drygmy_pending_source_cost";
    private static final String PENDING_ENERGY_NBT =
            "drygmy_pending_energy_per_tick";
    private static final ResourceLocation PROCESS_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "drygmy_simulation");

    private List<BasicInventorySlot> jarSlots;
    private List<BasicInventorySlot> outputSlots;
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    // BlockItem restores BLOCK_ENTITY_DATA before Mekanism's inventory data
    // components. Jar-slot listeners therefore run after our custom NBT and
    // would erase a prepared roll. Hold it until the first server tick, then
    // bind it only if the fully restored jar signature still matches.
    private final List<ItemStack> deferredPendingOutputs = new ArrayList<>();
    private final List<BufferedOutput> bufferedOutputs = new ArrayList<>();
    private final List<LivingEntity> cachedLootEntities = new ArrayList<>();
    private String pendingSignature = "";
    private int pendingOperationMultiplier = 1;
    private int pendingSourceCost;
    private long pendingEnergyPerTick;
    private String deferredPendingSignature = "";
    private int deferredPendingProgress;
    private int deferredPendingProgressRequired = 1;
    private int deferredPendingOperationMultiplier = 1;
    private int deferredPendingSourceCost;
    private long deferredPendingEnergyPerTick;
    private String cachedJarSignature = "";
    private String cachedLootEntitySignature = "";
    private int cachedUniqueEntityTypeCount;
    private boolean jarSignatureDirty = true;
    private long nextLootPreparationGameTime = Long.MIN_VALUE;
    private boolean outputTransferDirty;
    private boolean ejectRetryPending;
    private long nextOutputTransferGameTime;
    private long nextAeBatchGameTime = Long.MIN_VALUE;
    private boolean aeBackpressured;
    private boolean lastAeOnline;
    private long nextNativeEjectGameTime;
    private int directPushCallBudget =
            INITIAL_DIRECT_PUSH_CALLS_PER_TICK;
    private int directPushCursor;
    private boolean visualProcessing;
    private int visualStallTicks;
    private MachineRecipeResult cachedProcessResult;
    private int cachedProcessMultiplier = -1;
    private int cachedProcessSourceCost = -1;

    public DrygmySimulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        jarSlots = new ArrayList<>(JAR_SLOT_COUNT);
        outputSlots = new ArrayList<>(OUTPUT_SLOT_COUNT);
        IContentsListener inputListener = () -> {
            pendingOutputs.clear();
            pendingSignature = "";
            clearCurrentPendingOperationContext();
            cachedJarSignature = "";
            jarSignatureDirty = true;
            clearCachedLootEntities();
            nextLootPreparationGameTime = Long.MIN_VALUE;
            progress = 0;
            listener.onContentsChanged();
        };
        IContentsListener outputListener = () -> {
            // A pulling pipe only sees the visible output slots. If overflow
            // is waiting in the long buffer, refill a slot on the next server
            // tick rather than using the 20-tick active-eject retry cadence.
            if (bufferedOutputs != null && !bufferedOutputs.isEmpty()) {
                outputTransferDirty = true;
            }
            listener.onContentsChanged();
        };
        for (int index = 0; index < JAR_SLOT_COUNT; index++) {
            jarSlots.add(registerLogicalSlot(helper,
                    JAR_SLOT_START + index,
                    new PersistentJarSlot(inputListener,
                            24 + index * 18, 16)));
        }
        for (int index = 0; index < OUTPUT_SLOT_COUNT; index++) {
            int column = index % 9;
            int row = index / 9;
            BasicInventorySlot slot = registerLogicalSlot(helper,
                    OUTPUT_SLOT_START + index,
                    new DrygmyOutputSlot(outputListener,
                            24 + column * 18, 46 + row * 18));
            outputSlots.add(slot);
            if (index == 0) {
                outputSlot = slot;
            }
        }
        setupArsItemIO(jarSlots, outputSlots, List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return Optional.empty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return 800;
    }

    @Override
    protected int energySlotX() {
        return 188;
    }

    @Override
    protected int energySlotY() {
        return 106;
    }

    @Override
    protected int unsuccessfulSourcePullInterval() {
        // Radius ten contains 1,561 candidate positions. Once a full scan
        // finds no Source provider, retry at a lower idle cadence; a
        // successful pull immediately returns to the normal one-second rate.
        return 100;
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
    public List<IInventorySlot> mekanismMagicPatternInputs() {
        return List.of();
    }

    @Override
    public List<IInventorySlot> mekanismMagicPersistentInputs() {
        List<IInventorySlot> persistent = new ArrayList<>(jarSlots);
        return List.copyOf(persistent);
    }

    public boolean isDrygmyOutputSlot(IInventorySlot inventorySlot) {
        return inventorySlot != null
                && outputSlots != null
                && outputSlots.contains(inventorySlot);
    }

    @Override
    protected boolean isLongBufferedOutputSlot(IInventorySlot slot) {
        return isDrygmyOutputSlot(slot);
    }

    @Override
    protected ItemStack extractLongBufferedOutput(
            IInventorySlot inventorySlot, int amount, Action action) {
        if (amount <= 0 || outputSlots == null) {
            return ItemStack.EMPTY;
        }
        int outputIndex = outputSlots.indexOf(inventorySlot);
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
    protected boolean onUpdateServer() {
        clearNativeRecipeWarnings();
        restoreDeferredPendingState();
        boolean bufferedReady = attemptOutputTransfer();
        boolean changed = autoPullNearbySource();
        changed |= nativeBaseUpdate();
        if (!(level instanceof ServerLevel serverLevel)) {
            return finishServerUpdate(changed, false);
        }
        String signature = jarSignature();
        if (signature.isEmpty()) {
            resetProcess();
            return finishVisualUpdate(changed, false);
        }
        long gameTime = serverLevel.getGameTime();
        if (!signature.equals(pendingSignature)
                || pendingOutputs.isEmpty()
                && gameTime >= nextLootPreparationGameTime) {
            prepareOutputs(serverLevel, signature);
            nextLootPreparationGameTime = pendingOutputs.isEmpty()
                    ? gameTime + EMPTY_LOOT_RETRY_TICKS
                    : Long.MAX_VALUE;
        }
        if (pendingOutputs.isEmpty()) {
            setInputDoesntProduceOutputWarning(true);
            return finishVisualUpdate(changed, false);
        }
        if (!bufferedReady) {
            return finishBufferedWait(changed);
        }
        visualStallTicks = 0;
        MachineRecipeResult process = processResult();
        if (!hasRecipeResources(process)) {
            return finishVisualUpdate(changed, false);
        }
        long usage = Math.max(1L, pendingEnergyPerTick);
        boolean powered = hasEnergyForRecipe(process, usage);
        boolean sourceOnly = !powered && canRunWithoutEnergy(process);
        if (!powered && !sourceOnly) {
            setNotEnoughEnergyWarning(true);
            return finishVisualUpdate(changed, false);
        }
        setReducedEnergyWarning(sourceOnly);
        if (sourceOnly && !isEnergylessTick(process)) {
            return finishVisualUpdate(changed, true);
        }
        boolean visualChanged = setVisualProcessing(true);
        setActive(true);
        if (powered) {
            energyContainer.extract(usage, Action.EXECUTE,
                    AutomationType.INTERNAL);
        }
        progress++;
        if (progress >= progressRequired) {
            if (!consumeRecipeResources(process)) {
                progress = 0;
                return finishVisualUpdate(changed, false);
            }
            progress = 0;
            mergeBufferedOutputs(pendingOutputs);
            pendingOutputs.clear();
            clearCurrentPendingOperationContext();
            refreshSourceLimits();
            nextLootPreparationGameTime = Long.MIN_VALUE;
            outputTransferDirty = true;
            attemptOutputTransfer();
            changed = true;
        }
        return changed || visualChanged;
    }

    public boolean isVisuallyProcessing() {
        return visualProcessing;
    }

    private boolean finishBufferedWait(boolean changed) {
        visualStallTicks++;
        boolean keepAnimating = visualProcessing
                && visualStallTicks <= VISUAL_STALL_GRACE_TICKS;
        boolean visualChanged = setVisualProcessing(keepAnimating);
        return finishServerUpdate(changed || visualChanged, keepAnimating);
    }

    private boolean finishVisualUpdate(boolean changed,
                                       boolean processing) {
        visualStallTicks = 0;
        boolean visualChanged = setVisualProcessing(processing);
        return finishServerUpdate(changed || visualChanged, processing);
    }

    private boolean setVisualProcessing(boolean processing) {
        if (visualProcessing == processing) {
            return false;
        }
        visualProcessing = processing;
        setChanged();
        return true;
    }

    private void prepareOutputs(ServerLevel serverLevel,
                                String signature) {
        pendingOutputs.clear();
        pendingSignature = signature;
        pendingOperationMultiplier = stackOperationMultiplier();
        pendingSourceCost = sourceCostPerOperation(
                pendingOperationMultiplier);
        pendingEnergyPerTick = stackScaledEnergyUsage(
                pendingOperationMultiplier);
        cachedProcessResult = null;
        progress = 0;
        progressRequired = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        baseProcessDuration()));

        List<ItemStack> possibleLoot = new ArrayList<>();
        int experience = 0;
        if (!signature.equals(cachedLootEntitySignature)) {
            rebuildCachedLootEntities(serverLevel, signature);
        }
        int entityCount = cachedLootEntities.size();
        var fakePlayer = ANFakePlayer.getPlayer(serverLevel);
        var damage = serverLevel.damageSources().playerAttack(fakePlayer);
        for (LivingEntity entity : cachedLootEntities) {
            possibleLoot.addAll(lootFor(
                    serverLevel, entity, fakePlayer, damage));
            experience += Math.max(0,
                    entity.getExperienceReward(serverLevel, fakePlayer));
        }
        DrygmyLootPolicy.filterCandidates(possibleLoot);
        int operationMultiplier = pendingOperationMultiplier;
        List<ItemStack> fixedOutputs =
                DrygmyLootPolicy.extractFixedOutputs(
                        possibleLoot, cachedLootEntities,
                        operationMultiplier);
        addExperienceGems(experience / 4);
        if (!possibleLoot.isEmpty()) {
            int bonus = cachedUniqueEntityTypeCount
                    * Config.DRYGMY_UNIQUE_BONUS.get()
                    + Math.min(Config.DRYGMY_QUANTITY_CAP.get(),
                    entityCount);
            int targetItems = Math.max(1,
                    Config.DRYGMY_BASE_ITEM.get() + bonus);
            long produced = 0;
            int selectionRolls = Math.min(
                    targetItems, MAX_LOOT_SELECTION_ROLLS);
            for (int roll = 0;
                 roll < selectionRolls && produced < targetItems;
                 roll++) {
                ItemStack selected = possibleLoot.get(
                        serverLevel.random.nextInt(possibleLoot.size()))
                        .copy();
                if (selected.isEmpty()) {
                    continue;
                }
                long remaining = targetItems - produced;
                if (roll + 1 == selectionRolls
                        && selected.getCount() < remaining) {
                    selected.setCount((int) remaining);
                }
                pendingOutputs.add(selected);
                produced += selected.getCount();
            }
        }
        scalePendingOutputs(operationMultiplier);
        // Fixed custom-death drops already represent the stack-operation
        // count and must not be multiplied with ordinary selected loot again.
        pendingOutputs.addAll(fixedOutputs);
    }

    private void rebuildCachedLootEntities(
            ServerLevel serverLevel, String signature) {
        clearCachedLootEntities();
        Set<EntityType<?>> uniqueTypes = new HashSet<>();
        for (BasicInventorySlot slot : jarSlots) {
            Optional<CapturedEntity> captured =
                    EntityContainerRegistry.capturedEntity(
                            slot.getStack());
            if (captured.isEmpty()) {
                continue;
            }
            LivingEntity entity =
                    createLivingEntity(serverLevel, captured.get());
            if (entity == null
                    || entity.getType().is(EntityTags.DRYGMY_BLACKLIST)) {
                if (entity != null) {
                    entity.discard();
                }
                continue;
            }
            cachedLootEntities.add(entity);
            uniqueTypes.add(entity.getType());
        }
        cachedUniqueEntityTypeCount = uniqueTypes.size();
        cachedLootEntitySignature = signature;
    }

    private void clearCachedLootEntities() {
        for (LivingEntity entity : cachedLootEntities) {
            entity.discard();
        }
        cachedLootEntities.clear();
        cachedLootEntitySignature = "";
        cachedUniqueEntityTypeCount = 0;
    }

    private void scalePendingOutputs(int multiplier) {
        if (multiplier <= 1) {
            return;
        }
        for (ItemStack output : pendingOutputs) {
            long count = (long) output.getCount() * multiplier;
            output.setCount((int) Math.min(Integer.MAX_VALUE,
                    Math.max(1L, count)));
        }
    }

    private List<ItemStack> lootFor(
            ServerLevel level, LivingEntity entity,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.damagesource.DamageSource damage) {
        LootTable lootTable = level.getServer()
                .reloadableRegistries()
                .getLootTable(entity.getLootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN,
                        entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,
                        damage)
                .withOptionalParameter(
                        LootContextParams.ATTACKING_ENTITY, player)
                .withOptionalParameter(
                        LootContextParams.DIRECT_ATTACKING_ENTITY,
                        damage.getDirectEntity())
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER,
                        player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);
        return List.copyOf(lootTable.getRandomItems(params));
    }

    private LivingEntity createLivingEntity(
            ServerLevel level, CapturedEntity captured) {
        CompoundTag tag = captured.entityData();
        tag.putString("id", captured.entityId().toString());
        Entity entity = EntityType.create(tag, level).orElse(null);
        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        living.setPos(worldPosition.getCenter());
        return living;
    }

    private void addExperienceGems(int experience) {
        if (experience <= 3) {
            return;
        }
        int greater = experience / 12;
        int remainder = experience - greater * 12;
        int normal = Math.ceilDiv(remainder, 3);
        if (greater > 0) {
            pendingOutputs.add(new ItemStack(
                    ItemsRegistry.GREATER_EXPERIENCE_GEM.get(),
                    greater));
        }
        if (normal > 0) {
            pendingOutputs.add(new ItemStack(
                    ItemsRegistry.EXPERIENCE_GEM.get(), normal));
        }
    }

    private MachineRecipeResult processResult() {
        int multiplier = Math.max(1, pendingOperationMultiplier);
        int sourceCost = Math.max(0, pendingSourceCost);
        if (cachedProcessResult == null
                || cachedProcessMultiplier != multiplier
                || cachedProcessSourceCost != sourceCost) {
            cachedProcessMultiplier = multiplier;
            cachedProcessSourceCost = sourceCost;
            cachedProcessResult = new MachineRecipeResult(
                    PROCESS_ID, ItemStack.EMPTY, baseProcessDuration(),
                    List.of(), -1, -1, null, null,
                    Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                            sourceCost));
        }
        return cachedProcessResult;
    }

    /**
     * A stack upgrade represents multiple Drygmy harvests completed by one
     * machine operation. Keep Source consumption proportional to the scaled
     * loot and FE usage instead of charging only one vanilla harvest.
     */
    private int sourceCostPerOperation() {
        return sourceCostPerOperation(stackOperationMultiplier());
    }

    private static int sourceCostPerOperation(int multiplier) {
        long baseCost = Math.max(0, Config.DRYGMY_MANA_COST.get());
        return (int) Math.min(Integer.MAX_VALUE,
                baseCost * multiplier);
    }

    private static int baseProcessDuration() {
        return Math.max(100,
                Config.DRYGMY_MAX_PROGRESS.get() * 20);
    }

    private String jarSignature() {
        if (!jarSignatureDirty) {
            return cachedJarSignature;
        }
        StringBuilder builder = new StringBuilder();
        for (BasicInventorySlot slot : jarSlots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()
                    && EntityContainerRegistry.isFilled(stack)) {
                builder.append(stack.getItemHolder())
                        .append(':')
                        .append(stack.getComponentsPatch())
                        .append('|');
            }
        }
        cachedJarSignature = builder.toString();
        jarSignatureDirty = false;
        return cachedJarSignature;
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
        if (!outputTransferDirty
                && gameTime < nextOutputTransferGameTime) {
            return false;
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
            int chunk = (int) Math.min(
                    Integer.MAX_VALUE, buffered.count);
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

    private boolean flushBufferedOutputs() {
        for (int index = 0; index < bufferedOutputs.size();) {
            BufferedOutput buffered = bufferedOutputs.get(index);
            long moved = insertLongIntoOutputs(
                    buffered.stack, buffered.count);
            buffered.count -= moved;
            if (buffered.count <= 0) {
                bufferedOutputs.remove(index);
            } else {
                index++;
            }
        }
        return bufferedOutputs.isEmpty();
    }

    private long insertLongIntoOutputs(ItemStack template, long amount) {
        long remaining = amount;
        int limit = currentOutputLimit(template);
        for (BasicInventorySlot slot : outputSlots) {
            if (remaining <= 0) {
                break;
            }
            ItemStack existing = slot.getStack();
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(
                    existing, template)) {
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
        for (BasicInventorySlot slot : outputSlots) {
            if (remaining <= 0) {
                break;
            }
            if (!slot.getStack().isEmpty()) {
                continue;
            }
            int moved = (int) Math.min(remaining, limit);
            slot.setStack(template.copyWithCount(moved));
            remaining -= moved;
        }
        return amount - remaining;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    private void resetProcess() {
        pendingOutputs.clear();
        pendingSignature = "";
        clearCurrentPendingOperationContext();
        clearDeferredPendingState();
        nextLootPreparationGameTime = Long.MIN_VALUE;
        progress = 0;
        progressRequired = 1;
    }

    @Override
    protected void saveArsMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveArsMachineData(tag, registries);
        net.minecraft.nbt.ListTag outputs = new net.minecraft.nbt.ListTag();
        for (BufferedOutput buffered : bufferedOutputs) {
            CompoundTag entry = new CompoundTag();
            entry.put("stack", buffered.stack.save(registries));
            entry.putLong("count", buffered.count);
            outputs.add(entry);
        }
        if (!outputs.isEmpty()) {
            tag.put("drygmy_long_buffer", outputs);
        }
        List<ItemStack> pendingToSave = deferredPendingOutputs.isEmpty()
                ? pendingOutputs : deferredPendingOutputs;
        String signatureToSave = deferredPendingOutputs.isEmpty()
                ? pendingSignature : deferredPendingSignature;
        int multiplierToSave = deferredPendingOutputs.isEmpty()
                ? pendingOperationMultiplier
                : deferredPendingOperationMultiplier;
        int sourceCostToSave = deferredPendingOutputs.isEmpty()
                ? pendingSourceCost : deferredPendingSourceCost;
        long energyToSave = deferredPendingOutputs.isEmpty()
                ? pendingEnergyPerTick : deferredPendingEnergyPerTick;
        net.minecraft.nbt.ListTag pending = new net.minecraft.nbt.ListTag();
        for (ItemStack output : pendingToSave) {
            if (!output.isEmpty()) {
                pending.add(saveCountedItemStack(output, registries));
            }
        }
        if (!pending.isEmpty() && !signatureToSave.isEmpty()) {
            tag.put(PENDING_OUTPUTS_NBT, pending);
            tag.putString(PENDING_SIGNATURE_NBT, signatureToSave);
            tag.putInt(PENDING_MULTIPLIER_NBT,
                    Math.max(1, multiplierToSave));
            tag.putInt(PENDING_SOURCE_COST_NBT,
                    Math.max(0, sourceCostToSave));
            tag.putLong(PENDING_ENERGY_NBT,
                    Math.max(1L, energyToSave));
            if (!deferredPendingOutputs.isEmpty()) {
                // NativeMagicMachineBlockEntity wrote the common progress
                // fields before invoking this hook, so replace them with the
                // deferred values if inventory restoration reset the fields.
                tag.putInt("magic_progress", deferredPendingProgress);
                tag.putInt("magic_progress_required",
                        deferredPendingProgressRequired);
            }
        }
        tag.putBoolean(VISUAL_PROCESSING_NBT, visualProcessing);
    }

    @Override
    protected void loadArsMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.loadArsMachineData(tag, registries);
        clearCachedLootEntities();
        bufferedOutputs.clear();
        net.minecraft.nbt.ListTag outputs = tag.getList(
                "drygmy_long_buffer", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < outputs.size(); index++) {
            CompoundTag entry = outputs.getCompound(index);
            ItemStack stack = ItemStack.parseOptional(
                    registries, entry.getCompound("stack"));
            long count = entry.getLong("count");
            if (!stack.isEmpty() && count > 0) {
                bufferedOutputs.add(new BufferedOutput(stack, count));
            }
        }
        pendingOutputs.clear();
        pendingSignature = "";
        clearDeferredPendingState();
        deferredPendingSignature = tag.getString(PENDING_SIGNATURE_NBT);
        deferredPendingOperationMultiplier = tag.contains(
                PENDING_MULTIPLIER_NBT, net.minecraft.nbt.Tag.TAG_INT)
                ? Math.max(1, tag.getInt(PENDING_MULTIPLIER_NBT))
                : stackOperationMultiplier();
        deferredPendingSourceCost = tag.contains(
                PENDING_SOURCE_COST_NBT, net.minecraft.nbt.Tag.TAG_INT)
                ? Math.max(0, tag.getInt(PENDING_SOURCE_COST_NBT))
                : sourceCostPerOperation(
                        deferredPendingOperationMultiplier);
        deferredPendingEnergyPerTick = tag.contains(
                PENDING_ENERGY_NBT, net.minecraft.nbt.Tag.TAG_LONG)
                ? Math.max(1L, tag.getLong(PENDING_ENERGY_NBT))
                : stackScaledEnergyUsage(
                        deferredPendingOperationMultiplier);
        net.minecraft.nbt.ListTag pending = tag.getList(
                PENDING_OUTPUTS_NBT, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < pending.size(); index++) {
            ItemStack stack = loadCountedItemStack(
                    pending.getCompound(index), registries);
            if (!stack.isEmpty()) {
                deferredPendingOutputs.add(stack);
            }
        }
        if (deferredPendingSignature.isEmpty()
                || deferredPendingOutputs.isEmpty()) {
            clearDeferredPendingState();
            progress = 0;
        } else {
            deferredPendingProgress = Math.max(0, progress);
            deferredPendingProgressRequired = Math.max(1,
                    progressRequired);
        }
        normalizeVisibleOutputSlots();
        outputTransferDirty = !bufferedOutputs.isEmpty();
        aeBackpressured = !bufferedOutputs.isEmpty();
        nextAeBatchGameTime = Long.MIN_VALUE;
        visualProcessing = tag.getBoolean(VISUAL_PROCESSING_NBT);
        visualStallTicks = 0;
        cachedJarSignature = "";
        jarSignatureDirty = true;
        nextLootPreparationGameTime = Long.MIN_VALUE;
    }

    private void restoreDeferredPendingState() {
        if (deferredPendingOutputs.isEmpty()) {
            return;
        }
        String restoredJarSignature = jarSignature();
        boolean restored = deferredPendingSignature.equals(
                restoredJarSignature);
        if (restored) {
            pendingOutputs.clear();
            deferredPendingOutputs.stream()
                    .map(ItemStack::copy)
                    .forEach(pendingOutputs::add);
            pendingSignature = deferredPendingSignature;
            pendingOperationMultiplier =
                    deferredPendingOperationMultiplier;
            pendingSourceCost = deferredPendingSourceCost;
            pendingEnergyPerTick = deferredPendingEnergyPerTick;
            progress = deferredPendingProgress;
            progressRequired = deferredPendingProgressRequired;
            cachedProcessResult = null;
            refreshSourceLimits();
        }
        clearDeferredPendingState();
        if (!restored) {
            refreshSourceLimits();
        }
    }

    private void clearDeferredPendingState() {
        deferredPendingOutputs.clear();
        deferredPendingSignature = "";
        deferredPendingProgress = 0;
        deferredPendingProgressRequired = 1;
        deferredPendingOperationMultiplier = 1;
        deferredPendingSourceCost = 0;
        deferredPendingEnergyPerTick = 0L;
    }

    private void clearCurrentPendingOperationContext() {
        pendingOperationMultiplier = 1;
        pendingSourceCost = 0;
        pendingEnergyPerTick = 0L;
        cachedProcessResult = null;
        cachedProcessMultiplier = -1;
        cachedProcessSourceCost = -1;
    }

    @Override
    public CompoundTag getReducedUpdateTag(
            net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = super.getReducedUpdateTag(provider);
        tag.putBoolean(VISUAL_PROCESSING_NBT, visualProcessing);
        return tag;
    }

    @Override
    public void handleUpdateTag(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        visualProcessing = tag.getBoolean(VISUAL_PROCESSING_NBT);
    }

    @Override
    protected void onNativeUpgradeChanged(mekanism.api.Upgrade upgrade) {
        super.onNativeUpgradeChanged(upgrade);
        mekanism.api.Upgrade stackUpgrade =
                NativeMekanismRegistries.dimensionMinerStackUpgrade();
        if (stackUpgrade != null && upgrade == stackUpgrade) {
            cachedProcessResult = null;
            cachedProcessMultiplier = -1;
            cachedProcessSourceCost = -1;
            refreshSourceLimits();
            // Preserve the already rolled loot and elapsed progress. The new
            // stack multiplier is intentionally applied to the next harvest;
            // clearing here made upgrade insertion/removal a reroll button.
        }
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
                mekanism.common.util.MekanismUtils.getTicks(
                        this, baseProcessDuration()));
        progress = (int) Math.min(newRequired - 1L,
                (long) Math.max(0, progress) * newRequired / oldRequired);
        progressRequired = newRequired;
        pendingEnergyPerTick = stackScaledEnergyUsage(
                pendingOperationMultiplier);
    }

    @Override
    protected void onArsMachineLoaded() {
        refreshSourceLimits();
        jarSignatureDirty = true;
    }

    @Override
    protected void onNativeMachineRemoved() {
        super.onNativeMachineRemoved();
        clearCachedLootEntities();
    }

    @Override
    protected void onNativeMachineRevived() {
        super.onNativeMachineRevived();
        jarSignatureDirty = true;
    }

    @Override
    protected int sourceCapacity() {
        // SourceStorage is deserialized before every upgrade lifecycle is
        // guaranteed to have settled. Start at the supported maximum so a
        // stacked machine never clamps away saved Source during world load;
        // onArsMachineLoaded immediately applies its actual tier.
        return maximumScaledSourceValue(
                ArsNouveauMachineConfig.SOURCE_CAPACITY);
    }

    @Override
    protected int sourceMaxReceive() {
        return maximumScaledSourceValue(
                ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE);
    }

    @Override
    protected int sourceMaxExtract() {
        return maximumScaledSourceValue(
                ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE);
    }

    @Override
    public int getTransferRate() {
        return scaledSourceValue(
                ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE);
    }

    @Override
    public int getMaxSource() {
        // Upgrade counts reach the client through Mekanism's component sync,
        // which does not invoke the server recalculation hook there. Derive
        // the displayed limit so the Source bar updates immediately.
        return Math.max(Math.max(scaledSourceValue(
                ArsNouveauMachineConfig.SOURCE_CAPACITY), getSource()),
                pendingSourceCapacityFloor());
    }

    private void refreshSourceLimits() {
        // The client is constructed with the maximum supported capacity so
        // container synchronization cannot clamp a large saved value before
        // the stack-upgrade tracker arrives. The authoritative server sends
        // the actual display capacity separately.
        if (level == null || level.isClientSide()) {
            return;
        }
        var storage = getSourceStorage();
        int capacity = Math.max(scaledSourceValue(
                ArsNouveauMachineConfig.SOURCE_CAPACITY),
                pendingSourceCapacityFloor());
        storage.setMaxSource(capacity);
        storage.setMaxReceive(getTransferRate());
        storage.setMaxExtract(getTransferRate());
        if (storage.getSource() > capacity) {
            storage.setSource(capacity);
        }
    }

    private int scaledSourceValue(int base) {
        long scaled = (long) Math.max(1, base)
                * stackOperationMultiplier();
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    private int pendingSourceCapacityFloor() {
        return Math.max(pendingSourceCost,
                deferredPendingSourceCost);
    }

    private static int maximumScaledSourceValue(int base) {
        long scaled = (long) Math.max(1, base)
                * MAX_STACK_OPERATION_MULTIPLIER;
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    private int stackOperationMultiplier() {
        mekanism.api.Upgrade stackUpgrade =
                NativeMekanismRegistries.dimensionMinerStackUpgrade();
        if (stackUpgrade == null || upgradeComponent == null) {
            return 1;
        }
        int upgrades = Math.max(0,
                upgradeComponent.getUpgrades(stackUpgrade));
        return Math.min(MAX_STACK_OPERATION_MULTIPLIER,
                1 << Math.min(upgrades, 8));
    }

    private long stackScaledEnergyUsage(int operationMultiplier) {
        long base = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
        int multiplier = Math.max(1, operationMultiplier);
        long scaled = base > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : base * multiplier;
        return energyContainer == null
                ? scaled
                : Math.min(scaled, energyContainer.getMaxEnergy());
    }

    private static int currentOutputLimit(ItemStack stack) {
        return stack.isEmpty() ? 64 : Math.max(1, stack.getMaxStackSize());
    }

    private void normalizeVisibleOutputSlots() {
        for (BasicInventorySlot slot : outputSlots) {
            ItemStack stack = slot.getStack();
            int limit = currentOutputLimit(stack);
            if (stack.isEmpty() || stack.getCount() <= limit) {
                continue;
            }
            long excess = (long) stack.getCount() - limit;
            slot.setStack(stack.copyWithCount(limit));
            mergeBufferedOutput(stack, excess);
        }
    }

    @Override
    protected boolean shouldEjectOutputs() {
        long gameTime = level == null
                ? Long.MAX_VALUE : level.getGameTime();
        return ejectRetryPending
                || gameTime >= nextNativeEjectGameTime
                || outputBufferNeedsEject();
    }

    @Override
    protected boolean useFastEjectPath() {
        return true;
    }

    private boolean outputBufferNeedsEject() {
        for (BasicInventorySlot slot : outputSlots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()
                    && slot.getCount() >= currentOutputLimit(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onFastEjectFinished(
            boolean outputRemaining,
            boolean continueImmediately) {
        ejectRetryPending = outputRemaining && continueImmediately;
        nextNativeEjectGameTime = (level == null
                ? 0 : level.getGameTime()) + EJECT_INTERVAL_TICKS;
    }

    int seedDevelopmentTest(ServerLevel serverLevel,
                            ResourceLocation entityId) {
        EntityType<?> type =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getOptional(entityId)
                        .orElse(null);
        if (type == null) {
            return 0;
        }
        CompoundTag entityTag = new CompoundTag();
        entityTag.putString("id", entityId.toString());
        ItemStack jar = new ItemStack(BlockRegistry.MOB_JAR.get());
        jar.set(DataComponentRegistry.MOB_JAR.get(),
                new MobJarData(entityTag, null));
        jarSlots.getFirst().setStack(jar);
        setSource(getMaxSource());
        if (energyContainer != null) {
            energyContainer.setEnergy(energyContainer.getMaxEnergy());
        }
        prepareOutputs(serverLevel, jarSignature());
        if (pendingOutputs.isEmpty()) {
            return 0;
        }
        progress = Math.max(0, progressRequired - 1);
        return pendingOutputs.stream()
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    int developmentOutputCount() {
        return outputSlots.stream()
                .map(IInventorySlot::getStack)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    List<ItemStack> developmentPendingOutputs() {
        return pendingOutputs.stream().map(ItemStack::copy).toList();
    }

    String developmentPendingSignature() {
        return pendingSignature;
    }

    int developmentPendingOperationMultiplier() {
        return pendingOperationMultiplier;
    }

    long developmentPendingEnergyPerTick() {
        return pendingEnergyPerTick;
    }

    int developmentSourceCost() {
        return pendingOutputs.isEmpty() ? sourceCostPerOperation()
                : pendingSourceCost;
    }

    boolean developmentHasCreativeSourceUpgrade() {
        return hasCreativeSourceUpgrade();
    }

    private static final class PersistentJarSlot
            extends BasicInventorySlot {
        private PersistentJarSlot(IContentsListener listener,
                                  int x, int y) {
            super((stack, automation) ->
                            automation == AutomationType.MANUAL,
                    (stack, automation) -> true,
                    EntityContainerRegistry::isFilled,
                    listener, x, y);
            setSlotType(ContainerSlotType.INPUT);
        }
    }

    private static final class DrygmyOutputSlot
            extends BasicInventorySlot {
        private DrygmyOutputSlot(IContentsListener listener,
                                 int x, int y) {
            super(BASE_OUTPUT_LIMIT,
                    (stack, automation) -> true,
                    (stack, automation) ->
                            automation == AutomationType.INTERNAL
                                    || automation == AutomationType.EXTERNAL,
                    stack -> true, listener, x, y);
            setSlotType(ContainerSlotType.OUTPUT);
        }

        @Override
        public int getLimit(ItemStack stack) {
            return currentOutputLimit(stack);
        }
    }

    private static final class BufferedOutput {
        private final ItemStack stack;
        private long count;

        private BufferedOutput(ItemStack stack, long count) {
            this.stack = stack.copyWithCount(1);
            this.count = count;
        }
    }
}
