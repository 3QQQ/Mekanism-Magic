package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import com.example.mekanismmagic.client.gui.GuiDictionaryModuleTab;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.OccultismRecipeViewerTypes;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class NativeRitualScreen extends NativeMagicMachineScreen<
        NativeRitualEngineBlockEntity,
        MekanismTileContainer<NativeRitualEngineBlockEntity>> {
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
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                OccultismRecipeViewerTypes.RITUAL};
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
            if (child instanceof GuiSlot guiSlot) {
                if (isDictionaryGuiSlot(guiSlot)) {
                    dictionaryGuiSlots.add(guiSlot);
                } else if (isSacrificeGuiSlot(guiSlot)) {
                    guiSlot.hover(slot -> List.of(Component.translatable(
                            "gui.mekanism_magic.sacrifice")));
                }
            }
        }
        updateDictionarySlotVisibility();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (dictionaryModuleOpen) {
            int left = leftPos + 236;
            int top = topPos + 88;
            MagicGuiTheme.renderDockedPanel(graphics, left, top, 58, 52);
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        if (dictionaryModuleOpen) {
            MagicGuiTheme.renderPanelCaption(graphics, font,
                    Component.translatable(
                            "gui.mekanism_magic.dictionary_short"),
                    236, 88, 58);
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
            // GuiSlot is the visual overlay. Keeping it inactive lets the
            // underlying Mekanism container slot receive mouse clicks.
            slot.active = false;
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY,
                                        int left, int top, int button) {
        if (dictionaryModuleOpen
                && mouseX >= leftPos + 236 && mouseX < leftPos + 294
                && mouseY >= topPos + 88 && mouseY < topPos + 140) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private static boolean isDictionaryGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        return x == 240 && y == 104;
    }

    private static boolean isSacrificeGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        return x == 20 && y == 85;
    }
}
