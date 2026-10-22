package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.naturesaura.AuraFuelGeneratorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.AuraFuelGeneratorContainer;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraScreenLayout;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Item-powered generation: no decorative or nonfunctional FE slot/bar. */
public final class AuraFuelGeneratorScreen extends NativeMagicMachineScreen<AuraFuelGeneratorBlockEntity, AuraFuelGeneratorContainer> {
    public AuraFuelGeneratorScreen(AuraFuelGeneratorContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title, NatureAuraScreenLayout.COMPACT_HEIGHT);
        inventoryLabelY = NatureAuraScreenLayout.COMPACT_LABEL_Y;
    }
    @Override protected void addWorkGuiElements() {
        addRenderableWidget(new GuiProgress(() -> {
            long total = getTileEntity().getFuelTotalAura();
            return total <= 0 ? 0 : 1 - getTileEntity().getRemainingFuelAura() / (double) total;
        }, ProgressType.BAR, this, 86, 38)
                .warning(mekanism.common.inventory.warning.WarningTracker.WarningType.INPUT_DOESNT_PRODUCE_OUTPUT,
                        getTileEntity()::hasInputDoesntProduceOutputWarning)
                .recipeViewerCategories(NaturesAuraRecipeViewerTypes.AURA_FUEL));
    }
    @Override protected void addMachineGuiElements() {
        AuraIntegratedSideConfig.install(this, getTileEntity(), children(), this::addRenderableWidget, this::removeWidget);
        addRenderableWidget(NatureMachineGroups.caption(this, "gui.mekanism_magic.group.fuel", 44, 22, 40));
        addRenderableWidget(new GuiAuraBar(this, getTileEntity(), imageWidth - 14, 16, 52));
        addRenderableWidget(GuiMachineInstrument.line(this, MachinePanelLayout.FUEL_AMOUNT,
                () -> Component.translatable("gui.mekanism_magic.panel.fuel"),
                () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getRemainingFuelAura())), MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_fuel_remaining", getTileEntity().getRemainingFuelAura()))));
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.FUEL_FOOTER,
                () -> Component.translatable("gui.mekanism_magic.aura_mode.title"),
                () -> NatureMachineSettingsSection.auraTypeLabel(getTileEntity().getSelectedAuraTypeMode()),
                MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_mode.title"))));
    }

    @Override protected void addSlots() {
        super.addSlots();
        NatureMachineGroups.slotHints(menu, children());
    }
}
