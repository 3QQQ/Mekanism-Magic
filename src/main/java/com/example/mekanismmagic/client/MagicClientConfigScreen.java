package com.example.mekanismmagic.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Registers NeoForge's in-game editor for the client presentation config. */
public final class MagicClientConfigScreen {
    private MagicClientConfigScreen() {
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) ->
                        new ConfigurationScreen(container, parent));
    }
}
