package com.example.mekanismmagic.client;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.blockentity.OccultismRecipeBridge;
import com.example.mekanismmagic.client.screen.NativeRitualScreen;
import com.example.mekanismmagic.client.screen.NativeMiniRitualAssemblerScreen;
import com.example.mekanismmagic.client.screen.NativeSpiritFactoryScreen;
import com.example.mekanismmagic.client.screen.NativeSpiritScreen;
import com.example.mekanismmagic.client.screen.NativeDimensionMinerScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = "mekanism_magic", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeMagicClient {
    private NativeMagicClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(NativeMekanismRegistries.SPIRIT_CONTAINER.get(), NativeSpiritScreen::new);
        event.register(NativeMekanismRegistries.DIMENSION_MINER_CONTAINER.get(),
                NativeDimensionMinerScreen::new);
        event.register(NativeMekanismRegistries.RITUAL_CONTAINER.get(), NativeRitualScreen::new);
        event.register(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_CONTAINER.get(),
                NativeMiniRitualAssemblerScreen::new);
        event.register(NativeMekanismRegistries.SPIRIT_FACTORY_CONTAINER.get(),
                NativeSpiritFactoryScreen::new);
    }

    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                MekanismMagic.MINI_RITUAL.get(),
                ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "ritual"),
                (stack, level, entity, seed) ->
                        OccultismRecipeBridge.miniRitualModelData(stack)));
    }
}
