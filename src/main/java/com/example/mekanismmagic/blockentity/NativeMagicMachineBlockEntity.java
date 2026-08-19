package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;

import mekanism.api.IContentsListener;
import mekanism.api.Action;
import mekanism.api.AutomationType;
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
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.upgrade.MachineUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

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
        implements ISideConfiguration, IUpgradeTile {
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
    protected TileComponentUpgrade upgradeComponent;
    protected MachineEnergyContainer<? extends NativeMagicMachineBlockEntity> energyContainer;
    protected InputInventorySlot inputSlot;
    protected IInventorySlot outputSlot;
    protected EnergyInventorySlot energySlot;
    protected BasicInventorySlot containmentSlot;
    private Map<Integer, IInventorySlot> logicalSlots;
    protected int progress;
    protected int progressRequired = 1;
    protected String activeRecipe = "";

    protected NativeMagicMachineBlockEntity(IBlockProvider block, BlockPos pos, BlockState state) {
        super(block, pos, state);
    }

    @Override
    public net.minecraft.network.chat.Component getName() {
        return net.minecraft.network.chat.Component.translatable(
                getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected void presetVariables() {
        super.presetVariables();
        configComponent = new TileComponentConfig(this,
                TransmissionType.ITEM, TransmissionType.ENERGY);
        addComponent(configComponent);
        upgradeComponent = new TileComponentUpgrade(this);
        addComponent(upgradeComponent);
        ejectorComponent = new TileComponentEjector(this)
                .setOutputData(configComponent, TransmissionType.ITEM);
        addComponent(ejectorComponent);
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

    protected final void setupNativeItemIO(
            List<? extends IInventorySlot> inputs,
            List<? extends IInventorySlot> outputs,
            List<? extends IInventorySlot> extras) {
        List<IInventorySlot> inputSlots = new java.util.ArrayList<>(inputs);
        List<IInventorySlot> outputSlots = new java.util.ArrayList<>(outputs);
        var itemConfig = configComponent.setupItemIOConfig(
                inputSlots, outputSlots, energySlot, false);
        if (!extras.isEmpty()) {
            itemConfig.addSlotInfo(DataType.EXTRA,
                    new InventorySlotInfo(true, true,
                            new java.util.ArrayList<>(extras)));
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

    @Override
    protected void onUpdateServer() {
        nativeBaseUpdate();
        setActive(false);
        if (level == null) {
            return;
        }
        ItemStackHandler snapshot = snapshotInventory();
        Optional<OccultismRecipeBridge.RecipeResult> found = findRecipe(snapshot);
        if (found.isEmpty()) {
            progress = 0;
            progressRequired = 1;
            activeRecipe = "";
            return;
        }
        OccultismRecipeBridge.RecipeResult recipe = found.get();
        progressRequired = Math.max(1, mekanism.common.util.MekanismUtils.getTicks(this, recipe.duration()));
        ItemStack output = snapshot.getStackInSlot(OUTPUT_SLOT);
        if (!recipe.output().isEmpty() && !output.isEmpty()
                && (!ItemStack.isSameItemSameTags(output, recipe.output())
                || output.getCount() + recipe.output().getCount() > output.getMaxStackSize())) {
            return;
        }
        FloatingLong usage = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, FloatingLong.create(baseEnergyPerTick()));
        if (energyContainer == null || energyContainer.getEnergy().smallerThan(usage)) {
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

    protected final void tickEjectorAdditional(int calls) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel)
                || ejectorComponent == null) {
            return;
        }
        for (int index = 0; index < calls; index++) {
            ejectorComponent.tickServer();
        }
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

