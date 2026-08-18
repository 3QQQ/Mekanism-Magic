package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/**
 * Mekanism-style side tab that expands the assembler's chalk inventory.
 */
public final class GuiChalkModuleTab
        extends GuiInsetElement<NativeMiniRitualAssemblerBlockEntity> {
    private static final ResourceLocation CLOSED_ICON =
            new ResourceLocation("occultism", "textures/item/chalk_white.png");
    private static final ResourceLocation OPEN_ICON =
            new ResourceLocation("occultism", "textures/item/chalk_purple.png");

    private final BooleanSupplier open;
    private final Runnable toggle;

    public GuiChalkModuleTab(IGuiWrapper gui,
                             NativeMiniRitualAssemblerBlockEntity tile,
                             BooleanSupplier open, Runnable toggle) {
        super(CLOSED_ICON, gui, tile, gui.getWidth(), 80, 26, 18, false);
        this.open = open;
        this.toggle = toggle;
        setTooltip(Tooltip.create(Component.translatable(
                "gui.mekanism_magic.chalk_module")));
    }

    @Override
    protected ResourceLocation getOverlay() {
        return open.getAsBoolean() ? OPEN_ICON : CLOSED_ICON;
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        MekanismRenderer.color(graphics, 0xFFB879D6);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        toggle.run();
    }
}
