package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.config.MagicClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared visual language for Mekanism Magic screens.
 *
 * <p>Both schemes use cyan for Source, violet for rituals and warm gold for
 * attention states. All coordinates passed to foreground helpers are
 * GUI-relative; background helpers use absolute screen coordinates.</p>
 */
public final class MagicGuiTheme {
    private static final int DARK_SOURCE_DUST = 0xFF3F8291;
    private static final int DARK_RITUAL_DUST = 0xFF6D58A0;
    private static final int DARK_MAGIC_SPARK = 0xFFAFC8DB;

    private static final Palette DARK = new Palette(
            0xFFF7FAFF, 0xFFC3CFDF, 0xFFF1F6FE, 0xFFE7F6FF,
            0xFF4BD7E8, 0xFF9B72FF,
            0xFF0B0F17, 0xFF111725, 0xFF171E2A, 0xFF131923,
            0xFF070A10, 0xFF314158, 0xFF223047);
    private static final Palette LIGHT = new Palette(
            0xFF101C2D, 0xFF34465E, 0xFF14263C, 0xFFEAF8FF,
            0xFF128FA5, 0xFF7152D2,
            0xFF718096, 0xFFF7F9FD, 0xFFE7EDF4, 0xFFF1F5F9,
            0xFF4C5B70, 0xFFB8C6D5, 0xFF8C9CB0);

    private MagicGuiTheme() {
    }

    public static boolean isLight() {
        return MagicClientConfig.isLight();
    }

    public static int textPrimary() {
        return palette().textPrimary();
    }

    public static int textMuted() {
        return palette().textMuted();
    }

    public static int textPanel() {
        return palette().textPanel();
    }

    public static int textScreen() {
        return palette().textScreen();
    }

    public static int accentSource() {
        return palette().accentSource();
    }

    public static int accentRitual() {
        return palette().accentRitual();
    }

    /** Text drawn on the normal enabled button state. */
    public static int activeButtonText() {
        return isLight() ? textPrimary() : 0xFFF7FAFF;
    }

    public static int inactiveButtonText() {
        // The disabled strip is deliberately darker in both texture sets.
        return isLight() ? 0xFFF0F4FA : textMuted();
    }

    /** Both texture sets use a bright cyan hover strip. */
    public static int hoveredButtonText() {
        return isLight() ? 0xFF0B1D2D : 0xFF071821;
    }

    /**
     * Draws the common header, work deck and inventory deck.
     *
     * <p>Interactive and readable areas intentionally use uninterrupted
     * color fields. Decorative rails are confined to the outer frame so a
     * transparent slot or glyph can never reveal a line underneath it.</p>
     */
    public static void renderMachineFrame(GuiGraphics graphics,
                                          int left, int top,
                                          int width, int height,
                                          int inventoryLabelY) {
        int right = left + width;
        int bottom = top + height;
        Palette palette = palette();
        graphics.fill(left + 4, top + 4, right - 4, bottom - 4,
                palette.frame());

        // The title/status band stays completely flat. Previous horizontal
        // accent strokes crossed the font because titles begin at y=4.
        graphics.fill(left + 5, top + 4, right - 5, top + 15,
                palette.header());

        // Slots may begin at y=16 and outputs may extend down to the
        // inventory caption. Use one borderless work field so no horizontal
        // panel edge can pass through their transparent centers.
        int workTop = top + 15;
        int inventoryTop = Math.max(workTop + 8,
                top + inventoryLabelY - 2);
        graphics.fill(left + 7, workTop, right - 7, inventoryTop,
                palette.workSurface());

        // A quiet color shift groups the inventory without introducing a
        // bright divider that could touch low output slots.
        graphics.fill(left + 7, inventoryTop, right - 7, bottom - 7,
                palette.inventorySurface());

        drawDarkMagicStarlight(graphics, left, right, workTop,
                inventoryTop, bottom);
        drawLargeFrameDetails(graphics, left, top, right, bottom,
                width, height, palette);
        drawFrameRails(graphics, left, top, right, bottom, palette);
    }

    /** Draws an attached module such as the catalyst or chalk library. */
    public static void renderDockedPanel(GuiGraphics graphics,
                                         int left, int top,
                                         int width, int height) {
        Palette palette = palette();
        graphics.fill(left, top, left + width, top + height,
                palette.frame());
        graphics.fill(left, top, left + 12, top + 2,
                palette.accentRitual());
        graphics.fill(left, top + 2, left + 2, top + 12,
                palette.accentSource());
        graphics.fill(left + width - 2, top + 2, left + width,
                top + height, palette.panelShadow());
        graphics.fill(left + 2, top + height - 2, left + width,
                top + height, palette.panelShadow());
        graphics.fill(left + 3, top + 2, left + width - 2, top + 14,
                palette.header());
        graphics.fill(left + 4, top + 14, left + width - 4,
                top + height - 4, palette.workSurface());
        drawDockedPanelDetails(graphics, left, top, width, height, palette);
    }

    /** Draws the same visual system behind a JEI recipe layout. */
    public static void renderRecipePanel(GuiGraphics graphics,
                                         int width, int height) {
        Palette palette = palette();
        graphics.fill(0, 0, width, height, palette.frame());
        graphics.fill(2, 2, width - 2, height - 2,
                palette.workSurface());
        graphics.fill(0, 0, Math.min(12, width), 2,
                palette.accentRitual());
        graphics.fill(0, 2, 2, Math.min(12, height),
                palette.accentRitual());
        graphics.fill(Math.max(0, width - 12), height - 2,
                width, height, palette.accentSource());
        graphics.fill(width - 2, Math.max(0, height - 12),
                width, height - 2, palette.accentSource());
    }

    /** Draws a concise caption in the header of an attached module. */
    public static void renderPanelCaption(GuiGraphics graphics, Font font,
                                          Component caption,
                                          int left, int top, int width) {
        String text = font.plainSubstrByWidth(caption.getString(), width - 10);
        graphics.drawString(font, text, left + 5, top + 4,
                textPanel(), false);
    }

    /** Draws the centered page number between the two navigation buttons. */
    public static void renderPageNumber(GuiGraphics graphics, Font font,
                                        int page, int pageCount,
                                        int centerX, int y) {
        Component text = Component.translatable(
                "gui.mekanism_magic.page", page + 1,
                Math.max(1, pageCount));
        drawCenteredText(graphics, font, text, centerX, y, textMuted());
    }

    /**
     * Centers text without Minecraft's default one-pixel shadow. The shadow
     * makes dark glyphs look doubled on the light work surface.
     */
    public static void drawCenteredText(GuiGraphics graphics, Font font,
                                        Component text, int centerX, int y,
                                        int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2,
                y, color, false);
    }

    public static int availableTitleWidth(Font font, MachineStatus status,
                                          int screenWidth) {
        int statusStart = statusStart(font, status, screenWidth);
        return Math.max(28, statusStart - 16);
    }

    /** Draws a live state indicator at the right edge of the header. */
    public static void renderStatus(GuiGraphics graphics, Font font,
                                    MachineStatus status,
                                    int screenWidth, int y) {
        Component label = status.label();
        int textX = statusStart(font, status, screenWidth);
        int nodeY = y + 3;
        graphics.fill(textX - 7, nodeY, textX - 4, nodeY + 3,
                status.color());
        graphics.fill(textX - 6, nodeY - 1, textX - 5, nodeY + 4,
                status.color());
        graphics.drawString(font, label, textX, y,
                status.color(), false);
    }

    private static int statusStart(Font font, MachineStatus status,
                                   int screenWidth) {
        return Math.max(screenWidth / 2 + 12,
                screenWidth - 8 - font.width(status.label()));
    }

    private static void drawFrameRails(GuiGraphics graphics,
                                       int left, int top,
                                       int right, int bottom,
                                       Palette palette) {
        // Accent marks live only in the four-pixel frame gutter. They retain
        // the Source/ritual identity without entering any content region.
        graphics.fill(left + 4, top + 4, left + 6, top + 15,
                palette.accentRitual());
        graphics.fill(left + 4, top + 4, left + 8, top + 6,
                palette.accentRitual());
        graphics.fill(right - 6, top + 4, right - 4, top + 15,
                palette.accentSource());
        graphics.fill(right - 8, top + 4, right - 4, top + 6,
                palette.accentSource());
        graphics.fill(left + 4, bottom - 15, left + 6, bottom - 4,
                palette.accentSource());
        graphics.fill(left + 4, bottom - 6, left + 8, bottom - 4,
                palette.accentSource());
        graphics.fill(right - 6, bottom - 15, right - 4,
                bottom - 4, palette.accentRitual());
        graphics.fill(right - 8, bottom - 6, right - 4,
                bottom - 4, palette.accentRitual());
    }

    /**
     * Adds a deterministic pseudo-random star field behind the work deck,
     * inventory and hotbar. This is part of the background pass, so slots,
     * items and widgets drawn afterwards cover any star beneath them.
     */
    private static void drawDarkMagicStarlight(GuiGraphics graphics,
                                               int left, int right,
                                               int workTop,
                                               int inventoryTop,
                                               int bottom) {
        if (isLight() || bottom - workTop < 48) {
            return;
        }

        int minX = left + 10;
        int maxX = right - 13;
        int minY = workTop + 5;
        int maxY = bottom - 12;
        int usableWidth = maxX - minX + 1;
        int usableHeight = maxY - minY + 1;
        int inventoryMinY = Math.min(maxY,
                Math.max(minY, inventoryTop + 4));
        int inventoryHeight = maxY - inventoryMinY + 1;
        int area = (right - left - 14) * (bottom - workTop - 7);
        int starCount = Math.max(16, Math.min(24, area / 1_250));
        int prominentStars = Math.max(3, starCount / 6);
        int inventoryStars = Math.max(5, starCount / 3);
        long state = 0x6A09E667F3BCC909L
                ^ ((long) (right - left) << 32)
                ^ (bottom - workTop);

        for (int index = 0; index < starCount; index++) {
            state = nextStarlightState(state);
            long xNoise = state & Long.MAX_VALUE;
            int x;
            if (index == 0) {
                x = minX + (int) (xNoise % Math.min(12, usableWidth));
            } else if (index == 1) {
                x = maxX - (int) (xNoise % Math.min(12, usableWidth));
            } else {
                x = minX + (int) (xNoise % usableWidth);
            }
            state = nextStarlightState(state);
            boolean inventoryBiased = index == prominentStars - 1
                    || index >= starCount - inventoryStars;
            int y = inventoryBiased
                    ? inventoryMinY + (int) ((state & Long.MAX_VALUE)
                            % inventoryHeight)
                    : minY + (int) ((state & Long.MAX_VALUE)
                            % usableHeight);
            state = nextStarlightState(state);
            int dustColor = (state & 1L) == 0L
                    ? DARK_SOURCE_DUST
                    : DARK_RITUAL_DUST;

            if (index < prominentStars) {
                drawMagicStar(graphics, x, y, dustColor);
            } else if ((state & 3L) == 0L) {
                graphics.fill(x, y, x + 2, y + 1, dustColor);
            } else {
                graphics.fill(x, y, x + 1, y + 1,
                        (state & 7L) == 1L
                                ? DARK_MAGIC_SPARK
                                : dustColor);
            }
        }
    }

    private static long nextStarlightState(long state) {
        state ^= state << 13;
        state ^= state >>> 7;
        return state ^ state << 17;
    }

    private static void drawMagicStar(GuiGraphics graphics,
                                      int x, int y, int dustColor) {
        graphics.fill(x, y, x + 2, y + 2, DARK_MAGIC_SPARK);
        graphics.fill(x - 2, y, x, y + 1, dustColor);
        graphics.fill(x + 2, y + 1, x + 4, y + 2, dustColor);
        graphics.fill(x, y - 2, x + 1, y, dustColor);
        graphics.fill(x + 1, y + 2, x + 2, y + 4, dustColor);
    }

    /**
     * Adds scale-aware circuitry to the otherwise unused three-pixel frame.
     * Keeping it in the gutter makes the larger factory and miner screens
     * feel intentional without ever drawing beneath a slot or label.
     */
    private static void drawLargeFrameDetails(GuiGraphics graphics,
                                              int left, int top,
                                              int right, int bottom,
                                              int width, int height,
                                              Palette palette) {
        if (width < 200 && height < 190) {
            return;
        }

        int sideStart = top + 20;
        int sideEnd = bottom - 20;
        for (int y = sideStart; y < sideEnd; y += 12) {
            int color = ((y - sideStart) / 12 & 1) == 0
                    ? palette.frameDetail()
                    : palette.frameDetailSoft();
            graphics.fill(left + 4, y, left + 6, Math.min(y + 4, sideEnd),
                    color);
            graphics.fill(right - 6, y + 4, right - 4,
                    Math.min(y + 8, sideEnd), color);
        }

        if (width >= 200) {
            int detailY = bottom - 6;
            int index = 0;
            for (int x = left + 18; x < right - 18; x += 12) {
                int color = (index++ & 1) == 0
                        ? palette.frameDetail()
                        : palette.frameDetailSoft();
                graphics.fill(x, detailY, Math.min(x + 5, right - 18),
                        detailY + 1, color);
            }

            int center = (left + right) / 2;
            graphics.fill(center - 1, bottom - 7, center + 1, bottom - 4,
                    palette.accentSource());
            graphics.fill(center + 2, bottom - 6, center + 4, bottom - 5,
                    palette.accentRitual());
        }
    }

    /** Adds a restrained rune track to larger attached module frames. */
    private static void drawDockedPanelDetails(GuiGraphics graphics,
                                               int left, int top,
                                               int width, int height,
                                               Palette palette) {
        if (width < 70 && height < 80) {
            return;
        }
        int y = top + height - 3;
        for (int x = left + 9; x < left + width - 9; x += 11) {
            graphics.fill(x, y, Math.min(x + 4, left + width - 9), y + 1,
                    palette.frameDetail());
        }
    }

    private static Palette palette() {
        return isLight() ? LIGHT : DARK;
    }

    private record Palette(int textPrimary, int textMuted, int textPanel,
                           int textScreen, int accentSource,
                           int accentRitual, int frame, int header,
                           int workSurface, int inventorySurface,
                           int panelShadow, int frameDetail,
                           int frameDetailSoft) {
    }

    public enum MachineStatus {
        RUNNING("gui.mekanism_magic.status.running",
                0xFF71F0C4, 0xFF075E46),
        NO_POWER("gui.mekanism_magic.status.no_power",
                0xFFFFB36F, 0xFF8E3600),
        WAITING("gui.mekanism_magic.status.waiting",
                0xFFF5D982, 0xFF674800),
        IDLE("gui.mekanism_magic.status.idle",
                0xFFC3CFDF, 0xFF34465E);

        private final String translationKey;
        private final int darkColor;
        private final int lightColor;

        MachineStatus(String translationKey, int darkColor,
                      int lightColor) {
            this.translationKey = translationKey;
            this.darkColor = darkColor;
            this.lightColor = lightColor;
        }

        public Component label() {
            return Component.translatable(translationKey);
        }

        public int color() {
            return isLight() ? lightColor : darkColor;
        }
    }
}
