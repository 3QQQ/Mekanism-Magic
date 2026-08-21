package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.ModCompatibility;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public final class MekanismMagicJeiPlugin implements IModPlugin {
    public static final RecipeType<OccultismRecipeBridge.PentacleJeiData> MINI_RITUAL_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "mini_ritual",
                    OccultismRecipeBridge.PentacleJeiData.class);
    public static final RecipeType<OccultismRecipeBridge.RitualJeiData> RITUAL_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "ritual",
                    OccultismRecipeBridge.RitualJeiData.class);
    public static final RecipeType<OccultismRecipeBridge.SpiritJeiData> SPIRIT_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "spirit",
                    OccultismRecipeBridge.SpiritJeiData.class);
    public static final RecipeType<OccultismRecipeBridge.MinerJeiData> MINER_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "miner",
                    OccultismRecipeBridge.MinerJeiData.class);
    private static volatile List<OccultismRecipeBridge.RitualJeiData>
            registeredRituals = List.of();

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        if (ModCompatibility.occultismLoaded()) {
            registration.useNbtForSubtypes(
                    MekanismMagic.MINI_RITUAL.get());
        }
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        if (!ModCompatibility.occultismLoaded()
                || Minecraft.getInstance().level == null) {
            return;
        }
        registration.addExtraItemStacks(
                OccultismRecipeBridge.pentacleJeiRecipes(
                                Minecraft.getInstance().level).stream()
                        .map(OccultismRecipeBridge.PentacleJeiData::output)
                        .map(net.minecraft.world.item.ItemStack::copy)
                        .toList());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!ModCompatibility.occultismLoaded()) {
            return;
        }
        registration.addRecipeCategories(new MiniRitualJeiCategory(
                registration.getJeiHelpers().getGuiHelper()),
                new RitualJeiCategory(registration.getJeiHelpers().getGuiHelper()),
                new SpiritJeiCategory(registration.getJeiHelpers().getGuiHelper()),
                new MinerJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!ModCompatibility.occultismLoaded()) {
            return;
        }
        if (Minecraft.getInstance().level != null) {
            List<OccultismRecipeBridge.PentacleJeiData> pentacles =
                    OccultismRecipeBridge.pentacleJeiRecipes(
                            Minecraft.getInstance().level);
            registration.addItemStackInfo(
                    pentacles.stream()
                            .map(OccultismRecipeBridge.PentacleJeiData::output)
                            .map(net.minecraft.world.item.ItemStack::copy)
                            .toList(),
                    net.minecraft.network.chat.Component.translatable(
                            "jei.mekanism_magic.mini_ritual.info"));
            registration.getIngredientManager().addIngredientsAtRuntime(
                    VanillaTypes.ITEM_STACK,
                    pentacles.stream()
                            .map(OccultismRecipeBridge.PentacleJeiData::output)
                            .map(net.minecraft.world.item.ItemStack::copy)
                            .toList());
            List<OccultismRecipeBridge.RitualJeiData> rituals =
                    OccultismRecipeBridge.ritualJeiRecipes(
                            Minecraft.getInstance().level);
            registeredRituals = List.copyOf(rituals);
            registration.addRecipes(MINI_RITUAL_TYPE, pentacles);
            registration.addRecipes(RITUAL_TYPE, rituals);
            registration.addRecipes(SPIRIT_TYPE,
                    OccultismRecipeBridge.spiritJeiRecipes(
                            Minecraft.getInstance().level));
            registration.addRecipes(MINER_TYPE,
                    OccultismRecipeBridge.minerJeiRecipes(
                            Minecraft.getInstance().level));
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        registeredRituals = List.of();
    }

    static List<net.minecraft.world.item.ItemStack> boundRitualSelectors(
            ResourceLocation pentacleId) {
        return registeredRituals.stream()
                .filter(recipe -> recipe.pentacleId().equals(pentacleId))
                .map(OccultismRecipeBridge.RitualJeiData::selector)
                .map(net.minecraft.world.item.ItemStack::copy)
                .toList();
    }


    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (!ModCompatibility.occultismLoaded()) {
            return;
        }
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.getSecondary(),
                MINI_RITUAL_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.RITUAL_BLOCK.getSecondary(),
                RITUAL_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.SPIRIT_BLOCK.getSecondary(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.getSecondary(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ADVANCED_SPIRIT_FACTORY_BLOCK.getSecondary(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ELITE_SPIRIT_FACTORY_BLOCK.getSecondary(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.getSecondary(),
                SPIRIT_TYPE);
        addOptionalSpiritFactoryCatalyst(registration,
                "absolute_spirit_factory");
        addOptionalSpiritFactoryCatalyst(registration,
                "supreme_spirit_factory");
        addOptionalSpiritFactoryCatalyst(registration,
                "cosmic_spirit_factory");
        addOptionalSpiritFactoryCatalyst(registration,
                "infinite_spirit_factory");
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.DIMENSION_MINER_BLOCK.getSecondary(),
                MINER_TYPE);
    }

    private static void addOptionalSpiritFactoryCatalyst(
            IRecipeCatalystRegistration registration, String path) {
        net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath(
                                MekanismMagic.MOD_ID, path));
        if (item != net.minecraft.world.item.Items.AIR) {
            registration.addRecipeCatalyst(item, SPIRIT_TYPE);
        }
    }
}
