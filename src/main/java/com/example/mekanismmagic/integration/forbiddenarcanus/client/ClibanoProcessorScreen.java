package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.forbiddenarcanus.ClibanoProcessorBlockEntity;
import com.example.mekanismmagic.integration.forbiddenarcanus.ClibanoProcessorContainer;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** Uses synced scalar state only; never scans recipes or native registries in a GUI. */
public final class ClibanoProcessorScreen extends NativeMagicMachineScreen<ClibanoProcessorBlockEntity, ClibanoProcessorContainer> {
    private MekanismButton experienceButton;

    public ClibanoProcessorScreen(ClibanoProcessorContainer container, Inventory inventory, Component title) {
        super(container, inventory, title, MachinePanelLayout.CLIBANO_HEIGHT);
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected void addMachineGuiElements() {
        experienceButton = (MekanismButton) addRenderableWidget(new MekanismButton(this,
                84, 62, 44, 14, Component.translatable("gui.mekanism_magic.clibano.claim_xp"),
                (element, mouseX, mouseY) -> {
                    clickMenuButton(ClibanoProcessorContainer.CLAIM_EXPERIENCE);
                    return true;
                }));
        addRenderableWidget(GuiMachineInstrument.line(this, MachinePanelLayout.CLIBANO_FIRE, this::fireName,
                () -> Component.translatable("gui.mekanism_magic.panel.seconds", (getTileEntity().getSoulTicks() + 19) / 20),
                () -> 0xFFE5B765, () -> java.util.List.of(Component.translatable("gui.mekanism_magic.clibano.fire_status",
                        fireName(), (getTileEntity().getSoulTicks() + 19) / 20))));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (experienceButton != null) {
            experienceButton.active = getTileEntity().getStoredExperience() > 0;
            experienceButton.setTooltip(Tooltip.create(Component.translatable(
                    "gui.mekanism_magic.clibano.experience_available", experienceLabel(getTileEntity().getStoredExperience()))));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.fill(leftPos + 8, topPos + 57, leftPos + 152, topPos + 58, MagicGuiTheme.readoutTrack());
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        for (var child : children()) {
            if (!(child instanceof GuiSlot visual)) continue;
            for (var backing : menu.getInventoryContainerSlots()) {
                var inventory = backing.getInventorySlot();
                String key = inventory instanceof InputInventorySlot ? "gui.mekanism_magic.panel.inputs"
                        : inventory instanceof OutputInventorySlot ? "gui.mekanism_magic.panel.output" : null;
                if (key != null && isGuiSlotForInventory(visual, inventory)) {
                    visual.hover(ignored -> inventory.isEmpty()
                            ? java.util.List.of(Component.translatable(key)) : java.util.List.of());
                }
            }
        }
    }

    private Component fireName() {
        return Component.translatable("gui.mekanism_magic.clibano.fire." + getTileEntity().getFireType().name().toLowerCase(Locale.ROOT));
    }

    private static String experienceLabel(double amount) {
        if (amount >= 1_000_000D) return String.format(Locale.ROOT, "%.2fM", amount / 1_000_000D);
        if (amount >= 10_000D) return String.format(Locale.ROOT, "%.1fk", amount / 1_000D);
        return String.format(Locale.ROOT, "%.1f", amount);
    }
}
