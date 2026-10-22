package com.example.mekanismmagic.integration.mekextras.client;

import com.example.mekanismmagic.integration.naturesaura.client.AuraIntegratedSideConfig;
import com.example.mekanismmagic.integration.naturesaura.client.GuiAuraBar;
import com.example.mekanismmagic.integration.naturesaura.client.NatureMachineGroups;
import com.example.mekanismmagic.integration.naturesaura.client.NatureMachineLayout;
import com.example.mekanismmagic.integration.naturesaura.client.NatureMachineSettingsSection;
import com.example.mekanismmagic.integration.naturesaura.client.NatureAltarCatalystPanel;
import com.example.mekanismmagic.integration.naturesaura.client.NaturesAuraRecipeViewerTypes;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarFactoryLayout;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTheme.MachineStatus;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.client.gui.FactoryInputRow;
import com.example.mekanismmagic.integration.mekextras
        .ExtraNatureAltarFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras
        .ExtraNatureAltarFactoryContainer;
import com.example.mekanismmagic.integration.mekextras
        .ExtraNatureAltarFactoryLayout;
import com.jerry.mekextras.client.gui.element.tab.ExtraGuiSortingTab;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import com.example.mekanismmagic.client.gui.GuiMachineProgress;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** One responsive screen shared by all extended natural altar factories. */
public final class ExtraNatureAltarFactoryScreen extends GuiConfigurableTile<
        ExtraNatureAltarFactoryBlockEntity, ExtraNatureAltarFactoryContainer>
        implements MagicThemedScreen {
    private NatureAltarCatalystPanel catalystPanel;
    private FactoryInputRow factoryInputRow;
    public ExtraNatureAltarFactoryScreen(ExtraNatureAltarFactoryContainer container,
                                    Inventory inventory, Component title) {
        super(container, inventory, title);
        NatureMachineSettingsSection.registerAltar(ExtraNatureAltarFactoryBlockEntity.class,
                ExtraNatureAltarFactoryBlockEntity::getMatchingRecipeCount,
                ExtraNatureAltarFactoryBlockEntity::getSelectedRecipeOutput,
                ExtraNatureAltarFactoryContainer.SELECT_NEXT_RECIPE);
        imageHeight = ExtraNatureAltarFactoryLayout.IMAGE_HEIGHT;
        inventoryLabelY = ExtraNatureAltarFactoryLayout.INVENTORY_LABEL_Y;
        titleLabelY = MagicGuiTheme.MACHINE_TITLE_Y;
        dynamicSlots = true;
        imageWidth = ExtraNatureAltarFactoryLayout.imageWidth(
                getTileEntity().tier.ordinal());
        inventoryLabelX = ExtraNatureAltarFactoryLayout.inventoryX(
                getTileEntity().tier.ordinal());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        MagicGuiTheme.renderMachineFrame(graphics, leftPos, topPos,
                imageWidth, imageHeight, inventoryLabelY);
        if (catalystPanel != null) catalystPanel.renderBackground(
                graphics, leftPos, topPos, getWindows());
    }

    @Override
    protected void addGuiElements() {
        factoryInputRow = FactoryInputRow.prepare(factoryInputRow, menu);
        if (catalystPanel != null) catalystPanel.dispose();
        catalystPanel = new NatureAltarCatalystPanel(this, getTileEntity(),
                menu, getTileEntity().mekanismMagicPersistentInputs()
                .subList(0, 2),
                getTileEntity()::catalystPage,
                getTileEntity()::catalystPageCount,
                getTileEntity()::catalystVisibleSlotCount,
                getTileEntity()::setCatalystLibraryOpen,
                buttonId -> {
                    if (minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, buttonId);
                    }
                }, () -> { });
        super.addGuiElements();
        addRenderableWidget(catalystPanel.tab());
        addRenderableWidget(catalystPanel.previousButton());
        addRenderableWidget(catalystPanel.nextButton());
        AuraIntegratedSideConfig.install(this, getTileEntity(), children(),
                this::addRenderableWidget, this::removeWidget);
        addRenderableWidget(new ExtraGuiSortingTab(this, getTileEntity()));
        addRenderableWidget(new GuiAuraBar(this, getTileEntity(),
                imageWidth - 20, NatureAltarFactoryLayout.BAR_Y,
                NatureAltarFactoryLayout.BAR_HEIGHT));
        addRenderableWidget(new GuiVerticalPowerBar(this,
                getTileEntity().getEnergyContainer(), imageWidth - 12,
                NatureAltarFactoryLayout.BAR_Y,
                NatureAltarFactoryLayout.BAR_HEIGHT))
                .warning(WarningType.NOT_ENOUGH_ENERGY,
                        getTileEntity().getWarningCheck(
                                RecipeError.NOT_ENOUGH_ENERGY, 0));
        addRenderableWidget(MagicLeftControlLayout.alignEnergyTab(this,
                new GuiEnergyTab(this,
                        getTileEntity().getEnergyContainer(),
                        getTileEntity()::getLastUsage)));

        for (int process = 0;
             process < getTileEntity().tier.processes; process++) {
            int index = process;
            addRenderableWidget(GuiMachineProgress.factoryDown(
                    () -> getTileEntity().getScaledProgress(1, index),
                    this, ExtraNatureAltarFactoryLayout.progressX(process))
                    .recipeViewerCategories(
                            NaturesAuraRecipeViewerTypes.NATURE_ALTAR)
                    .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT,
                            getTileEntity().getWarningCheck(
                                    RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT,
                                    index)));
        }
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.factoryFooter(imageWidth),
                () -> Component.translatable("gui.mekanism_magic.panel.recipe"),
                () -> NatureMachineSettingsSection.recipeName(getTileEntity().getSelectedRecipeOutput()),
                MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("tooltip.mekanism_magic.nature_altar.choose_recipe",
                        NatureMachineSettingsSection.recipeName(getTileEntity().getSelectedRecipeOutput())))));
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX,
                                      int mouseY) {
        MachineStatus status = getTileEntity().getActive()
                ? MachineStatus.RUNNING : MachineStatus.IDLE;
        drawMachineHeader(graphics, title, status, imageWidth);
        renderInventoryText(graphics);
        super.drawForegroundText(graphics, mouseX, mouseY);
        if (catalystPanel != null) catalystPanel.renderCaption(graphics, font);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }

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
    protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
        return !(catalystPanel != null && catalystPanel.contains(
                x, y, leftPos, topPos))
                && super.hasClickedOutside(x, y, left, top, button);
    }

    @Override
    public void removed() {
        if (factoryInputRow != null) factoryInputRow.dispose();
        if (catalystPanel != null) catalystPanel.dispose();
        super.removed();
    }
}
