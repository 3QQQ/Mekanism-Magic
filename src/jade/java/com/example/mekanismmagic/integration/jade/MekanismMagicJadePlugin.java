package com.example.mekanismmagic.integration.jade;

import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Optional discovery entry point; never referenced by common mod startup. */
@WailaPlugin
public final class MekanismMagicJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(
                MagicMachineJadeProvider.INSTANCE, TileEntityMekanism.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(
                MagicMachineJadeProvider.INSTANCE, Block.class);
    }
}
