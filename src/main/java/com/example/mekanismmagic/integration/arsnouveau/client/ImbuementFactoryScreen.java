package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.container.ImbuementFactoryContainer;
import com.example.mekanismmagic.client.gui.ArsIntegratedSideConfig;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.client.GuiSourceBar;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiSortingTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ImbuementFactoryScreen extends GuiConfigurableTile<
        ImbuementFactoryBlockEntity, ImbuementFactoryContainer> {
    public ImbuementFactoryScreen(ImbuementFactoryContainer container,
                                  Inventory inventory, Component title) {
        super(container, inventory, title);
        inventoryLabelY = 75;
        titleLabelY = 4;
        dynamicSlots = true;
        if (getTileEntity().tier == mekanism.common.tier.FactoryTier.ULTIMATE) {
            imageWidth += 34;
            inventoryLabelX = 26;
        }
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiSortingTab(this, getTileEntity()));
        addRenderableWidget(new GuiSourceBar(this, getTileEntity(),
                imageWidth - 31, 16, 52));
        ArsIntegratedSideConfig.install(this, getTileEntity(), children(),
                this::addRenderableWidget);
        addRenderableWidget(new GuiVerticalPowerBar(this,
                getTileEntity().getEnergyContainer(), imageWidth - 12,
                16, 52));
        addRenderableWidget(new GuiEnergyTab(this,
                getTileEntity().getEnergyContainer(),
                getTileEntity()::getLastUsage));
        int processes = getTileEntity().tier.processes;
        int firstX = processes == 1 ? 55
                : processes == 2 ? 35
                : processes == 3 ? 29 : 27;
        int spacing = processes == 1 ? 38
                : processes == 2 ? 26 : 19;
        for (int process = 0; process < processes; process++) {
            int index = process;
            addRenderableWidget(new GuiProgress(
                    () -> getTileEntity().getScaledProgress(1, index),
                    ProgressType.DOWN, this,
                    4 + firstX + process * spacing, 33));
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX,
                                      int mouseY) {
        renderTitleText(graphics);
        renderInventoryText(graphics);
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
