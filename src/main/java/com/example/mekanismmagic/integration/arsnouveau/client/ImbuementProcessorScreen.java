package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.gui.GuiCatalystLibraryTab;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MekEnergisticsGuiCompat;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
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
    private static final int LOCK_SLOT_X = 64;
    private static final int LOCK_SLOT_Y = 53;
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
                LOCK_SLOT_X, LOCK_SLOT_Y)
                .stored(getTileEntity()::selectedCatalystIdentifier)
                .setGhostHandler(new IRecipeViewerGhostTarget
                        .IGhostIngredientConsumer() {
                    @Override
                    public Object supportedTarget(Object ingredient) {
                        return ingredient instanceof ItemStack stack
                                && stack.is(
                                com.example.mekanismmagic.integration
                                        .arsnouveau.ArsNouveauRegistries
                                        .CATALYST_IDENTIFIER_ITEM.get())
                                ? stack : null;
                    }

                    @Override
                    public void accept(Object ingredient) {
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
                getWindows(), catalystLockSlot);
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (libraryOpen) {
            int left = leftPos + CatalystLibraryLayout.panelLeft(imageWidth);
            int top = topPos + CatalystLibraryLayout.PANEL_TOP;
            MagicGuiTheme.renderDockedPanel(graphics, left, top,
                    CatalystLibraryLayout.PANEL_WIDTH,
                    CatalystLibraryLayout.PANEL_HEIGHT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MekEnergisticsGuiCompat.updateMachineSlot(
                getWindows(), catalystLockSlot)
                && button == 1
                && mouseX >= leftPos + LOCK_SLOT_X - 1
                && mouseX < leftPos + LOCK_SLOT_X + 19
                && mouseY >= topPos + LOCK_SLOT_Y - 1
                && mouseY < topPos + LOCK_SLOT_Y + 19) {
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
                int index = getTileEntity().catalystPage() * 16
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
            slot.visible = libraryOpen && index / 16 == currentPage;
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

    @Override
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                ArsRecipeViewerTypes.IDENTIFIER_IMBUEMENT};
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
        super.drawForegroundText(graphics, mouseX, mouseY);
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
    }
}
