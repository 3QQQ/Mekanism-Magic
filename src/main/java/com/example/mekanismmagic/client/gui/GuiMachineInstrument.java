package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.container.MachinePanelLayout;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.tooltip.TooltipUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Compact, borderless information using the same palette and type as the machine shell. */
public final class GuiMachineInstrument extends GuiElement {
    private final boolean stacked;
    private final Supplier<Component> label;
    private final Supplier<Component> value;
    private final IntSupplier accent;
    private final Supplier<List<Component>> details;
    private final DoubleSupplier fill;
    private List<Component> previousTooltip = List.of();

    private GuiMachineInstrument(IGuiWrapper gui, MachinePanelLayout.Box box,
                                boolean stacked, Supplier<Component> label,
                                Supplier<Component> value, IntSupplier accent,
                                Supplier<List<Component>> details, DoubleSupplier fill) {
        super(gui, box.x(), box.y(), box.width(), box.height());
        this.stacked = stacked;
        this.label = label;
        this.value = value;
        this.accent = accent;
        this.details = details;
        this.fill = fill;
        active = false;
    }

    public static GuiMachineInstrument line(IGuiWrapper gui, MachinePanelLayout.Box box,
                                            Supplier<Component> label, Supplier<Component> value,
                                            IntSupplier accent, Supplier<List<Component>> details) {
        return new GuiMachineInstrument(gui, box, false, label, value, accent, details, null);
    }

    public static GuiMachineInstrument stacked(IGuiWrapper gui, MachinePanelLayout.Box box,
                                               Supplier<Component> label, Supplier<Component> value,
                                               IntSupplier accent, Supplier<List<Component>> details,
                                               DoubleSupplier fill) {
        return new GuiMachineInstrument(gui, box, true, label, value, accent, details, fill);
    }

    @Override public void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Leave the shared work surface visible; only resource ticks and
        // understated rules organize these readings, never a second skin.
        if (!stacked) {
            int center = relativeY + height / 2;
            graphics.fill(relativeX + 1, center - 2, relativeX + 3, center + 2, accent.getAsInt());
            graphics.fill(relativeX + 7, relativeY + height - 2,
                    relativeX + width - 4, relativeY + height - 1, MagicGuiTheme.readoutTrack());
        } else if (fill == null) {
            graphics.fill(relativeX + 1, relativeY + 3, relativeX + 3, relativeY + height - 3,
                    accent.getAsInt());
        }
        if (fill != null) {
            int inner = Math.max(0, width - 8);
            double ratio = fill.getAsDouble();
            int filled = Double.isFinite(ratio) ? (int) (inner * Math.clamp(ratio, 0, 1)) : 0;
            graphics.fill(relativeX + 4, relativeY + height - 4,
                    relativeX + width - 4, relativeY + height - 2, MagicGuiTheme.readoutTrack());
            if (filled > 0) graphics.fill(relativeX + 4, relativeY + height - 4,
                    relativeX + 4 + filled, relativeY + height - 2, accent.getAsInt());
        }
    }

    @Override public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        if (stacked) {
            String heading = bounded(label.get(), width - 6);
            String amount = bounded(value.get(), width - 6);
            graphics.drawString(font(), heading, relativeX + (width - font().width(heading)) / 2,
                    relativeY + 3, MagicGuiTheme.textMuted(), false);
            graphics.drawString(font(), amount, relativeX + (width - font().width(amount)) / 2,
                    relativeY + 13, MagicGuiTheme.textPrimary(), false);
        } else {
            String amount = bounded(value.get(), Math.max(0, width / 2 - 4));
            int valueX = relativeX + width - 5 - font().width(amount);
            String heading = bounded(label.get(), valueX - relativeX - 12);
            int y = relativeY + (height - font().lineHeight) / 2;
            graphics.drawString(font(), heading, relativeX + 7, y, MagicGuiTheme.textMuted(), false);
            graphics.drawString(font(), amount, valueX, y, MagicGuiTheme.textPrimary(), false);
        }
    }

    private String bounded(Component text, int maxWidth) {
        if (maxWidth <= 0) return "";
        String raw = text.getString();
        if (font().width(raw) <= maxWidth) return raw;
        if (maxWidth < font().width("…")) return font().plainSubstrByWidth(raw, maxWidth);
        return font().plainSubstrByWidth(raw, maxWidth - font().width("…")) + "…";
    }

    @Override public void updateTooltip(int mouseX, int mouseY) {
        List<Component> current = new ArrayList<>();
        current.add(label.get().copy().append(": ").append(value.get()));
        for (Component detail : details.get()) {
            if (current.stream().noneMatch(existing -> existing.getString().equals(detail.getString()))) current.add(detail);
        }
        if (!current.equals(previousTooltip)) {
            previousTooltip = List.copyOf(current);
            setTooltip(TooltipUtils.create(previousTooltip));
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) { return false; }

    @Override public boolean isMouseOver(double x, double y) {
        return visible && x >= getX() && y >= getY() && x < getX() + width && y < getY() + height;
    }
}
