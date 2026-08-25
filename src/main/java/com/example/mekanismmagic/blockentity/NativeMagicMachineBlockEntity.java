package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;

import mekanism.api.IContentsListener;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.EnumMap;
import java.util.EnumSet;
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
    public static final int MACHINE_INVENTORY_SIZE = 43;
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
    private final EnumMap<Direction, LazyOptional<IItemHandler>>
            fastEjectTargets = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> fastEjectBackoff =
            new EnumMap<>(Direction.class);
    protected int progress;
    protected int progressRequired = 1;
    protected String activeRecipe = "";
    private int recipeLookupBackoff;
    private long lastRecipeInputFingerprint = Long.MIN_VALUE;

    protected NativeMagicMachineBlockEntity(IBlockProvider block, BlockPos pos, BlockState state) {
        super(block, pos, state);
    }

    @Override
    public Component getName() {
        return Component.translatable(
                getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected void presetVariables() {
        super.presetVariables();
        // Both component constructors register themselves with the tile.
        // Calling addComponent a second time replaces Mekanism's capability
        // mappings and leaves the machine with duplicated component state.
        configComponent = new TileComponentConfig(this,
                TransmissionType.ITEM, TransmissionType.ENERGY);
        ejectorComponent = new TileComponentEjector(this)
                .setCanEject(type -> level instanceof ServerLevel)
                .setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper helper = EnergyContainerHelper.forSideWithConfig(
                this::getDirection, () -> configComponent);
        energyContainer = MachineEnergyContainer.input(this, listener);
        helper.addContainer(energyContainer);
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        return helper.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper helper = InventorySlotHelper.forSideWithConfig(
                this::getDirection, () -> configComponent);
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

    protected abstract Optional<OccultismRecipeBridge.RecipeResult> findRecipe(ItemStackHandler inventory);

    protected abstract int baseEnergyPerTick();

    protected boolean shouldAttemptRecipeLookup(ItemStackHandler inventory) {
        for (int slot = 0; slot < MACHINE_INVENTORY_SIZE; slot++) {
            if (slot != OUTPUT_SLOT
                    && !inventory.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs only Mekanism's common tile update. Specialized machines with a
     * different processing shape (for example multi-output miners) can use
     * this without invoking the default single-output recipe loop again.
     */
    protected final void nativeBaseUpdate() {
        super.onUpdateServer();
        if (energySlot != null) {
            energySlot.fillContainerOrConvert();
        }
        if (ejectorComponent != null && level instanceof ServerLevel) {
            if (useFastEjectPath()) {
                if (hasEjectableItem() && shouldEjectOutputs()) {
                    boolean fallback = fastEjectItems();
                    onFastEjectFinished(hasEjectableItem(), fallback);
                    if (fallback) {
                        ejectorComponent.tickServer();
                    }
                }
            } else {
                // Match Mekanism's configurable-machine behavior for all
                // machines except the dimensional miner.
                ejectorComponent.tickServer();
            }
        }
    }

    protected boolean useFastEjectPath() {
        return false;
    }

    protected boolean shouldEjectOutputs() {
        return true;
    }

    protected void onFastEjectFinished(boolean outputRemaining,
                                       boolean nativeFallbackRequired) {
    }

    protected ItemStack pushDirectlyToTargets(ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof ServerLevel)) {
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
            LazyOptional<IItemHandler> capability =
                    fastEjectTargets.get(direction);
            if (capability == null || !capability.isPresent()) {
                BlockEntity targetBlock = level.getBlockEntity(
                        worldPosition.relative(direction));
                if (targetBlock != null) {
                    capability = targetBlock.getCapability(
                            ForgeCapabilities.ITEM_HANDLER,
                            direction.getOpposite());
                    fastEjectTargets.put(direction, capability);
                }
            }
            IItemHandler target = capability == null
                    ? null : capability.orElse(null);
            if (target == null
                    || target instanceof mekanism.common.capabilities.item
                    .CursedTransporterItemHandler) {
                continue;
            }
            int accepted = acceptedAmount(target, remaining);
            if (accepted <= 0) {
                continue;
            }
            ItemStack extracted = remaining.copyWithCount(accepted);
            ItemStack remainder = insertIntoTarget(target, extracted);
            remaining = remaining.copyWithCount(
                    remaining.getCount() - accepted
                            + remainder.getCount());
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
        if (itemConfig == null || !itemConfig.isEjecting()) {
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
            LazyOptional<IItemHandler> capability =
                    fastEjectTargets.get(direction);
            if (capability == null || !capability.isPresent()) {
                BlockEntity targetBlock = level.getBlockEntity(
                        worldPosition.relative(direction));
                if (targetBlock == null) {
                    fastEjectTargets.remove(direction);
                    fastEjectBackoff.put(direction,
                            FAST_EJECT_BACKOFF_TICKS);
                    continue;
                }
                capability = targetBlock.getCapability(
                        ForgeCapabilities.ITEM_HANDLER,
                        direction.getOpposite());
                LazyOptional<IItemHandler> tracked = capability;
                capability.addListener(ignored -> {
                    if (fastEjectTargets.get(direction) == tracked) {
                        fastEjectTargets.remove(direction);
                        fastEjectBackoff.remove(direction);
                    }
                });
                fastEjectTargets.put(direction, capability);
            }
            IItemHandler target = capability.orElse(null);
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
            boolean moved = false;
            for (IInventorySlot output : configuredOutputSlots) {
                moved |= moveIntoTarget(output, target);
            }
            fastEjectBackoff.put(direction,
                    moved ? 0 : FAST_EJECT_BACKOFF_TICKS);
        }
        return needsNativeFallback;
    }

    private static boolean moveIntoTarget(IInventorySlot source,
                                          IItemHandler target) {
        ItemStack available = source.getStack();
        if (available.isEmpty()) {
            return false;
        }
        ItemStack simulated = source.extractItem(available.getCount(),
                Action.SIMULATE, AutomationType.EXTERNAL);
        if (simulated.isEmpty()) {
            return false;
        }
        int accepted = acceptedAmount(target, simulated);
        if (accepted <= 0) {
            return false;
        }
        ItemStack extracted = source.extractItem(accepted,
                Action.EXECUTE, AutomationType.EXTERNAL);
        if (extracted.isEmpty()) {
            return false;
        }
        ItemStack remainder = insertIntoTarget(target, extracted);
        if (!remainder.isEmpty()) {
            restoreToSource(source, remainder);
        }
        return remainder.getCount() < extracted.getCount();
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
        } else if (ItemStack.isSameItemSameTags(current, remainder)) {
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

    protected void onNativeUpgradeChanged(Upgrade upgrade) {
    }

    protected void onRecipeFinished(
            OccultismRecipeBridge.RecipeResult recipe) {
    }

    public int getProgress() {
        return progress;
    }

    public int getProgressRequired() {
        return progressRequired;
    }

    private boolean canFunctionNative() {
        return switch (getControlType()) {
            case DISABLED -> true;
            case HIGH -> isPowered();
            case LOW -> !isPowered();
            case PULSE -> isPowered() && !wasPowered();
        };
    }

    @Override
    protected void onUpdateServer() {
        nativeBaseUpdate();
        if (!canFunctionNative()) {
            setActive(false);
            return;
        }
        setActive(false);
        if (level == null) {
            return;
        }
        if (!hasAnyRecipeInput()) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
            recipeLookupBackoff = 0;
            lastRecipeInputFingerprint = Long.MIN_VALUE;
            return;
        }
        ItemStackHandler snapshot = snapshotInventory();
        if (!shouldAttemptRecipeLookup(snapshot)) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
            recipeLookupBackoff = 0;
            lastRecipeInputFingerprint = Long.MIN_VALUE;
            return;
        }
        long inputFingerprint = recipeInputFingerprint(snapshot);
        if (activeRecipe.isEmpty()
                && recipeLookupBackoff > 0
                && inputFingerprint == lastRecipeInputFingerprint) {
            recipeLookupBackoff--;
            return;
        }
        lastRecipeInputFingerprint = inputFingerprint;
        Optional<OccultismRecipeBridge.RecipeResult> found = findRecipe(snapshot);
        if (found.isEmpty()) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
            recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
            return;
        }
        recipeLookupBackoff = 0;
        OccultismRecipeBridge.RecipeResult recipe = found.get();
        progressRequired = Math.max(1, mekanism.common.util.MekanismUtils.getTicks(this, recipe.duration()));
        ItemStack output = snapshot.getStackInSlot(OUTPUT_SLOT);
        if (!recipe.output().isEmpty() && !output.isEmpty()
                && (!ItemStack.isSameItemSameTags(output, recipe.output())
                || output.getCount() + recipe.output().getCount() > output.getMaxStackSize())) {
            recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
            return;
        }
        FloatingLong usage = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, FloatingLong.create(baseEnergyPerTick()));
        if (energyContainer == null || energyContainer.getEnergy().smallerThan(usage)) {
            recipeLookupBackoff = RECIPE_LOOKUP_BACKOFF_TICKS;
            return;
        }
        String recipeKey = recipeKey(recipe);
        if (!activeRecipe.equals(recipeKey)) {
            activeRecipe = recipeKey;
            progress = 0;
        }
        setActive(true);
        energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
        progress++;
        if (progress >= progressRequired) {
            if (recipe.isCommand()
                    && !(level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && OccultismRecipeBridge.executeCommandRitual(
                    serverLevel, worldPosition, recipe.command()))) {
                progress = 0;
                setActive(false);
                return;
            }
            if (!consumeSnapshot(snapshot, recipe)) {
                progress = 0;
                setActive(false);
                return;
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
            setChanged();
        }
    }

    protected final ItemStackHandler snapshotInventory() {
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
                    + (stack.getTag() == null ? 0 : stack.getTag().hashCode());
        }
        return fingerprint;
    }

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

    private boolean consumeSnapshot(ItemStackHandler snapshot, OccultismRecipeBridge.RecipeResult recipe) {
        if (recipe.activationSlot() >= 0 && snapshot.getStackInSlot(recipe.activationSlot()).isEmpty()) {
            return false;
        }
        if (recipe.sacrificeSlot() >= 0
                && !OccultismRecipeBridge.isSacrificeItem(snapshot.getStackInSlot(recipe.sacrificeSlot()))) {
            return false;
        }
        for (OccultismRecipeBridge.InputUse input : recipe.inputs()) {
            if (snapshot.getStackInSlot(input.slot()).getCount() < input.count()) {
                return false;
            }
        }
        for (OccultismRecipeBridge.InputUse input : recipe.inputs()) {
            snapshot.extractItem(input.slot(), input.count(), false);
        }
        if (recipe.activationSlot() >= 0) {
            snapshot.extractItem(recipe.activationSlot(), 1, false);
        }
        return recipe.sacrificeSlot() < 0
                || OccultismRecipeBridge.consumeSacrifice(snapshot, recipe.sacrificeSlot());
    }

    private static String recipeKey(OccultismRecipeBridge.RecipeResult recipe) {
        return recipe.id() + "|" + recipe.output().getCount() + "|"
                + recipe.duration() + "|" + recipe.inputs();
    }

    @Override
    public void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("magic_progress", progress);
        tag.putInt("magic_progress_required", progressRequired);
        tag.putString("magic_active_recipe", activeRecipe);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        progress = tag.getInt("magic_progress");
        progressRequired = Math.max(1, tag.getInt("magic_progress_required"));
        activeRecipe = tag.getString("magic_active_recipe");
    }
}

