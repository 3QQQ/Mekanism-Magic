package com.example.mekanismmagic.integration.mekenergistics.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import com.beipuo.mekenergistics.blockentity.api.MeAeSupportOwner;
import com.beipuo.mekenergistics.blockentity.support.MeNetworkEnergyHelper;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversionHelper;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The shared switch also gates MekE's direct recipe-energy and refill paths. */
@Mixin(value = MeNetworkEnergyHelper.class, remap = false)
public abstract class MekEnergisticsAeSupplyGateMixin {
    @Inject(method = "extractNetworkFe", at = @At("HEAD"),
            cancellable = true)
    private static void mekanismMagic$gateAeEnergyForOurMachines(
            IGrid grid, IActionSource source, long requested,
            Action action, CallbackInfoReturnable<Long> cir) {
        if (source == null) return;
        TileEntityMekanism tile = ownerTile(source);
        if (tile instanceof IMekanismMagicAutomation
                && !MachineAeResourceHooks.isInputEnabled(tile, MachineAeResourceHooks.ENERGY)) {
            cir.setReturnValue(0L);
        }
    }

    @Inject(method = "extractNetworkFe", at = @At("RETURN"))
    private static void mekanismMagic$trackAeEnergyForOurMachines(
            IGrid grid, IActionSource source, long requested,
            Action action, CallbackInfoReturnable<Long> cir) {
        long receivedFe = cir.getReturnValue();
        if (source == null || !action.execute()
                || receivedFe <= 0L) return;
        TileEntityMekanism tile = ownerTile(source);
        if (tile instanceof IMekanismMagicAutomation) {
            long joules = IEnergyConversionHelper.INSTANCE
                    .feConversion().convertFrom(receivedFe);
            MachineAeResourceHooks.recordTransfer(tile,
                    MachineAeResourceHooks.ENERGY, joules);
        }
    }

    private static TileEntityMekanism ownerTile(IActionSource source) {
        IActionHost host = source.machine().orElse(null);
        return host instanceof MeAeSupportOwner owner
                ? owner.getAeOwnerTile()
                : host instanceof TileEntityMekanism machine
                ? machine : null;
    }
}
