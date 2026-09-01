package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IRecipeEntityDisplay;
import com.example.mekanismmagic.api.IRecipeItemDisplay;
import com.example.mekanismmagic.api.RecipeEntityDisplayState;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.integration.common.network.MachineDirectOutputHooks;
import com.example.mekanismmagic.integration.common.network
        .MachineDirectOutputHooks.DirectNetworkStatus;
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
import mekanism.common.inventory.container.sync.SyncableBoolean;
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
import java.util.IdentityHashMap;
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
        IMekanismMagicAutomation, IRecipeEntityDisplay,
        IRecipeItemDisplay {
    private static final int FAST_EJECT_BACKOFF_TICKS = 10;
    private static final int FAST_EJECT_REJECTED_BACKOFF_TICKS = 10;
    protected static final int MAX_DIRECT_PUSH_CALLS_PER_TICK = 256;
    protected static final int DIRECT_NETWORK_BATCH_INTERVAL_TICKS = 20;
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
    // Access declared by the machine's DataType configuration. The shared
    // top INPUT_OUTPUT port necessarily exposes several DataTypes at once,
    // so keep the original per-slot permissions here instead of widening an
    // input-only special slot into an externally extractable one.
    private Map<IInventorySlot, NativeSlotAccess> nativeSlotAccess;
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
    private boolean notEnoughEnergyWarning;
    private boolean reducedEnergyWarning;
    private boolean inputDoesntProduceOutputWarning;
    private DirectNetworkStatus syncedDirectNetworkStatus =
            DirectNetworkStatus.UNAVAILABLE;
    private int recipeLookupBackoff;
    private long lastRecipeInputFingerprint = Long.MIN_VALUE;
    /**
     * The immutable recipe description currently being processed. Keeping it
     * here avoids rebuilding a 73-slot snapshot and walking an optional mod's
     * complete recipe list on every working tick.
     */
    private MachineRecipeResult cachedActiveRecipe;
    private long cachedActiveRecipeFingerprint = Long.MIN_VALUE;
    private final RecipeEntityDisplayState recipeEntityDisplay =
            new RecipeEntityDisplayState();
    private final RecipeItemDisplayState recipeItemDisplay =
            new RecipeItemDisplayState();

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
        createMachineAddonSlots(helper, listener);
        completeNativeItemIOContract();
        DefaultMachineSideConfig.apply(configComponent);
        return helper.build();
    }

    /**
     * Repairs the most common omission in a newly added machine: registering
     * its slots but forgetting to create an item side configuration. Standard
     * Mekanism input/output slot types can be inferred safely. Custom slots
     * remain manual-only until the machine deliberately exposes them with
     * {@link #addNativeItemSlotInfo}; guessing permissions for a catalyst or
     * persistent container is more dangerous than leaving it unexposed.
     */
    private void completeNativeItemIOContract() {
        if (configuredInputSlots != null && configuredOutputSlots != null) {
            return;
        }
        List<IInventorySlot> inputs = new ArrayList<>();
        List<IInventorySlot> outputs = new ArrayList<>();
        List<IInventorySlot> extras = new ArrayList<>();
        if (logicalSlots != null) {
            for (IInventorySlot slot : logicalSlots.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue).toList()) {
                if (slot instanceof InputInventorySlot) {
                    inputs.add(slot);
                } else if (slot instanceof OutputInventorySlot) {
                    outputs.add(slot);
                } else {
                    extras.add(slot);
                }
            }
        }
        setupNativeItemIO(inputs, outputs, List.of());
        com.example.mekanismmagic.MekanismMagic.LOGGER.warn(
                "Machine {} omitted setupNativeItemIO; inferred {} input, "
                        + "{} output; left {} custom slots manual-only",
                getClass().getName(), inputs.size(), outputs.size(),
                extras.size());
    }

    protected int energySlotX() {
        return 64;
    }

    protected int energySlotY() {
        return 53;
    }

    protected abstract void createMachineSlots(InventorySlotHelper helper, IContentsListener listener);

    /** Optional slots supplied by integration-specific machine families. */
    protected void createMachineAddonSlots(InventorySlotHelper helper,
                                           IContentsListener listener) {
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> progress,
                value -> progress = value));
        container.track(SyncableInt.create(() -> progressRequired,
                value -> progressRequired = Math.max(1, value)));
        container.track(SyncableBoolean.create(
                () -> notEnoughEnergyWarning,
                value -> notEnoughEnergyWarning = value));
        container.track(SyncableBoolean.create(
                () -> reducedEnergyWarning,
                value -> reducedEnergyWarning = value));
        container.track(SyncableBoolean.create(
                () -> inputDoesntProduceOutputWarning,
                value -> inputDoesntProduceOutputWarning = value));
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            container.track(SyncableInt.create(
                    () -> MachineDirectOutputHooks.status(this).ordinal(),
                    value -> syncedDirectNetworkStatus =
                            DirectNetworkStatus.values()[Math.max(0,
                                    Math.min(DirectNetworkStatus.values()
                                            .length - 1, value))]));
        }
    }

    protected final ConfigInfo setupNativeItemIO(
            List<? extends IInventorySlot> inputs,
            List<? extends IInventorySlot> outputs,
            List<? extends IInventorySlot> extras) {
        List<IInventorySlot> inputSlots = new java.util.ArrayList<>(inputs);
        List<IInventorySlot> outputSlots = new java.util.ArrayList<>(outputs);
        configuredInputSlots = List.copyOf(inputSlots);
        configuredOutputSlots = List.copyOf(outputSlots);
        registerNativeSlotAccess(inputSlots, true, false);
        registerNativeSlotAccess(outputSlots, false, true);
        if (energySlot != null) {
            registerNativeSlotAccess(List.of(energySlot), true, true);
        }
        var itemConfig = configComponent.setupItemIOConfig(
                inputSlots, outputSlots, energySlot, false);
        addNativeItemSlotInfo(itemConfig, DataType.EXTRA,
                true, true, extras);
        return itemConfig;
    }

    protected final void addNativeItemSlotInfo(
            ConfigInfo itemConfig, DataType type,
            boolean canInput, boolean canOutput,
            List<? extends IInventorySlot> slots) {
        if (itemConfig != null && !slots.isEmpty()) {
            registerNativeSlotAccess(slots, canInput, canOutput);
            itemConfig.addSlotInfo(type,
                    new InventorySlotInfo(canInput, canOutput,
                            new java.util.ArrayList<>(slots)));
        }
    }

    private void registerNativeSlotAccess(
            List<? extends IInventorySlot> slots,
            boolean canInput, boolean canOutput) {
        if (slots.isEmpty()) {
            return;
        }
        if (nativeSlotAccess == null) {
            nativeSlotAccess = new IdentityHashMap<>();
        }
        for (IInventorySlot slot : slots) {
            nativeSlotAccess.merge(slot,
                    new NativeSlotAccess(canInput, canOutput),
                    NativeSlotAccess::merge);
        }
    }

    protected final <SLOT extends IInventorySlot> SLOT registerLogicalSlot(
            InventorySlotHelper helper, int index, SLOT slot) {
        if (logicalSlots == null) {
            logicalSlots = new HashMap<>();
        }
        if (logicalSlots.containsKey(index)) {
            throw new IllegalStateException("Duplicate logical machine slot "
                    + index + " in " + getClass().getName());
        }
        logicalSlots.put(index, slot);
        helper.addSlot(slot);
        return slot;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, Direction side,
                                Action action) {
        IInventorySlot inventorySlot = getInventorySlot(slot, side);
        if (inventorySlot == null) {
            return stack;
        }
        NativeSlotAccess access = nativeSlotAccess == null ? null
                : nativeSlotAccess.get(inventorySlot);
        if (side != null && (access == null || !access.canInput())) {
            return stack;
        }
        return inventorySlot.insertItem(stack, action,
                AutomationType.handler(side));
    }

    @Override
    public ItemStack extractItem(int slot, int amount, Direction side,
                                 Action action) {
        IInventorySlot inventorySlot = getInventorySlot(slot, side);
        if (inventorySlot == null) {
            return ItemStack.EMPTY;
        }
        NativeSlotAccess access = nativeSlotAccess == null ? null
                : nativeSlotAccess.get(inventorySlot);
        if (side != null && (access == null || !access.canOutput())) {
            return ItemStack.EMPTY;
        }
        if (side != null && amount > 0
                && com.example.mekanismmagic.integration.pipez
                .PipezItemHandlerCompat.isBulkExtractionActive()) {
            ItemStack stored = inventorySlot.getStack();
            if (stored.isEmpty()
                    || stored.getCount() <= stored.getMaxStackSize()
                    || inventorySlot.extractItem(1, Action.SIMULATE,
                    AutomationType.handler(side)).isEmpty()) {
                return inventorySlot.extractItem(amount, action,
                        AutomationType.handler(side));
            }
            ItemStack template = stored.copy();
            int requested = Math.min(amount, template.getCount());
            int extracted = inventorySlot.shrinkStack(requested, action);
            if (extracted <= 0) {
                return ItemStack.EMPTY;
            }
            return template.copyWithCount(extracted);
        }
        return inventorySlot.extractItem(amount, action,
                AutomationType.handler(side));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack, Direction side) {
        IInventorySlot inventorySlot = getInventorySlot(slot, side);
        if (inventorySlot == null) {
            return false;
        }
        NativeSlotAccess access = nativeSlotAccess == null ? null
                : nativeSlotAccess.get(inventorySlot);
        return (side == null || access != null && access.canInput())
                && inventorySlot.isItemValid(stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack, Direction side) {
        IInventorySlot inventorySlot = getInventorySlot(slot, side);
        if (inventorySlot == null) {
            return;
        }
        if (side != null) {
            NativeSlotAccess access = nativeSlotAccess == null ? null
                    : nativeSlotAccess.get(inventorySlot);
            if (access == null) {
                return;
            }
            ItemStack current = inventorySlot.getStack();
            boolean same = ItemStack.isSameItemSameComponents(current, stack);
            boolean adds = !stack.isEmpty() && (current.isEmpty() || !same
                    || stack.getCount() > current.getCount());
            boolean removes = !current.isEmpty() && (stack.isEmpty() || !same
                    || stack.getCount() < current.getCount());
            if ((adds && (!access.canInput()
                    || !inventorySlot.isItemValid(stack)))
                    || (removes && !access.canOutput())) {
                return;
            }
        }
        inventorySlot.setStack(stack);
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
        boolean changed = false;
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            // Drain visible fallback slots into AE before the physical
            // ejector scans them. Long-buffer producers use the same
            // AE-first ordering, preventing the same output from taking both
            // expensive paths during one server tick.
            changed |= MachineDirectOutputHooks.tick(this);
        }
        changed |= updateNativeBase();
        return changed;
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
                configuredInputSlots == null ? List.of()
                        : List.copyOf(configuredInputSlots),
                configuredOutputSlots == null ? List.of()
                        : List.copyOf(configuredOutputSlots),
                false, getComponents(), getSpiritSourceForUpgrade(),
                new int[]{Math.max(1, progressRequired)},
                snapshotLogicalSlotsForUpgrade());
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
        restoreUpgradeSlots(registries, configuredInputSlots,
                upgrade.inputSlots);
        restoreUpgradeSlots(registries, configuredOutputSlots,
                upgrade.outputSlots);
        if (upgrade.progress.length > 0) {
            progress = Math.max(0, upgrade.progress[0]);
        }
        for (mekanism.common.tile.component.ITileComponent component : getComponents()) {
            component.read(upgrade.components, registries);
        }
        if (upgrade instanceof SpiritMachineUpgradeData spiritUpgrade) {
            setSpiritSourceFromUpgrade(spiritUpgrade.spiritSource);
            if (logicalSlots != null) {
                spiritUpgrade.logicalSlots.forEach((index, stack) -> {
                    IInventorySlot target = logicalSlots.get(index);
                    if (target != null) {
                        target.setStack(stack.copy());
                    }
                });
            }
            if (spiritUpgrade.requiredTicks.length > 0) {
                progressRequired = Math.max(1,
                        spiritUpgrade.requiredTicks[0]);
            }
        }
    }

    private Map<Integer, ItemStack> snapshotLogicalSlotsForUpgrade() {
        if (logicalSlots == null || logicalSlots.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ItemStack> snapshot = new java.util.TreeMap<>();
        logicalSlots.forEach((index, slot) ->
                snapshot.put(index, slot.getStack().copy()));
        return snapshot;
    }

    private static void restoreUpgradeSlots(
            net.minecraft.core.HolderLookup.Provider registries,
            @Nullable List<IInventorySlot> targets,
            List<IInventorySlot> sources) {
        if (targets == null || targets.isEmpty() || sources.isEmpty()) {
            return;
        }
        int slots = Math.min(targets.size(), sources.size());
        for (int slot = 0; slot < slots; slot++) {
            targets.get(slot).deserializeNBT(registries,
                    sources.get(slot).serializeNBT(registries));
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

    public boolean hasNotEnoughEnergyWarning() {
        return notEnoughEnergyWarning;
    }

    public boolean hasReducedEnergyWarning() {
        return reducedEnergyWarning;
    }

    public boolean hasInputDoesntProduceOutputWarning() {
        return inputDoesntProduceOutputWarning && hasAnyRecipeInput();
    }

    public final DirectNetworkStatus getDirectNetworkStatus() {
        if (level != null && level.isClientSide()) {
            return syncedDirectNetworkStatus;
        }
        return MachineDirectOutputHooks.status(this);
    }

    protected final void clearNativeRecipeWarnings() {
        notEnoughEnergyWarning = false;
        reducedEnergyWarning = false;
        inputDoesntProduceOutputWarning = false;
    }

    protected final void setNotEnoughEnergyWarning(boolean warning) {
        notEnoughEnergyWarning = warning;
        if (warning) {
            reducedEnergyWarning = false;
        }
    }

    protected final void setReducedEnergyWarning(boolean warning) {
        reducedEnergyWarning = warning;
        if (warning) {
            notEnoughEnergyWarning = false;
        }
    }

    protected final void setInputDoesntProduceOutputWarning(boolean warning) {
        inputDoesntProduceOutputWarning = warning;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = updateNativeBase();
        notEnoughEnergyWarning = false;
        reducedEnergyWarning = false;
        if (level == null) {
            inputDoesntProduceOutputWarning = false;
            return finishServerUpdate(clearRecipeDisplays() || changed,
                    false);
        }
        if (!canFunction()) {
            inputDoesntProduceOutputWarning = false;
            return finishServerUpdate(clearRecipeDisplays() || changed,
                    false);
        }
        if (!hasAnyRecipeInput()) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
            recipeLookupBackoff = 0;
            lastRecipeInputFingerprint = Long.MIN_VALUE;
            clearActiveRecipeCache();
            inputDoesntProduceOutputWarning = false;
            return finishServerUpdate(clearRecipeDisplays() || changed,
                    false);
        }
        long inputFingerprint = recipeInputFingerprint();
        ItemStackHandler snapshot = null;
        MachineRecipeResult recipe;
        boolean usedActiveRecipeCache = !activeRecipe.isEmpty()
                && cachedActiveRecipe != null
                && cachedActiveRecipeFingerprint == inputFingerprint
                && activeRecipe.equals(recipeKey(cachedActiveRecipe));
        if (usedActiveRecipeCache) {
            recipe = cachedActiveRecipe;
        } else {
            snapshot = snapshotInventory();
            if (!shouldAttemptRecipeLookup(snapshot)) {
                progress = 0;
                progressRequired = 1;
                activeRecipe = "";
                recipeLookupBackoff = 0;
                lastRecipeInputFingerprint = Long.MIN_VALUE;
                clearActiveRecipeCache();
                inputDoesntProduceOutputWarning = false;
                return finishServerUpdate(clearRecipeDisplays() || changed,
                        false);
            }
            if (activeRecipe.isEmpty()
                    && recipeLookupBackoff > 0
                    && inputFingerprint == lastRecipeInputFingerprint) {
                recipeLookupBackoff--;
                return finishServerUpdate(changed, false);
            }
            lastRecipeInputFingerprint = inputFingerprint;
            Optional<MachineRecipeResult> found = findRecipe(snapshot);
            if (found.isEmpty()) {
                progress = 0;
                progressRequired = 1;
                activeRecipe = "";
                recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
                clearActiveRecipeCache();
                inputDoesntProduceOutputWarning = true;
                return finishServerUpdate(clearRecipeDisplays() || changed,
                        false);
            }
            recipe = found.get();
        }
        inputDoesntProduceOutputWarning = false;
        recipeLookupBackoff = 0;
        if (!usedActiveRecipeCache) {
            changed |= recipeEntityDisplay.update(
                    recipeEntityDisplaySource(recipe));
            changed |= recipeItemDisplay.updateRecipe(snapshot, recipe);
        }
        progressRequired = Math.max(1, mekanism.common.util.MekanismUtils.getTicks(this, recipe.duration()));
        ItemStack output = outputSlot == null
                ? ItemStack.EMPTY : outputSlot.getStack();
        if (!recipe.output().isEmpty() && !output.isEmpty()
                && (!ItemStack.isSameItemSameComponents(output, recipe.output())
                || output.getCount() + recipe.output().getCount() > output.getMaxStackSize())) {
            recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
            return finishServerUpdate(changed, false);
        }
        long usage = energyUsagePerTick(recipe);
        boolean powered = hasEnergyForRecipe(recipe, usage);
        boolean sourceOnly = !powered && canRunWithoutEnergy(recipe);
        boolean resourcesAvailable = hasRecipeResources(recipe);
        if (!resourcesAvailable || (!powered && !sourceOnly)) {
            notEnoughEnergyWarning = !powered && !sourceOnly
                    && resourcesAvailable;
            recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
            return finishServerUpdate(changed, false);
        }
        reducedEnergyWarning = sourceOnly;
        if (sourceOnly && !isEnergylessTick(recipe)) {
            // Source-only recipes intentionally advance on a reduced server
            // cadence, but they are still continuously processing. Keeping
            // the active state stable prevents the client animation from
            // blinking off between those work ticks.
            return finishServerUpdate(changed, true);
        }
        String recipeKey = recipeKey(recipe);
        if (!activeRecipe.equals(recipeKey)) {
            activeRecipe = recipeKey;
            progress = 0;
        }
        cachedActiveRecipe = recipe;
        cachedActiveRecipeFingerprint = inputFingerprint;
        setActive(true);
        if (powered) {
            energyContainer.extract(usage, Action.EXECUTE,
                    AutomationType.INTERNAL);
        }
        progress++;
        if (progress >= progressRequired) {
            if (snapshot == null) {
                snapshot = snapshotInventory();
            }
            if (level instanceof ServerLevel serverLevel
                    && !recipe.complete(serverLevel, worldPosition)) {
                progress = 0;
                return finishServerUpdate(clearRecipeDisplays() || changed,
                        false);
            }
            if (!consumeSnapshot(snapshot, recipe)) {
                progress = 0;
                return finishServerUpdate(clearRecipeDisplays() || changed,
                        false);
            }
            if (!consumeRecipeResources(recipe)) {
                progress = 0;
                return finishServerUpdate(clearRecipeDisplays() || changed,
                        false);
            }
            if (!recipe.output().isEmpty()) {
                ItemStack snapshotOutput = snapshot.getStackInSlot(
                        OUTPUT_SLOT);
                if (snapshotOutput.isEmpty()) {
                    snapshot.setStackInSlot(OUTPUT_SLOT, recipe.output().copy());
                } else {
                    snapshotOutput.grow(recipe.output().getCount());
                }
            }
            copyBack(snapshot);
            progress = 0;
            activeRecipe = "";
            clearActiveRecipeCache();
            onRecipeFinished(recipe);
            changed = true;
        }
        return changed;
    }

    /**
     * Commits the final active state once at the end of a server tick. Calling
     * {@code setActive(false)} at the start of a tick and {@code setActive(true)}
     * later made the client restart active models and animations every tick.
     */
    protected final boolean finishServerUpdate(boolean changed,
                                               boolean active) {
        setActive(active);
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
                    // Long-buffer machines keep a dedicated batched output
                    // path. This also feeds logistical transporters through
                    // their item-handler contract so transporter tier limits
                    // apply without falling back to one native request for
                    // the whole oversized inventory.
                    FastEjectResult ejectResult = fastEjectItems();
                    onFastEjectFinished(hasEjectableItem(),
                            ejectResult.moved()
                                    || ejectResult.nativeFallbackRequired());
                    if (ejectResult.nativeFallbackRequired()) {
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
     * Long-buffer machines such as the dimensional miner and Drygmy
     * simulator opt into the custom batched output path. Other machines keep
     * Mekanism/Mekanism Extras ejection timing and transport behavior.
     */
    protected boolean useFastEjectPath() {
        return false;
    }

    protected boolean shouldEjectOutputs() {
        return true;
    }

    protected void onFastEjectFinished(boolean outputRemaining,
                                       boolean continueImmediately) {
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

    private FastEjectResult fastEjectItems() {
        ConfigInfo itemConfig = configComponent.getConfig(
                TransmissionType.ITEM);
        if (itemConfig == null || !itemConfig.isEjecting()
                || !(level instanceof ServerLevel serverLevel)) {
            return FastEjectResult.NONE;
        }
        boolean movedAny = false;
        boolean nativeFallbackRequired = false;
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
                nativeFallbackRequired = true;
                continue;
            }
            boolean moved = moveBatchedIntoTarget(configuredOutputSlots, target);
            movedAny |= moved;
            fastEjectBackoff.put(direction,
                    moved ? 0 : FAST_EJECT_REJECTED_BACKOFF_TICKS);
        }
        return new FastEjectResult(movedAny, nativeFallbackRequired);
    }

    private record FastEjectResult(
            boolean moved, boolean nativeFallbackRequired) {
        private static final FastEjectResult NONE =
                new FastEjectResult(false, false);
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
            // IInventorySlot follows the item-handler contract and therefore
            // never returns more than the item's normal max stack size from
            // extractItem, even when the backing slot contains millions of
            // items. These two long-buffer machines deliberately allow
            // oversized internal stacks, so use the real stored count after
            // the simulated extraction has confirmed external extraction is
            // allowed.
            int availableCount = available.getCount();
            batch.sources.add(new OutputSource(source, availableCount));
            batch.count += (long) availableCount;
        }

        boolean moved = false;
        for (OutputBatch batch : batches) {
            ItemStack simulated = batch.stack.copy();
            // Multiple integer-sized logical output slots can contain the
            // same item. Keep the aggregate as a long and expose at most one
            // safe ItemStack-sized batch per target and server tick.
            simulated.setCount((int) Math.min(Integer.MAX_VALUE,
                    batch.count));
            int remaining = acceptedAmount(target, simulated);
            if (remaining <= 0) {
                continue;
            }
            for (OutputSource source : batch.sources) {
                if (remaining <= 0) {
                    break;
                }
                int requested = Math.min(remaining, Math.min(source.count,
                        source.slot.getCount()));
                if (requested <= 0) {
                    continue;
                }
                // shrinkStack can remove an oversized amount in one operation
                // without violating IItemHandler's rule that extractItem must
                // return at most one normal ItemStack. The target still caps
                // requested via acceptedAmount above, so Mekanism logistical
                // transporter tier throughput remains authoritative.
                int extracted = source.slot.shrinkStack(requested,
                        Action.EXECUTE);
                if (extracted <= 0) {
                    continue;
                }
                ItemStack transfer = batch.stack.copyWithCount(extracted);
                ItemStack remainder = insertIntoTarget(target, transfer);
                if (!remainder.isEmpty()) {
                    restoreToSource(source.slot, remainder);
                }
                int accepted = extracted - remainder.getCount();
                remaining -= accepted;
                moved |= accepted > 0;
            }
        }
        return moved;
    }

    private static final class OutputBatch {
        private final ItemStack stack;
        private final List<OutputSource> sources;
        private long count;

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
            source.growStack(remainder.getCount(), Action.EXECUTE);
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

    /**
     * Entity-bearing recipe outputs (notably Occultism summon rituals) are
     * displayed by default. Machines whose entity is a persistent catalyst
     * can return that source instead.
     */
    protected ItemStack recipeEntityDisplaySource(
            MachineRecipeResult recipe) {
        return recipe.output();
    }

    @Override
    public RecipeEntityDisplayState mekanismMagicRecipeEntityDisplay() {
        return recipeEntityDisplay;
    }

    @Override
    public RecipeItemDisplayState mekanismMagicRecipeItemDisplay() {
        return recipeItemDisplay;
    }

    private boolean clearRecipeDisplays() {
        // Non-short-circuiting OR is deliberate: both client display states
        // must be cleared by the same update packet.
        return recipeEntityDisplay.clear() | recipeItemDisplay.clear();
    }

    private ItemStackHandler snapshotInventory() {
        ItemStackHandler snapshot = new ItemStackHandler(MACHINE_INVENTORY_SIZE);
        if (logicalSlots != null) {
            logicalSlots.forEach((index, slot) -> snapshot.setStackInSlot(index, slot.getStack().copy()));
        }
        return snapshot;
    }

    private long recipeInputFingerprint() {
        long fingerprint = 1;
        for (int slot = 0; slot < MACHINE_INVENTORY_SIZE; slot++) {
            IInventorySlot inventorySlot = logicalSlots == null
                    ? null : logicalSlots.get(slot);
            ItemStack stack = inventorySlot == null
                    ? ItemStack.EMPTY : inventorySlot.getStack();
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

    private void clearActiveRecipeCache() {
        cachedActiveRecipe = null;
        cachedActiveRecipeFingerprint = Long.MIN_VALUE;
    }

    /**
     * Returns whether the machine has enough non-special input state to make a
     * recipe lookup worthwhile. Machines with a selector or other manual
     * control item can override this to avoid scanning while their real
     * processing inputs are empty.
     */
    protected boolean hasAnyRecipeInput() {
        if (configuredInputSlots == null || configuredInputSlots.isEmpty()) {
            return false;
        }
        for (IInventorySlot slot : configuredInputSlots) {
            if (!slot.getStack().isEmpty()) {
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
    public net.minecraft.nbt.CompoundTag getReducedUpdateTag(
            net.minecraft.core.HolderLookup.Provider provider) {
        net.minecraft.nbt.CompoundTag tag =
                super.getReducedUpdateTag(provider);
        recipeEntityDisplay.writeUpdateTag(tag);
        recipeItemDisplay.writeUpdateTag(tag, provider);
        return tag;
    }

    @Override
    public void handleUpdateTag(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        recipeEntityDisplay.readUpdateTag(tag);
        recipeItemDisplay.readUpdateTag(tag, provider);
    }

    @Override
    public final void onLoad() {
        // BlockCapabilityCache instances permanently stop querying after
        // their owner is removed. Never reuse a cache retained across chunk
        // unload/revive; this also covers machines moved by an upgrade.
        clearFastEjectCapabilityCaches();
        super.onLoad();
        onNativeMachineLoaded();
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            MachineDirectOutputHooks.onLoad(this);
        }
    }

    /**
     * Lifecycle hook for machine-specific load registration. The public
     * {@code onLoad} method is final so future machines cannot accidentally
     * skip Mekanism's component and capability initialization.
     */
    protected void onNativeMachineLoaded() {
    }

    @Override
    public final void setRemoved() {
        clearFastEjectCapabilityCaches();
        super.setRemoved();
        onNativeMachineRemoved();
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            MachineDirectOutputHooks.onRemoved(this);
        }
    }

    /** Optional cleanup hook; common capability caches are already cleared. */
    protected void onNativeMachineRemoved() {
    }

    @Override
    public final void clearRemoved() {
        super.clearRemoved();
        clearFastEjectCapabilityCaches();
        onNativeMachineRevived();
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            MachineDirectOutputHooks.onRevived(this);
        }
    }

    /** Optional revive hook; common capability caches are already fresh. */
    protected void onNativeMachineRevived() {
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            MachineDirectOutputHooks.onChunkUnloaded(this);
        }
    }

    @Override
    public void blockRemoved() {
        super.blockRemoved();
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            MachineDirectOutputHooks.onBlockRemoved(this);
        }
    }

    private void clearFastEjectCapabilityCaches() {
        fastEjectTargets.clear();
        fastEjectBackoff.clear();
    }

    @Override
    public final void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                                     net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("magic_progress", progress);
        tag.putInt("magic_progress_required", progressRequired);
        tag.putString("magic_active_recipe", activeRecipe);
        saveNativeMachineData(tag, registries);
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            MachineDirectOutputHooks.save(this, tag, registries);
        }
    }

    @Override
    public final void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                                     net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("magic_progress");
        progressRequired = Math.max(1, tag.getInt("magic_progress_required"));
        activeRecipe = tag.getString("magic_active_recipe");
        // The cached result is runtime-only and may refer to recipe data from
        // before a chunk reload or datapack refresh. Rebuild it once from the
        // persisted inputs on the next server tick.
        clearActiveRecipeCache();
        loadNativeMachineData(tag, registries);
        if (mekanismMagicSupportsDirectNetworkOutput()) {
            MachineDirectOutputHooks.load(this, tag, registries);
        }
    }

    /** Machine-specific persistent data, always chained after common data. */
    protected void saveNativeMachineData(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
    }

    /** Machine-specific persistent data, always chained after common data. */
    protected void loadNativeMachineData(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
    }

    private record NativeSlotAccess(boolean canInput, boolean canOutput) {
        private NativeSlotAccess merge(NativeSlotAccess other) {
            return new NativeSlotAccess(canInput || other.canInput,
                    canOutput || other.canOutput);
        }
    }
}
