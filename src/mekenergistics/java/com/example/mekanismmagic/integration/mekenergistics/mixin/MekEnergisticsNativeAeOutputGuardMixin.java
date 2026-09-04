package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau
        .DrygmySimulatorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau
        .ImbuementProcessorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * These machines own a native AE2 node. Explicitly suppress any in-place
 * Mek Energistics runtime so legacy PATTERN_PROVIDER data cannot create a
 * second node, duplicate a crafting provider, or consume another channel.
 */
@Mixin(value = {
        NativeDimensionMinerBlockEntity.class,
        DrygmySimulatorBlockEntity.class,
        ImbuementProcessorBlockEntity.class
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
