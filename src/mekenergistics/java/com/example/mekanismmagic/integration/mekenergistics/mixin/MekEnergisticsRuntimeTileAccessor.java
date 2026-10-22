package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineRuntime;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 3.0.6 runtime owns the tile, while its upgrade container owns an inner delegate. */
@Mixin(value = MeUpgradeRecipeMachineRuntime.class, remap = false)
public interface MekEnergisticsRuntimeTileAccessor {
    @Accessor("tile")
    TileEntityMekanism mekanismMagic$getUpgradeTile();
}
