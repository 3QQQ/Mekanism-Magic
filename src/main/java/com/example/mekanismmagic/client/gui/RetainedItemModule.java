package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.container.RetainedModuleLayout;
import com.example.mekanismmagic.container.RetainedSlotAccess;
import com.example.mekanismmagic.container.RetainedSlotProfiles;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.tab.GuiInsetToggleElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Chalk-style client presentation over the original menu slots. Never copies, replaces or mutates their contents. */
public final class RetainedItemModule {
    private final IGuiWrapper gui;
    private final TileEntityMekanism tile;
    private final RetainedModuleLayout layout;
    private final List<Binding> bindings;
    private final Runnable onOpen;
    private final List<GuiSlot> overlays = new ArrayList<>();
    private boolean open;
    private boolean visible;

    public static @Nullable RetainedItemModule prepare(@Nullable RetainedItemModule previous, IGuiWrapper gui,
                                                       MekanismTileContainer<?> menu, int imageWidth, Runnable onOpen) {
        boolean wasOpen = previous != null && previous.open;
        if (previous != null) previous.dispose();
        var entries = RetainedSlotProfiles.entries(menu.getTileEntity());
        if (entries.isEmpty()) return null;
        List<Binding> bindings = new ArrayList<>();
        for (var entry : entries) {
            var matches = menu.getInventoryContainerSlots().stream()
                    .filter(slot -> slot.getInventorySlot() == entry.inventory()).toList();
            if (matches.size() != 1) throw new IllegalStateException("Retained backing slot must appear exactly once in its menu");
            var slot = matches.getFirst();
            if (!(slot instanceof RetainedSlotAccess access)) throw new IllegalStateException("Retained slot presentation hook is unavailable");
            bindings.add(new Binding(slot, access, entry.tooltipKey()));
        }
        boolean dictionaryNeighbour = menu.getTileEntity() instanceof com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
        return new RetainedItemModule(gui, menu.getTileEntity(),
                RetainedModuleLayout.of(imageWidth, bindings.size(), dictionaryNeighbour), bindings, wasOpen, onOpen);
    }

    private RetainedItemModule(IGuiWrapper gui, TileEntityMekanism tile, RetainedModuleLayout layout,
                               List<Binding> bindings, boolean open, Runnable onOpen) {
        this.gui = gui;
        this.tile = tile;
        this.layout = layout;
        this.bindings = List.copyOf(bindings);
        this.open = open;
        this.visible = open;
        this.onOpen = onOpen;
        for (int index = 0; index < bindings.size(); index++) {
            bindings.get(index).access().mekanismMagic$positionRetainedSlot(layout.slotX(index), layout.slotY(index), open);
        }
    }

    public GuiInsetToggleElement<TileEntityMekanism> tab() { return new ModuleTab(); }

    /** Called after Mekanism creates overlays from the already-repositioned original menu slots. */
    public void captureOverlays(List<? extends GuiEventListener> children) {
        overlays.clear();
        for (var child : children) {
            if (!(child instanceof GuiSlot slot)) continue;
            for (var binding : bindings) {
                if (matches(slot, binding.slot())) {
                    overlays.add(slot);
                    slot.hover(ignored -> binding.slot().hasItem() ? List.of()
                            : List.of(Component.translatable(binding.tooltipKey())));
                    slot.active = false; // The real container slot handles clicks, not its visual border.
                    break;
                }
            }
        }
        updateVisibility(visible);
    }

    public boolean isVisualSlot(GuiSlot slot) { return bindings.stream().anyMatch(binding -> matches(slot, binding.slot())); }

    private static boolean matches(GuiSlot visual, InventoryContainerSlot actual) {
        return visual.getRelativeX() + 1 == actual.x && visual.getRelativeY() + 1 == actual.y;
    }

    public void renderBackground(GuiGraphics graphics, int left, int top, Iterable<?> windows) {
        updateVisibility(open && !MekEnergisticsGuiCompat.isPatternWindowOpen(windows));
        if (visible) MagicGuiTheme.renderDockedPanel(graphics, left + layout.panelX(), top + layout.panelY(), layout.width(), layout.height());
    }

    public void renderCaption(GuiGraphics graphics, Font font) {
        if (visible) MagicGuiTheme.renderPanelCaption(graphics, font,
                Component.translatable("gui.mekanism_magic.retained.caption"), layout.panelX(), layout.panelY(), layout.width());
    }

    public boolean contains(double mouseX, double mouseY, int left, int top) {
        return visible && layout.contains(mouseX - left, mouseY - top);
    }

    private void toggle() {
        open = !open;
        if (open) onOpen.run();
        updateVisibility(open);
    }

    public void close() { open = false; updateVisibility(false); }

    private void updateVisibility(boolean visible) {
        this.visible = visible;
        for (var binding : bindings) binding.access().mekanismMagic$setRetainedVisible(visible);
        for (var overlay : overlays) { overlay.visible = visible; overlay.active = false; }
    }

    public void dispose() {
        for (var binding : bindings) binding.access().mekanismMagic$restoreRetainedSlot();
        overlays.clear();
    }

    private record Binding(InventoryContainerSlot slot, RetainedSlotAccess access, String tooltipKey) { }

    private final class ModuleTab extends GuiInsetToggleElement<TileEntityMekanism> {
        private static final ResourceLocation ICON = ResourceLocation.withDefaultNamespace("textures/item/ender_eye.png");
        private ModuleTab() {
            super(RetainedItemModule.this.gui, RetainedItemModule.this.tile, RetainedItemModule.this.gui.getXSize(),
                    RetainedItemModule.this.layout.tabY(), 26, 18, false, ICON, ICON, () -> RetainedItemModule.this.open);
            setTooltip(Tooltip.create(Component.translatable("gui.mekanism_magic.retained.module")));
        }
        @Override protected void colorTab(GuiGraphics graphics) { MekanismRenderer.color(graphics, MagicGuiTheme.accentRitual()); }
        @Override public void onClick(double x, double y, int button) { toggle(); }
    }
}
