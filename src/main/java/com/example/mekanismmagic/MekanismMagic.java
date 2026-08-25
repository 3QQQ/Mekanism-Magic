package com.example.mekanismmagic;

import com.example.mekanismmagic.item.MiniRitualItem;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.item.UltimateMiniRitualItem;
import com.example.mekanismmagic.recipe.UltimateMiniRitualRecipe;
import com.example.mekanismmagic.recipe.SpecificPentacleIngredient;
import com.example.mekanismmagic.integration.IntegrationBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
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

    public MekanismMagic(IEventBus modBus) {
        IntegrationBootstrap.initialize();
        IntegrationBootstrap.registerContent(modBus);
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
