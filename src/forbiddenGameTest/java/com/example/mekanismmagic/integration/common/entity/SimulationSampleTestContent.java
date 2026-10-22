package com.example.mekanismmagic.integration.common.entity;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Isolated test-source registrations; never packaged or registered by the normal mod/client. */
@EventBusSubscriber(modid = MekanismMagic.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SimulationSampleTestContent {
    public static final ResourceLocation FIXED_EGG = ResourceLocation.fromNamespaceAndPath("mekanism_magic", "simulation_fixed_egg_test");
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism_magic", "simulation_deferred_egg_test"),
                () -> new DeferredSpawnEggItem(() -> EntityType.COW, 0x443322, 0xAABBCC, new Item.Properties()));
        event.register(Registries.ITEM, FIXED_EGG, () -> new RitualSpawnEggItem(new Item.Properties()));
    }
}
