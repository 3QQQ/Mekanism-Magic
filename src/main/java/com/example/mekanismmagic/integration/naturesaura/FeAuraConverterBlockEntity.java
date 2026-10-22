package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.ModConfig;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.Upgrade;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Converts real FE to locally typed Aura without changing world Aura. */
public final class FeAuraConverterBlockEntity
        extends NatureAuraGeneratorBlockEntity {
    private static final int UPGRADE_REFERENCE_TICKS = 20;
    private long syncedFePerAura = NatureAuraMachineConfig.FE_CONVERTER_FE_PER_AURA;
    private int syncedGenerationLimit = NatureAuraMachineConfig.FE_CONVERTER_AURA_PER_TICK;

    public FeAuraConverterBlockEntity(BlockPos pos, BlockState state) {
        super(NaturesAuraRegistries.FE_AURA_CONVERTER_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        setupNativeItemIO(List.of(), List.of(), List.of());
    }

    @Override
    protected int energySlotX() {
        return 28;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    /** Registry energy rates are Joules, while the conversion itself uses FE. */
    public static long baseEnergyUsage() {
        return energyForAura(NatureAuraMachineConfig.FE_CONVERTER_AURA_PER_TICK,
                NatureAuraMachineConfig.FE_CONVERTER_FE_PER_AURA);
    }

    @Override
    protected int baseEnergyPerTick() {
        return (int) Math.min(Integer.MAX_VALUE, baseEnergyUsage());
    }

    public int getGenerationLimit() {
        if (level != null && level.isClientSide()) {
            return syncedGenerationLimit;
        }
        int ticks = Math.max(1, MekanismUtils.getTicks(this,
                UPGRADE_REFERENCE_TICKS));
        return Math.max(1, NatureAuraMachineConfig.FE_CONVERTER_AURA_PER_TICK
                * UPGRADE_REFERENCE_TICKS / ticks);
    }

    public long getFePerAura() {
        if (level != null && level.isClientSide()) {
            return syncedFePerAura;
        }
        long usage = Math.max(1L, MekanismUtils.getEnergyPerTick(this,
                NatureAuraMachineConfig.FE_CONVERTER_BASE_FE_PER_TICK));
        int limit = Math.max(1, getGenerationLimit());
        long upgradedCost = usage / limit + (usage % limit == 0 ? 0 : 1);
        double nativeRatio = ModConfig.instance == null
                || ModConfig.instance.auraToRFRatio == null
                ? 0.05D : ModConfig.instance.auraToRFRatio.get();
        // The native converter creates aura * ratio FE. Remain strictly more
        // expensive even after upgrades or edits to Nature's Aura's config.
        long loopSafeMinimum = !Double.isFinite(nativeRatio)
                || nativeRatio >= Long.MAX_VALUE - 1D
                ? Long.MAX_VALUE
                : Math.max(1L, (long) Math.floor(nativeRatio) + 1L);
        return Math.max(upgradedCost, loopSafeMinimum);
    }

    @Override
    protected void beforeGeneratorTick(ServerLevel serverLevel) {
        refreshEnergyRate();
    }

    @Override
    protected int generateAura(ServerLevel serverLevel) {
        if (energyContainer == null) {
            return 0;
        }
        long fePerAura = getFePerAura();
        int generationLimit = getGenerationLimit();
        long nominalUsage = energyForAura(generationLimit, fePerAura);
        // Mekanism syncs this field for its energy tab. It must describe this
        // converter's actual rounded rate, including the native loop floor.
        energyContainer.setEnergyPerTick(nominalUsage);
        int requested = Math.min(generationLimit,
                getAuraCapacity() - getStoredAura());
        if (requested <= 0 || !storeAuraExact(requested, true)) {
            return 0;
        }
        long available = energyContainer.getEnergy();
        int low = 0;
        int high = requested;
        while (low < high) {
            int candidate = low + (high - low + 1) / 2;
            long cost = energyForAura(candidate, fePerAura);
            if (cost < Long.MAX_VALUE && cost <= available) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        int produced = low;
        if (produced <= 0) {
            setNotEnoughEnergyWarning(true);
            return 0;
        }
        setReducedEnergyWarning(produced < requested);
        long usage = energyForAura(produced, fePerAura);
        if (energyContainer.extract(usage, Action.SIMULATE,
                AutomationType.INTERNAL) != usage) {
            return 0;
        }
        long extracted = energyContainer.extract(usage, Action.EXECUTE,
                AutomationType.INTERNAL);
        if (extracted != usage || !storeAuraExact(produced, false)) {
            refundProcessingEnergy(extracted);
            return 0;
        }
        return produced;
    }

    /** Round once per committed batch, including nonintegral J/FE settings. */
    private static long energyForAura(int aura, long fePerAura) {
        if (aura <= 0) {
            return 0;
        }
        double joules = aura * (double) fePerAura
                * EnergyUnit.FORGE_ENERGY.getConversion();
        if (!Double.isFinite(joules) || joules >= Long.MAX_VALUE
                || joules <= 0D) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(joules));
    }

    @Override
    protected void onNativeUpgradeChanged(Upgrade upgrade) {
        refreshEnergyRate();
    }

    private void refreshEnergyRate() {
        if (energyContainer != null) {
            energyContainer.setEnergyPerTick(energyForAura(
                    getGenerationLimit(), getFePerAura()));
        }
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableLong.create(this::getFePerAura,
                value -> syncedFePerAura = Math.max(1L, value)));
        container.track(SyncableInt.create(this::getGenerationLimit,
                value -> syncedGenerationLimit = Math.max(1, value)));
    }
}
