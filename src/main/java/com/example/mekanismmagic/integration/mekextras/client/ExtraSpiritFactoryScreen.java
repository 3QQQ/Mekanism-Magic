package com.example.mekanismmagic.integration.mekextras.client;

import com.example.mekanismmagic.client.OccultismRecipeViewerTypes;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTheme.MachineStatus;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.RetainedItemModule;
import com.example.mekanismmagic.client.gui.FactoryInputRow;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.integration.mekextras
        .ExtraSpiritFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras
        .ExtraSpiritFactoryContainer;
import com.example.mekanismmagic.integration.mekextras
        .ExtraSpiritFactoryLayout;
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

/** Complete themed layout for the 11/13/15/17-process spirit factories. */
public final class ExtraSpiritFactoryScreen extends GuiConfigurableTile<
        ExtraSpiritFactoryBlockEntity, ExtraSpiritFactoryContainer>
        implements MagicThemedScreen {
    private RetainedItemModule retainedModule;
    private FactoryInputRow factoryInputRow;

    public ExtraSpiritFactoryScreen(ExtraSpiritFactoryContainer container,
                                    Inventory inventory, Component title) {
        super(container, inventory, title);
        int tier = getTileEntity().tier.ordinal();
        imageWidth = ExtraSpiritFactoryLayout.imageWidth(tier);
        inventoryLabelX = ExtraSpiritFactoryLayout.inventoryX(tier);
        imageHeight = MachinePanelLayout.FACTORY_HEIGHT;
        inventoryLabelY = MachinePanelLayout.inventoryLabelY(imageHeight);
        titleLabelY = MagicGuiTheme.MACHINE_TITLE_Y;
        dynamicSlots = true;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        MagicGuiTheme.renderMachineFrame(graphics, leftPos, topPos,
                imageWidth, imageHeight, inventoryLabelY);
        if (retainedModule != null) retainedModule.renderBackground(graphics, leftPos, topPos, getWindows());
    }

    @Override
    protected void addGuiElements() {
        factoryInputRow = FactoryInputRow.prepare(factoryInputRow, menu);
        retainedModule = RetainedItemModule.prepare(retainedModule, this, menu, imageWidth, () -> { });
        super.addGuiElements();
        if (retainedModule != null) addRenderableWidget(retainedModule.tab());
        ExtraSpiritFactoryBlockEntity tile = getTileEntity();
        addRenderableWidget(new ExtraGuiSortingTab(this, tile));
        addRenderableWidget(new GuiVerticalPowerBar(this,
                tile.getEnergyContainer(), imageWidth - 12, 16, 52))
                .warning(WarningType.NOT_ENOUGH_ENERGY,
                        tile.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY, 0));
        addRenderableWidget(MagicLeftControlLayout.alignEnergyTab(this,
                new GuiEnergyTab(this, tile.getEnergyContainer(),
                        tile::getLastUsage)));

        for (int process = 0; process < tile.tier.processes; process++) {
            int index = process;
            addRenderableWidget(GuiMachineProgress.factoryDown(
                    () -> tile.getScaledProgress(1, index),
                    this, ExtraSpiritFactoryLayout.progressX(process))
                    .recipeViewerCategories(
                            OccultismRecipeViewerTypes.spiritCategories())
                    .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT,
                            tile.getWarningCheck(
                                    RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT,
                                    index)));
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX,
                                      int mouseY) {
        MachineStatus status = getTileEntity().getActive()
                ? MachineStatus.RUNNING : MachineStatus.IDLE;
        drawMachineHeader(graphics, title, status, imageWidth);
        renderInventoryText(graphics);
        if (retainedModule != null) retainedModule.renderCaption(graphics, font);
        super.drawForegroundText(graphics, mouseX, mouseY);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        if (retainedModule != null) retainedModule.captureOverlays(children());
    }

    @Override
    protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
        return !(retainedModule != null && retainedModule.contains(x, y, leftPos, topPos))
                && super.hasClickedOutside(x, y, left, top, button);
    }

    @Override
    public void removed() {
        if (factoryInputRow != null) factoryInputRow.dispose();
        if (retainedModule != null) retainedModule.dispose();
        super.removed();
    }
}
