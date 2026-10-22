package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.MekanismMagic;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Optional resource-pack palette. No file is shipped by the mod, so the default skin is unchanged. */
@EventBusSubscriber(modid = MekanismMagic.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class MagicGuiStyle implements ResourceManagerReloadListener {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(
            MekanismMagic.MOD_ID, "gui/style.json");
    private static volatile Palettes palettes;

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new MagicGuiStyle());
    }

    public static boolean isTerminal() { return palettes != null; }

    static MagicGuiTheme.Palette palette(boolean light, MagicGuiTheme.Palette fallback) {
        Palettes current = palettes;
        return current == null ? fallback : light ? current.light() : current.dark();
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Palettes loaded = null;
        var resource = manager.getResource(LOCATION);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.get("schema_version").getAsInt() != 1
                        || !"terminal".equals(json.get("style").getAsString())) {
                    throw new IllegalArgumentException("Unsupported GUI style schema");
                }
                loaded = new Palettes(parsePalette(json.getAsJsonObject("dark")),
                        parsePalette(json.getAsJsonObject("light")));
            } catch (Exception failure) {
                LOGGER.warn("Ignoring invalid resource-pack GUI style {}", LOCATION, failure);
            }
        }
        // Reset on removal or parse failure, even if a previous reload enabled the style.
        palettes = loaded;
        MagicGuiTextures.clearCaches();
    }

    private static MagicGuiTheme.Palette parsePalette(JsonObject json) {
        return new MagicGuiTheme.Palette(color(json, "textPrimary"), color(json, "textMuted"),
                color(json, "textPanel"), color(json, "textScreen"), color(json, "accentSource"),
                color(json, "accentRitual"), color(json, "frame"), color(json, "header"),
                color(json, "workSurface"), color(json, "inventorySurface"), color(json, "panelShadow"),
                color(json, "frameDetail"), color(json, "frameDetailSoft"));
    }

    private static int color(JsonObject json, String key) {
        String value = json.get(key).getAsString();
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid color: " + key);
        return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
    }

    private record Palettes(MagicGuiTheme.Palette dark, MagicGuiTheme.Palette light) { }
}
