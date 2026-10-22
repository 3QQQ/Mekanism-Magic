package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraScreenLayout;
import com.example.mekanismmagic.integration.naturesaura
        .NatureAltarProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura
        .NatureAltarProcessorContainer;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact work screen for the single-block natural altar. */
public final class NatureAltarProcessorScreen
        extends NativeMagicMachineScreen<NatureAltarProcessorBlockEntity,
        NatureAltarProcessorContainer> {
    private NatureAltarCatalystPanel catalystPanel;

    public NatureAltarProcessorScreen(
            NatureAltarProcessorContainer container,
            Inventory inventory, Component title) {
        super(container, inventory, title, NatureAuraScreenLayout.COMPACT_HEIGHT);
        inventoryLabelY = NatureAuraScreenLayout.COMPACT_LABEL_Y;
    }

    @Override
    protected void prepareMachineGuiElements() {
        if (catalystPanel != null) catalystPanel.dispose();
        catalystPanel = new NatureAltarCatalystPanel(this, getTileEntity(),
                menu, getTileEntity().mekanismMagicPersistentInputs()
                .subList(0, 2),
                getTileEntity()::catalystPage,
                getTileEntity()::catalystPageCount,
                getTileEntity()::catalystVisibleSlotCount,
                getTileEntity()::setCatalystLibraryOpen,
                this::clickMenuButton, this::closeRetainedModule);
    }

    @Override
    protected void addMachineGuiElements() {
        addRenderableWidget(catalystPanel.tab());
        addRenderableWidget(catalystPanel.previousButton());
        addRenderableWidget(catalystPanel.nextButton());
        AuraIntegratedSideConfig.install(this, getTileEntity(), children(),
                this::addRenderableWidget, this::removeWidget);
        addRenderableWidget(new GuiAuraBar(this, getTileEntity(),
                imageWidth - 20, 16, 52));
        addRenderableWidget(GuiMachineInstrument.line(this, MachinePanelLayout.AURA_AMOUNT,
                () -> Component.translatable("gui.mekanism_magic.panel.aura"),
                () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getStoredAura())), MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_amount",
                        getTileEntity().getStoredAura(), getTileEntity().getAuraCapacity()))));
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                NaturesAuraRecipeViewerTypes.NATURE_ALTAR};
    }

    @Override protected boolean showUpArrow() { return false; }

    @Override protected int workProgressX() { return 77; }

    @Override
    public void containerTick() {
        super.containerTick();
        if (catalystPanel != null) catalystPanel.containerTick();
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        if (catalystPanel != null) catalystPanel.captureOverlays(children());
        NatureMachineGroups.slotHints(menu, children());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (catalystPanel != null) catalystPanel.renderBackground(
                graphics, leftPos, topPos, getWindows());
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics,
                                      int mouseX, int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        if (catalystPanel != null) catalystPanel.renderCaption(graphics, font);
    }

    @Override
    protected void onRetainedModuleOpened() {
        if (catalystPanel != null) catalystPanel.close();
    }

    @Override
    protected boolean usesRetainedModule() {
        return false;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY,
                                        int left, int top, int button) {
        return !(catalystPanel != null && catalystPanel.contains(
                mouseX, mouseY, leftPos, topPos))
                && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    @Override
    public void removed() {
        if (catalystPanel != null) catalystPanel.dispose();
        super.removed();
    }

}
