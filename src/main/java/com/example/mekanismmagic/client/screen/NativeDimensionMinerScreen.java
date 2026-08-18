package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import com.example.mekanismmagic.client.gui.GuiMinerModuleTab;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class NativeDimensionMinerScreen extends NativeMagicMachineScreen<
        NativeDimensionMinerBlockEntity> {
    private final List<GuiSlot> minerGuiSlots = new ArrayList<>();
    private boolean minerModuleOpen;

    public NativeDimensionMinerScreen(
            MekanismTileContainer<NativeDimensionMinerBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 166);
        imageWidth = 210;
        inventoryLabelX = 26;
        getTileEntity().setMinerModuleOpen(false);
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected boolean showProgress() {
        return false;
    }

    @Override
    protected int energyBarX() {
        return 4;
    }

    @Override
    protected int energyBarHeight() {
        return 52;
    }

    @Override
    protected void addMachineGuiElements() {
        addRenderableWidget(new GuiMinerModuleTab(this, getTileEntity(),
                () -> minerModuleOpen, this::toggleMinerModule));
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        minerGuiSlots.clear();
        for (GuiEventListener child : children()) {
            if (child instanceof GuiSlot guiSlot && isMinerGuiSlot(guiSlot)) {
                minerGuiSlots.add(guiSlot);
            }
        }
        updateMinerSlotVisibility();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (minerModuleOpen) {
            int left = leftPos + 216;
            int top = topPos + 88;
            graphics.fill(left, top, left + 58, top + 52, 0xFF151A21);
            graphics.fill(left, top, left + 58, top + 2, 0xFF78838F);
            graphics.fill(left, top, left + 2, top + 52, 0xFF78838F);
            graphics.fill(left + 4, top + 14, left + 54, top + 50,
                    0xFF252B34);
            graphics.fill(left + 56, top, left + 58, top + 52,
                    0xFF090C11);
            graphics.fill(left, top + 50, left + 58, top + 52,
                    0xFF090C11);
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics,
                                      int mouseX, int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        if (minerModuleOpen) {
            graphics.drawString(font,
                    Component.translatable("gui.mekanism_magic.miner"),
                    220, 92, 0xD8DEE8, false);
        }
    }

    private void toggleMinerModule() {
        minerModuleOpen = !minerModuleOpen;
        getTileEntity().setMinerModuleOpen(minerModuleOpen);
        updateMinerSlotVisibility();
    }

    private void updateMinerSlotVisibility() {
        for (GuiSlot slot : minerGuiSlots) {
            slot.visible = minerModuleOpen;
            slot.active = minerModuleOpen;
        }
    }

    private static boolean isMinerGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        return x == 220 && y == 104;
    }
}
