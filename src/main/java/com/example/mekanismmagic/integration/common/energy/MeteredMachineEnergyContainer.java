package com.example.mekanismmagic.integration.common.energy;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.block.attribute.AttributeEnergy;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongSupplier;

/** Native input container with unchanged billing/capabilities and actual INTERNAL debit metering. */
public final class MeteredMachineEnergyContainer<TILE extends TileEntityMekanism> extends MachineEnergyContainer<TILE> {
    private final ProcessingEnergyUsage usage = new ProcessingEnergyUsage();
    private final LongSupplier tickClock;

    public static <T extends TileEntityMekanism> MeteredMachineEnergyContainer<T> forInput(T tile, @Nullable IContentsListener listener) {
        return new MeteredMachineEnergyContainer<>(tile, listener,
                () -> tile.getLevel() == null ? 0L : tile.getLevel().getGameTime());
    }

    // Clock injection keeps accounting tests deterministic without changing server/world time.
    MeteredMachineEnergyContainer(TILE tile, @Nullable IContentsListener listener, LongSupplier tickClock) {
        this(validateBlock(tile), tile, listener, tickClock);
    }

    private MeteredMachineEnergyContainer(AttributeEnergy attribute, TILE tile, @Nullable IContentsListener listener, LongSupplier tickClock) {
        super(attribute.getStorage(), attribute.getUsage(), automation -> automation != AutomationType.EXTERNAL,
                automation -> true, tile, listener);
        this.tickClock = tickClock;
    }

    @Override
    public long extract(long amount, Action action, AutomationType automationType) {
        long extracted = super.extract(amount, action, automationType);
        if (action.execute() && automationType == AutomationType.INTERNAL && extracted > 0) {
            usage.recordExtraction(tickClock.getAsLong(), extracted);
        }
        return extracted;
    }

    /** Restore a same-tick failed/partly committed operation, not an ordinary energy input. */
    public long refundInternalEnergy(long amount) {
        if (amount <= 0) return 0;
        long remainder = super.insert(amount, Action.EXECUTE, AutomationType.INTERNAL);
        long restored = amount - remainder;
        usage.recordRefund(tickClock.getAsLong(), restored);
        return restored;
    }

    public long getLastProcessingUsage() { return usage.lastCompletedTick(tickClock.getAsLong()); }
    public void clearProcessingUsage() { usage.clear(); }
}
