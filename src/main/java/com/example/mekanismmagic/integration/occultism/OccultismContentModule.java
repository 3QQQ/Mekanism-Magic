package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.ContentIntegrationModule;
import com.example.mekanismmagic.integration.ModCompatibility;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Registers all content currently backed by Occultism recipes and data.
 */
public final class OccultismContentModule
        implements ContentIntegrationModule {
    public static final OccultismContentModule INSTANCE =
            new OccultismContentModule();

    private OccultismContentModule() {
    }

    @Override
    public String modId() {
        return ModCompatibility.OCCULTISM;
    }

    @Override
    public void register(IEventBus modBus) {
        MekanismMagic.ITEMS.register(modBus);
        MekanismMagic.RECIPE_SERIALIZERS.register(modBus);
        MekanismMagic.INGREDIENT_TYPES.register(modBus);
        NativeMekanismRegistries.register(modBus);
        registerMekanismExtrasFactories(modBus);
        NeoForge.EVENT_BUS.addListener(
                OccultismContentModule::onDatapackSync);
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        OccultismRecipeBridge.invalidateRecipeCaches();
    }

    private static void registerMekanismExtrasFactories(IEventBus modBus) {
        if (!ModCompatibility.mekanismExtrasLoaded()) {
            return;
        }
        try {
            Class.forName("com.example.mekanismmagic.integration.mekextras."
                            + "MekanismExtrasSpiritFactories")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ReflectiveOperationException | LinkageError failure) {
            MekanismMagic.LOGGER.warn(
                    "Skipping optional Mekanism Extras spirit factories "
                            + "because their runtime API is incompatible",
                    failure);
        }
    }
}
