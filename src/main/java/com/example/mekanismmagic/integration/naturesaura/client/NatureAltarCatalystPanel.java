package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MekEnergisticsGuiCompat;
import com.example.mekanismmagic.container.RetainedSlotAccess;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryStorage;
import mekanism.api.inventory.IInventorySlot;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** The fixed 16-slot view over a dynamically sized Nature catalyst store. */
public final class NatureAltarCatalystPanel {
    private final IGuiWrapper gui;
    private final TileEntityMekanism tile;
    private final IntSupplier page;
    private final IntSupplier pageCount;
    private final IntSupplier visibleSlots;
    private final Consumer<Boolean> setOpen;
    private final IntConsumer sendButton;
    private final Runnable onOpen;
    private final List<BaseSlot> baseSlots = new ArrayList<>(2);
    private final List<BaseOverlay> baseOverlays = new ArrayList<>(2);
    private final List<VisualSlot> overlays = new ArrayList<>();
    private final GuiNatureCatalystTab<TileEntityMekanism> tab;
    private final MekanismButton previous;
    private final MekanismButton next;
    private boolean open;
    private boolean visible;
    private int displayedPage = -1;
    private int displayedSlotCount = -1;
    private NatureCatalystPanelLayout layout;

    public NatureAltarCatalystPanel(IGuiWrapper gui,
                                    TileEntityMekanism tile,
                                    MekanismTileContainer<?> menu,
                                    List<IInventorySlot> retainedCatalysts,
                                    IntSupplier page, IntSupplier pageCount,
                                    IntSupplier visibleSlots,
                                    Consumer<Boolean> setOpen,
                                    IntConsumer sendButton,
                                    Runnable onOpen) {
        this.gui = gui;
        this.tile = tile;
        this.page = page;
        this.pageCount = pageCount;
        this.visibleSlots = visibleSlots;
        this.setOpen = setOpen;
        this.sendButton = sendButton;
        this.onOpen = onOpen;
        this.tab = new GuiNatureCatalystTab<>(gui, tile,
                () -> open, this::toggle);
        layout = currentLayout();
        if (retainedCatalysts.size() != 2) {
            throw new IllegalArgumentException(
                    "Nature catalyst panel requires two stable retained slots");
        }
        for (int index = 0; index < retainedCatalysts.size(); index++) {
            IInventorySlot retained = retainedCatalysts.get(index);
            List<InventoryContainerSlot> matches = menu
                    .getInventoryContainerSlots().stream()
                    .filter(slot -> slot.getInventorySlot() == retained)
                    .toList();
            if (matches.size() != 1
                    || !(matches.getFirst() instanceof RetainedSlotAccess access)) {
                throw new IllegalStateException(
                        "Nature catalyst retained slot is absent from its menu");
            }
            InventoryContainerSlot menuSlot = matches.getFirst();
            int x = layout.baseSlotX(index);
            int y = layout.baseY();
            access.mekanismMagic$positionRetainedSlot(x, y, false);
            baseSlots.add(new BaseSlot(menuSlot, access));
        }
        this.previous = new MekanismButton(gui, layout.x() + 4,
                Math.max(0, layout.footerY()), 28, 18,
                Component.literal("<"), (element, x, y) -> {
                    sendButton.accept(220);
                    return true;
                });
        this.next = new MekanismButton(gui, layout.x() + 66,
                Math.max(0, layout.footerY()), 28, 18,
                Component.literal(">"), (element, x, y) -> {
                    sendButton.accept(221);
                    return true;
                });
        setOpen.accept(false);
        updateVisibility(false);
    }

    public GuiNatureCatalystTab<TileEntityMekanism> tab() {
        return tab;
    }

    public MekanismButton previousButton() {
        return previous;
    }

    public MekanismButton nextButton() {
        return next;
    }

    public void captureOverlays(
            List<? extends GuiEventListener> children) {
        overlays.clear();
        baseOverlays.clear();
        int slotLeft = CatalystLibraryLayout.slotLeft(gui.getXSize());
        for (GuiEventListener child : children) {
            if (!(child instanceof GuiSlot slot)) {
                continue;
            }
            int x = slot.getRelativeX() + 1;
            int y = slot.getRelativeY() + 1;
            for (int index = 0; index < baseSlots.size(); index++) {
                BaseSlot base = baseSlots.get(index);
                if (base.menuSlot().x == x && base.menuSlot().y == y) {
                    baseOverlays.add(new BaseOverlay(slot, index));
                    slot.active = false;
                    break;
                }
            }
            if (baseOverlays.stream().anyMatch(base -> base.slot() == slot)) {
                continue;
            }
            if (x >= slotLeft && x < slotLeft
                    + CatalystLibraryLayout.COLUMNS
                    * CatalystLibraryLayout.SLOT_SPACING
                    && (x - slotLeft)
                    % CatalystLibraryLayout.SLOT_SPACING == 0
                    && y >= CatalystLibraryLayout.SLOT_TOP
                    && y < CatalystLibraryLayout.SLOT_TOP
                    + 4 * CatalystLibraryLayout.SLOT_SPACING
                    && (y - CatalystLibraryLayout.SLOT_TOP)
                    % CatalystLibraryLayout.SLOT_SPACING == 0) {
                int pageSlot = (y - CatalystLibraryLayout.SLOT_TOP)
                        / CatalystLibraryLayout.SLOT_SPACING
                        * CatalystLibraryLayout.COLUMNS
                        + (x - slotLeft)
                        / CatalystLibraryLayout.SLOT_SPACING;
                overlays.add(new VisualSlot(slot, pageSlot));
                slot.active = false;
            }
        }
        updateVisibility(visible);
    }

    public void renderBackground(GuiGraphics graphics, int left, int top,
                                 Iterable<?> windows) {
        updateVisibility(open
                && !MekEnergisticsGuiCompat.isPatternWindowOpen(windows));
        if (visible) {
            MagicGuiTheme.renderDockedPanel(graphics,
                    left + layout.x(), top + layout.y(),
                    layout.width(), layout.height(),
                    layout.headerHeight());
        }
    }

    public void renderCaption(GuiGraphics graphics, Font font) {
        if (!visible) {
            return;
        }
        MagicGuiTheme.renderPanelCaption(graphics, font,
                Component.translatable(
                        "gui.mekanism_magic.nature_catalyst_library"),
                layout.x(), layout.y(), layout.width(),
                layout.headerHeight());
        if (layout.navigation()) {
            MagicGuiTheme.renderPageNumber(graphics, font, page.getAsInt(),
                    pageCount.getAsInt(), layout.x() + layout.width() / 2,
                    layout.footerY() + 5);
        }
    }

    public void containerTick() {
        if (displayedPage != page.getAsInt()
                || displayedSlotCount != visibleSlots.getAsInt()) {
            updateVisibility(visible);
        }
    }

    public boolean contains(double mouseX, double mouseY,
                            int left, int top) {
        int x = left + layout.x();
        int y = top + layout.y();
        return visible && mouseX >= x
                && mouseX < x + layout.width()
                && mouseY >= y
                && mouseY < y + layout.height();
    }

    public void close() {
        open = false;
        setOpen.accept(false);
        updateVisibility(false);
    }

    public void dispose() {
        close();
        for (BaseSlot base : baseSlots) {
            base.access().mekanismMagic$restoreRetainedSlot();
        }
        baseSlots.clear();
        baseOverlays.clear();
        overlays.clear();
    }

    private void toggle() {
        open = !open;
        setOpen.accept(open);
        if (open) {
            onOpen.run();
        }
        updateVisibility(open);
    }

    private void updateVisibility(boolean shown) {
        visible = shown;
        displayedPage = page.getAsInt();
        displayedSlotCount = visibleSlots.getAsInt();
        NatureCatalystPanelLayout nextLayout = currentLayout();
        if (!nextLayout.equals(layout)) {
            for (int index = 0; index < baseSlots.size(); index++) {
                BaseSlot base = baseSlots.get(index);
                int oldX = base.menuSlot().x;
                int oldY = base.menuSlot().y;
                int newX = nextLayout.baseSlotX(index);
                int newY = nextLayout.baseY();
                base.access().mekanismMagic$positionRetainedSlot(
                        newX, newY, shown);
                for (BaseOverlay overlay : baseOverlays) {
                    if (overlay.index() == index) {
                        overlay.slot().move(newX - oldX, newY - oldY);
                    }
                }
            }
            int buttonY = Math.max(0, nextLayout.footerY());
            previous.move(0, buttonY - previous.getRelativeY());
            next.move(0, buttonY - next.getRelativeY());
            layout = nextLayout;
        }
        for (BaseSlot base : baseSlots) {
            base.access().mekanismMagic$setRetainedVisible(shown);
        }
        for (BaseOverlay overlay : baseOverlays) {
            overlay.slot().visible = shown;
            overlay.slot().active = false;
        }
        for (VisualSlot visual : overlays) {
            GuiSlot slot = visual.slot();
            int absolute = CatalystLibraryStorage.absoluteIndex(
                    displayedPage, visual.pageSlot());
            slot.visible = shown && layout.overflow()
                    && absolute < displayedSlotCount;
            slot.active = false;
        }
        previous.visible = shown && layout.navigation();
        next.visible = shown && layout.navigation();
        previous.active = shown && layout.navigation()
                && displayedPage > 0;
        next.active = shown && layout.navigation()
                && displayedPage + 1 < pageCount.getAsInt();
    }

    private NatureCatalystPanelLayout currentLayout() {
        return NatureCatalystPanelLayout.of(gui.getXSize(),
                visibleSlots.getAsInt(), page.getAsInt(),
                pageCount.getAsInt());
    }

    private record VisualSlot(GuiSlot slot, int pageSlot) {
    }

    private record BaseSlot(InventoryContainerSlot menuSlot,
                            RetainedSlotAccess access) {
    }

    private record BaseOverlay(GuiSlot slot, int index) {
    }
}
