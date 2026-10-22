package com.example.mekanismmagic.integration.ae2;

import com.example.mekanismmagic.integration.ModCompatibility;
import net.neoforged.bus.api.IEventBus;

/** AE entry point that does not link either optional magic mod's API. */
public final class Ae2MagicInterfaceCompat {
    private static boolean registered;

    private Ae2MagicInterfaceCompat() {}

    public static synchronized void register(IEventBus modBus) {
        if (registered) {
            return;
        }
        if (ModCompatibility.forbiddenArcanusLoaded()) {
            Ae2EssenceInterfaceStorage.register(modBus);
            Ae2MachineResourceAccess.register(
                    Ae2EssenceNetworkAccess.INSTANCE);
        }
        if (ModCompatibility.naturesAuraLoaded()) {
            try {
                Class.forName("com.example.mekanismmagic.integration.ae2."
                                + "Ae2AuraInterfaceStorage")
                        .getMethod("register", IEventBus.class)
                        .invoke(null, modBus);
                Object auraAccess = Class.forName(
                                "com.example.mekanismmagic.integration.ae2."
                                        + "Ae2NatureAuraNetworkAccess")
                        .getField("INSTANCE").get(null);
                Ae2MachineResourceAccess.register(
                        (Ae2MachineResourceAccess.ResourceExtension)
                                auraAccess);
                Class.forName("com.example.mekanismmagic.integration.ae2."
                                + "Ae2AuraBottlingProvider")
                        .getMethod("register")
                        .invoke(null);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException(
                        "Unable to register AE Aura interface capabilities",
                        failure);
            }
        }
        registered = true;
    }
}
