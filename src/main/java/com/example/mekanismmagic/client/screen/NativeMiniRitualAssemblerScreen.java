package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import com.example.mekanismmagic.client.gui.GuiChalkModuleTab;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class NativeMiniRitualAssemblerScreen
        extends NativeMagicMachineScreen<NativeMiniRitualAssemblerBlockEntity> {
    private final List<GuiSlot> chalkGuiSlots = new ArrayList<>();
    private boolean chalkModuleOpen;

    public NativeMiniRitualAssemblerScreen(
            MekanismTileContainer<NativeMiniRitualAssemblerBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = 320;
        inventoryLabelX = 80;
        getTileEntity().setChalkModuleOpen(false);
    }

    @Override
    protected int energyBarX() {
        return 198;
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
    protected void addMachineGuiElements() {
        addRenderableWidget(new GuiChalkModuleTab(this, getTileEntity(),
                () -> chalkModuleOpen, this::toggleChalkModule));
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        chalkGuiSlots.clear();
        for (GuiEventListener child : children()) {
            if (child instanceof GuiSlot guiSlot && isChalkGuiSlot(guiSlot)) {
                chalkGuiSlots.add(guiSlot);
            }
        }
        updateChalkSlotVisibility();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (chalkModuleOpen) {
            int left = leftPos + 236;
            int top = topPos + 88;
            graphics.fill(left, top, left + 80, top + 92, 0xFF151A21);
            graphics.fill(left, top, left + 80, top + 2, 0xFF78838F);
            graphics.fill(left, top, left + 2, top + 92, 0xFF78838F);
            graphics.fill(left, top + 90, left + 80, top + 92, 0xFF090C11);
            graphics.fill(left + 78, top, left + 80, top + 92, 0xFF090C11);
            graphics.fill(left + 4, top + 14, left + 76, top + 88, 0xFF252B34);
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        if (chalkModuleOpen) {
            graphics.drawString(font,
                    Component.translatable("gui.mekanism_magic.chalk"),
                    240, 92, 0xD8DEE8, false);
        }
    }

    private void toggleChalkModule() {
        chalkModuleOpen = !chalkModuleOpen;
        getTileEntity().setChalkModuleOpen(chalkModuleOpen);
        updateChalkSlotVisibility();
    }

    private void updateChalkSlotVisibility() {
        for (GuiSlot slot : chalkGuiSlots) {
            slot.visible = chalkModuleOpen;
            slot.active = chalkModuleOpen;
        }
    }

    private static boolean isChalkGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        return x >= 240 && x <= 294 && (x - 240) % 18 == 0
                && y >= 104 && y <= 158 && (y - 104) % 18 == 0;
    }
}
