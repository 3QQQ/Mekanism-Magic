package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.container.ImbuementFactoryContainer;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.client.gui.ArsIntegratedSideConfig;
import com.example.mekanismmagic.client.gui.GuiCatalystLibraryTab;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTheme.MachineStatus;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.MekEnergisticsGuiCompat;
import com.example.mekanismmagic.client.gui.FactoryInputRow;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryLayout;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import com.example.mekanismmagic.client.gui.GuiMachineProgress;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiSortingTab;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class ImbuementFactoryScreen extends GuiConfigurableTile<
        ImbuementFactoryBlockEntity, ImbuementFactoryContainer>
        implements MagicThemedScreen {
    private final List<GuiSlot> librarySlots = new ArrayList<>();
    private final List<MekanismButton> libraryButtons = new ArrayList<>();
    private GuiSlot catalystLockSlot;
    private boolean libraryOpen;
    private int displayedCatalystPage = -1;
    private int displayedCatalystVisibleSlotCount = -1;
    private FactoryInputRow factoryInputRow;

    public ImbuementFactoryScreen(ImbuementFactoryContainer container,
                                  Inventory inventory, Component title) {
        super(container, inventory, title);
        imageHeight = MachinePanelLayout.FACTORY_HEIGHT;
        inventoryLabelY = MachinePanelLayout.inventoryLabelY(imageHeight);
        titleLabelY = MagicGuiTheme.MACHINE_TITLE_Y;
        dynamicSlots = true;
        imageWidth = ImbuementFactoryLayout.standardImageWidth(
                getTileEntity().tier);
        inventoryLabelX = ImbuementFactoryLayout.standardInventoryX(
                getTileEntity().tier);
        getTileEntity().setCatalystLibraryOpen(false);
    }

    @Override
    protected void addGuiElements() {
        factoryInputRow = FactoryInputRow.prepare(factoryInputRow, menu);
        super.addGuiElements();
        addRenderableWidget(new GuiSortingTab(this, getTileEntity()));
        addRenderableWidget(new GuiSourceBar(this, getTileEntity(),
                imageWidth - 20, ImbuementFactoryLayout.BAR_Y,
                ImbuementFactoryLayout.BAR_HEIGHT));
        ArsIntegratedSideConfig.install(this, getTileEntity(), children(),
                this::addRenderableWidget);
        addRenderableWidget(new GuiCatalystLibraryTab<>(this,
                getTileEntity(), () -> libraryOpen, this::toggleLibrary));
        catalystLockSlot = addRenderableWidget(
                new GuiSlot(SlotType.EXTRA, this,
                        CatalystLibraryLayout.activeSlotX(imageWidth) - 1,
                        CatalystLibraryLayout.ACTIVE_SLOT_Y - 1));
        configureLockGuiSlot(catalystLockSlot);
        addLibraryButtons();
        BooleanSupplier energyWarning = getTileEntity().getWarningCheck(
                RecipeError.NOT_ENOUGH_ENERGY, 0);
        addRenderableWidget(new GuiVerticalPowerBar(this,
                getTileEntity().getEnergyContainer(), imageWidth - 12,
                ImbuementFactoryLayout.BAR_Y,
                ImbuementFactoryLayout.BAR_HEIGHT))
                .warning(WarningType.NOT_ENOUGH_ENERGY,
                        energyWarning);
        addRenderableWidget(MagicLeftControlLayout.alignEnergyTab(this,
                new GuiEnergyTab(this,
                getTileEntity().getEnergyContainer(),
                getTileEntity()::getLastUsage)));
        int processes = getTileEntity().tier.processes;
        int firstX = ImbuementFactoryLayout.firstProcessX(
                getTileEntity().tier);
        int spacing = ImbuementFactoryLayout.processSpacing(
                getTileEntity().tier);
        for (int process = 0; process < processes; process++) {
            int index = process;
            BooleanSupplier inputWarning = getTileEntity().getWarningCheck(
                    RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT, index);
            addRenderableWidget(GuiMachineProgress.factoryDown(
                    () -> getTileEntity().getScaledProgress(1, index),
                    this,
                    ImbuementFactoryLayout.progressX(
                            firstX, spacing, process))
                    .recipeViewerCategories(
                            ArsRecipeViewerTypes.IDENTIFIER_IMBUEMENT))
                    .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT,
                            inputWarning);
        }
    }

    private void addLibraryButtons() {
        int panelLeft = CatalystLibraryLayout.panelLeft(imageWidth);
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

    private void clickMachineButton(int id) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId, id);
        }
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

    private void configureLockGuiSlot(GuiSlot guiSlot) {
        guiSlot.stored(getTileEntity()::selectedCatalystIdentifier)
                .setGhostHandler(new IRecipeViewerGhostTarget
                        .IGhostIngredientConsumer() {
                    @Override
                    public Object supportedTarget(Object ingredient) {
                        if (!libraryOpen || MekEnergisticsGuiCompat.isPatternWindowOpen(getWindows())) return null;
                        return ingredient instanceof ItemStack stack
                                && stack.is(com.example.mekanismmagic
                                .integration.arsnouveau.ArsNouveauRegistries
                                .CATALYST_IDENTIFIER_ITEM.get())
                                ? stack : null;
                    }

                    @Override
                    public void accept(Object ingredient) {
                        if (!libraryOpen || MekEnergisticsGuiCompat.isPatternWindowOpen(getWindows())) return;
                        if (!(ingredient instanceof ItemStack stack)) {
                            return;
                        }
                        var data = stack.get(net.minecraft.core.component
                                .DataComponents.CUSTOM_DATA);
                        if (data == null) {
                            return;
                        }
                        int index = catalystRecipeIndex(data.copyTag()
                                .getString("catalyst_id"));
                        if (index >= 0) {
                            clickMachineButton(400 + index);
                        }
                    }
                });
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        MekEnergisticsGuiCompat.updateMachineSlot(
                getWindows(), catalystLockSlot, libraryOpen);
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        MagicGuiTheme.renderMachineFrame(graphics, leftPos, topPos,
                imageWidth, imageHeight, inventoryLabelY);
        if (!libraryOpen) {
            return;
        }
        int left = leftPos + CatalystLibraryLayout.panelLeft(imageWidth);
        int top = topPos + CatalystLibraryLayout.FRAME_TOP;
        MagicGuiTheme.renderDockedPanel(graphics, left, top,
                CatalystLibraryLayout.PANEL_WIDTH,
                CatalystLibraryLayout.FRAME_HEIGHT, CatalystLibraryLayout.HEADER_HEIGHT);
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
            int pageSlot = CatalystLibraryLayout.pageSlotAt(imageWidth, mouseX - leftPos, mouseY - topPos);
            if (pageSlot >= 0) {
                int index = getTileEntity().catalystPage()
                        * CatalystLibraryLayout.PAGE_SIZE + pageSlot;
                if (index < getTileEntity().catalystVisibleSlotCount()) {
                    clickMachineButton(300 + pageSlot);
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
        displayedCatalystPage = currentPage;
        displayedCatalystVisibleSlotCount =
                getTileEntity().catalystVisibleSlotCount();
        for (int index = 0; index < librarySlots.size(); index++) {
            GuiSlot slot = librarySlots.get(index);
            slot.visible = libraryOpen
                    && currentPage * CatalystLibraryLayout.PAGE_SIZE + index
                    < displayedCatalystVisibleSlotCount;
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

    private boolean isCatalystLibraryGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        int slotLeft = CatalystLibraryLayout.slotLeft(imageWidth);
        return x >= slotLeft
                && x <= slotLeft + 3 * CatalystLibraryLayout.SLOT_SPACING
                && (x - slotLeft) % CatalystLibraryLayout.SLOT_SPACING == 0
                && y >= CatalystLibraryLayout.SLOT_TOP
                && y <= CatalystLibraryLayout.SLOT_TOP
                + 3 * CatalystLibraryLayout.SLOT_SPACING
                && (y - CatalystLibraryLayout.SLOT_TOP)
                % CatalystLibraryLayout.SLOT_SPACING == 0;
    }


    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX,
                                      int mouseY) {
        MachineStatus status = getTileEntity().getActive()
                ? MachineStatus.RUNNING : MachineStatus.IDLE;
        drawMachineHeader(graphics, title, status, imageWidth);
        renderInventoryText(graphics);
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
        super.drawForegroundText(graphics, mouseX, mouseY);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }

    @Override
    public void removed() {
        if (factoryInputRow != null) factoryInputRow.dispose();
        super.removed();
    }
}
