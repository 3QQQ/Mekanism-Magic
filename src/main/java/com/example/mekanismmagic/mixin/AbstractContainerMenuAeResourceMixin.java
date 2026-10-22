package com.example.mekanismmagic.mixin;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One authenticated menu-button route for native AE input/output controls. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuAeResourceMixin {
    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$toggleNativeAeSupply(
            Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        if (!MachineAeResourceHooks.isConfigurationButton(buttonId)) return;
        if (!(player instanceof ServerPlayer)
                || player.containerMenu != (Object) this
                || !((Object) this instanceof MekanismTileContainer<?> menu)
                || !menu.canPlayerAccess(player)
                || !menu.stillValid(player)
                || !(menu.getTileEntity() instanceof IMekanismMagicAutomation)
                || menu.getTileEntity().isRemoved()
                || player.level().getBlockEntity(
                menu.getTileEntity().getBlockPos()) != menu.getTileEntity()
                || !MachineAeResourceHooks.snapshot(
                menu.getTileEntity()).supported()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(MachineAeResourceHooks.toggleFunction(
                menu.getTileEntity(), buttonId));
    }
}
