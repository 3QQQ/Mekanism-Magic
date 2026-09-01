package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import com.example.mekanismmagic.client.gui.GuiChalkModuleTab;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.OccultismRecipeViewerTypes;
import com.example.mekanismmagic.container.NativeMiniRitualAssemblerContainer;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class NativeMiniRitualAssemblerScreen
        extends NativeMagicMachineScreen<NativeMiniRitualAssemblerBlockEntity,
        NativeMiniRitualAssemblerContainer> {
    private static final int LOCK_SLOT_X = 30;
    private static final int LOCK_SLOT_Y = 78;
    private final List<GuiSlot> chalkGuiSlots = new ArrayList<>();
    private boolean chalkModuleOpen;

    public NativeMiniRitualAssemblerScreen(
        NativeMiniRitualAssemblerContainer container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = 210;
        inventoryLabelX = 26;
        getTileEntity().setChalkModuleOpen(false);
    }

    @Override
    protected int energyBarX() {
        return imageWidth - 12;
    }

    @Override
    protected int energyBarHeight() {
        return 87;
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
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                OccultismRecipeViewerTypes.MINI_RITUAL};
    }

    @Override
    protected void addMachineGuiElements() {
        addRenderableWidget(new GuiChalkModuleTab(this, getTileEntity(),
                () -> chalkModuleOpen, this::toggleChalkModule));
        addRenderableWidget(new GuiSlot(SlotType.INPUT, this,
                LOCK_SLOT_X, LOCK_SLOT_Y)
                .stored(getTileEntity()::getLockedPentacleStack)
                .setGhostHandler(new IRecipeViewerGhostTarget.IGhostIngredientConsumer() {
                    @Override
                    public Object supportedTarget(Object ingredient) {
                        if (!(ingredient instanceof ItemStack stack)
                                || OccultismRecipeBridge.miniRitualPentacle(stack)
                                .isEmpty()) {
                            return null;
                        }
                        return stack;
                    }

                    @Override
                    public void accept(Object ingredient) {
                        if (!(ingredient instanceof ItemStack stack)) {
                            return;
                        }
                        OccultismRecipeBridge.miniRitualPentacle(stack)
                                .ifPresent(id -> {
                                    if (getTileEntity().getLevel() == null) {
                                        return;
                                    }
                                    int index = OccultismRecipeBridge
                                            .miniRitualPentacleIds(
                                                    getTileEntity().getLevel())
                                            .indexOf(id);
                                    if (index >= 0) {
                                        clickMachineButton(100 + index);
                                    }
                                });
                    }
                }));
    }

    private boolean clickMachineButton(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1
                && mouseX >= leftPos + LOCK_SLOT_X - 1
                && mouseX < leftPos + LOCK_SLOT_X + 19
                && mouseY >= topPos + LOCK_SLOT_Y - 1
                && mouseY < topPos + LOCK_SLOT_Y + 19) {
            clickMachineButton(99);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
            MagicGuiTheme.renderDockedPanel(graphics, left, top, 80, 92);
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        MagicGuiTheme.drawCenteredText(graphics, font,
                Component.translatable(
                        "gui.mekanism_magic.mini_ritual.recipe_lock"),
                LOCK_SLOT_X + 9, 64, MagicGuiTheme.textMuted());
        if (chalkModuleOpen) {
            MagicGuiTheme.renderPanelCaption(graphics, font,
                    Component.translatable("gui.mekanism_magic.chalk"),
                    236, 88, 80);
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
        if (chalkModuleOpen
                && mouseX >= leftPos + 236 && mouseX < leftPos + 316
                && mouseY >= topPos + 88 && mouseY < topPos + 180) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private static boolean isChalkGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        return x >= 240 && x <= 294 && (x - 240) % 18 == 0
                && y >= 104 && y <= 158 && (y - 104) % 18 == 0;
    }
}
