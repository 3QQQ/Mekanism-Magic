package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeSpiritFactoryBlockEntity;
import com.example.mekanismmagic.container.NativeSpiritFactoryContainer;
import com.example.mekanismmagic.client.OccultismRecipeViewerTypes;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTheme.MachineStatus;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.GuiThemeToggleTab;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiSortingTab;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.tier.FactoryTier;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Native Mekanism factory composition with this addon's strongly typed
 * container.
 */
public final class NativeSpiritFactoryScreen extends GuiConfigurableTile<
        NativeSpiritFactoryBlockEntity, NativeSpiritFactoryContainer>
        implements MagicThemedScreen {

    public NativeSpiritFactoryScreen(NativeSpiritFactoryContainer container,
                                     Inventory inventory, Component title) {
        super(container, inventory, title);
        inventoryLabelY = 75;
        titleLabelY = 4;
        dynamicSlots = true;
        if (getTileEntity().tier == FactoryTier.ULTIMATE) {
            imageWidth += 34;
            inventoryLabelX = 26;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        MagicGuiTheme.renderMachineFrame(graphics, leftPos, topPos,
                imageWidth, imageHeight, inventoryLabelY);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiThemeToggleTab<>(this, getTileEntity()));
        addRenderableWidget(new GuiSortingTab(this, getTileEntity()));
        addRenderableWidget(new GuiVerticalPowerBar(this,
                getTileEntity().getEnergyContainer(), imageWidth - 12, 16, 52))
                .warning(WarningType.NOT_ENOUGH_ENERGY,
                        getTileEntity().getWarningCheck(
                                RecipeError.NOT_ENOUGH_ENERGY, 0));
        addRenderableWidget(MagicLeftControlLayout.alignEnergyTab(this,
                new GuiEnergyTab(this,
                getTileEntity().getEnergyContainer(),
                getTileEntity()::getLastUsage)));

        int firstX = switch (getTileEntity().tier) {
            case BASIC -> 55;
            case ADVANCED -> 35;
            case ELITE -> 29;
            case ULTIMATE -> 27;
        };
        int spacing = switch (getTileEntity().tier) {
            case BASIC -> 38;
            case ADVANCED -> 26;
            case ELITE, ULTIMATE -> 19;
        };
        for (int process = 0; process < getTileEntity().tier.processes; process++) {
            int index = process;
            addRenderableWidget(new GuiProgress(
                    () -> getTileEntity().getScaledProgress(1, index),
                    ProgressType.DOWN, this,
                    4 + firstX + process * spacing, 33)
                    .recipeViewerCategories(
                            OccultismRecipeViewerTypes.SPIRIT)
                    .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT,
                            getTileEntity().getWarningCheck(
                                    RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT,
                                    index)));
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics gui, int mouseX, int mouseY) {
        MachineStatus status = getTileEntity().getActive()
                ? MachineStatus.RUNNING : MachineStatus.IDLE;
        drawScrollingString(gui, title, 10, titleLabelY,
                TextAlignment.LEFT, MagicGuiTheme.textPrimary(),
                MagicGuiTheme.availableTitleWidth(font, status, imageWidth),
                0, false);
        MagicGuiTheme.renderStatus(gui, font, status, imageWidth,
                titleLabelY);
        renderInventoryText(gui);
        super.drawForegroundText(gui, mouseX, mouseY);
    }
}
