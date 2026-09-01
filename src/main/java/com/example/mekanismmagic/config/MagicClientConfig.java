package com.example.mekanismmagic.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only presentation settings that never affect world data. */
public final class MagicClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.EnumValue<GuiTheme> GUI_THEME;
    private static final ModConfigSpec.BooleanValue ANIMATIONS_ENABLED;
    private static final ModConfigSpec.BooleanValue FORCE_WORKING_ANIMATIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        GUI_THEME = builder
                .comment("Color scheme used by Mekanism Magic machine GUIs.")
                .translation("config.mekanism_magic.gui_theme")
                .defineEnum("guiTheme", GuiTheme.DARK);
        ANIMATIONS_ENABLED = builder
                .comment("Render animated machine mechanisms, Drygmy displays, "
                        + "and Source inside magic pipes. Disabling this does "
                        + "not affect machine operation or Source transfer.")
                .translation("config.mekanism_magic.animations_enabled")
                .define("animationsEnabled", true);
        FORCE_WORKING_ANIMATIONS = builder
                .comment("Client-side visual test option. When enabled, show "
                        + "working animations on idle machines and empty Source "
                        + "pipes without changing recipes, energy, or networks.")
                .translation("config.mekanism_magic.force_working_animations")
                .define("forceWorkingAnimations", false);
        SPEC = builder.build();
    }

    private MagicClientConfig() {
    }

    public static GuiTheme guiTheme() {
        return SPEC.isLoaded() ? GUI_THEME.get() : GuiTheme.DARK;
    }

    public static boolean isLight() {
        return guiTheme() == GuiTheme.LIGHT;
    }

    public static boolean animationsEnabled() {
        return !SPEC.isLoaded() || ANIMATIONS_ENABLED.get();
    }

    public static boolean forceWorkingAnimations() {
        return SPEC.isLoaded() && FORCE_WORKING_ANIMATIONS.get();
    }

    public static void toggleTheme() {
        if (!SPEC.isLoaded()) {
            return;
        }
        GUI_THEME.set(isLight() ? GuiTheme.DARK : GuiTheme.LIGHT);
        GUI_THEME.save();
    }

    public enum GuiTheme {
        DARK,
        LIGHT
    }
}
