package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import com.example.mekanismmagic.client.gui.GuiDictionaryModuleTab;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class NativeRitualScreen extends NativeMagicMachineScreen<
        NativeRitualEngineBlockEntity> {
    private final List<GuiSlot> dictionaryGuiSlots = new ArrayList<>();
    private boolean dictionaryModuleOpen;

    public NativeRitualScreen(MekanismTileContainer<NativeRitualEngineBlockEntity> container,
                              Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = 210;
        inventoryLabelX = 26;
        getTileEntity().setDictionaryModuleOpen(false);
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected int workProgressX() {
        return 145;
    }

    @Override
    protected int workProgressY() {
        return 62;
    }

    @Override
    protected mekanism.client.gui.element.progress.ProgressType progressType() {
        return mekanism.client.gui.element.progress.ProgressType.SMALL_RIGHT;
    }

    @Override
    protected int energyBarHeight() {
        return 87;
    }

    @Override
    protected void addMachineGuiElements() {
        addRenderableWidget(new GuiDictionaryModuleTab(this, getTileEntity(),
                () -> dictionaryModuleOpen, this::toggleDictionaryModule));
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        dictionaryGuiSlots.clear();
        for (GuiEventListener child : children()) {
            if (child instanceof GuiSlot guiSlot && isDictionaryGuiSlot(guiSlot)) {
                dictionaryGuiSlots.add(guiSlot);
            }
        }
        updateDictionarySlotVisibility();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (dictionaryModuleOpen) {
            int left = leftPos + 216;
            int top = topPos + 88;
            graphics.fill(left, top, left + 58, top + 52, 0xFF151A21);
            graphics.fill(left, top, left + 58, top + 2, 0xFF78838F);
            graphics.fill(left, top, left + 2, top + 52, 0xFF78838F);
            graphics.fill(left + 4, top + 14, left + 54, top + 50, 0xFF252B34);
            graphics.fill(left + 56, top, left + 58, top + 52, 0xFF090C11);
            graphics.fill(left, top + 50, left + 58, top + 52, 0xFF090C11);
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        if (dictionaryModuleOpen) {
            graphics.drawString(font,
                    Component.translatable("gui.mekanism_magic.dictionary"),
                    220, 92, 0xD8DEE8, false);
        }
    }

    private void toggleDictionaryModule() {
        dictionaryModuleOpen = !dictionaryModuleOpen;
        getTileEntity().setDictionaryModuleOpen(dictionaryModuleOpen);
        updateDictionarySlotVisibility();
    }

    private void updateDictionarySlotVisibility() {
        for (GuiSlot slot : dictionaryGuiSlots) {
            slot.visible = dictionaryModuleOpen;
            slot.active = dictionaryModuleOpen;
        }
    }

    private static boolean isDictionaryGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        return x == 220 && y == 104;
    }
}
