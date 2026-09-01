package com.example.mekanismmagic.integration.mekextras.client;

import com.example.mekanismmagic.client.OccultismRecipeViewerTypes;
import com.example.mekanismmagic.client.gui.GuiThemeToggleTab;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTheme.MachineStatus;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
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
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Complete themed layout for the 11/13/15/17-process spirit factories. */
public final class ExtraSpiritFactoryScreen extends GuiConfigurableTile<
        ExtraSpiritFactoryBlockEntity, ExtraSpiritFactoryContainer>
        implements MagicThemedScreen {

    public ExtraSpiritFactoryScreen(ExtraSpiritFactoryContainer container,
                                    Inventory inventory, Component title) {
        super(container, inventory, title);
        int tier = getTileEntity().tier.ordinal();
        imageWidth = ExtraSpiritFactoryLayout.imageWidth(tier);
        inventoryLabelX = ExtraSpiritFactoryLayout.inventoryX(tier);
        inventoryLabelY = 75;
        titleLabelY = 4;
        dynamicSlots = true;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        MagicGuiTheme.renderMachineFrame(graphics, leftPos, topPos,
                imageWidth, imageHeight, inventoryLabelY);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        ExtraSpiritFactoryBlockEntity tile = getTileEntity();
        addRenderableWidget(new GuiThemeToggleTab<>(this, tile));
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
            addRenderableWidget(new GuiProgress(
                    () -> tile.getScaledProgress(1, index),
                    ProgressType.DOWN, this,
                    ExtraSpiritFactoryLayout.progressX(process), 33)
                    .recipeViewerCategories(
                            OccultismRecipeViewerTypes.SPIRIT)
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
        drawScrollingString(graphics, title, 10, titleLabelY,
                TextAlignment.LEFT, MagicGuiTheme.textPrimary(),
                MagicGuiTheme.availableTitleWidth(font, status, imageWidth),
                0, false);
        MagicGuiTheme.renderStatus(graphics, font, status, imageWidth,
                titleLabelY);
        renderInventoryText(graphics);
        super.drawForegroundText(graphics, mouseX, mouseY);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }
}
