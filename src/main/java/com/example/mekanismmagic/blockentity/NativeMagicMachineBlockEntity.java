package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.IContentsListener;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Native Mekanism machine foundation. Concrete machines only provide recipe
 * processing; side configuration, energy capability, ejector and upgrade
 * components are owned by this base.
 */
public abstract class NativeMagicMachineBlockEntity extends TileEntityMekanism
        implements ISideConfiguration, IUpgradeTile,
        IMekanismMagicAutomation {
    private static final int FAST_EJECT_BACKOFF_TICKS = 10;
    private static final int RECIPE_LOOKUP_BACKOFF_TICKS = 5;
    public static final int INPUT_SLOTS = 16;
    public static final int OUTPUT_SLOT = 16;
    public static final int CONTAINMENT_SLOT = 17;
    public static final int RITUAL_SLOT = 18;
    public static final int ACTIVATION_SLOT = 19;
    public static final int SACRIFICE_SLOT = 20;
    public static final int CHALK_SLOT_START = 21;
    public static final int DICTIONARY_SLOT = CHALK_SLOT_START;
    public static final int CHALK_SLOT_COUNT = 16;
    public static final int CATALYST_LIBRARY_SLOT_START = 43;
    public static final int CATALYST_LIBRARY_SLOT_COUNT = 30;
    public static final int MACHINE_INVENTORY_SIZE =
            CATALYST_LIBRARY_SLOT_START + CATALYST_LIBRARY_SLOT_COUNT;
    protected TileComponentConfig configComponent;
    protected TileComponentEjector ejectorComponent;
    protected MachineEnergyContainer<? extends NativeMagicMachineBlockEntity> energyContainer;
    protected InputInventorySlot inputSlot;
    protected IInventorySlot outputSlot;
    protected EnergyInventorySlot energySlot;
    protected BasicInventorySlot containmentSlot;
    private Map<Integer, IInventorySlot> logicalSlots;
    private List<IInventorySlot> configuredInputSlots;
    // Populated while TileEntityMekanism's constructor builds the inventory.
    // Do not use a field initializer here: it would run afterwards and erase
    // the output slots registered during the superclass construction.
    private List<IInventorySlot> configuredOutputSlots;
    private final EnumMap<Direction,
            BlockCapabilityCache<IItemHandler, @Nullable Direction>>
            fastEjectTargets = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> fastEjectBackoff =
            new EnumMap<>(Direction.class);
    protected int progress;
    protected int progressRequired = 1;
    protected String activeRecipe = "";
    private int recipeLookupBackoff;
    private long lastRecipeInputFingerprint = Long.MIN_VALUE;

    protected NativeMagicMachineBlockEntity(Holder<Block> block, BlockPos pos, BlockState state) {
        super(block, pos, state);
    }

    @Override
    protected void presetVariables() {
        super.presetVariables();
        configComponent = new TileComponentConfig(this,
                EnumSet.of(TransmissionType.ITEM, TransmissionType.ENERGY));
        ejectorComponent = new TileComponentEjector(this)
                .setCanEject(type -> level instanceof ServerLevel)
                .setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper helper = EnergyContainerHelper.forSideWithConfig(this);
        energyContainer = MachineEnergyContainer.input(this, listener);
        helper.addContainer(energyContainer);
        var energyConfig = configComponent.setupInputConfig(
                TransmissionType.ENERGY, energyContainer);
        if (energyConfig != null) {
            for (RelativeSide side : RelativeSide.values()) {
                energyConfig.setDataType(DataType.INPUT, side);
            }
        }
        return helper.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper helper = InventorySlotHelper.forSideWithConfig(this);
        energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, () -> level,
                listener, energySlotX(), energySlotY());
        helper.addSlot(energySlot);
        createMachineSlots(helper, listener);
        return helper.build();
    }

    protected int energySlotX() {
        return 64;
    }

    protected int energySlotY() {
        return 53;
    }

    protected abstract void createMachineSlots(InventorySlotHelper helper, IContentsListener listener);

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> progress,
                value -> progress = value));
        container.track(SyncableInt.create(() -> progressRequired,
                value -> progressRequired = Math.max(1, value)));
    }

    protected final ConfigInfo setupNativeItemIO(
            List<? extends IInventorySlot> inputs,
            List<? extends IInventorySlot> outputs,
            List<? extends IInventorySlot> extras) {
        List<IInventorySlot> inputSlots = new java.util.ArrayList<>(inputs);
        List<IInventorySlot> outputSlots = new java.util.ArrayList<>(outputs);
        configuredInputSlots = List.copyOf(inputSlots);
        configuredOutputSlots = List.copyOf(outputSlots);
        var itemConfig = configComponent.setupItemIOConfig(
                inputSlots, outputSlots, energySlot, false);
        addNativeItemSlotInfo(itemConfig, DataType.EXTRA,
                true, true, extras);
        // Match Mekanism's normal machine behavior for newly placed machines:
        // every item side exposes the configured inventory and automatic
        // ejection is enabled. Players can still change these modes through
        // the standard side-configuration window.
        for (RelativeSide side : RelativeSide.values()) {
            itemConfig.setDataType(DataType.INPUT_OUTPUT, side);
        }
        itemConfig.setEjecting(true);
        return itemConfig;
    }

    protected final void addNativeItemSlotInfo(
            ConfigInfo itemConfig, DataType type,
            boolean canInput, boolean canOutput,
            List<? extends IInventorySlot> slots) {
        if (itemConfig != null && !slots.isEmpty()) {
            itemConfig.addSlotInfo(type,
                    new InventorySlotInfo(canInput, canOutput,
                            new java.util.ArrayList<>(slots)));
        }
    }

    protected final <SLOT extends IInventorySlot> SLOT registerLogicalSlot(
            InventorySlotHelper helper, int index, SLOT slot) {
        if (logicalSlots == null) {
            logicalSlots = new HashMap<>();
        }
        logicalSlots.put(index, slot);
        helper.addSlot(slot);
        return slot;
    }

    protected final Map<Integer, IInventorySlot> logicalSlots() {
        return logicalSlots;
    }

    protected abstract Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory);

    /**
     * Avoid repeatedly walking large Occultism recipe lists while a machine is
     * completely empty. Concrete machines can further narrow this predicate
     * when they have non-inventory recipe state.
     */
    protected boolean shouldAttemptRecipeLookup(ItemStackHandler inventory) {
        for (int slot = 0; slot < MACHINE_INVENTORY_SIZE; slot++) {
            if (slot != OUTPUT_SLOT
                    && !inventory.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    protected abstract int baseEnergyPerTick();

    protected long energyUsagePerTick(MachineRecipeResult recipe) {
        return mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
    }

    protected final boolean hasEnergyForRecipe(MachineRecipeResult recipe) {
        return hasEnergyForRecipe(recipe, energyUsagePerTick(recipe));
    }

    protected final boolean hasEnergyForRecipe(MachineRecipeResult recipe,
                                               long usage) {
        return energyContainer != null
                && energyContainer.getEnergy() >= Math.max(0, usage);
    }

    protected boolean canRunWithoutEnergy(MachineRecipeResult recipe) {
        return false;
    }

    protected int energylessTickInterval(MachineRecipeResult recipe) {
        return 5;
    }

    protected final boolean isEnergylessTick(MachineRecipeResult recipe) {
        int interval = Math.max(1, energylessTickInterval(recipe));
        return level != null && level.getGameTime() % interval == 0;
    }

    protected boolean hasRecipeResources(MachineRecipeResult recipe) {
        return true;
    }

    protected boolean consumeRecipeResources(MachineRecipeResult recipe) {
        return true;
    }

    /**
     * Runs only Mekanism's common tile update. Specialized machines with a
     * different processing shape (for example multi-output miners) can use
     * this without invoking the default single-output recipe loop again.
     */
    protected final boolean nativeBaseUpdate() {
        return updateNativeBase();
    }

    protected ItemStack getSpiritSourceForUpgrade() {
        return ItemStack.EMPTY;
    }

    protected void setSpiritSourceFromUpgrade(ItemStack source) {
    }

    public final TileComponentConfig getConfig() {
        return configComponent;
    }

    public final TileComponentEjector getEjector() {
        return ejectorComponent;
    }

    public final TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    public final MachineEnergyContainer<? extends NativeMagicMachineBlockEntity> getNativeEnergyContainer() {
        return energyContainer;
    }

    public final IInventorySlot getNativeInputSlot() {
        return inputSlot;
    }

    public final IInventorySlot getNativeOutputSlot() {
        return outputSlot;
    }

    public final EnergyInventorySlot getNativeEnergySlot() {
        return energySlot;
    }

    @Override
    public net.minecraft.resources.ResourceLocation mekanismMagicMachineId() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                getBlockState().getBlock());
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternInputs() {
        return configuredInputSlots == null ? List.of()
                : configuredInputSlots;
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternOutputs() {
        return configuredOutputSlots == null ? List.of()
                : configuredOutputSlots;
    }

    @Override
    public IEnergyContainer mekanismMagicEnergyContainer() {
        return energyContainer;
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        return progress > 0;
    }

    @Override
    public void recalculateUpgrades(Upgrade upgrade) {
        if (energyContainer == null) {
            return;
        }
        if (upgrade == Upgrade.SPEED) {
            energyContainer.updateEnergyPerTick();
        } else if (upgrade == Upgrade.ENERGY) {
            energyContainer.updateMaxEnergy();
            energyContainer.updateEnergyPerTick();
        }
        onNativeUpgradeChanged(upgrade);
    }

    @Override
    public mekanism.common.upgrade.IUpgradeData getUpgradeData(
            net.minecraft.core.HolderLookup.Provider registries) {
        return new SpiritMachineUpgradeData(registries, redstone, getControlType(),
                energyContainer, new int[]{progress}, energySlot,
                inputSlot == null ? List.of() : List.of(inputSlot),
                outputSlot == null ? List.of() : List.of(outputSlot),
                false, getComponents(), getSpiritSourceForUpgrade());
    }

    @Override
    public void parseUpgradeData(net.minecraft.core.HolderLookup.Provider registries,
                                 IUpgradeData data) {
        if (!(data instanceof MachineUpgradeData upgrade)) {
            super.parseUpgradeData(registries, data);
            return;
        }
        redstone = upgrade.redstone;
        setControlType(upgrade.controlType);
        if (energyContainer != null && upgrade.energyContainer != null) {
            energyContainer.setEnergy(upgrade.energyContainer.getEnergy());
        }
        if (energySlot != null && upgrade.energySlot != null) {
            energySlot.deserializeNBT(registries,
                    upgrade.energySlot.serializeNBT(registries));
        }
        if (inputSlot != null && !upgrade.inputSlots.isEmpty()) {
            inputSlot.deserializeNBT(registries,
                    upgrade.inputSlots.getFirst().serializeNBT(registries));
        }
        if (outputSlot != null && !upgrade.outputSlots.isEmpty()) {
            outputSlot.setStack(upgrade.outputSlots.getFirst().getStack());
        }
        for (mekanism.common.tile.component.ITileComponent component : getComponents()) {
            component.read(upgrade.components, registries);
        }
        if (upgrade instanceof SpiritMachineUpgradeData spiritUpgrade) {
            setSpiritSourceFromUpgrade(spiritUpgrade.spiritSource);
        }
    }

    protected void onNativeUpgradeChanged(Upgrade upgrade) {
    }

    public int getProgress() {
        return progress;
    }

    public int getProgressRequired() {
        return progressRequired;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = updateNativeBase();
        setActive(false);
        if (level == null) {
            return changed;
        }
        if (!canFunction()) {
            return changed;
        }
        if (!hasAnyRecipeInput()) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
            recipeLookupBackoff = 0;
            lastRecipeInputFingerprint = Long.MIN_VALUE;
            return changed;
        }
        ItemStackHandler snapshot = snapshotInventory();
        if (!shouldAttemptRecipeLookup(snapshot)) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
            recipeLookupBackoff = 0;
            lastRecipeInputFingerprint = Long.MIN_VALUE;
            return changed;
        }
        long inputFingerprint = recipeInputFingerprint(snapshot);
        if (activeRecipe.isEmpty()
                && recipeLookupBackoff > 0
                && inputFingerprint == lastRecipeInputFingerprint) {
            recipeLookupBackoff--;
            return changed;
        }
        lastRecipeInputFingerprint = inputFingerprint;
        Optional<MachineRecipeResult> found = findRecipe(snapshot);
        if (found.isEmpty()) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
            recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
            return changed;
        }
        recipeLookupBackoff = 0;
        MachineRecipeResult recipe = found.get();
        progressRequired = Math.max(1, mekanism.common.util.MekanismUtils.getTicks(this, recipe.duration()));
        ItemStack output = snapshot.getStackInSlot(OUTPUT_SLOT);
        if (!recipe.output().isEmpty() && !output.isEmpty()
                && (!ItemStack.isSameItemSameComponents(output, recipe.output())
                || output.getCount() + recipe.output().getCount() > output.getMaxStackSize())) {
            recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
            return changed;
        }
        long usage = energyUsagePerTick(recipe);
        boolean powered = hasEnergyForRecipe(recipe, usage);
        boolean sourceOnly = !powered && canRunWithoutEnergy(recipe);
        if (!hasRecipeResources(recipe) || (!powered && !sourceOnly)) {
            recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
            return changed;
        }
        if (sourceOnly && !isEnergylessTick(recipe)) {
            return changed;
        }
        String recipeKey = recipeKey(recipe);
        if (!activeRecipe.equals(recipeKey)) {
            activeRecipe = recipeKey;
            progress = 0;
        }
        setActive(true);
        if (powered) {
            energyContainer.extract(usage, Action.EXECUTE,
                    AutomationType.INTERNAL);
        }
        progress++;
        if (progress >= progressRequired) {
            if (level instanceof ServerLevel serverLevel
                    && !recipe.complete(serverLevel, worldPosition)) {
                progress = 0;
                setActive(false);
                return changed;
            }
            if (!consumeSnapshot(snapshot, recipe)) {
                progress = 0;
                setActive(false);
                return changed;
            }
            if (!consumeRecipeResources(recipe)) {
                progress = 0;
                setActive(false);
                return changed;
            }
            if (!recipe.output().isEmpty()) {
                if (output.isEmpty()) {
                    snapshot.setStackInSlot(OUTPUT_SLOT, recipe.output().copy());
                } else {
                    output.grow(recipe.output().getCount());
                }
            }
            copyBack(snapshot);
            progress = 0;
            activeRecipe = "";
            onRecipeFinished(recipe);
            changed = true;
        }
        return changed;
    }

    private boolean updateNativeBase() {
        boolean changed = super.onUpdateServer();
        if (energySlot != null) {
            energySlot.fillContainerOrConvert();
        }
        if (ejectorComponent != null && level instanceof ServerLevel) {
            if (useFastEjectPath()) {
                if (hasEjectableItem() && shouldEjectOutputs()) {
                    // The dimensional miner keeps its dedicated batched
                    // output path. Logistical transporters still use the
                    // native colored transit request as a fallback.
                    boolean fallback = fastEjectItems();
                    onFastEjectFinished(hasEjectableItem(), fallback);
                    if (fallback) {
                        ejectorComponent.tickServer();
                    }
                }
            } else {
                // Match Mekanism's configurable-machine behavior for every
                // machine except the dimensional miner: one native ejector
                // tick per server tick, including when the inventory is empty.
                ejectorComponent.tickServer();
            }
        }
        return changed;
    }

    /**
     * Only the dimensional miner uses the custom long-buffer/batched output
     * path. All other machines retain Mekanism/Mekanism Extras ejection
     * timing and transport behavior.
     */
    protected boolean useFastEjectPath() {
        return false;
    }

    protected boolean shouldEjectOutputs() {
        return true;
    }

    protected void onFastEjectFinished(boolean outputRemaining,
                                       boolean nativeFallbackRequired) {
    }

    /**
     * Pushes one logical output stack directly into adjacent configured item
     * handlers. This is used by machines with a long logical output buffer so
     * successful output never needs to round-trip through their inventory.
     */
    protected ItemStack pushDirectlyToTargets(ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return stack;
        }
        ConfigInfo itemConfig = configComponent.getConfig(
                TransmissionType.ITEM);
        if (itemConfig == null || !itemConfig.isEjecting()) {
            return stack;
        }
        ItemStack remaining = stack;
        for (RelativeSide relativeSide : RelativeSide.values()) {
            if (remaining.isEmpty()) {
                break;
            }
            DataType mode = itemConfig.getDataType(relativeSide);
            if (mode == null || !mode.canOutput()) {
                continue;
            }
            Direction direction = relativeSide.getDirection(getDirection());
            BlockCapabilityCache<IItemHandler, @Nullable Direction> cache =
                    fastEjectTargets.computeIfAbsent(direction, side ->
                            BlockCapabilityCache.create(
                                    Capabilities.ItemHandler.BLOCK,
                                    serverLevel,
                                    worldPosition.relative(side),
                                    side.getOpposite(),
                                    () -> !isRemoved(),
                                    () -> fastEjectBackoff.remove(side)));
            IItemHandler target = cache.getCapability();
            if (target == null || target instanceof
                    mekanism.common.capabilities.item.CursedTransporterItemHandler) {
                continue;
            }
            int accepted = acceptedAmount(target, remaining);
            if (accepted <= 0) {
                continue;
            }
            ItemStack extracted = remaining.copyWithCount(accepted);
            ItemStack remainder = insertIntoTarget(target, extracted);
            remaining = remaining.copyWithCount(
                    remaining.getCount() - accepted + remainder.getCount());
        }
        return remaining;
    }

    /**
     * @return {@code true} when a Mekanism logistical transporter was found
     * and needs the native ejector fallback.
     */
    private boolean fastEjectItems() {
        ConfigInfo itemConfig = configComponent.getConfig(
                TransmissionType.ITEM);
        if (itemConfig == null || !itemConfig.isEjecting()
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        boolean needsNativeFallback = false;
        for (RelativeSide relativeSide : RelativeSide.values()) {
            DataType mode = itemConfig.getDataType(relativeSide);
            if (mode == null || !mode.canOutput()) {
                continue;
            }
            Direction direction = relativeSide.getDirection(getDirection());
            int backoff = fastEjectBackoff.getOrDefault(direction, 0);
            if (backoff > 0) {
                fastEjectBackoff.put(direction, backoff - 1);
                continue;
            }
            BlockCapabilityCache<IItemHandler, @Nullable Direction> cache =
                    fastEjectTargets.computeIfAbsent(direction, side ->
                            BlockCapabilityCache.create(
                                    Capabilities.ItemHandler.BLOCK,
                                    serverLevel,
                                    worldPosition.relative(side),
                                    side.getOpposite(),
                                    () -> !isRemoved(),
                                    () -> fastEjectBackoff.remove(side)));
            IItemHandler target = cache.getCapability();
            if (target == null) {
                fastEjectBackoff.put(direction,
                        FAST_EJECT_BACKOFF_TICKS);
                continue;
            }
            if (target instanceof mekanism.common.capabilities.item
                    .CursedTransporterItemHandler) {
                needsNativeFallback = true;
                continue;
            }
            boolean moved = moveBatchedIntoTarget(configuredOutputSlots, target);
            fastEjectBackoff.put(direction,
                    moved ? 0 : FAST_EJECT_BACKOFF_TICKS);
        }
        return needsNativeFallback;
    }

    private static boolean moveBatchedIntoTarget(
            List<IInventorySlot> sources, IItemHandler target) {
        List<OutputBatch> batches = new ArrayList<>();
        for (IInventorySlot source : sources) {
            ItemStack available = source.getStack();
            if (available.isEmpty()) {
                continue;
            }
            ItemStack simulated = source.extractItem(available.getCount(),
                    Action.SIMULATE, AutomationType.EXTERNAL);
            if (simulated.isEmpty()) {
                continue;
            }
            OutputBatch batch = null;
            for (OutputBatch candidate : batches) {
                if (ItemStack.isSameItemSameComponents(
                        candidate.stack, simulated)) {
                    batch = candidate;
                    break;
                }
            }
            if (batch == null) {
                batch = new OutputBatch(simulated.copy(), new ArrayList<>());
                batches.add(batch);
            }
            batch.sources.add(new OutputSource(source, simulated.getCount()));
            batch.count += simulated.getCount();
        }

        boolean moved = false;
        for (OutputBatch batch : batches) {
            ItemStack simulated = batch.stack.copy();
            simulated.setCount(batch.count);
            int remaining = acceptedAmount(target, simulated);
            if (remaining <= 0) {
                continue;
            }
            for (OutputSource source : batch.sources) {
                if (remaining <= 0) {
                    break;
                }
                int requested = Math.min(remaining, source.count);
                ItemStack extracted = source.slot.extractItem(requested,
                        Action.EXECUTE, AutomationType.EXTERNAL);
                if (extracted.isEmpty()) {
                    continue;
                }
                ItemStack remainder = insertIntoTarget(target, extracted);
                if (!remainder.isEmpty()) {
                    restoreToSource(source.slot, remainder);
                }
                int accepted = extracted.getCount() - remainder.getCount();
                remaining -= accepted;
                moved |= accepted > 0;
            }
        }
        return moved;
    }

    private static final class OutputBatch {
        private final ItemStack stack;
        private final List<OutputSource> sources;
        private int count;

        private OutputBatch(ItemStack stack, List<OutputSource> sources) {
            this.stack = stack;
            this.sources = sources;
        }
    }

    private record OutputSource(IInventorySlot slot, int count) {
    }

    private static int acceptedAmount(IItemHandler target, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < target.getSlots()
                && !remainder.isEmpty(); slot++) {
            remainder = target.insertItem(slot, remainder, true);
        }
        return stack.getCount() - remainder.getCount();
    }

    private static ItemStack insertIntoTarget(IItemHandler target,
                                              ItemStack stack) {
        ItemStack remainder = stack;
        for (int slot = 0; slot < target.getSlots()
                && !remainder.isEmpty(); slot++) {
            remainder = target.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    private static void restoreToSource(IInventorySlot source,
                                        ItemStack remainder) {
        ItemStack current = source.getStack();
        if (current.isEmpty()) {
            source.setStack(remainder);
        } else if (ItemStack.isSameItemSameComponents(current, remainder)) {
            current.grow(remainder.getCount());
            source.setStack(current);
        }
    }

    private boolean hasEjectableItem() {
        if (configuredOutputSlots == null) {
            return false;
        }
        for (IInventorySlot slot : configuredOutputSlots) {
            if (!slot.getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    protected void onRecipeFinished(MachineRecipeResult recipe) {
    }

    private ItemStackHandler snapshotInventory() {
        ItemStackHandler snapshot = new ItemStackHandler(MACHINE_INVENTORY_SIZE);
        if (logicalSlots != null) {
            logicalSlots.forEach((index, slot) -> snapshot.setStackInSlot(index, slot.getStack().copy()));
        }
        return snapshot;
    }

    private static long recipeInputFingerprint(ItemStackHandler inventory) {
        long fingerprint = 1;
        for (int slot = 0; slot < MACHINE_INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            fingerprint = 31 * fingerprint + slot;
            if (stack.isEmpty()) {
                continue;
            }
            fingerprint = 31 * fingerprint
                    + net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getId(stack.getItem());
            fingerprint = 31 * fingerprint + stack.getCount();
            fingerprint = 31 * fingerprint
                    + stack.getComponents().hashCode();
        }
        return fingerprint;
    }

    /**
     * Returns whether the machine has enough non-special input state to make a
     * recipe lookup worthwhile. Machines with a selector or other manual
     * control item can override this to avoid scanning while their real
     * processing inputs are empty.
     */
    protected boolean hasAnyRecipeInput() {
        if (logicalSlots == null || logicalSlots.isEmpty()) {
            return false;
        }
        for (Map.Entry<Integer, IInventorySlot> entry : logicalSlots.entrySet()) {
            if (entry.getKey() != OUTPUT_SLOT
                    && !entry.getValue().getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void copyBack(ItemStackHandler snapshot) {
        if (logicalSlots != null) {
            logicalSlots.forEach((index, slot) -> slot.setStack(snapshot.getStackInSlot(index).copy()));
        }
    }

    private boolean consumeSnapshot(ItemStackHandler snapshot,
                                    MachineRecipeResult recipe) {
        if (recipe.activationSlot() >= 0 && snapshot.getStackInSlot(recipe.activationSlot()).isEmpty()) {
            return false;
        }
        if (recipe.specialInputSlot() >= 0
                && !recipe.matchesSpecialInput(
                snapshot.getStackInSlot(recipe.specialInputSlot()))) {
            return false;
        }
        for (InputUse input : recipe.inputs()) {
            if (snapshot.getStackInSlot(input.slot()).getCount() < input.count()) {
                return false;
            }
        }
        for (InputUse input : recipe.inputs()) {
            snapshot.extractItem(input.slot(), input.count(), false);
        }
        if (recipe.activationSlot() >= 0) {
            snapshot.extractItem(recipe.activationSlot(), 1, false);
        }
        return recipe.consumeSpecialInput(snapshot);
    }

    private static String recipeKey(MachineRecipeResult recipe) {
        return recipe.id() + "|" + recipe.output().getCount() + "|"
                + recipe.duration() + "|" + recipe.inputs();
    }

    @Override
    public void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("magic_progress", progress);
        tag.putInt("magic_progress_required", progressRequired);
        tag.putString("magic_active_recipe", activeRecipe);
    }

    @Override
    public void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("magic_progress");
        progressRequired = Math.max(1, tag.getInt("magic_progress_required"));
        activeRecipe = tag.getString("magic_active_recipe");
    }
}
