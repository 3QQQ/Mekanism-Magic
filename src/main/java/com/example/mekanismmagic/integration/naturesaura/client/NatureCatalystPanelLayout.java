package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.container.DockedPanelLayout;
import com.example.mekanismmagic.container.RetainedModuleLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;

/** Geometry for the retained catalysts and their optional overflow page. */
public record NatureCatalystPanelLayout(int x, int y, int width, int height,
                                        int headerHeight, int baseX, int baseY,
                                        int footerY, boolean overflow,
                                        boolean navigation) {
    private static final int EXPANDED_TOP = 44;

    public static NatureCatalystPanelLayout of(int imageWidth,
                                                int visibleOverflowSlots,
                                                int page, int pageCount) {
        if (visibleOverflowSlots <= 0) {
            RetainedModuleLayout compact = RetainedModuleLayout.of(
                    imageWidth, 2, false);
            return new NatureCatalystPanelLayout(
                    compact.panelX(), compact.panelY(), compact.width(),
                    compact.height(), DockedPanelLayout.HEADER,
                    compact.slotX(0), compact.slotY(0), -1,
                    false, false);
        }
        int panelX = CatalystLibraryLayout.panelLeft(imageWidth);
        int pageSlots = Math.max(1, Math.min(CatalystLibraryLayout.PAGE_SIZE,
                visibleOverflowSlots - Math.max(0, page)
                        * CatalystLibraryLayout.PAGE_SIZE));
        int rows = Math.ceilDiv(pageSlots, CatalystLibraryLayout.COLUMNS);
        boolean navigation = pageCount > 1;
        int footerY = CatalystLibraryLayout.SLOT_TOP - 1
                + rows * CatalystLibraryLayout.SLOT_SPACING
                + DockedPanelLayout.PADDING;
        int bottom = footerY + (navigation ? DockedPanelLayout.SLOT : 0)
                + DockedPanelLayout.PADDING;
        return new NatureCatalystPanelLayout(panelX, EXPANDED_TOP,
                CatalystLibraryLayout.PANEL_WIDTH, bottom - EXPANDED_TOP,
                DockedPanelLayout.HEADER,
                panelX + (CatalystLibraryLayout.PANEL_WIDTH
                        - 2 * DockedPanelLayout.SLOT) / 2 + 1,
                EXPANDED_TOP + DockedPanelLayout.HEADER
                        + DockedPanelLayout.PADDING + 1,
                footerY, true, navigation);
    }

    public int baseSlotX(int index) {
        if (index < 0 || index >= 2) {
            throw new IndexOutOfBoundsException(index);
        }
        return baseX + index * DockedPanelLayout.SLOT;
    }
}
