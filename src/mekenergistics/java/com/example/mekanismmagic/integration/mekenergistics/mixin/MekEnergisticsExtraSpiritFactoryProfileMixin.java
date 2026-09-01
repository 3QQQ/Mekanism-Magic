package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MePatternAutomationProfiles;
import com.beipuo.mekenergistics.upgrade.MeUpgradeMachineProfile;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Factory adapters contribute their own generic Mekanism recipe profile.
 * Keep a concrete external-host profile on every custom factory so ME uses
 * Mekanism Magic's declared input, persistent and output slots instead of
 * guessing from SMELTING/CRUSHING, and so high-tier subclasses cannot fail
 * with AbstractMethodError.
 */
@Mixin(targets = {
        "com.example.mekanismmagic.blockentity.NativeSpiritFactoryBlockEntity",
        "com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity",
        "com.example.mekanismmagic.integration.mekextras.ExtraSpiritFactoryBlockEntity",
        "com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity"
},
        remap = false)
public abstract class MekEnergisticsExtraSpiritFactoryProfileMixin {
    public MeUpgradeMachineProfile<?> getMeUpgradeProfile() {
        return MePatternAutomationProfiles.forTile(
                (mekanism.common.tile.base.TileEntityMekanism) (Object) this);
    }
}
