package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.integration.arsnouveau.SourceAmplifierBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SourceAmplifierScreen
        extends ArsSourceMachineScreen<SourceAmplifierBlockEntity> {
    public SourceAmplifierScreen(
            MekanismTileContainer<SourceAmplifierBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 166);
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected int workProgressX() {
        return 78;
    }

    @Override
    protected boolean showSourceBar() {
        return false;
    }

    @Override
    protected boolean showSourceSideConfig() {
        return false;
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX,
                                      int mouseY) {
        super.drawForegroundText(graphics, mouseX, mouseY);
        Component amplification = Component.translatable(
                "gui.mekanism_magic.source_amplification",
                getTileEntity().getAmplificationPercent());
        MagicGuiTheme.drawCenteredText(graphics, font, amplification,
                imageWidth / 2, 55, MagicGuiTheme.textMuted());
    }
}
