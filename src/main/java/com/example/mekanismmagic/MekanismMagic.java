package com.example.mekanismmagic;

import com.example.mekanismmagic.item.MiniRitualItem;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.item.UltimateMiniRitualItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MekanismMagic.MOD_ID)
public final class MekanismMagic {
    public static final String MOD_ID = "mekanism_magic";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<Item, MiniRitualItem> MINI_RITUAL =
            ITEMS.register("mini_ritual",
                    () -> new MiniRitualItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, RitualSpawnEggItem> RITUAL_SPAWN_EGG =
            ITEMS.register("ritual_spawn_egg",
                    () -> new RitualSpawnEggItem(new Item.Properties().stacksTo(64)));
    public static final DeferredHolder<Item, UltimateMiniRitualItem> ULTIMATE_MINI_RITUAL =
            ITEMS.register("ultimate_mini_ritual",
                    () -> new UltimateMiniRitualItem(
                            new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
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
                        acceptOptional(output, "absolute_spirit_factory");
                        acceptOptional(output, "supreme_spirit_factory");
                        acceptOptional(output, "cosmic_spirit_factory");
                        acceptOptional(output, "infinite_spirit_factory");
                        output.accept(NativeMekanismRegistries.RITUAL_BLOCK.asItem());
                        output.accept(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.asItem());
                        output.accept(ULTIMATE_MINI_RITUAL.get());
                        output.accept(RITUAL_SPAWN_EGG.get());
                    }).build());

    public MekanismMagic(IEventBus modBus) {
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
        NativeMekanismRegistries.register(modBus);
        registerMekanismExtrasIntegration(modBus);
    }

    private static void acceptOptional(CreativeModeTab.Output output, String path) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, path));
        if (item != net.minecraft.world.item.Items.AIR) {
            output.accept(item);
        }
    }

    private static void registerMekanismExtrasIntegration(IEventBus modBus) {
        if (!ModList.get().isLoaded("mekanism_extras")) {
            return;
        }
        try {
            Class.forName("com.example.mekanismmagic.integration.mekextras."
                            + "MekanismExtrasSpiritFactories")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "Failed to initialize Mekanism Extras spirit factories",
                    failure);
        }
    }
}
