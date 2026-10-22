package com.example.mekanismmagic.client.gui;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.lib.collection.LRU;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** One slot-free native window for appearance, network and optional machine-specific controls. */
public final class GuiMachineSettingsWindow extends GuiWindow {
    public static final int WIDTH = 176;
    public static final int NAV_Y = 22;
    public static final int NAV_HEIGHT = 14;
    public static final int BODY_Y = 42;
    public static final int INSET = 7;
    public static final int BOTTOM_INSET = 5;
    public static final int MIN_BODY_HEIGHT = 20;
    private static final int VISIBLE_NAVIGATION = 3;
    private final GuiMekanism<?> screen;
    private final TileEntityMekanism tile;
    private List<MachineSettingsSections.Section> sections;
    private List<Integer> preferredSectionHeights;
    private final List<GuiElement> navigation = new ArrayList<>();
    private final List<GuiElement> sectionChildren = new ArrayList<>();
    private final Map<String, Integer> sectionPages = new HashMap<>();
    private String selectedSectionId;

    public static void open(GuiMekanism<?> gui, TileEntityMekanism tile) {
        open(gui, tile, null);
    }

    public static void open(GuiMekanism<?> gui, TileEntityMekanism tile, String sectionId) {
        for (GuiWindow window : gui.getWindows()) {
            if (window instanceof GuiMachineSettingsWindow settings) {
                settings.refreshSections();
                var windows = (LRU<GuiWindow>) gui.getWindows();
                GuiWindow previous = windows.peek();
                if (previous != window) {
                    if (previous != null) previous.onFocusLost();
                    windows.moveUp(window);
                    window.onFocused();
                }
                gui.setFocused(window);
                if (sectionId != null) settings.selectSection(sectionId);
                return;
            }
        }
        if (MachineSettingsSections.supported(tile).isEmpty()) return;
        GuiMachineSettingsWindow window = new GuiMachineSettingsWindow(gui, tile);
        if (sectionId != null) window.selectSection(sectionId);
        gui.addWindow(window);
    }

    private GuiMachineSettingsWindow(GuiMekanism<?> gui, TileEntityMekanism tile) {
        this(gui, tile, windowHeight(tile, Minecraft.getInstance().getWindow().getGuiScaledHeight()));
    }

    private GuiMachineSettingsWindow(GuiMekanism<?> gui, TileEntityMekanism tile, int windowHeight) {
        super(gui, clampedRelativePosition((gui.getXSize() - WIDTH) / 2, gui.getGuiLeft(), WIDTH,
                        Minecraft.getInstance().getWindow().getGuiScaledWidth()),
                clampedRelativePosition(18, gui.getGuiTop(), windowHeight,
                        Minecraft.getInstance().getWindow().getGuiScaledHeight()),
                WIDTH, windowHeight, WindowType.UNSPECIFIED);
        screen = gui;
        this.tile = tile;
        interactionStrategy = InteractionStrategy.ALL;
        sections = MachineSettingsSections.supported(tile);
        preferredSectionHeights = sections.stream().map(section -> section.preferredHeight(tile)).toList();
        selectedSectionId = retainedSectionId(sections, null);
        rebuildPage();
    }

    /** Pure sizing contract; sections paginate their controls when the viewport limits their content. */
    public static int windowHeight(TileEntityMekanism tile, int viewportHeight) {
        return windowHeight(tile, viewportHeight, null);
    }

    public static int windowHeight(TileEntityMekanism tile, int viewportHeight, String sectionId) {
        return windowHeight(MachineSettingsSections.supported(tile), tile, viewportHeight, sectionId);
    }

    private static int windowHeight(List<MachineSettingsSections.Section> supported, TileEntityMekanism tile,
                                    int viewportHeight, String sectionId) {
        String selected = retainedSectionId(supported, sectionId);
        int desired = supported.stream().filter(section -> section.id().equals(selected))
                .mapToInt(section -> section.preferredHeight(tile)).findFirst().orElse(MIN_BODY_HEIGHT);
        int minimum = BODY_Y + MIN_BODY_HEIGHT + BOTTOM_INSET;
        return Math.min(BODY_Y + Math.max(MIN_BODY_HEIGHT, desired) + BOTTOM_INSET,
                Math.max(minimum, viewportHeight - 12));
    }

    /** Retains only a still-supported selection; no unregistered ID is made available by a refresh. */
    public static String retainedSectionId(List<MachineSettingsSections.Section> supported, String previous) {
        if (supported.stream().anyMatch(section -> section.id().equals(previous))) return previous;
        return supported.isEmpty() ? null : supported.getFirst().id();
    }

    /** Pure counterpart of the native move/clamp operation, relative to the current machine GUI. */
    public static int clampedRelativePosition(int position, int guiOrigin, int extent, int viewportExtent) {
        int minimum = 6 - guiOrigin;
        int maximum = Math.max(minimum, viewportExtent - guiOrigin - extent - 6);
        return Math.clamp(position, minimum, maximum);
    }

    private void refreshSections() {
        refreshSections(false);
    }

    private void refreshSections(boolean forceRebuild) {
        var supported = MachineSettingsSections.supported(tile);
        var preferred = supported.stream().map(section -> section.preferredHeight(tile)).toList();
        var viewport = Minecraft.getInstance().getWindow();
        String nextSelection = retainedSectionId(supported, selectedSectionId);
        int nextHeight = windowHeight(supported, tile, viewport.getGuiScaledHeight(), nextSelection);
        boolean rebuild = forceRebuild || !supported.equals(sections) || !preferred.equals(preferredSectionHeights)
                || nextHeight != height || !java.util.Objects.equals(nextSelection, selectedSectionId);
        sections = supported;
        preferredSectionHeights = preferred;
        selectedSectionId = nextSelection;
        setHeight(nextHeight);
        int targetX = clampedRelativePosition(relativeX, screen.getGuiLeft(), width, viewport.getGuiScaledWidth());
        int targetY = clampedRelativePosition(relativeY, screen.getGuiTop(), height, viewport.getGuiScaledHeight());
        if (targetX != relativeX || targetY != relativeY) move(targetX - relativeX, targetY - relativeY);
        if (rebuild) rebuildPage();
    }

    @Override public void tick() {
        // Refresh before the native child traversal, never while it is iterating.
        refreshSections();
        super.tick();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // Native ALL permits outside interaction. Inside whitespace/disabled
        // controls still belong to this window, including right clicks.
        return handled || isMouseOver(mouseX, mouseY);
    }

    public String selectedSectionId() { return selectedSectionId; }
    public List<MachineSettingsSections.Section> sections() { return sections; }
    public int sectionPage(String id) { return sectionPages.getOrDefault(id, 0); }
    public void setSectionPage(String id, int page) {
        sectionPages.put(id, Math.max(0, page));
        if (id.equals(selectedSectionId)) rebuildPage();
    }

    public MachineSettingsSections.Context sectionContext() {
        return new MachineSettingsSections.Context(screen, tile, this,
                relativeX + INSET, relativeY + BODY_Y, width - 2 * INSET, height - BODY_Y - BOTTOM_INSET);
    }

    public <T extends GuiElement> T addSectionChild(T child) {
        sectionChildren.add(child);
        return addChild(child);
    }

    public boolean selectSection(String id) {
        if (sections.stream().noneMatch(section -> section.id().equals(id))) return false;
        if (!id.equals(selectedSectionId)) {
            selectedSectionId = id;
            refreshSections(true);
        }
        return true;
    }

    private void rebuildPage() {
        setFocused(null);
        children().removeAll(navigation);
        children().removeAll(sectionChildren);
        navigation.clear();
        sectionChildren.clear();
        if (sections.isEmpty()) return;
        int selected = 0;
        for (int index = 0; index < sections.size(); index++) {
            if (sections.get(index).id().equals(selectedSectionId)) selected = index;
        }
        int start = selected / VISIBLE_NAVIGATION * VISIBLE_NAVIGATION;
        int visible = Math.min(VISIBLE_NAVIGATION, sections.size() - start);
        boolean paged = sections.size() > VISIBLE_NAVIGATION;
        int navX = relativeX + INSET + (paged ? 18 : 0);
        int navWidth = width - 2 * INSET - (paged ? 36 : 0);
        int buttonWidth = (navWidth - 4 * (visible - 1)) / visible;
        for (int index = 0; index < visible; index++) {
            var section = sections.get(start + index);
            NavButton button = new NavButton(navX + index * (buttonWidth + 4), relativeY + NAV_Y,
                    buttonWidth, section);
            navigation.add(addChild(button));
        }
        if (paged) {
            int previous = Math.max(0, start - VISIBLE_NAVIGATION);
            int next = Math.min(sections.size() - 1, start + VISIBLE_NAVIGATION);
            var back = new MekanismButton(screen, relativeX + INSET, relativeY + NAV_Y, 14, NAV_HEIGHT,
                    Component.literal("‹"), (element, mouseX, mouseY) -> selectSection(sections.get(previous).id()), null);
            back.active = start > 0;
            navigation.add(addChild(back));
            var forward = new MekanismButton(screen, relativeX + width - INSET - 14, relativeY + NAV_Y, 14, NAV_HEIGHT,
                    Component.literal("›"), (element, mouseX, mouseY) -> selectSection(sections.get(next).id()), null);
            forward.active = start + visible < sections.size();
            navigation.add(addChild(forward));
        }
        sections.get(selected).build(sectionContext());
    }

    @Override public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderForeground(graphics, mouseX, mouseY);
        drawTitleText(graphics, Component.translatable("gui.mekanism_magic.settings.title"), 6);
    }

    private final class NavButton extends MekanismButton {
        private final String sectionId;

        private NavButton(int x, int y, int buttonWidth, MachineSettingsSections.Section section) {
            super(screen, x, y, buttonWidth, NAV_HEIGHT, section.title(),
                    (element, mouseX, mouseY) -> selectSection(section.id()), null);
            sectionId = section.id();
            active = !sectionId.equals(selectedSectionId);
            setTooltip(Tooltip.create(section.title()));
        }
    }
}
