package com.example.mekanismmagic.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Draws unusually large machine-stack counts without leaving their slot. */
public final class MagicItemCountRenderer {
    private static final float MAX_SCALE = 0.8F;
    private static final float MAX_TEXT_WIDTH = 15F;
    private static final int TEXT_COLOR = 0xFFF7FAFF;
    private static final int FORMAT_CACHE_SIZE = 256;
    private static final int FORMAT_CACHE_MASK = FORMAT_CACHE_SIZE - 1;
    private static final int[] CACHED_COUNTS = new int[FORMAT_CACHE_SIZE];
    private static final String[] CACHED_TEXT = new String[FORMAT_CACHE_SIZE];

    private MagicItemCountRenderer() {
    }

    public static boolean needsCompactCount(int count) {
        return count >= 100;
    }

    /**
     * Uses at most three significant digits: 999, 9.9k, 99k, 999k,
     * 9.9m and so on. The final glyphs are then scaled to the slot width.
     */
    public static String format(int count) {
        int cacheIndex = cacheIndex(count);
        String cached = CACHED_TEXT[cacheIndex];
        if (cached != null && CACHED_COUNTS[cacheIndex] == count) {
            return cached;
        }
        String formatted = formatUncached(count);
        CACHED_COUNTS[cacheIndex] = count;
        CACHED_TEXT[cacheIndex] = formatted;
        return formatted;
    }

    private static String formatUncached(int count) {
        if (count < 1_000) {
            return Integer.toString(count);
        }
        if (count < 1_000_000) {
            return formatUnit(count, 1_000, "k");
        }
        if (count < 1_000_000_000) {
            return formatUnit(count, 1_000_000, "m");
        }
        return formatUnit(count, 1_000_000_000, "b");
    }

    private static String formatUnit(int count, int unit,
                                     String suffix) {
        int whole = count / unit;
        if (whole < 10) {
            // Truncation prevents 9,999 from becoming the wider "10.0k".
            int tenth = count / (unit / 10) % 10;
            return tenth == 0
                    ? whole + suffix
                    : whole + "." + tenth + suffix;
        }
        return whole + suffix;
    }

    private static int cacheIndex(int count) {
        int mixed = count ^ count >>> 16;
        return mixed & FORMAT_CACHE_MASK;
    }

    public static void render(GuiGraphics graphics, Font font, int count,
                              int slotX, int slotY) {
        String text = format(count);
        int textWidth = Math.max(1, font.width(text));
        float scale = Math.min(MAX_SCALE, MAX_TEXT_WIDTH / textWidth);
        float scaledWidth = textWidth * scale;
        float scaledHeight = font.lineHeight * scale;
        float right = slotX + 17F;
        float bottom = slotY + 17F;
        float textX = right - scaledWidth;
        float textY = bottom - scaledHeight;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(textX, textY, 201);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, TEXT_COLOR, false);
        pose.popPose();
    }
}
