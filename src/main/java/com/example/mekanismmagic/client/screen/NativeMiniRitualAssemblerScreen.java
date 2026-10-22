package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import com.example.mekanismmagic.client.gui.GuiChalkModuleTab;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.OccultismRecipeViewerTypes;
import com.example.mekanismmagic.container.NativeMiniRitualAssemblerContainer;
import com.example.mekanismmagic.container.DockedPanelLayout;
import com.example.mekanismmagic.container.MachinePanelLayout;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class NativeMiniRitualAssemblerScreen
        extends NativeMagicMachineScreen<NativeMiniRitualAssemblerBlockEntity,
        NativeMiniRitualAssemblerContainer> {
    private final List<GuiSlot> chalkGuiSlots = new ArrayList<>();
    private boolean chalkModuleOpen;
    private static final DockedPanelLayout PANEL = DockedPanelLayout.CHALK;

    public NativeMiniRitualAssemblerScreen(
        NativeMiniRitualAssemblerContainer container,
            Inventory inventory, Component title) {
        super(container, inventory, title, MachinePanelLayout.MINI_RITUAL_HEIGHT);
        imageWidth = MachinePanelLayout.MINI_RITUAL_WIDTH;
        inventoryLabelX = MachinePanelLayout.inventoryX(imageWidth);
        getTileEntity().setChalkModuleOpen(false);
    }

    @Override
    protected int energyBarX() {
        return imageWidth - 12;
    }

    @Override
    protected int energyBarHeight() {
        return 75;
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected int workProgressX() {
        return MachinePanelLayout.MINI_RITUAL_PROGRESS_X;
    }

    @Override
    protected int workProgressY() {
        return MachinePanelLayout.MINI_RITUAL_PROGRESS_Y;
    }

    @Override
    protected mekanism.client.gui.element.progress.ProgressType progressType() {
        return mekanism.client.gui.element.progress.ProgressType.SMALL_RIGHT;
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                OccultismRecipeViewerTypes.MINI_RITUAL};
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
            MagicGuiTheme.renderDockedPanel(graphics, leftPos + PANEL.x(), topPos + PANEL.y(), PANEL.width(), PANEL.height());
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        if (chalkModuleOpen) {
            MagicGuiTheme.renderPanelCaption(graphics, font,
                    Component.translatable("gui.mekanism_magic.chalk"),
                    PANEL.x(), PANEL.y(), PANEL.width());
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
            // GuiSlot is the visual overlay. Keeping it inactive lets the
            // underlying Mekanism container slot receive mouse clicks.
            slot.active = false;
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY,
                                        int left, int top, int button) {
        if (chalkModuleOpen && PANEL.contains(mouseX - leftPos, mouseY - topPos)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private static boolean isChalkGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        return PANEL.isGridSlot(x, y, 16);
    }
}
