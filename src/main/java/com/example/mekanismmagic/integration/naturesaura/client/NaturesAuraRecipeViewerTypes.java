package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.client.MagicRecipeViewerType;
import com.example.mekanismmagic.integration.naturesaura
        .NatureAuraRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura
        .NaturesAuraRegistries;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Maps natural altar screens to the dedicated machine JEI category. */
public final class NaturesAuraRecipeViewerTypes {
    public static final IRecipeViewerRecipeType<com.example.mekanismmagic.integration.naturesaura.NatureAuraFuelCatalog.Fuel>
            AURA_FUEL = new MagicRecipeViewerType<>(ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "aura_fuel"),
            Component.translatable("jei.mekanism_magic.aura_fuel"), NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK,
            com.example.mekanismmagic.integration.naturesaura.NatureAuraFuelCatalog.Fuel.class,
            false, 166, 82, NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK);
    public static final IRecipeViewerRecipeType<com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge.TreeRitualRecipeView>
            TREE_RITUAL = new MagicRecipeViewerType<>(ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "tree_ritual"),
            Component.translatable("jei.mekanism_magic.tree_ritual"), NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_BLOCK,
            com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge.TreeRitualRecipeView.class,
            false, 178, 96, NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_BLOCK);
    public static final IRecipeViewerRecipeType<com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView>
            ANIMAL_SPAWNER = new MagicRecipeViewerType<>(ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "animal_spawner"),
            Component.translatable("jei.mekanism_magic.animal_spawner"), NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK,
            com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView.class,
            false, 178, 96, NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK);
    public static final IRecipeViewerRecipeType<
            com.example.mekanismmagic.integration.naturesaura.OfferingRecipeBridge.OfferingRecipeView>
            OFFERING = new MagicRecipeViewerType<>(
            ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "offering"),
            Component.translatable("jei.mekanism_magic.offering"),
            NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK,
            com.example.mekanismmagic.integration.naturesaura.OfferingRecipeBridge.OfferingRecipeView.class,
            false, 150, 76, NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK);
    public static final IRecipeViewerRecipeType<
            NatureAuraRecipeBridge.AltarRecipeView> NATURE_ALTAR =
            new MagicRecipeViewerType<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MekanismMagic.MOD_ID, "nature_altar"),
                    Component.translatable(
                            "jei.mekanism_magic.nature_altar"),
                    NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK,
                    NatureAuraRecipeBridge.AltarRecipeView.class,
                    false, 150, 76,
                    NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK,
                    NaturesAuraRegistries.BASIC_NATURE_ALTAR_FACTORY_BLOCK,
                    NaturesAuraRegistries
                            .ADVANCED_NATURE_ALTAR_FACTORY_BLOCK,
                    NaturesAuraRegistries.ELITE_NATURE_ALTAR_FACTORY_BLOCK,
                    NaturesAuraRegistries
                            .ULTIMATE_NATURE_ALTAR_FACTORY_BLOCK);

    private NaturesAuraRecipeViewerTypes() {
    }
}
