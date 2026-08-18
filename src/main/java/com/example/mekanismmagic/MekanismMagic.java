package com.example.mekanismmagic;

import com.example.mekanismmagic.item.MiniRitualItem;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.item.UltimateMiniRitualItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(MekanismMagic.MOD_ID)
public final class MekanismMagic {
    public static final String MOD_ID = "mekanism_magic";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<MiniRitualItem> MINI_RITUAL =
            ITEMS.register("mini_ritual",
                    () -> new MiniRitualItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<RitualSpawnEggItem> RITUAL_SPAWN_EGG =
            ITEMS.register("ritual_spawn_egg",
                    () -> new RitualSpawnEggItem(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<UltimateMiniRitualItem> ULTIMATE_MINI_RITUAL =
            ITEMS.register("ultimate_mini_ritual",
                    () -> new UltimateMiniRitualItem(
                            new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mekanism_magic"))
                    .icon(() -> new ItemStack(NativeMekanismRegistries.SPIRIT_BLOCK.asItem()))
                    .displayItems((parameters, output) -> {
                        output.accept(NativeMekanismRegistries.SPIRIT_BLOCK.asItem());
                        output.accept(NativeMekanismRegistries.DIMENSION_MINER_BLOCK.asItem());
                        output.accept(NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.asItem());
                        output.accept(NativeMekanismRegistries.ADVANCED_SPIRIT_FACTORY_BLOCK.asItem());
                        output.accept(NativeMekanismRegistries.ELITE_SPIRIT_FACTORY_BLOCK.asItem());
                        output.accept(NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.asItem());
                        output.accept(NativeMekanismRegistries.RITUAL_BLOCK.asItem());
                        output.accept(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.asItem());
                        output.accept(ULTIMATE_MINI_RITUAL.get());
                        output.accept(RITUAL_SPAWN_EGG.get());
                    }).build());

    public MekanismMagic(IEventBus modBus) {
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
        NativeMekanismRegistries.register(modBus);
    }
}

