package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MePatternAutomationProfiles;
import com.beipuo.mekenergistics.upgrade.MeUpgradeMachineProfile;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mekanism Extras' factory adapter can add the external upgrade interface to
 * a subclass without inheriting the interface default profile method. Keep a
 * concrete profile on the high-tier spirit factory so AE2 node readiness
 * cannot fail with AbstractMethodError.
 */
@Mixin(targets = {
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
