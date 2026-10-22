package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.naturesaura
        .NatureAuraRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura
        .NatureAuraRecipeScanner;
import com.example.mekanismmagic.integration.naturesaura
        .NaturesAuraRegistries;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;

/** Optional JEI entry point for the compact natural altar machines. */
public final class NaturesAuraJeiIntegration {
    public static final RecipeType<com.example.mekanismmagic.integration.naturesaura.NatureAuraFuelCatalog.Fuel>
            AURA_FUEL_TYPE = RecipeType.create(MekanismMagic.MOD_ID, "aura_fuel",
            com.example.mekanismmagic.integration.naturesaura.NatureAuraFuelCatalog.Fuel.class);
    public static final RecipeType<com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge.TreeRitualRecipeView>
            TREE_RITUAL_TYPE = RecipeType.create(MekanismMagic.MOD_ID, "tree_ritual",
            com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge.TreeRitualRecipeView.class);
    public static final RecipeType<com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView>
            ANIMAL_SPAWNER_TYPE = RecipeType.create(MekanismMagic.MOD_ID, "animal_spawner",
            com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView.class);
    public static final RecipeType<com.example.mekanismmagic.integration.naturesaura
            .OfferingRecipeBridge.OfferingRecipeView> OFFERING_TYPE = RecipeType.create(
            MekanismMagic.MOD_ID, "offering", com.example.mekanismmagic.integration.naturesaura
                    .OfferingRecipeBridge.OfferingRecipeView.class);
    public static final RecipeType<
            NatureAuraRecipeBridge.AltarRecipeView>
            NATURE_ALTAR_RECIPE_TYPE = RecipeType.create(
                    MekanismMagic.MOD_ID, "nature_altar",
                    NatureAuraRecipeBridge.AltarRecipeView.class);

    private NaturesAuraJeiIntegration() {
    }

    public static void registerCategories(
            IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AuraFuelJeiCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new NatureAltarJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new OfferingJeiCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new TreeRitualJeiCategory(registration.getJeiHelpers().getGuiHelper()),
                new AnimalSpawnerJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    public static void registerSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(NaturesAuraRegistries.ANIMAL_SPAWN_EGG.get(), AnimalSpawnEggSubtypeInterpreter.INSTANCE);
        registration.registerSubtypeInterpreter(
                NaturesAuraRegistries.NATURE_ALTAR_CONTEXT_ITEM.get(),
                NatureAltarContextSubtypeInterpreter.INSTANCE);
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        registration.addRecipes(AURA_FUEL_TYPE,
                com.example.mekanismmagic.integration.naturesaura.NatureAuraFuelCatalog.displayFuels(level.registryAccess()));
        // RecipesUpdatedEvent refreshes the shared catalog after a data-pack
        // reload. Reading the immutable snapshot here deliberately avoids a
        // second full RecipeManager traversal during JEI registration.
        registration.addRecipes(NATURE_ALTAR_RECIPE_TYPE,
                NatureAuraRecipeScanner.scan(level.getRecipeManager()));
        registration.addRecipes(OFFERING_TYPE, NatureAuraRecipeScanner.scanOfferings(level.getRecipeManager()));
        registration.addRecipes(TREE_RITUAL_TYPE,
                com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge.scan(level.getRecipeManager()));
        registration.addRecipes(ANIMAL_SPAWNER_TYPE,
                com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge.scan(level.getRecipeManager()));
    }

    public static void registerCatalysts(
            IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK, AURA_FUEL_TYPE);
        registration.addRecipeCatalyst(NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_BLOCK, TREE_RITUAL_TYPE);
        registration.addRecipeCatalyst(NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK, ANIMAL_SPAWNER_TYPE);
        registration.addRecipeCatalyst(NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK, OFFERING_TYPE);
        registration.addRecipeCatalyst(
                NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.asItem(),
                NATURE_ALTAR_RECIPE_TYPE);
        registration.addRecipeCatalyst(
                NaturesAuraRegistries.BASIC_NATURE_ALTAR_FACTORY_BLOCK
                        .asItem(),
                NATURE_ALTAR_RECIPE_TYPE);
        registration.addRecipeCatalyst(
                NaturesAuraRegistries.ADVANCED_NATURE_ALTAR_FACTORY_BLOCK
                        .asItem(),
                NATURE_ALTAR_RECIPE_TYPE);
        registration.addRecipeCatalyst(
                NaturesAuraRegistries.ELITE_NATURE_ALTAR_FACTORY_BLOCK
                        .asItem(),
                NATURE_ALTAR_RECIPE_TYPE);
        registration.addRecipeCatalyst(
                NaturesAuraRegistries.ULTIMATE_NATURE_ALTAR_FACTORY_BLOCK
                        .asItem(),
                NATURE_ALTAR_RECIPE_TYPE);
        // Registry lookup keeps the optional Extras classes out of Nature-only clients.
        for (String tier : new String[]{"absolute", "supreme", "cosmic", "infinite"}) {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, tier + "_nature_altar_factory");
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            if (item != net.minecraft.world.item.Items.AIR) {
                registration.addRecipeCatalyst(item, NATURE_ALTAR_RECIPE_TYPE);
            }
        }
    }
}
