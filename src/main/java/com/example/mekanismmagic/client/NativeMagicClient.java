package com.example.mekanismmagic.client;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.client.screen.NativeRitualScreen;
import com.example.mekanismmagic.client.screen.NativeMiniRitualAssemblerScreen;
import com.example.mekanismmagic.client.screen.NativeSpiritFactoryScreen;
import com.example.mekanismmagic.client.screen.NativeSpiritScreen;
import com.example.mekanismmagic.client.screen.NativeDimensionMinerScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = "mekanism_magic",
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeMagicClient {
    private NativeMagicClient() {
    }

    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        if (!ModCompatibility.occultismLoaded()) {
            return;
        }
        event.enqueueWork(() -> {
            MenuScreens.register(NativeMekanismRegistries.SPIRIT_CONTAINER.get(),
                    NativeSpiritScreen::new);
            MenuScreens.register(NativeMekanismRegistries.DIMENSION_MINER_CONTAINER.get(),
                    NativeDimensionMinerScreen::new);
            MenuScreens.register(NativeMekanismRegistries.RITUAL_CONTAINER.get(),
                    NativeRitualScreen::new);
            MenuScreens.register(
                    NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_CONTAINER.get(),
                    NativeMiniRitualAssemblerScreen::new);
            MenuScreens.register(NativeMekanismRegistries.SPIRIT_FACTORY_CONTAINER.get(),
                    NativeSpiritFactoryScreen::new);
            ItemProperties.register(MekanismMagic.MINI_RITUAL.get(),
                    ResourceLocation.fromNamespaceAndPath(
                            MekanismMagic.MOD_ID, "ritual"),
                    (stack, level, entity, seed) ->
                            OccultismRecipeBridge.miniRitualModelData(stack));
        });
    }
}

