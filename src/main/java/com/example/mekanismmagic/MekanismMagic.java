package com.example.mekanismmagic;

import com.example.mekanismmagic.item.MiniRitualItem;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.item.UltimateMiniRitualItem;
import com.example.mekanismmagic.integration.ModCompatibility;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MekanismMagic.MOD_ID)
public final class MekanismMagic {
    public static final String MOD_ID = "mekanism_magic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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
                    .icon(() -> new ItemStack(NativeMekanismRegistries.SPIRIT_BLOCK.getSecondary()))
                    .displayItems((parameters, output) -> {
                        output.accept(NativeMekanismRegistries.SPIRIT_BLOCK.getSecondary());
                        output.accept(NativeMekanismRegistries.DIMENSION_MINER_BLOCK.getSecondary());
                        output.accept(NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.getSecondary());
                        output.accept(NativeMekanismRegistries.ADVANCED_SPIRIT_FACTORY_BLOCK.getSecondary());
                        output.accept(NativeMekanismRegistries.ELITE_SPIRIT_FACTORY_BLOCK.getSecondary());
                        output.accept(NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.getSecondary());
                        acceptOptional(output, "absolute_spirit_factory");
                        acceptOptional(output, "supreme_spirit_factory");
                        acceptOptional(output, "cosmic_spirit_factory");
                        acceptOptional(output, "infinite_spirit_factory");
                        output.accept(NativeMekanismRegistries.RITUAL_BLOCK.getSecondary());
                        output.accept(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.getSecondary());
                        output.accept(ULTIMATE_MINI_RITUAL.get());
                        output.accept(RITUAL_SPAWN_EGG.get());
                    }).build());

    public MekanismMagic(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        if (!ModCompatibility.occultismLoaded()) {
            return;
        }
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
        NativeMekanismRegistries.register(modBus);
        registerMekanismExtrasIntegration(modBus);
    }

    private static void acceptOptional(CreativeModeTab.Output output, String path) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation
                        .fromNamespaceAndPath(MOD_ID, path));
        if (item != net.minecraft.world.item.Items.AIR) {
            output.accept(item);
        }
    }

    private static void registerMekanismExtrasIntegration(IEventBus modBus) {
        if (!ModCompatibility.loaded(ModCompatibility.MEKANISM_EXTRAS)) {
            return;
        }
        try {
            Class.forName("com.example.mekanismmagic.integration.mekextras."
                            + "MekanismExtrasSpiritFactories")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ReflectiveOperationException | LinkageError failure) {
            LOGGER.warn("Skipping optional Mekanism Extras spirit factories "
                    + "because their runtime API is incompatible", failure);
        }
    }
}
