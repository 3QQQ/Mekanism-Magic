package com.example.mekanismmagic.mixin.client;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.client.gui.GuiMachineSettingsTab;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.container.MachinePanelLayout.Box;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiElement;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One entry for ordinary machines and all factory families, after their existing tabs and warnings exist. */
@Mixin(value = GuiMekanism.class, remap = false)
abstract class GuiMachineSettingsMixin {
    @Shadow protected abstract <T extends GuiElement> T addRenderableWidget(T element);

    @Inject(method = "init", at = @At("TAIL"))
    private void mekanismMagic$addMachineSettings(CallbackInfo ci) {
        GuiMekanism<?> gui = (GuiMekanism<?>) (Object) this;
        if (!(gui instanceof MagicThemedScreen) || !(gui.getMenu() instanceof MekanismTileContainer<?> menu)
                || !(menu.getTileEntity() instanceof IMekanismMagicAutomation)) return;
        if (gui.children().stream().anyMatch(child -> child instanceof GuiMachineSettingsTab)) return;
        addRenderableWidget(new GuiMachineSettingsTab(gui, menu.getTileEntity(),
                new Box(-26, MagicLeftControlLayout.settingsY(gui), 26, 26)));
    }
}
