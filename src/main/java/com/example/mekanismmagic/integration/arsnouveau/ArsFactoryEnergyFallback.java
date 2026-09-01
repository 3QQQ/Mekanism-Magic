package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/** Applies the shared FE-or-Source processing rule to imbuement factories. */
public final class ArsFactoryEnergyFallback {
    private ArsFactoryEnergyFallback() {
    }

    public static long energyRequirement(IEnergyContainer energyContainer,
                                         long energyPerTick) {
        return isPowered(energyContainer, energyPerTick) ? energyPerTick : 0;
    }

    public static void limitOperations(
            OperationTracker tracker, @Nullable Level level,
            IEnergyContainer energyContainer, long energyPerTick,
            int sourceCost, BooleanSupplier sourceAvailable,
            boolean creativeSource) {
        if (isPowered(energyContainer, energyPerTick)) {
            return;
        }
        boolean canUseSource = sourceCost > 0 && !creativeSource
                && sourceAvailable.getAsBoolean();
        if (!canUseSource) {
            tracker.updateOperations(0);
            tracker.addError(OperationTracker.RecipeError.NOT_ENOUGH_ENERGY);
            return;
        }
        tracker.addError(OperationTracker.RecipeError
                .NOT_ENOUGH_ENERGY_REDUCED_RATE);
        if (level == null || level.getGameTime()
                % ArsNouveauMachineConfig.ENERGYLESS_TICK_INTERVAL != 0) {
            tracker.updateOperations(0);
        }
    }

    private static boolean isPowered(IEnergyContainer energyContainer,
                                     long energyPerTick) {
        return energyContainer.getEnergy() >= Math.max(0, energyPerTick);
    }
}
