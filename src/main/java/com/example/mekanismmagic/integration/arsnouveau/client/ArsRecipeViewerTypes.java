package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.client.MagicRecipeViewerType;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ArmorUpgradeRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantmentRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

public final class ArsRecipeViewerTypes {
    public static final IRecipeViewerRecipeType<
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData>
            CATALYST_IDENTIFIER = new MagicRecipeViewerType<>(
            ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, "catalyst_identifier"),
            Component.translatable(
                    "jei.mekanism_magic.catalyst_identifier"),
            ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_BLOCK,
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData.class,
            false, 126, 72,
            ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_BLOCK);

    public static final IRecipeViewerRecipeType<
            ArsNouveauRecipeBridge.IdentifierImbuementJeiData>
            IDENTIFIER_IMBUEMENT = new MagicRecipeViewerType<>(
            ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, "identifier_imbuement"),
            Component.translatable(
                    "jei.mekanism_magic.identifier_imbuement"),
            ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK,
            ArsNouveauRecipeBridge.IdentifierImbuementJeiData.class,
            false, 150, 72,
            ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK);

    public static final IRecipeViewerRecipeType<EnchantingApparatusRecipe>
            ENCHANTING_APPARATUS = holderType(
            RecipeRegistry.APPARATUS_TYPE.getId(),
            ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK,
            EnchantingApparatusRecipe.class, 150, 110,
            BlockRegistry.ENCHANTING_APP_BLOCK);
    public static final IRecipeViewerRecipeType<EnchantmentRecipe>
            ENCHANTMENT = holderType(
            RecipeRegistry.ENCHANTMENT_TYPE.getId(),
            ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK,
            EnchantmentRecipe.class, 150, 110,
            BlockRegistry.ENCHANTING_APP_BLOCK);
    public static final IRecipeViewerRecipeType<ArmorUpgradeRecipe>
            ARMOR_UPGRADE = holderType(
            RecipeRegistry.ARMOR_UPGRADE_TYPE.getId(),
            ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK,
            ArmorUpgradeRecipe.class, 150, 110,
            BlockRegistry.ENCHANTING_APP_BLOCK);

    private ArsRecipeViewerTypes() {
    }

    private static <RECIPE> IRecipeViewerRecipeType<RECIPE> holderType(
            ResourceLocation id, ItemLike machine,
            Class<? extends RECIPE> recipeClass,
            int width, int height, ItemLike... originalWorkstations) {
        ItemLike[] workstations = new ItemLike[
                originalWorkstations.length + 1];
        workstations[0] = machine;
        System.arraycopy(originalWorkstations, 0, workstations, 1,
                originalWorkstations.length);
        return new MagicRecipeViewerType<>(id,
                machine.asItem().getDescription(), machine,
                recipeClass, true, width, height, workstations);
    }
}
