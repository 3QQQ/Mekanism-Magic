package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau
        .DrygmySimulatorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * These two producers own a native AE2 node. Explicitly suppress any old
 * in-place Mek Energistics runtime so legacy PATTERN_PROVIDER data cannot
 * create a second node or consume a second channel after migration.
 */
@Mixin(value = {
        NativeDimensionMinerBlockEntity.class,
        DrygmySimulatorBlockEntity.class
}, remap = false)
public abstract class MekEnergisticsNativeAeOutputGuardMixin
        implements MeUpgradeRecipeMachineAdapter {
    @Override
    public boolean isMeUpgradeTarget() {
        return false;
    }

    @Override
    public boolean isMeUpgradeActive() {
        return false;
    }
}
