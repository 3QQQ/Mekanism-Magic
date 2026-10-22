package com.example.mekanismmagic.integration.mekenergistics.mixin.client;

import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.container.MachineFrameLayout;
import mekanism.client.gui.GuiMekanism;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Align the native ME-interface entry without changing any other mod's screen. */
@Pseudo
@Mixin(targets = "com.beipuo.mekenergistics.client.overlay.MeInterfaceWindowOverlay", remap = false)
abstract class MekEnergisticsInterfaceTabFrameMixin {
    @ModifyArgs(method = "buttonBounds", at = @At(value = "INVOKE", target =
            "Lcom/beipuo/mekenergistics/client/overlay/MeInterfaceWindowOverlay$ButtonBounds;<init>(II)V"))
    private static void mekanismMagic$alignInterfaceTab(Args args, GuiMekanism<?> gui) {
        if (gui instanceof MagicThemedScreen) {
            args.set(0, (Integer) args.get(0) + MachineFrameLayout.CASE_PADDING);
        }
    }
}
