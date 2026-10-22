package com.example.mekanismmagic.mixin.client;

import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.container.MachineFrameLayout;
import mekanism.client.gui.GuiMekanism;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** The visible native bevel and inventory outside-click bounds use the same padded case. */
@Mixin(value = GuiMekanism.class, remap = false)
abstract class GuiMachineFrameMixin {
    @ModifyArgs(method = "renderBg", at = @At(value = "INVOKE", target =
            "Lmekanism/client/gui/GuiUtils;renderBackgroundTexture(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"))
    private void mekanismMagic$padMachineCase(Args args) {
        if (!((Object) this instanceof MagicThemedScreen)) return;
        int padding = MachineFrameLayout.CASE_PADDING;
        args.set(4, (Integer) args.get(4) - padding);
        args.set(5, (Integer) args.get(5) - padding);
        args.set(6, (Integer) args.get(6) + 2 * padding);
        args.set(7, (Integer) args.get(7) + 2 * padding);
    }

    @Inject(method = "hasClickedOutside", at = @At("RETURN"), cancellable = true)
    private void mekanismMagic$keepPaddedBorderInside(double mouseX, double mouseY, int left, int top,
                                                       int button, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() || !((Object) this instanceof MagicThemedScreen)) return;
        GuiMekanism<?> gui = (GuiMekanism<?>) (Object) this;
        if (MachineFrameLayout.containsFrame(mouseX, mouseY, gui.getGuiLeft(), gui.getGuiTop(),
                gui.getXSize(), gui.getYSize())) callback.setReturnValue(false);
    }
}
