package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.container.MachinePanelLayout;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.tooltip.TooltipUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Value-first readouts. Explanations belong to the values' hover details, never to extra reminder controls. */
public final class GuiMachineReadout extends GuiElement {
    private enum Style { LINE, STACKED }
    private final Style style;
    private final Supplier<Component> label;
    private final Supplier<Component> value;
    private final IntSupplier accent;
    private final Supplier<List<Component>> details;
    private final DoubleSupplier fill;
    private List<Component> previousTooltip = List.of();

    private GuiMachineReadout(IGuiWrapper gui, MachinePanelLayout.Box box, Style style,
                              Supplier<Component> label, Supplier<Component> value, IntSupplier accent,
                              Supplier<List<Component>> details, DoubleSupplier fill) {
        super(gui, box.x(), box.y(), box.width(), box.height());
        this.style = style;
        this.label = label;
        this.value = value;
        this.accent = accent;
        this.details = details;
        this.fill = fill;
        active = false;
    }

    public static GuiMachineReadout line(IGuiWrapper gui, int x, int y, int width, int height,
                                         Supplier<Component> label, Supplier<Component> value, IntSupplier color,
                                         Supplier<List<Component>> details) {
        return new GuiMachineReadout(gui, new MachinePanelLayout.Box(x, y, width, height), Style.LINE,
                label, value, color, details, null);
    }

    public static GuiMachineReadout line(IGuiWrapper gui, MachinePanelLayout.Box box,
                                         Supplier<Component> label, Supplier<Component> value, IntSupplier color,
                                         Supplier<List<Component>> details) {
        return new GuiMachineReadout(gui, box, Style.LINE, label, value, color, details, null);
    }

    public static GuiMachineReadout stacked(IGuiWrapper gui, MachinePanelLayout.Box box,
                                            Supplier<Component> label, Supplier<Component> value, IntSupplier color,
                                            Supplier<List<Component>> details, DoubleSupplier fill) {
        return new GuiMachineReadout(gui, box, Style.STACKED, label, value, color, details, fill);
    }

    @Override public void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MagicGuiTheme.renderReadoutFrame(graphics, relativeX, relativeY, width, height);
        if (fill != null) {
            double ratio = fill.getAsDouble();
            int inner = width - 8;
            int amount = Double.isFinite(ratio) ? (int) (inner * Math.clamp(ratio, 0D, 1D)) : 0;
            graphics.fill(relativeX + 4, relativeY + height - 4, relativeX + width - 4, relativeY + height - 2, MagicGuiTheme.readoutTrack());
            if (amount > 0) graphics.fill(relativeX + 4, relativeY + height - 4, relativeX + 4 + amount, relativeY + height - 2, accent.getAsInt());
        }
    }

    @Override public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        int textY = relativeY + (height - font().lineHeight) / 2;
        if (style == Style.STACKED) {
            int availableHeight = height - (fill == null ? 0 : 4);
            drawCenteredBounded(graphics, value.get(), relativeY + (availableHeight - font().lineHeight) / 2,
                    width - 8, textAccent(accent.getAsInt(), MagicGuiTheme.isLight()));
        } else {
            graphics.fill(relativeX + 5, relativeY + height / 2 - 1, relativeX + 8, relativeY + height / 2 + 2, accent.getAsInt());
            String bounded = bounded(value.get(), width - 16);
            graphics.drawString(font(), bounded, relativeX + 12 + (width - 16 - font().width(bounded)) / 2, textY,
                    textAccent(accent.getAsInt(), MagicGuiTheme.isLight()), false);
        }
    }

    private void drawCenteredBounded(GuiGraphics graphics, Component text, int y, int maxWidth, int color) {
        String bounded = bounded(text, maxWidth);
        graphics.drawString(font(), bounded, relativeX + (width - font().width(bounded)) / 2, y, color, false);
    }
    private String bounded(Component text, int maxWidth) {
        if (maxWidth <= 0) return "";
        String raw = text.getString();
        if (maxWidth < font().width("…")) return font().plainSubstrByWidth(raw, maxWidth);
        return font().width(raw) <= maxWidth ? raw : font().plainSubstrByWidth(raw, Math.max(0, maxWidth - font().width("…"))) + "…";
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

    public static String compact(long amount) {
        if (Math.abs(amount) >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fG", amount / 1_000_000_000D);
        if (Math.abs(amount) >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000D);
        if (Math.abs(amount) >= 10_000L) return String.format(Locale.ROOT, "%.0fk", amount / 1000D);
        if (Math.abs(amount) >= 1_000L) return String.format(Locale.ROOT, "%.1fk", amount / 1000D);
        return Long.toString(amount);
    }

    /** Bright resource hues remain readable as text on the light palette's white cards. */
    public static int textAccent(int color, boolean light) {
        if (!light) return color;
        return 0xFF000000 | Math.round((color >> 16 & 255) * 0.55F) << 16
                | Math.round((color >> 8 & 255) * 0.55F) << 8 | Math.round((color & 255) * 0.55F);
    }
}
