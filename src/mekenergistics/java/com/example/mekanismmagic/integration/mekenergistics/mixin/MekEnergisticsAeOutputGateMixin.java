package com.example.mekanismmagic.integration.mekenergistics.mixin;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.blockentity.support.io.MeOutputPort;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Gates new machine-output drains without changing patterns, feeding or recovery debts. */
@Mixin(value = AbstractMeAeSupport.class, remap = false)
public abstract class MekEnergisticsAeOutputGateMixin {
    @Shadow @Final protected TileEntityMekanism ownerTile;

    @ModifyVariable(method = "drainOutputPorts(Lcom/beipuo/mekenergistics/blockentity/api/AeOutputMode;Ljava/util/List;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<? extends MeOutputPort> mekanismMagic$filterDisabledOutputPorts(
            List<? extends MeOutputPort> ports) {
        if (!(ownerTile instanceof IMekanismMagicAutomation)) return ports;
        return ports.stream().filter(port -> {
            var key = port.key();
            if (key instanceof AEItemKey) return MachineAeResourceHooks.isOutputEnabled(
                    ownerTile, MachineAeResourceHooks.OUTPUT_ITEMS);
            if (key instanceof AEFluidKey) return MachineAeResourceHooks.isOutputEnabled(
                    ownerTile, MachineAeResourceHooks.OUTPUT_FLUIDS);
            return true;
        }).toList();
    }
}
