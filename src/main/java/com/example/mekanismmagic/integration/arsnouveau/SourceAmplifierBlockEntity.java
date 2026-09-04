package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.common.block.tile.SourcelinkTile;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.Upgrade;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/**
 * Uses FE to increase new Source production in nearby vanilla Ars Nouveau
 * sourcelinks. Source never enters this machine and existing Source cannot be
 * amplified repeatedly.
 */
public final class SourceAmplifierBlockEntity
        extends ArsSourceMachineBlockEntity {
    private int activityTicks;
    private int syncedAmplificationPercent = -1;

    public SourceAmplifierBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.SOURCE_AMPLIFIER_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        setupArsItemIO(List.of(), List.of(), List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return Optional.empty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return 500;
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
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    @Override
    protected boolean hasInternalSourceBuffer() {
        return false;
    }

    @Override
    protected int sourceCapacity() {
        return 0;
    }

    @Override
    protected int sourceMaxReceive() {
        return 0;
    }

    @Override
    protected int sourceMaxExtract() {
        return 0;
    }

    @Override
    public int getTransferRate() {
        return 0;
    }

    @Override
    public boolean canAcceptSource() {
        return false;
    }

    @Override
    public boolean canProvideSource() {
        return false;
    }

    /** Total yield after amplification, where 100 means unmodified output. */
    public int getAmplificationPercent() {
        if (level != null && level.isClientSide
                && syncedAmplificationPercent >= 0) {
            return syncedAmplificationPercent;
        }
        return calculateAmplificationPercent();
    }

    private int calculateAmplificationPercent() {
        return 100 + getAmplificationBonusPercent();
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(
                this::calculateAmplificationPercent,
                value -> syncedAmplificationPercent = Math.max(100, value)));
    }

    public int getSpeedUpgradeCount() {
        return upgradeComponent == null ? 0
                : upgradeComponent.getUpgrades(Upgrade.SPEED);
    }

    private int getAmplificationBonusPercent() {
        int baseBonus = (ArsNouveauMachineConfig
                .AMPLIFIED_SOURCE_PER_OPERATION
                - ArsNouveauMachineConfig.RAW_SOURCE_PER_OPERATION) * 100
                / ArsNouveauMachineConfig.RAW_SOURCE_PER_OPERATION;
        return baseBonus
                + getSpeedUpgradeCount()
                * ArsNouveauMachineConfig
                .SOURCE_AMPLIFICATION_SPEED_BONUS_PERCENT;
    }

    @Override
    protected boolean onUpdateServer() {
        clearNativeRecipeWarnings();
        boolean changed = nativeBaseUpdate();
        progressRequired = Math.max(1, MekanismUtils.getTicks(this,
                ArsNouveauMachineConfig.SOURCE_AMPLIFICATION_DURATION));
        updateActivityPulse();
        return finishServerUpdate(changed, activityTicks > 0);
    }

    /**
     * Wraps one of Ars Nouveau's explicit sourcelink generation commits.
     * External Source capability insertion and third-party direct additions
     * never pass through this method, so they cannot be amplified.
     *
     * @return the same value returned by the original addSource call
     */
    public static int commitOriginalProduction(
            SourcelinkTile sourcelink, int requestedSource) {
        int before = sourcelink.getSource();
        int result = sourcelink.addSource(requestedSource);
        amplifyOriginalProductionDelta(sourcelink, before);
        return result;
    }

    /** Called after an explicitly wrapped vanilla production invocation. */
    public static void amplifyOriginalProductionDelta(
            SourcelinkTile sourcelink, int sourceBeforeCommit) {
        int produced = Math.max(0,
                sourcelink.getSource() - sourceBeforeCommit);
        if (produced <= 0 || sourcelink.getLevel() == null
                || sourcelink.getLevel().isClientSide
                || !isOriginalArsSourcelink(sourcelink)) {
            return;
        }
        SourceAmplifierBlockEntity amplifier = primaryAmplifier(sourcelink);
        if (amplifier != null) {
            amplifier.amplifyCommittedProduction(sourcelink, produced);
        }
    }

    private void amplifyCommittedProduction(
            SourcelinkTile sourcelink, int produced) {
        if (!canFunction() || sourcelink.isDisabled) {
            return;
        }
        int amplified = amplifyProduction(sourcelink, produced);
        if (amplified > 0) {
            activityTicks = Math.max(1, progressRequired);
            progress = 1;
            setChanged();
        }
    }

    private void updateActivityPulse() {
        if (activityTicks <= 0) {
            activityTicks = 0;
            progress = 0;
            return;
        }
        activityTicks--;
        progress = Math.min(progressRequired, progress + 1);
        if (activityTicks == 0) {
            progress = 0;
        }
    }

    private int amplifyProduction(SourcelinkTile sourcelink,
                                  int newlyProduced) {
        if (energyContainer == null || newlyProduced <= 0) {
            return 0;
        }
        int freeSource = sourcelink.getMaxSource() - sourcelink.getSource();
        if (freeSource <= 0) {
            return 0;
        }

        int bonusPercent = getAmplificationBonusPercent();
        int rawLimitedBySpace = Math.max(1,
                (int) ((long) freeSource * 100L / bonusPercent));
        long energyPerOperation = energyPerOperation();
        long storedEnergy = energyContainer.getEnergy();
        int rawLimitedByEnergy = (int) Math.min(Integer.MAX_VALUE,
                storedEnergy * ArsNouveauMachineConfig
                        .RAW_SOURCE_PER_OPERATION / energyPerOperation);
        int amplifiedRaw = Math.min(newlyProduced,
                Math.min(rawLimitedBySpace, rawLimitedByEnergy));
        if (amplifiedRaw <= 0) {
            setNotEnoughEnergyWarning(true);
            return 0;
        }

        int bonus = Math.max(1, (int) (((long) amplifiedRaw * bonusPercent
                + 50L) / 100L));
        while (bonus > freeSource && amplifiedRaw > 0) {
            amplifiedRaw--;
            bonus = amplifiedRaw == 0 ? 0 : Math.max(1,
                    (int) (((long) amplifiedRaw * bonusPercent + 50L)
                            / 100L));
        }
        if (bonus <= 0) {
            return 0;
        }

        long energyCost = ((energyPerOperation * amplifiedRaw)
                + ArsNouveauMachineConfig.RAW_SOURCE_PER_OPERATION - 1L)
                / ArsNouveauMachineConfig.RAW_SOURCE_PER_OPERATION;
        if (storedEnergy < energyCost) {
            setNotEnoughEnergyWarning(true);
            return 0;
        }
        if (amplifiedRaw < newlyProduced) {
            setReducedEnergyWarning(true);
        }
        energyContainer.extract(energyCost, Action.EXECUTE,
                AutomationType.INTERNAL);
        int before = sourcelink.getSource();
        sourcelink.addSource(bonus);
        return Math.max(0, sourcelink.getSource() - before);
    }

    private long energyPerOperation() {
        long energyPerTick = Math.max(1L,
                MekanismUtils.getEnergyPerTick(this, baseEnergyPerTick()));
        long duration = Math.max(1, MekanismUtils.getTicks(this,
                ArsNouveauMachineConfig.SOURCE_AMPLIFICATION_DURATION));
        return energyPerTick > Long.MAX_VALUE / duration
                ? Long.MAX_VALUE : energyPerTick * duration;
    }

    private static boolean isOriginalArsSourcelink(
            SourcelinkTile sourcelink) {
        return "ars_nouveau".equals(BuiltInRegistries.BLOCK.getKey(
                sourcelink.getBlockState().getBlock()).getNamespace());
    }

    /**
     * A sourcelink is owned by one deterministic amplifier. This prevents
     * nearby amplifiers from treating each other's bonus as new production.
     */
    private static SourceAmplifierBlockEntity primaryAmplifier(
            SourcelinkTile sourcelink) {
        if (sourcelink.getLevel() == null) {
            return null;
        }
        int radius = ArsNouveauMachineConfig.SOURCE_AMPLIFICATION_RADIUS;
        BlockPos sourcePos = sourcelink.getBlockPos();
        SourceAmplifierBlockEntity best = null;
        long bestDistance = Long.MAX_VALUE;
        for (BlockPos position : BlockPos.betweenClosed(
                sourcePos.offset(-radius, -radius, -radius),
                sourcePos.offset(radius, radius, radius))) {
            if (!sourcelink.getLevel().isLoaded(position)
                    || !(sourcelink.getLevel().getBlockEntity(position)
                    instanceof SourceAmplifierBlockEntity amplifier)
                    || amplifier.isRemoved()) {
                continue;
            }
            long dx = position.getX() - sourcePos.getX();
            long dy = position.getY() - sourcePos.getY();
            long dz = position.getZ() - sourcePos.getZ();
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance
                    || distance == bestDistance
                    && (best == null
                    || position.asLong() < best.getBlockPos().asLong())) {
                bestDistance = distance;
                best = amplifier;
            }
        }
        return best;
    }

    boolean seedDevelopmentTest() {
        if (level == null) {
            return false;
        }
        int radius = ArsNouveauMachineConfig.SOURCE_AMPLIFICATION_RADIUS;
        for (BlockPos position : BlockPos.betweenClosed(
                worldPosition.offset(-radius, -radius, -radius),
                worldPosition.offset(radius, radius, radius))) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof SourcelinkTile sourcelink
                    && isOriginalArsSourcelink(sourcelink)) {
                sourcelink.setSource(1_000);
                if (energyContainer != null) {
                    energyContainer.setEnergy(energyContainer.getMaxEnergy());
                }
                commitOriginalProduction(sourcelink,
                        ArsNouveauMachineConfig.RAW_SOURCE_PER_OPERATION);
                return true;
            }
        }
        return false;
    }
}
