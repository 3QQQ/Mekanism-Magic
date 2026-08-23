package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.common.block.tile.SourcelinkTile;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/**
 * Uses Mekanism energy to amplify Source produced by nearby vanilla
 * Ars Nouveau sourcelinks.
 */
public final class SourceAmplifierBlockEntity
        extends ArsSourceMachineBlockEntity {
    private BlockPos cachedSourcelinkPosition;
    private long nextSourcelinkScan;

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
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    @Override
    protected int sourceMaxReceive() {
        // Prevent network feedback: raw Source is taken only from an actual
        // nearby Ars Nouveau sourcelink.
        return 0;
    }

    @Override
    public boolean canAcceptSource() {
        return false;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = nativeBaseUpdate();
        setActive(false);
        progressRequired = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        ArsNouveauMachineConfig.SOURCE_AMPLIFICATION_DURATION));
        if (getMaxSource() - getSource()
                < ArsNouveauMachineConfig.AMPLIFIED_SOURCE_PER_OPERATION) {
            progress = 0;
            return changed;
        }
        SourcelinkTile sourcelink = findSourceLink();
        if (sourcelink == null
                || sourcelink.getSource()
                < ArsNouveauMachineConfig.RAW_SOURCE_PER_OPERATION) {
            progress = 0;
            return changed;
        }
        long usage = mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
        if (energyContainer == null || energyContainer.getEnergy() < usage) {
            return changed;
        }
        setActive(true);
        energyContainer.extract(usage, Action.EXECUTE,
                AutomationType.INTERNAL);
        progress++;
        if (progress >= progressRequired) {
            if (!isUsableSourcelink(sourcelink)
                    || getMaxSource() - getSource()
                    < ArsNouveauMachineConfig
                    .AMPLIFIED_SOURCE_PER_OPERATION) {
                progress = 0;
                setActive(false);
                return changed;
            }
            sourcelink.removeSource(
                    ArsNouveauMachineConfig.RAW_SOURCE_PER_OPERATION);
            addSource(
                    ArsNouveauMachineConfig.AMPLIFIED_SOURCE_PER_OPERATION);
            progress = 0;
            changed = true;
        }
        return changed;
    }

    private SourcelinkTile findSourceLink() {
        if (level == null) {
            return null;
        }
        if (cachedSourcelinkPosition != null
                && level.isLoaded(cachedSourcelinkPosition)) {
            BlockEntity cached =
                    level.getBlockEntity(cachedSourcelinkPosition);
            if (cached instanceof SourcelinkTile sourcelink
                    && isUsableSourcelink(sourcelink)) {
                return sourcelink;
            }
            cachedSourcelinkPosition = null;
        }
        if (level.getGameTime() < nextSourcelinkScan) {
            return null;
        }
        nextSourcelinkScan = level.getGameTime() + 20;
        int radius = ArsNouveauMachineConfig.SOURCE_AMPLIFICATION_RADIUS;
        SourcelinkTile best = null;
        int mostSource = 0;
        for (BlockPos position : BlockPos.betweenClosed(
                worldPosition.offset(-radius, -radius, -radius),
                worldPosition.offset(radius, radius, radius))) {
            if (!level.isLoaded(position)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof SourcelinkTile candidate
                    && candidate.getSource() > mostSource) {
                best = candidate;
                mostSource = candidate.getSource();
            }
        }
        cachedSourcelinkPosition =
                best == null ? null : best.getBlockPos().immutable();
        return best;
    }

    private static boolean isUsableSourcelink(
            SourcelinkTile sourcelink) {
        return !sourcelink.isRemoved()
                && !sourcelink.isDisabled
                && sourcelink.getSource()
                >= ArsNouveauMachineConfig.RAW_SOURCE_PER_OPERATION;
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
            if (blockEntity instanceof SourcelinkTile sourcelink) {
                sourcelink.setSource(1_000);
                cachedSourcelinkPosition = position.immutable();
                if (energyContainer != null) {
                    energyContainer.setEnergy(
                            energyContainer.getMaxEnergy());
                }
                progressRequired = Math.max(1,
                        mekanism.common.util.MekanismUtils.getTicks(
                                this,
                                ArsNouveauMachineConfig
                                        .SOURCE_AMPLIFICATION_DURATION));
                progress = Math.max(0, progressRequired - 1);
                return true;
            }
        }
        return false;
    }
}
