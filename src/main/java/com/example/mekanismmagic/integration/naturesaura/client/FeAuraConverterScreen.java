package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.naturesaura.FeAuraConverterBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.FeAuraConverterContainer;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraScreenLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class FeAuraConverterScreen extends NativeMagicMachineScreen<FeAuraConverterBlockEntity, FeAuraConverterContainer> {
    public FeAuraConverterScreen(FeAuraConverterContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title, NatureAuraScreenLayout.COMPACT_HEIGHT);
        inventoryLabelY = NatureAuraScreenLayout.COMPACT_LABEL_Y;
    }
    @Override protected boolean showUpArrow() { return false; }
    @Override protected boolean showProgress() { return false; }
    @Override protected void addMachineGuiElements() {
        AuraIntegratedSideConfig.install(this, getTileEntity(), children(), this::addRenderableWidget, this::removeWidget);
        addRenderableWidget(NatureMachineGroups.caption(this, "gui.mekanism_magic.group.energy", 8, 22, 42));
        addRenderableWidget(NatureMachineGroups.rule(this, 51, 23, 1, 46));
        addRenderableWidget(new GuiAuraBar(this, getTileEntity(), imageWidth - 20, 16, 52));
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.COMPACT_UPPER,
                () -> Component.translatable("gui.mekanism_magic.panel.efficiency"),
                () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getFePerAura())), MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_fe_ratio", getTileEntity().getFePerAura()))));
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.COMPACT_LOWER,
                () -> Component.translatable("gui.mekanism_magic.panel.generated"),
                () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getLastGeneratedAura())), MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_generated", getTileEntity().getLastGeneratedAura()))));
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.COMPACT_FOOTER,
                () -> Component.translatable("gui.mekanism_magic.aura_mode.title"),
                () -> NatureMachineSettingsSection.auraTypeLabel(getTileEntity().getSelectedAuraTypeMode()),
                MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_mode.title"))));
    }
}
