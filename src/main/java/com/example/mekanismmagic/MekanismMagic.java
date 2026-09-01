package com.example.mekanismmagic;

import com.example.mekanismmagic.client.MagicClientConfigScreen;
import com.example.mekanismmagic.item.MiniRitualItem;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.item.UltimateMiniRitualItem;
import com.example.mekanismmagic.item.CreativeMagicUpgradeItem;
import com.example.mekanismmagic.config.MagicClientConfig;
import com.example.mekanismmagic.event.MachineDropPreserver;
import com.example.mekanismmagic.recipe.UltimateMiniRitualRecipe;
import com.example.mekanismmagic.recipe.SpecificPentacleIngredient;
import com.example.mekanismmagic.integration.IntegrationBootstrap;
import com.example.mekanismmagic.integration.ModCompatibility;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MekanismMagic.MOD_ID)
public final class MekanismMagic {
    public static final String MOD_ID = "mekanism_magic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister.Items PLUGIN_ITEMS =
            DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES, MOD_ID);

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
    public static final DeferredHolder<Item, CreativeMagicUpgradeItem>
            CREATIVE_MAGIC_UPGRADE = PLUGIN_ITEMS.register(
            "creative_source_upgrade",
            () -> new CreativeMagicUpgradeItem(
                    new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<
            UltimateMiniRitualRecipe>> ULTIMATE_MINI_RITUAL_RECIPE =
            RECIPE_SERIALIZERS.register("ultimate_mini_ritual",
                    () -> UltimateMiniRitualRecipe.SERIALIZER);
    public static final DeferredHolder<IngredientType<?>,
            IngredientType<SpecificPentacleIngredient>> SPECIFIC_PENTACLE_INGREDIENT =
            INGREDIENT_TYPES.register("specific_pentacle",
                    () -> new IngredientType<SpecificPentacleIngredient>(
                            SpecificPentacleIngredient.CODEC,
                            SpecificPentacleIngredient.STREAM_CODEC));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mekanism_magic"))
                    .icon(MekanismMagic::creativeIcon)
                    .displayItems((parameters, output) -> {
                        acceptOptional(output, "source_generator");
                        acceptOptional(output, "source_converter");
                        acceptOptional(output, "catalyst_identifier_assembler");
                        acceptOptional(output, "basic_imbuement_factory");
                        acceptOptional(output, "advanced_imbuement_factory");
                        acceptOptional(output, "elite_imbuement_factory");
                        acceptOptional(output, "ultimate_imbuement_factory");
                        acceptOptional(output, "absolute_imbuement_factory");
                        acceptOptional(output, "supreme_imbuement_factory");
                        acceptOptional(output, "cosmic_imbuement_factory");
                        acceptOptional(output, "infinite_imbuement_factory");
                        acceptOptional(output, "imbuement_processor");
                        acceptOptional(output, "enchanting_apparatus_processor");
                        acceptOptional(output, "drygmy_simulator");
                        acceptOptional(output, "magic_source_pipe");
                        acceptOptional(output, "advanced_magic_source_pipe");
                        acceptOptional(output, "elite_magic_source_pipe");
                        acceptOptional(output, "ultimate_magic_source_pipe");
                        acceptOptional(output, "absolute_magic_source_pipe");
                        acceptOptional(output, "supreme_magic_source_pipe");
                        acceptOptional(output, "cosmic_magic_source_pipe");
                        acceptOptional(output, "infinite_magic_source_pipe");
                        acceptOptional(output, "source_link_tool");
                        acceptOptional(output, "creative_source_upgrade");
                        acceptOptional(output, "spirit_processor");
                        acceptOptional(output, "dimension_miner");
                        acceptOptional(output, "basic_spirit_factory");
                        acceptOptional(output, "advanced_spirit_factory");
                        acceptOptional(output, "elite_spirit_factory");
                        acceptOptional(output, "ultimate_spirit_factory");
                        acceptOptional(output, "absolute_spirit_factory");
                        acceptOptional(output, "supreme_spirit_factory");
                        acceptOptional(output, "cosmic_spirit_factory");
                        acceptOptional(output, "infinite_spirit_factory");
                        acceptOptional(output, "ritual_engine");
                        acceptOptional(output, "mini_ritual_assembler");
                        acceptOptional(output, "ultimate_mini_ritual");
                        acceptOptional(output, "ritual_spawn_egg");
                    }).build());

    public MekanismMagic(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT,
                MagicClientConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MagicClientConfigScreen.register(modContainer);
        }
        IntegrationBootstrap.initialize();
        IntegrationBootstrap.registerContent(modBus);
        NeoForge.EVENT_BUS.addListener(MachineDropPreserver::onBlockDrops);
        registerAe2DirectOutput(modBus);
        registerMekEnergistics();
    }

    private static void registerAe2DirectOutput(IEventBus modBus) {
        if (!ModCompatibility.loaded("ae2")) {
            return;
        }
        try {
            Class.forName("com.example.mekanismmagic.integration.ae2."
                            + "Ae2DirectOutputCompat")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ReflectiveOperationException | LinkageError failure) {
            LOGGER.warn("Unable to register native AE direct output",
                    failure);
        }
    }

    /**
     * ME automation covers both Ars Nouveau and Occultism machines, so its
     * registration must not depend on either content module being present.
     */
    private static void registerMekEnergistics() {
        if (!ModCompatibility.mekenergisticsAutomationSupported()) {
            return;
        }
        try {
            Class.forName("com.example.mekanismmagic.integration.mekenergistics."
                            + "MekEnergisticsCompat")
                    .getMethod("registerBlocks")
                    .invoke(null);
        } catch (ReflectiveOperationException | LinkageError failure) {
            LOGGER.warn("Unable to register Mek Energistics automation blocks",
                    failure);
        }
    }

    private static void acceptOptional(CreativeModeTab.Output output, String path) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, path));
        if (item != net.minecraft.world.item.Items.AIR) {
            output.accept(item);
        }
    }

    private static ItemStack creativeIcon() {
        Item sourceGenerator = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "source_generator"));
        if (sourceGenerator != net.minecraft.world.item.Items.AIR) {
            return new ItemStack(sourceGenerator);
        }
        Item spiritProcessor = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, "spirit_processor"));
        return spiritProcessor == net.minecraft.world.item.Items.AIR
                ? new ItemStack(net.minecraft.world.item.Items.NETHER_STAR)
                : new ItemStack(spiritProcessor);
    }

}
