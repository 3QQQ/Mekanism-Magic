package com.example.mekanismmagic.client;

import com.example.mekanismmagic.integration.ModCompatibility;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Client-only notices for optional integrations that are present but too old
 * to expose the API used by this build.
 */
@EventBusSubscriber(
        modid = "mekanism_magic",
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT)
public final class MekEnergisticsClientEvents {
    private MekEnergisticsClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ModCompatibility.mekenergisticsVersion().ifPresent(version -> {
            if (!ModCompatibility.mekenergisticsAutomationSupported()) {
                event.getPlayer().displayClientMessage(
                        Component.translatable(
                                "chat.mekanism_magic.mekenergistics_too_old",
                                version,
                                ModCompatibility.MEK_ENERGISTICS_AUTOMATION_VERSION),
                        false);
            }
        });
    }
}
