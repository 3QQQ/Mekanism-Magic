package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves Mekanism GUI assets to this addon's isolated themed copies. */
public final class MagicGuiTextures {
    private static final String MEKANISM_NAMESPACE = "mekanism";
    private static final String GUI_PREFIX = "gui/";
    private static final String DARK_THEME_PREFIX = "gui_theme/";
    private static final String LIGHT_THEME_PREFIX = "gui_theme_light/";
    private static final Map<ResourceLocation, ResourceLocation>
            DARK_THEME_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation>
            LIGHT_THEME_TEXTURES = new ConcurrentHashMap<>();

    private MagicGuiTextures() {
    }

    public static ResourceLocation resolve(ResourceLocation original) {
        if (!isThemeActive()
                || !MEKANISM_NAMESPACE.equals(original.getNamespace())
                || !original.getPath().startsWith(GUI_PREFIX)) {
            return original;
        }
        boolean light = MagicGuiTheme.isLight();
        Map<ResourceLocation, ResourceLocation> cache = light
                ? LIGHT_THEME_TEXTURES : DARK_THEME_TEXTURES;
        ResourceLocation resolved = cache.get(original);
        if (resolved != null) {
            return resolved;
        }
        String prefix = light
                ? LIGHT_THEME_PREFIX : DARK_THEME_PREFIX;
        resolved = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                prefix + original.getPath().substring(GUI_PREFIX.length()));
        ResourceLocation raced = cache.putIfAbsent(original, resolved);
        return raced == null ? resolved : raced;
    }

    public static boolean isThemeActive() {
        return Minecraft.getInstance().screen instanceof MagicThemedScreen;
    }
}
