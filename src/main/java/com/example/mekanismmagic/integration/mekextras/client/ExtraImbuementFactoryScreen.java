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
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
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

    public ExtraImbuementFactoryScreen(
            ExtraImbuementFactoryContainer container,
            Inventory inventory, Component title) {
        super(container, inventory, title);
        inventoryLabelY = 75;
        int tier = getTileEntity().tier.ordinal();
        imageWidth = ImbuementFactoryLayout.extraImageWidth(tier);
        inventoryLabelX = ImbuementFactoryLayout.extraInventoryX(tier);
        titleLabelY = 4;
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
                        ImbuementFactoryLayout.LOCK_SLOT_X - 1,
                        ImbuementFactoryLayout.LOCK_SLOT_Y - 1));
        configureLockGuiSlot(catalystLockSlot);
        addLibraryButtons();
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
        guiSlot.stored(tile()::selectedCatalystIdentifier)
                .setGhostHandler(new IRecipeViewerGhostTarget
                        .IGhostIngredientConsumer() {
                    @Override
                    public Object supportedTarget(Object ingredient) {
                        return ingredient instanceof ItemStack stack
                                && stack.is(ArsNouveauRegistries
                                .CATALYST_IDENTIFIER_ITEM.get())
                                ? stack : null;
                    }

                    @Override
                    public void accept(Object ingredient) {
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
                int index = tile().catalystPage()
                        * CatalystLibraryLayout.PAGE_SIZE
                        + (y / 18) * CatalystLibraryLayout.COLUMNS + x / 18;
                clickMachineButton(300 + index);
                return true;
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
        int currentPage = tile().catalystPage();
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
        if (libraryOpen && displayedCatalystPage != tile().catalystPage()) {
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
                    tile().catalystPage(), tile().catalystPageCount(),
                    panelLeft + 49,
                    CatalystLibraryLayout.PANEL_TOP + 95);
        }
        super.drawForegroundText(graphics, mouseX, mouseY);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }
}
