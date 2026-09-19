package com.example.mekanismmagic.integration.mekextras.client;

import com.example.mekanismmagic.client.gui.GuiCatalystLibraryTab;
import com.example.mekanismmagic.client.gui.ArsIntegratedSideConfig;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTheme.MachineStatus;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.MekEnergisticsGuiCompat;
import com.example.mekanismmagic.client.gui.GuiThemeToggleTab;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryLayout;
import com.example.mekanismmagic.integration.arsnouveau.client.ArsRecipeViewerTypes;
import com.example.mekanismmagic.integration.arsnouveau.client.GuiSourceBar;
import com.example.mekanismmagic.integration.mekextras
        .ExtraImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras
        .ExtraImbuementFactoryContainer;
import com.jerry.mekextras.client.gui.element.tab.ExtraGuiSortingTab;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Mekanism Extras factory layout with the Ars Source and catalyst controls. */
public final class ExtraImbuementFactoryScreen extends GuiConfigurableTile<
        ExtraImbuementFactoryBlockEntity, ExtraImbuementFactoryContainer>
        implements MagicThemedScreen {
    private final List<GuiSlot> librarySlots = new ArrayList<>();
    private final List<MekanismButton> libraryButtons = new ArrayList<>();
    private GuiSlot catalystLockSlot;
    private boolean libraryOpen;
    private int displayedCatalystPage = -1;
    private int displayedCatalystVisibleSlotCount = -1;

    public ExtraImbuementFactoryScreen(
            ExtraImbuementFactoryContainer container,
            Inventory inventory, Component title) {
        super(container, inventory, title);
        inventoryLabelY = 75;
        int tier = getTileEntity().tier.ordinal();
        imageWidth = ImbuementFactoryLayout.extraImageWidth(tier);
        inventoryLabelX = ImbuementFactoryLayout.extraInventoryX(tier);
        titleLabelY = MagicGuiTheme.MACHINE_TITLE_Y;
        dynamicSlots = true;
        tile().setCatalystLibraryOpen(false);
    }

    private ExtraImbuementFactoryBlockEntity tile() {
        return (ExtraImbuementFactoryBlockEntity) getTileEntity();
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiThemeToggleTab<>(this, tile()));
        addRenderableWidget(new ExtraGuiSortingTab(this, tile()));
        ArsIntegratedSideConfig.install(this, tile(), children(),
                this::addRenderableWidget);
        BooleanSupplier energyWarning = tile().getWarningCheck(
                RecipeError.NOT_ENOUGH_ENERGY, 0);
        addRenderableWidget(new GuiVerticalPowerBar(this,
                tile().getEnergyContainer(), imageWidth - 12,
                ImbuementFactoryLayout.BAR_Y,
                ImbuementFactoryLayout.BAR_HEIGHT))
                .warning(WarningType.NOT_ENOUGH_ENERGY,
                        energyWarning);
        addRenderableWidget(MagicLeftControlLayout.alignEnergyTab(this,
                new GuiEnergyTab(this, tile().getEnergyContainer(),
                        tile()::getLastUsage)));
        for (int process = 0; process < tile().tier.processes; process++) {
            int index = process;
            BooleanSupplier inputWarning = tile().getWarningCheck(
                    RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT, index);
            addRenderableWidget(new GuiProgress(
                    () -> tile().getScaledProgress(1, index),
                    ProgressType.DOWN, this,
                    ImbuementFactoryLayout.progressX(27, 19, process), 33)
                    .recipeViewerCategories(
                            ArsRecipeViewerTypes.IDENTIFIER_IMBUEMENT)
                    .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT,
                            inputWarning));
        }
        addRenderableWidget(new GuiSourceBar(this, tile(),
                imageWidth - 20, ImbuementFactoryLayout.BAR_Y,
                ImbuementFactoryLayout.BAR_HEIGHT));
        addRenderableWidget(new GuiCatalystLibraryTab<>(this, tile(),
                () -> libraryOpen, this::toggleLibrary));
        catalystLockSlot = addRenderableWidget(
                new GuiSlot(SlotType.EXTRA, this,
                        CatalystLibraryLayout.activeSlotX(imageWidth) - 1,
                        CatalystLibraryLayout.ACTIVE_SLOT_Y - 1));
        configureLockGuiSlot(catalystLockSlot);
        addLibraryButtons();
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
        guiSlot.stored(tile()::selectedCatalystIdentifier)
                .setGhostHandler(new IRecipeViewerGhostTarget
                        .IGhostIngredientConsumer() {
                    @Override
                    public Object supportedTarget(Object ingredient) {
                        if (!libraryOpen || MekEnergisticsGuiCompat.isPatternWindowOpen(getWindows())) return null;
                        return ingredient instanceof ItemStack stack
                                && stack.is(ArsNouveauRegistries
                                .CATALYST_IDENTIFIER_ITEM.get())
                                ? stack : null;
                    }

                    @Override
                    public void accept(Object ingredient) {
                        if (!libraryOpen || MekEnergisticsGuiCompat.isPatternWindowOpen(getWindows())) return;
                        if (!(ingredient instanceof ItemStack stack)) {
                            return;
                        }
                        var data = stack.get(DataComponents.CUSTOM_DATA);
                        if (data == null) {
                            return;
                        }
                        int index = ArsNouveauRecipeBridge
                                .catalystIdentifierJeiIndex(
                                        tile().getLevel(), data.copyTag()
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
                int index = tile().catalystPage()
                        * CatalystLibraryLayout.PAGE_SIZE + pageSlot;
                if (index < tile().catalystVisibleSlotCount()) {
                    clickMachineButton(300 + pageSlot);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleLibrary() {
        libraryOpen = !libraryOpen;
        tile().setCatalystLibraryOpen(libraryOpen);
        updateLibraryVisibility();
    }

    private void updateLibraryVisibility() {
        MekEnergisticsGuiCompat.updateMachineSlot(getWindows(), catalystLockSlot, libraryOpen);
        int currentPage = tile().catalystPage();
        displayedCatalystPage = currentPage;
        displayedCatalystVisibleSlotCount =
                tile().catalystVisibleSlotCount();
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
        if (libraryOpen && (displayedCatalystPage != tile().catalystPage()
                || displayedCatalystVisibleSlotCount
                != tile().catalystVisibleSlotCount())) {
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
        MachineStatus status = tile().getActive()
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
                    tile().catalystPage(), tile().catalystPageCount(),
                    panelLeft + 49,
                    CatalystLibraryLayout.FOOTER_TOP + 5);
        }
        super.drawForegroundText(graphics, mouseX, mouseY);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }
}
