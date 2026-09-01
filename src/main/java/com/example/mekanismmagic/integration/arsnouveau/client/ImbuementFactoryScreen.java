package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.container.ImbuementFactoryContainer;
import com.example.mekanismmagic.client.gui.ArsIntegratedSideConfig;
import com.example.mekanismmagic.client.gui.GuiCatalystLibraryTab;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTheme.MachineStatus;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.MekEnergisticsGuiCompat;
import com.example.mekanismmagic.client.gui.GuiThemeToggleTab;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryLayout;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiSortingTab;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
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

    public ImbuementFactoryScreen(ImbuementFactoryContainer container,
                                  Inventory inventory, Component title) {
        super(container, inventory, title);
        inventoryLabelY = 75;
        titleLabelY = 4;
        dynamicSlots = true;
        imageWidth = ImbuementFactoryLayout.standardImageWidth(
                getTileEntity().tier);
        inventoryLabelX = ImbuementFactoryLayout.standardInventoryX(
                getTileEntity().tier);
        getTileEntity().setCatalystLibraryOpen(false);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiThemeToggleTab<>(this, getTileEntity()));
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
                        ImbuementFactoryLayout.LOCK_SLOT_X - 1,
                        ImbuementFactoryLayout.LOCK_SLOT_Y - 1));
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
            addRenderableWidget(new GuiProgress(
                    () -> getTileEntity().getScaledProgress(1, index),
                    ProgressType.DOWN, this,
                    ImbuementFactoryLayout.progressX(
                            firstX, spacing, process), 33)
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
                        CatalystLibraryLayout.PANEL_TOP + 90, 28, 18,
                        Component.literal("<"),
                        (element, mouseX, mouseY) -> {
                            clickMachineButton(220);
                            return true;
                        })));
        libraryButtons.add((MekanismButton) addRenderableWidget(
                new MekanismButton(this, panelLeft + 66,
                        CatalystLibraryLayout.PANEL_TOP + 90, 28, 18,
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
                        return ingredient instanceof ItemStack stack
                                && stack.is(com.example.mekanismmagic
                                .integration.arsnouveau.ArsNouveauRegistries
                                .CATALYST_IDENTIFIER_ITEM.get())
                                ? stack : null;
                    }

                    @Override
                    public void accept(Object ingredient) {
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
                getWindows(), catalystLockSlot);
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        MagicGuiTheme.renderMachineFrame(graphics, leftPos, topPos,
                imageWidth, imageHeight, inventoryLabelY);
        if (!libraryOpen) {
            return;
        }
        int left = leftPos + CatalystLibraryLayout.panelLeft(imageWidth);
        int top = topPos + CatalystLibraryLayout.PANEL_TOP;
        MagicGuiTheme.renderDockedPanel(graphics, left, top,
                CatalystLibraryLayout.PANEL_WIDTH,
                CatalystLibraryLayout.PANEL_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MekEnergisticsGuiCompat.updateMachineSlot(
                getWindows(), catalystLockSlot)
                && button == 1
                && mouseX >= leftPos + ImbuementFactoryLayout.LOCK_SLOT_X - 1
                && mouseX < leftPos + ImbuementFactoryLayout.LOCK_SLOT_X + 19
                && mouseY >= topPos + ImbuementFactoryLayout.LOCK_SLOT_Y - 1
                && mouseY < topPos + ImbuementFactoryLayout.LOCK_SLOT_Y + 18) {
            clickMachineButton(399);
            return true;
        }
        if (libraryOpen && button == 1) {
            int x = (int) (mouseX - leftPos
                    - CatalystLibraryLayout.slotLeft(imageWidth));
            int y = (int) (mouseY - topPos
                    - CatalystLibraryLayout.SLOT_TOP);
            if (x >= 0 && x < 72 && y >= 0 && y < 72
                    && x % 18 < 17 && y % 18 < 17) {
                int index = getTileEntity().catalystPage()
                        * CatalystLibraryLayout.PAGE_SIZE
                        + (y / 18) * 4 + x / 18;
                clickMachineButton(300 + index);
                return true;
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
        int currentPage = getTileEntity().catalystPage();
        displayedCatalystPage = currentPage;
        for (int index = 0; index < librarySlots.size(); index++) {
            GuiSlot slot = librarySlots.get(index);
            slot.visible = libraryOpen
                    && index / CatalystLibraryLayout.PAGE_SIZE == currentPage;
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
        if (libraryOpen && displayedCatalystPage
                != getTileEntity().catalystPage()) {
            updateLibraryVisibility();
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY,
                                        int left, int top, int button) {
        int panelLeft = leftPos
                + CatalystLibraryLayout.panelLeft(imageWidth);
        int panelTop = topPos + CatalystLibraryLayout.PANEL_TOP;
        if (libraryOpen && mouseX >= panelLeft
                && mouseX < panelLeft + CatalystLibraryLayout.PANEL_WIDTH
                && mouseY >= panelTop
                && mouseY < panelTop + CatalystLibraryLayout.PANEL_HEIGHT) {
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
        drawScrollingString(graphics, title, 10, titleLabelY,
                TextAlignment.LEFT, MagicGuiTheme.textPrimary(),
                MagicGuiTheme.availableTitleWidth(font, status, imageWidth),
                0, false);
        MagicGuiTheme.renderStatus(graphics, font, status, imageWidth,
                titleLabelY);
        renderInventoryText(graphics);
        if (libraryOpen) {
            int panelLeft = CatalystLibraryLayout.panelLeft(imageWidth);
            MagicGuiTheme.renderPanelCaption(graphics, font,
                    Component.translatable("gui.mekanism_magic.catalysts"),
                    panelLeft, CatalystLibraryLayout.PANEL_TOP,
                    CatalystLibraryLayout.PANEL_WIDTH);
            MagicGuiTheme.renderPageNumber(graphics, font,
                    getTileEntity().catalystPage(),
                    getTileEntity().catalystPageCount(), panelLeft + 49,
                    CatalystLibraryLayout.PANEL_TOP + 95);
        }
        super.drawForegroundText(graphics, mouseX, mouseY);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }
}
