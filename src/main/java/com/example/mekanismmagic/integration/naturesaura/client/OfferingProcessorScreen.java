package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.naturesaura.OfferingProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.OfferingProcessorContainer;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class OfferingProcessorScreen extends NativeMagicMachineScreen<OfferingProcessorBlockEntity,
        OfferingProcessorContainer> {
    public OfferingProcessorScreen(OfferingProcessorContainer container, Inventory inventory, Component title) {
        super(container, inventory, title, MachinePanelLayout.OFFERING_HEIGHT);
    }

    @Override
    protected boolean showUpArrow() { return false; }

    @Override
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{NaturesAuraRecipeViewerTypes.OFFERING};
    }

    @Override protected void addMachineGuiElements() {
        addRenderableWidget(NatureMachineGroups.rule(this, 8, 71, 160, 1));
        addRenderableWidget(GuiMachineInstrument.line(this, MachinePanelLayout.OFFERING_BONUS,
                () -> Component.translatable("gui.mekanism_magic.panel.bonus"),
                () -> Component.translatable(getTileEntity().hasWitherRoseBonus()
                        ? "gui.mekanism_magic.panel.enabled" : "gui.mekanism_magic.panel.disabled"),
                MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.offering.wither_bonus",
                        getTileEntity().getWitherRoseSlot().getCount(), OfferingProcessorBlockEntity.WITHER_ROSES_FOR_BONUS))));
    }

    @Override protected void addSlots() {
        super.addSlots();
        NatureMachineGroups.slotHints(menu, children());
    }
}
