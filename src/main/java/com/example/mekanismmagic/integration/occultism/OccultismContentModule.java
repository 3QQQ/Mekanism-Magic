package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.ContentIntegrationModule;
import com.example.mekanismmagic.integration.ModCompatibility;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.lang.reflect.InvocationTargetException;

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
            Class<?> factoryBridge = Class.forName(
                    "com.example.mekanismmagic.integration.mekextras."
                            + "MekanismExtrasSpiritFactories");
            try {
                factoryBridge
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
            } catch (InvocationTargetException failure) {
                throw new IllegalStateException(
                        "Mekanism Extras spirit factory registration failed",
                        failure.getCause());
            }
        } catch (ClassNotFoundException missingBridge) {
            MekanismMagic.LOGGER.warn(
                    "Mekanism Extras is installed, but this build does not "
                            + "contain its optional spirit factory bridge; "
                            + "continuing without those factories");
        } catch (NoSuchMethodException | IllegalAccessException
                | LinkageError failure) {
            MekanismMagic.LOGGER.warn(
                    "Skipping optional Mekanism Extras spirit factories "
                            + "because their runtime API is incompatible",
                    failure);
        }
    }
}
