package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.config.MagicClientConfig;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Client-only extension point for controls sharing the single machine-settings window. */
public final class MachineSettingsSections {
    private static final LinkedHashMap<String, Section> SECTIONS = new LinkedHashMap<>();

    static {
        register(new ThemeSection());
        register(new MachineAeSettingsSection());
    }

    private MachineSettingsSections() { }

    public static synchronized void register(Section section) {
        Objects.requireNonNull(section, "section");
        String id = Objects.requireNonNull(section.id(), "section id");
        if (id.isBlank()) throw new IllegalArgumentException("Settings section needs a stable ID");
        Section previous = SECTIONS.putIfAbsent(id, section);
        if (previous != null && previous != section) {
            throw new IllegalArgumentException("Duplicate machine-settings section: " + id);
        }
    }

    public static synchronized List<Section> registered() {
        return List.copyOf(SECTIONS.values());
    }

    public static List<Section> supported(TileEntityMekanism tile) {
        return registered().stream().filter(section -> section.supports(tile)).toList();
    }

    public interface Section {
        String id();
        Component title();
        boolean supports(TileEntityMekanism tile);
        /** Preferred content height, excluding the shared title/navigation and bottom inset. */
        int preferredHeight(TileEntityMekanism tile);
        /** All controls belong to this page in the existing window, not to a second window. */
        void build(Context context);
    }

    /** Coordinates are relative to the machine GUI, just like native GuiElement constructors. */
    public record Context(GuiMekanism<?> gui, TileEntityMekanism tile,
                          GuiMachineSettingsWindow window, int x, int y, int width, int height) {
        public <T extends GuiElement> T add(T child) {
            return window.addSectionChild(child);
        }
    }

    private static final class ThemeSection implements Section {
        @Override public String id() { return "theme"; }
        @Override public Component title() {
            return Component.translatable("gui.mekanism_magic.settings.section.theme");
        }
        @Override public boolean supports(TileEntityMekanism tile) { return tile != null; }
        @Override public int preferredHeight(TileEntityMekanism tile) { return 22; }

        @Override public void build(Context context) {
            context.add(new ThemeToggle(context));
        }
    }

    private static final class ThemeToggle extends MekanismButton {
        private ThemeToggle(Context context) {
            super(context.gui(), context.x(), context.y() + 2, context.width(), 14, Component.empty(),
                    (element, mouseX, mouseY) -> {
                        MagicClientConfig.toggleTheme();
                        ((ThemeToggle) element).refresh();
                        return true;
                    }, null);
            refresh();
        }

        private void refresh() {
            Component theme = Component.translatable(MagicClientConfig.isLight()
                    ? "gui.mekanism_magic.theme.light" : "gui.mekanism_magic.theme.dark");
            Component label = Component.translatable("gui.mekanism_magic.settings.theme.value", theme);
            setMessage(label);
            setTooltip(Tooltip.create(label.copy().append("\n").append(Component.translatable(
                    "gui.mekanism_magic.settings.theme.description"))));
        }

        @Override public void tick() { super.tick(); refresh(); }
    }
}
