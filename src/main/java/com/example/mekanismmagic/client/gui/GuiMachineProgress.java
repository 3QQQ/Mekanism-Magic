package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.container.MachineArrowLayout;
import com.example.mekanismmagic.container.MachinePanelLayout;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import net.minecraft.client.gui.GuiGraphics;

/** A shorter native factory arrow, preserving its pixel head, warnings and recipe-viewer behavior. */
public final class GuiMachineProgress extends GuiProgress {
    private GuiMachineProgress(IProgressInfoHandler handler, IGuiWrapper gui, MachinePanelLayout.Box bounds) {
        super(handler, ProgressType.DOWN, gui, bounds.x(), bounds.y());
        width = bounds.width();
        height = bounds.height();
    }

    public static GuiMachineProgress factoryDown(IProgressInfoHandler handler, IGuiWrapper gui, int arrowX) {
        return new GuiMachineProgress(handler, gui, MachineArrowLayout.factoryDown(arrowX));
    }

    public MachinePanelLayout.Box displayBounds() {
        return new MachinePanelLayout.Box(relativeX, relativeY, width, height);
    }

    @Override protected double getProgress() {
        double progress = super.getProgress();
        if (Double.isNaN(progress) || progress <= 0) return 0;
        if (progress >= 1) return 1;
        int visibleFill = (int) (progress * MachineArrowLayout.FACTORY_DOWN_HEIGHT);
        int nativeFill = visibleFill > MachineArrowLayout.FACTORY_SHAFT_CUT_START
                ? visibleFill + MachineArrowLayout.FACTORY_SHAFT_CUT_LENGTH : visibleFill;
        // The native renderer truncates progress * nativeHeight. nextUp avoids
        // losing an integer pixel to floating-point division/multiplication.
        return nativeFill == 0 ? 0 : Math.nextUp(nativeFill / (double) type.getHeight());
    }

    @Override public void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int displayWidth = width;
        int displayHeight = height;
        var matrix = graphics.pose().last().pose();
        int left = (int) matrix.m30() + relativeX;
        int top = (int) matrix.m31() + relativeY;
        // Invoke Mekanism's renderer at its actual sprite dimensions. Only
        // shaft pixels are removed; no stretch or head cropping is involved.
        width = type.getWidth();
        height = type.getHeight();
        try {
            renderSlice(graphics, mouseX, mouseY, partialTicks, left, top,
                    displayWidth, 0, MachineArrowLayout.FACTORY_SHAFT_CUT_START, 0);
            renderSlice(graphics, mouseX, mouseY, partialTicks, left, top,
                    displayWidth, MachineArrowLayout.FACTORY_SHAFT_CUT_START, displayHeight,
                    -MachineArrowLayout.FACTORY_SHAFT_CUT_LENGTH);
        } finally {
            width = displayWidth;
            height = displayHeight;
        }
    }

    private void renderSlice(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                             int left, int top, int displayWidth, int from, int to, int shiftY) {
        graphics.enableScissor(left, top + from, left + displayWidth, top + to);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, shiftY, 0);
            super.drawBackground(graphics, mouseX, mouseY, partialTicks);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }
}
