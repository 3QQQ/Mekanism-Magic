package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * Converts Mekanism FE into Ars Nouveau Source and exposes the result to the
 * Ars Source network.
 */
public final class FeSourceConverterBlockEntity
        extends ArsSourceMachineBlockEntity {
    private static final int MAX_STACK_UPGRADES = 8;
    private int ejectSideCursor;

    public FeSourceConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.SOURCE_CONVERTER_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        setupArsItemIO(java.util.List.of(), java.util.List.of(),
                java.util.List.of());
    }

    @Override
    protected boolean supportsAutomaticSourcePull() {
        return false;
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return Optional.empty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return ArsNouveauMachineConfig.SOURCE_CONVERTER_FE_PER_TICK;
    }

    @Override
    protected int energySlotX() {
        return 28;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    @Override
    protected boolean hasLegacyCreativeMagicUpgradeSlot() {
        return false;
    }

    @Override
    protected int sourceMaxReceive() {
        return 0;
    }

    @Override
    protected int sourceCapacity() {
        // Construct at the supported maximum so world loading cannot clamp a
        // stacked converter before Mekanism has restored its upgrades.
        return saturatingMultiply(
                ArsNouveauMachineConfig.SOURCE_CAPACITY,
                1 << MAX_STACK_UPGRADES);
    }

    @Override
    protected int sourceMaxExtract() {
        return getTransferRate();
    }

    @Override
    public int getTransferRate() {
        return saturatingMultiply(
                ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE,
                stackOperationMultiplier());
    }

    @Override
    public int getMaxSource() {
        // Upgrade counts are container-synced without invoking the tile's
        // recalculation hook on the client. Deriving the displayed capacity
        // here keeps the Source bar correct on both sides. When an upgrade is
        // removed, preserve temporary room for already stored excess Source.
        return Math.max(desiredSourceCapacity(), getSource());
    }

    @Override
    public boolean canAcceptSource() {
        return false;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    @Override
    protected boolean onUpdateServer() {
        clearNativeRecipeWarnings();
        synchronizeStackUpgradeLimits();
        boolean changed = nativeBaseUpdate();
        int ejected = ejectSource(getTransferRate());
        changed |= ejected > 0;
        progressRequired = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        ArsNouveauMachineConfig.SOURCE_CONVERTER_DURATION));
        if (!canFunction()) {
            return finishServerUpdate(changed, false);
        }
        int sourcePerOperation = sourcePerOperation();
        if (getMaxSource() - getSource() < sourcePerOperation) {
            progress = 0;
            return finishServerUpdate(changed, false);
        }
        long usage = stackScaledEnergyUsage();
        if (energyContainer == null || energyContainer.getEnergy() < usage) {
            setNotEnoughEnergyWarning(true);
            return finishServerUpdate(changed, false);
        }
        setActive(true);
        energyContainer.extract(usage, Action.EXECUTE,
                AutomationType.INTERNAL);
        progress++;
        if (progress >= progressRequired) {
            addSource(sourcePerOperation);
            progress = 0;
            changed = true;
            changed |= ejectSource(Math.max(0,
                    getTransferRate() - ejected)) > 0;
        }
        return changed;
    }

    @Override
    protected void onArsMachineLoaded() {
        synchronizeStackUpgradeLimits();
        synchronizeDisplayedEnergyUsage();
    }

    @Override
    protected void onNativeUpgradeChanged(mekanism.api.Upgrade upgrade) {
        super.onNativeUpgradeChanged(upgrade);
        mekanism.api.Upgrade stackUpgrade = NativeMekanismRegistries
                .dimensionMinerStackUpgrade();
        if (upgrade == mekanism.api.Upgrade.SPEED
                || upgrade == mekanism.api.Upgrade.ENERGY
                || stackUpgrade != null && upgrade == stackUpgrade) {
            synchronizeDisplayedEnergyUsage();
        }
        if (stackUpgrade != null && upgrade == stackUpgrade) {
            // Do not let progress paid at a lower stack multiplier finish a
            // newly enlarged conversion batch for free.
            progress = 0;
            synchronizeStackUpgradeLimits();
        }
    }

    private int stackOperationMultiplier() {
        mekanism.api.Upgrade stackUpgrade = NativeMekanismRegistries
                .dimensionMinerStackUpgrade();
        if (stackUpgrade == null || upgradeComponent == null) {
            return 1;
        }
        int upgrades = Math.max(0,
                upgradeComponent.getUpgrades(stackUpgrade));
        return 1 << Math.min(upgrades, MAX_STACK_UPGRADES);
    }

    private int sourcePerOperation() {
        return saturatingMultiply(
                ArsNouveauMachineConfig
                        .SOURCE_CONVERTER_SOURCE_PER_OPERATION,
                stackOperationMultiplier());
    }

    private int desiredSourceCapacity() {
        return saturatingMultiply(
                ArsNouveauMachineConfig.SOURCE_CAPACITY,
                stackOperationMultiplier());
    }

    private long stackScaledEnergyUsage() {
        long base = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
        int multiplier = stackOperationMultiplier();
        long scaled = base > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : base * multiplier;
        // Never discount a parallel batch merely because the internal
        // battery is too small. In that case normal Mekanism energy upgrades
        // are required before the machine can run the requested batch.
        return scaled;
    }

    private void synchronizeDisplayedEnergyUsage() {
        if (energyContainer != null) {
            energyContainer.setEnergyPerTick(stackScaledEnergyUsage());
        }
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableLong.create(
                this::stackScaledEnergyUsage,
                value -> {
                    if (energyContainer != null) {
                        energyContainer.setEnergyPerTick(
                                Math.max(0L, value));
                    }
                }));
    }

    private void synchronizeStackUpgradeLimits() {
        // Keep the client's constructor-time maximum until the server's
        // explicit Source display/current trackers have arrived. Shrinking
        // here during client onLoad used to reset every stacked converter to
        // the base 100k capacity on each game entry.
        if (level == null || level.isClientSide()) {
            return;
        }
        var storage = getSourceStorage();
        int desiredCapacity = desiredSourceCapacity();
        // Removing upgrades must never delete Source already held by the
        // machine. The temporary excess capacity shrinks as it is ejected.
        storage.setMaxSource(Math.max(desiredCapacity, storage.getSource()));
        storage.setMaxReceive(0);
        storage.setMaxExtract(getTransferRate());
    }

    private static int saturatingMultiply(int value, int multiplier) {
        long scaled = (long) Math.max(0, value)
                * Math.max(1, multiplier);
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    /**
     * Pushes Source into adjacent Ars-compatible blocks and pipes. The total
     * amount sent in one call is capped to the machine transfer rate and side
     * configuration is honored in the same way as the exposed capability.
     */
    private int ejectSource(int transferLimit) {
        if (level == null || level.isClientSide() || getSource() <= 0
                || transferLimit <= 0) {
            return 0;
        }
        Direction[] sides = Direction.values();
        int remaining = Math.min(transferLimit, getSource());
        int transferred = 0;
        int start = Math.floorMod(ejectSideCursor++, sides.length);
        for (int offset = 0; offset < sides.length && remaining > 0;
             offset++) {
            Direction side = sides[(start + offset) % sides.length];
            if (!getSourceMode(side).allowsOutput()) {
                continue;
            }
            ISourceCap target = level.getCapability(
                    CapabilityRegistry.SOURCE_CAPABILITY,
                    worldPosition.relative(side), side.getOpposite());
            if (target == null) {
                continue;
            }
            int extractable = getSourceStorage().extractSource(remaining,
                    true);
            if (extractable <= 0) {
                break;
            }
            int accepted = target.receiveSource(extractable, true);
            if (accepted <= 0) {
                continue;
            }
            int delivered = target.receiveSource(accepted, false);
            if (delivered <= 0) {
                continue;
            }
            int extracted = getSourceStorage().extractSource(delivered,
                    false);
            transferred += extracted;
            remaining -= extracted;
        }
        return transferred;
    }
}
