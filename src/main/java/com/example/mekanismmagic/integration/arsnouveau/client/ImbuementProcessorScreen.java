package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.gui.GuiCatalystLibraryTab;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MekEnergisticsGuiCompat;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryStorage;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.button.MekanismButton;
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

public final class ImbuementProcessorScreen
        extends ArsSourceMachineScreen<ImbuementProcessorBlockEntity> {
    private final List<GuiSlot> librarySlots = new ArrayList<>();
    private final List<MekanismButton> libraryButtons = new ArrayList<>();
    private GuiSlot catalystLockSlot;
    private boolean libraryOpen;
    private int displayedCatalystPage = -1;
    private int displayedCatalystVisibleSlotCount = -1;
    public ImbuementProcessorScreen(
            MekanismTileContainer<ImbuementProcessorBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 166);
        getTileEntity().setCatalystLibraryOpen(false);
    }

    private void clickMachineButton(int id) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId, id);
        }
    }

    @Override
    protected void addArsMachineGuiElements() {
        addRenderableWidget(new GuiCatalystLibraryTab<>(this, getTileEntity(),
                () -> libraryOpen, this::toggleLibrary));
        int panelLeft = CatalystLibraryLayout.panelLeft(imageWidth);
        catalystLockSlot = addRenderableWidget(
                new GuiSlot(SlotType.INPUT, this,
                CatalystLibraryLayout.activeSlotX(imageWidth) - 1, CatalystLibraryLayout.ACTIVE_SLOT_Y - 1)
                .stored(getTileEntity()::selectedCatalystIdentifier)
                .setGhostHandler(new IRecipeViewerGhostTarget
                        .IGhostIngredientConsumer() {
                    @Override
                    public Object supportedTarget(Object ingredient) {
                        if (!libraryOpen || MekEnergisticsGuiCompat.isPatternWindowOpen(getWindows())) return null;
                        return ingredient instanceof ItemStack stack
                                && stack.is(
                                com.example.mekanismmagic.integration
                                        .arsnouveau.ArsNouveauRegistries
                                        .CATALYST_IDENTIFIER_ITEM.get())
                                ? stack : null;
                    }

                    @Override
                    public void accept(Object ingredient) {
                        if (!libraryOpen || MekEnergisticsGuiCompat.isPatternWindowOpen(getWindows())) return;
                        if (ingredient instanceof ItemStack stack) {
                            var data = stack.get(
                                    net.minecraft.core.component.DataComponents
                                            .CUSTOM_DATA);
                            if (data != null) {
                                int index = catalystRecipeIndex(
                                        data.copyTag().getString(
                                                "catalyst_id"));
                                if (index >= 0) {
                                    clickMachineButton(400 + index);
                                }
                            }
                        }
                    }
                }));
        libraryButtons.clear();
        libraryButtons.add((MekanismButton) addRenderableWidget(
                new MekanismButton(this, panelLeft + 4,
                CatalystLibraryLayout.FOOTER_TOP, 28, 18,
                Component.literal("<"),
                (element, mouseX, mouseY) -> {
                    clickMachineButton(220);
                    return true;
                })));
        libraryButtons.add((MekanismButton) addRenderableWidget(
                new MekanismButton(this, panelLeft + 66,
                CatalystLibraryLayout.FOOTER_TOP, 28, 18,
                Component.literal(">"),
                (element, mouseX, mouseY) -> {
                    clickMachineButton(221);
                    return true;
                })));
        updateLibraryVisibility();
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        librarySlots.clear();
        for (GuiEventListener child : children()) {
            if (child instanceof GuiSlot guiSlot
                    && isCatalystLibraryGuiSlot(guiSlot)) {
                librarySlots.add(guiSlot);
            }
        }
        updateLibraryVisibility();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        MekEnergisticsGuiCompat.updateMachineSlot(
                getWindows(), catalystLockSlot, libraryOpen);
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (libraryOpen) {
            int left = leftPos + CatalystLibraryLayout.panelLeft(imageWidth);
            int top = topPos + CatalystLibraryLayout.FRAME_TOP;
            MagicGuiTheme.renderDockedPanel(graphics, left, top,
                    CatalystLibraryLayout.PANEL_WIDTH,
                    CatalystLibraryLayout.FRAME_HEIGHT, CatalystLibraryLayout.HEADER_HEIGHT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MekEnergisticsGuiCompat.updateMachineSlot(
                getWindows(), catalystLockSlot, libraryOpen)
                && button == 1
                && CatalystLibraryLayout.containsActiveSlot(imageWidth, mouseX - leftPos, mouseY - topPos)) {
            clickMachineButton(399);
            return true;
        }
        if (libraryOpen && button == 1) {
            int windowSlot = CatalystLibraryLayout.pageSlotAt(imageWidth, mouseX - leftPos, mouseY - topPos);
            if (windowSlot >= 0) {
                int index = CatalystLibraryStorage.absoluteIndex(
                        getTileEntity().catalystPage(), windowSlot);
                if (index < getTileEntity().catalystVisibleSlotCount()) {
                    clickMachineButton(300 + windowSlot);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleLibrary() {
        libraryOpen = !libraryOpen;
        getTileEntity().setCatalystLibraryOpen(libraryOpen);
        updateLibraryVisibility();
    }

    private void updateLibraryVisibility() {
        MekEnergisticsGuiCompat.updateMachineSlot(getWindows(), catalystLockSlot, libraryOpen);
        int currentPage = getTileEntity().catalystPage();
        int visibleSlotCount = getTileEntity().catalystVisibleSlotCount();
        displayedCatalystPage = currentPage;
        displayedCatalystVisibleSlotCount = visibleSlotCount;
        for (int index = 0; index < librarySlots.size(); index++) {
            GuiSlot slot = librarySlots.get(index);
            int absoluteIndex = CatalystLibraryStorage.absoluteIndex(
                    currentPage, index);
            slot.visible = libraryOpen
                    && absoluteIndex < visibleSlotCount;
            slot.active = false;
        }
        for (MekanismButton button : libraryButtons) {
            button.visible = libraryOpen;
            button.active = libraryOpen;
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (libraryOpen && (displayedCatalystPage
                != getTileEntity().catalystPage()
                || displayedCatalystVisibleSlotCount
                != getTileEntity().catalystVisibleSlotCount())) {
            updateLibraryVisibility();
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY,
                                        int left, int top, int button) {
        int panelLeft = leftPos
                + CatalystLibraryLayout.panelLeft(imageWidth);
        int panelTop = topPos + CatalystLibraryLayout.FRAME_TOP;
        if (libraryOpen && mouseX >= panelLeft
                && mouseX < panelLeft + CatalystLibraryLayout.PANEL_WIDTH
                && mouseY >= panelTop
                && mouseY < panelTop + CatalystLibraryLayout.FRAME_HEIGHT) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private int catalystRecipeIndex(String catalystId) {
        return com.example.mekanismmagic.integration.arsnouveau
                .ArsNouveauRecipeBridge.catalystIdentifierJeiIndex(
                        getTileEntity().getLevel(), catalystId);
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                ArsRecipeViewerTypes.IDENTIFIER_IMBUEMENT};
    }

    private boolean isCatalystLibraryGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        int slotLeft = CatalystLibraryLayout.slotLeft(imageWidth);
        int rows = Math.ceilDiv(CatalystLibraryLayout.PAGE_SIZE,
                CatalystLibraryLayout.COLUMNS);
        return x >= slotLeft
                && x <= slotLeft + (CatalystLibraryLayout.COLUMNS - 1)
                * CatalystLibraryLayout.SLOT_SPACING
                && (x - slotLeft) % CatalystLibraryLayout.SLOT_SPACING == 0
                && y >= CatalystLibraryLayout.SLOT_TOP
                && y <= CatalystLibraryLayout.SLOT_TOP
                + (rows - 1) * CatalystLibraryLayout.SLOT_SPACING
                && (y - CatalystLibraryLayout.SLOT_TOP)
                % CatalystLibraryLayout.SLOT_SPACING == 0;
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX,
                                      int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        if (libraryOpen) {
            int panelLeft = CatalystLibraryLayout.panelLeft(imageWidth);
            MagicGuiTheme.renderPanelCaption(graphics, font,
                    Component.translatable("gui.mekanism_magic.catalysts"),
                    panelLeft, CatalystLibraryLayout.FRAME_TOP,
                    CatalystLibraryLayout.PANEL_WIDTH - 26, CatalystLibraryLayout.HEADER_HEIGHT);
            MagicGuiTheme.renderPageNumber(graphics, font,
                    getTileEntity().catalystPage(),
                    getTileEntity().catalystPageCount(), panelLeft + 49,
                    CatalystLibraryLayout.FOOTER_TOP + 5);
        }
    }
}
