package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Quiet section captions and rules on the existing machine surface, never another panel skin. */
public final class NatureMachineGroups {
    private NatureMachineGroups() { }

    public static GuiElement caption(IGuiWrapper gui, String key, int x, int y, int width) {
        return new GuiElement(gui, x, y, width, 10) {
            { active = false; }
            @Override public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
                String label = font().plainSubstrByWidth(Component.translatable(key).getString(), width);
                graphics.drawString(font(), label, relativeX + (width - font().width(label)) / 2,
                        relativeY, MagicGuiTheme.textMuted(), false);
            }
        };
    }

    public static GuiElement rule(IGuiWrapper gui, int x, int y, int width, int height) {
        return new GuiElement(gui, x, y, width, height) {
            { active = false; }
            @Override public void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                graphics.fill(relativeX, relativeY, relativeX + width, relativeY + height,
                        MagicGuiTheme.readoutTrack());
            }
        };
    }

    /** Empty physical processing slots explain their role on hover, not as permanent labels. */
    public static void slotHints(MekanismTileContainer<?> menu, Iterable<? extends GuiEventListener> children) {
        if (!(menu.getTileEntity() instanceof IMekanismMagicAutomation machine)) return;
        var inputs = machine.mekanismMagicPatternInputs();
        var outputs = machine.mekanismMagicPatternOutputs();
        for (var child : children) {
            if (!(child instanceof GuiSlot visual) || !visual.visible) continue;
            for (var slot : menu.getInventoryContainerSlots()) {
                if (visual.getRelativeX() + 1 != slot.x || visual.getRelativeY() + 1 != slot.y) continue;
                var inventory = slot.getInventorySlot();
                boolean input = inputs.stream().anyMatch(candidate -> candidate == inventory);
                boolean output = outputs.stream().anyMatch(candidate -> candidate == inventory);
                if (!input && !output) continue; // Never touch energy, retained catalysts or library overlays.
                String role = output ? "gui.mekanism_magic.panel.output" : "gui.mekanism_magic.panel.inputs";
                visual.hover(ignored -> inventory.isEmpty() ? List.of(Component.translatable(role)) : List.of());
                visual.active = false; // Preserve all clicks on the original menu slot.
                break;
            }
        }
    }
}
