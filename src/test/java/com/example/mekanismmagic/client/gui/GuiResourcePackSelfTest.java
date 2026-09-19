package com.example.mekanismmagic.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Verifies enable/reload/disable and invalid-pack fallback through the real reload listener. */
public final class GuiResourcePackSelfTest {
    private GuiResourcePackSelfTest() { }

    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/test/resources/gui/ae-terminal-style.json"));
        var fixture = com.google.gson.JsonParser.parseString(source).getAsJsonObject();
        String lightPrimary = fixture.getAsJsonObject("light").get("textPrimary").getAsString();
        String darkPrimary = fixture.getAsJsonObject("dark").get("textPrimary").getAsString();
        String darkSurface = fixture.getAsJsonObject("dark").get("workSurface").getAsString();
        try {
            reload(null);
            require(!MagicGuiStyle.isTerminal(), "Default GUI unexpectedly opted into the resource pack");
            reload(source);
            require(MagicGuiStyle.isTerminal(), "Resource pack did not enable the terminal style");
            require(MagicGuiStyle.palette(true, null).textPrimary() == argb(lightPrimary), "Light palette not loaded");
            require(MagicGuiStyle.palette(false, null).textPrimary() == argb(darkPrimary), "Dark palette not loaded");
            // A newly winning pack must replace all previously cached colors.
            reload(source.replace(lightPrimary, "#303040"));
            require(MagicGuiStyle.palette(true, null).textPrimary() == 0xFF303040, "Reload kept stale palette");
            reload(source.replace(darkSurface, "bad-color"));
            require(!MagicGuiStyle.isTerminal(), "Partially invalid palette left a stale enabled skin");
            reload(source);
            reload(null);
            require(!MagicGuiStyle.isTerminal() && MagicGuiStyle.palette(true, null) == null,
                    "Disabling the pack failed to restore default rendering");
        } finally { reload(null); }
        System.out.println("GuiResourcePackSelfTest: absent, enabled, replaced, malformed and removed pack states passed");
    }

    public static void reload(String json) {
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath("mekanism_magic", "gui/style.json");
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) {
                        require(expected.equals(args[0]), "Listener requested the wrong resource path");
                        return json == null ? Optional.empty() : Optional.of(new Resource(null,
                                () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        new MagicGuiStyle().onResourceManagerReload(manager);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int argb(String color) {
        return 0xFF000000 | Integer.parseInt(color.substring(1), 16);
    }
}
