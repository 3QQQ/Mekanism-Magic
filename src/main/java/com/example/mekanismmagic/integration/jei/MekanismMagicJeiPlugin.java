package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.recipe.UltimateMiniRitualRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
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
    public static final RecipeType<UltimateMiniRitualRecipe>
            ULTIMATE_MINI_RITUAL_TYPE = RecipeType.create(
                    MekanismMagic.MOD_ID, "ultimate_mini_ritual",
                    UltimateMiniRitualRecipe.class);
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
            registration.registerSubtypeInterpreter(
                    MekanismMagic.MINI_RITUAL.get(),
                    MiniRitualSubtypeInterpreter.INSTANCE);
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiSubtypeRegistration(registration);
        }
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        if (ModCompatibility.occultismLoaded()) {
            registration.addExtraItemStacks(
                    OccultismRecipeBridge.pentacleJeiRecipes(
                                    Minecraft.getInstance().level).stream()
                            .map(OccultismRecipeBridge.PentacleJeiData::output)
                            .map(net.minecraft.world.item.ItemStack::copy)
                            .toList());
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiExtraRegistration(registration);
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (ModCompatibility.occultismLoaded()) {
            registration.addRecipeCategories(new MiniRitualJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()),
                    new RitualJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()),
                    new SpiritJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()),
                    new MinerJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()),
                    new UltimateMiniRitualJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()));
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiCategoryRegistration(registration);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiRecipeRegistration(registration);
        }
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
            List<UltimateMiniRitualRecipe> ultimateRecipes =
                    Minecraft.getInstance().level.getRecipeManager()
                            .getAllRecipesFor(
                                    net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                            .stream()
                            .map(holder -> holder.value())
                            .filter(UltimateMiniRitualRecipe.class::isInstance)
                            .map(UltimateMiniRitualRecipe.class::cast)
                            .toList();
            registration.addRecipes(ULTIMATE_MINI_RITUAL_TYPE,
                    ultimateRecipes);
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
        if (ModCompatibility.occultismLoaded()) {
            registerOccultismCatalysts(registration);
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiRegistration(registration,
                    "arsnouveau.client.ArsNouveauJeiIntegration");
        }
    }

    @Override
    public void registerRecipeTransferHandlers(
            IRecipeTransferRegistration registration) {
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            try {
                Class.forName("com.example.mekanismmagic.integration."
                                + "ae2.Ae2IdentifierImbuementJeiTransfer")
                        .getMethod("register",
                                IRecipeTransferRegistration.class)
                        .invoke(null, registration);
            } catch (ClassNotFoundException missing) {
                // AE2 is optional, but an installed AE2 with a build that
                // omitted the bridge must not fail silently as a seemingly
                // successful recipe transfer.
                if (net.neoforged.fml.ModList.get().isLoaded("ae2")) {
                    com.example.mekanismmagic.MekanismMagic.LOGGER.error(
                            "AE2 is loaded but the imbuement pattern transfer "
                                    + "bridge is missing", missing);
                }
            } catch (LinkageError incompatible) {
                com.example.mekanismmagic.MekanismMagic.LOGGER.error(
                        "AE2 imbuement pattern transfer is incompatible with "
                                + "the installed AE2/JEI version",
                        incompatible);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Failed to register AE2 imbuement pattern transfer",
                        failure);
            }
        }
    }

    private static void registerOccultismCatalysts(
            IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.asItem(),
                MINI_RITUAL_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.RITUAL_BLOCK.asItem(),
                RITUAL_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.SPIRIT_BLOCK.asItem(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.asItem(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ADVANCED_SPIRIT_FACTORY_BLOCK.asItem(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ELITE_SPIRIT_FACTORY_BLOCK.asItem(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.asItem(),
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
                NativeMekanismRegistries.DIMENSION_MINER_BLOCK.asItem(),
                MINER_TYPE);
    }

    private static void invokeOptionalJeiRegistration(
            IRecipeCatalystRegistration registration, String className) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + className)
                    .getMethod("registerCatalysts",
                            IRecipeCatalystRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional JEI catalysts", failure);
        }
    }

    private static void invokeOptionalJeiExtraRegistration(
            IExtraIngredientRegistration registration) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + "arsnouveau.client.ArsNouveauJeiIntegration")
                    .getMethod("registerExtraIngredients",
                            IExtraIngredientRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional Ars JEI ingredients",
                    failure);
        }
    }

    private static void invokeOptionalJeiSubtypeRegistration(
            ISubtypeRegistration registration) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + "arsnouveau.client.ArsNouveauJeiIntegration")
                    .getMethod("registerSubtypes",
                            ISubtypeRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional Ars JEI subtypes",
                    failure);
        }
    }

    private static void invokeOptionalJeiCategoryRegistration(
            IRecipeCategoryRegistration registration) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + "arsnouveau.client.ArsNouveauJeiIntegration")
                    .getMethod("registerCategories",
                            IRecipeCategoryRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional Ars JEI categories",
                    failure);
        }
    }

    private static void invokeOptionalJeiRecipeRegistration(
            IRecipeRegistration registration) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + "arsnouveau.client.ArsNouveauJeiIntegration")
                    .getMethod("registerRecipes",
                            IRecipeRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional Ars JEI recipes", failure);
        }
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
