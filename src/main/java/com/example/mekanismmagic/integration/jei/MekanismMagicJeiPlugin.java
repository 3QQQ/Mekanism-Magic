package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.recipe.UltimateMiniRitualRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public final class MekanismMagicJeiPlugin implements IModPlugin {
    public static final RecipeType<OccultismRecipeBridge.PentacleJeiData> MINI_RITUAL_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "mini_ritual",
                    OccultismRecipeBridge.PentacleJeiData.class);
    public static final RecipeType<UltimateMiniRitualRecipe>
            ULTIMATE_MINI_RITUAL_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "ultimate_mini_ritual",
                    UltimateMiniRitualRecipe.class);
    public static final RecipeType<OccultismRecipeBridge.RitualJeiData> RITUAL_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "ritual",
                    OccultismRecipeBridge.RitualJeiData.class);
    public static final RecipeType<OccultismRecipeBridge.SpiritJeiData> SPIRIT_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "spirit",
                    OccultismRecipeBridge.SpiritJeiData.class);
    public static final RecipeType<OccultismRecipeBridge.MinerJeiData> MINER_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "miner",
                    OccultismRecipeBridge.MinerJeiData.class);

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(
                MekanismMagic.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!ModCompatibility.occultismLoaded()) {
            return;
        }
        registration.addRecipeCategories(new MiniRitualJeiCategory(
                registration.getJeiHelpers().getGuiHelper()),
                new UltimateMiniRitualJeiCategory(
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
            registration.addRecipes(MINI_RITUAL_TYPE, pentacles);
            registration.addRecipes(ULTIMATE_MINI_RITUAL_TYPE,
                    List.of(new UltimateMiniRitualRecipe(
                            new ResourceLocation(MekanismMagic.MOD_ID,
                                    "ultimate_mini_ritual"),
                            net.minecraft.world.item.crafting
                                    .CraftingBookCategory.MISC)));
            registration.addRecipes(RITUAL_TYPE,
                    OccultismRecipeBridge.ritualJeiRecipes(
                            Minecraft.getInstance().level));
            registration.addRecipes(SPIRIT_TYPE,
                    OccultismRecipeBridge.spiritJeiRecipes(
                            Minecraft.getInstance().level));
            registration.addRecipes(MINER_TYPE,
                    OccultismRecipeBridge.minerJeiRecipes(
                            Minecraft.getInstance().level));
        }
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
                        new ResourceLocation(
                                MekanismMagic.MOD_ID, path));
        if (item != net.minecraft.world.item.Items.AIR) {
            registration.addRecipeCatalyst(item, SPIRIT_TYPE);
        }
    }
}
