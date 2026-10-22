package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraScreenLayout;
import com.example.mekanismmagic.integration.naturesaura
        .AuraFieldInterfaceBlockEntity;
import com.example.mekanismmagic.integration.naturesaura
        .AuraFieldInterfaceContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Minimal field-mode and Aura status screen. */
public final class AuraFieldInterfaceScreen extends NativeMagicMachineScreen<
        AuraFieldInterfaceBlockEntity, AuraFieldInterfaceContainer> {

    public AuraFieldInterfaceScreen(AuraFieldInterfaceContainer container,
                                    Inventory inventory, Component title) {
        super(container, inventory, title, NatureAuraScreenLayout.COMPACT_HEIGHT);
        inventoryLabelY = NatureAuraScreenLayout.COMPACT_LABEL_Y;
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected boolean showProgress() {
        return false;
    }

    @Override
    protected void addMachineGuiElements() {
        AuraIntegratedSideConfig.install(this, getTileEntity(), children(),
                this::addRenderableWidget, this::removeWidget);
        addRenderableWidget(new GuiAuraBar(this, getTileEntity(),
                imageWidth - 20, 16, 52));
        addRenderableWidget(NatureMachineGroups.caption(this, "gui.mekanism_magic.group.energy", 8, 22, 42));
        addRenderableWidget(NatureMachineGroups.rule(this, 51, 23, 1, 46));
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.COMPACT_UPPER,
                () -> Component.translatable("gui.mekanism_magic.panel.mode"),
                () -> Component.translatable(getTileEntity().getFieldMode().translationKey()),
                MagicGuiTheme::accentSource, () -> java.util.List.of(modeLabel())));
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.COMPACT_LOWER,
                () -> Component.translatable("gui.mekanism_magic.panel.area"),
                () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getLastAreaAura())), MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_area_amount", getTileEntity().getLastAreaAura()))));
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.COMPACT_FOOTER,
                () -> Component.translatable("gui.mekanism_magic.panel.aura"),
                () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getStoredAura())),
                MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_amount",
                        getTileEntity().getStoredAura(), getTileEntity().getAuraCapacity()))));
    }

    private Component modeLabel() {
        return Component.translatable("gui.mekanism_magic.aura_mode",
                Component.translatable(getTileEntity().getFieldMode()
                        .translationKey()));
    }

}
