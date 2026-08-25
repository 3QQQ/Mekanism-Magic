package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.gui.GuiCatalystLibraryTab;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
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
    private boolean libraryOpen;
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
        addRenderableWidget(new GuiCatalystLibraryTab(this, getTileEntity(),
                () -> libraryOpen, this::toggleLibrary));
        addRenderableWidget(new GuiSlot(SlotType.INPUT, this,
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
                                int index = catalystIndex(
                                        data.copyTag().getString(
                                                "catalyst_id"));
                                if (index >= 0) {
                                    clickMachineButton(300 + index);
                                }
                            }
                        }
                    }
                }));
        libraryButtons.clear();
        libraryButtons.add((MekanismButton) addRenderableWidget(
                new MekanismButton(this, 240, 178, 28, 18,
                Component.literal("<"),
                (element, mouseX, mouseY) -> {
                    clickMachineButton(220);
                    return true;
                })));
        libraryButtons.add((MekanismButton) addRenderableWidget(
                new MekanismButton(this, 284, 178, 28, 18,
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
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (libraryOpen) {
            int left = leftPos + 236;
            int top = topPos + 88;
            graphics.fill(left, top, left + 98, top + 112, 0xFF151A21);
            graphics.fill(left, top, left + 98, top + 2, 0xFF78838F);
            graphics.fill(left, top, left + 2, top + 112, 0xFF78838F);
            graphics.fill(left + 4, top + 14, left + 94, top + 108,
                    0xFF252B34);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (libraryOpen && button == 1) {
            int x = (int) (mouseX - leftPos - 240);
            int y = (int) (mouseY - topPos - 104);
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
    protected boolean hasClickedOutside(double mouseX, double mouseY,
                                        int left, int top, int button) {
        if (libraryOpen && mouseX >= leftPos + 236
                && mouseX < leftPos + 334
                && mouseY >= topPos + 88
                && mouseY < topPos + 202) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private int catalystIndex(String catalystId) {
        return getTileEntity().catalystIdentifierIndex(catalystId);
    }

    private static boolean isCatalystLibraryGuiSlot(GuiSlot slot) {
        int x = slot.getRelativeX() + 1;
        int y = slot.getRelativeY() + 1;
        return x >= 240 && x <= 294 && (x - 240) % 18 == 0
                && y >= 104 && y <= 158 && (y - 104) % 18 == 0;
    }
}
