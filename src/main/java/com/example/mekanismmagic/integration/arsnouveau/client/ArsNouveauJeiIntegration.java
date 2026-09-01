package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.hollingsworth.arsnouveau.client.jei.JEIArsNouveauPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;

/**
 * Reuses Ars Nouveau's existing JEI categories for the equivalent machines.
 */
public final class ArsNouveauJeiIntegration {
    public static final RecipeType<
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData>
            CATALYST_IDENTIFIER_RECIPE_TYPE = RecipeType.create(
                    "mekanism_magic", "catalyst_identifier",
                    ArsNouveauRecipeBridge.CatalystIdentifierJeiData.class);
    public static final RecipeType<
            ArsNouveauRecipeBridge.IdentifierImbuementJeiData>
            IDENTIFIER_IMBUEMENT_RECIPE_TYPE = RecipeType.create(
                    "mekanism_magic", "identifier_imbuement",
                    ArsNouveauRecipeBridge.IdentifierImbuementJeiData.class);

    private ArsNouveauJeiIntegration() {
    }

    public static void registerCategories(
            IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new CatalystIdentifierJeiCategory(
                        registration.getJeiHelpers().getGuiHelper()),
                new IdentifierImbuementJeiCategory(
                        registration.getJeiHelpers().getGuiHelper()));
    }

    public static void registerSubtypes(
            ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(
                ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get(),
                CatalystIdentifierSubtypeInterpreter.INSTANCE);
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level != null) {
            ArsNouveauRecipeScanner.refresh(
                    Minecraft.getInstance().level.getRecipeManager());
            java.util.List<ArsNouveauRecipeBridge
                    .CatalystIdentifierJeiData> recipes =
                    ArsNouveauRecipeBridge.catalystIdentifierJeiRecipes(
                            Minecraft.getInstance().level);
            registration.addRecipes(CATALYST_IDENTIFIER_RECIPE_TYPE,
                    recipes);
            registration.addRecipes(IDENTIFIER_IMBUEMENT_RECIPE_TYPE,
                    ArsNouveauRecipeBridge
                            .identifierImbuementJeiRecipes(
                                    Minecraft.getInstance().level));
            java.util.List<net.minecraft.world.item.ItemStack> identifiers =
                    recipes.stream()
                            .map(ArsNouveauRecipeBridge
                                    .CatalystIdentifierJeiData::output)
                            .map(net.minecraft.world.item.ItemStack::copy)
                            .toList();
            registration.addItemStackInfo(identifiers,
                    net.minecraft.network.chat.Component.translatable(
                            "jei.mekanism_magic.catalyst_identifier.info"));
            registration.getIngredientManager().addIngredientsAtRuntime(
                    VanillaTypes.ITEM_STACK, identifiers);
        }
    }

    public static void registerCatalysts(
            IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_BLOCK
                        .asItem(),
                CATALYST_IDENTIFIER_RECIPE_TYPE);
        registration.addRecipeCatalyst(
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.asItem(),
                IDENTIFIER_IMBUEMENT_RECIPE_TYPE);
        for (String path : new String[]{
                "basic_imbuement_factory",
                "advanced_imbuement_factory",
                "elite_imbuement_factory",
                "ultimate_imbuement_factory",
                "absolute_imbuement_factory",
                "supreme_imbuement_factory",
                "cosmic_imbuement_factory",
                "infinite_imbuement_factory"}) {
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            net.minecraft.resources.ResourceLocation
                                    .fromNamespaceAndPath(
                                            "mekanism_magic", path));
            if (item != net.minecraft.world.item.Items.AIR) {
                registration.addRecipeCatalyst(item,
                        IDENTIFIER_IMBUEMENT_RECIPE_TYPE);
            }
        }
        registration.addRecipeCatalyst(
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK
                        .asItem(),
                JEIArsNouveauPlugin.ENCHANTING_APP_RECIPE_TYPE.get(),
                JEIArsNouveauPlugin.ENCHANTING_RECIPE_TYPE.get(),
                JEIArsNouveauPlugin.ARMOR_RECIPE_TYPE.get());
    }

    public static void registerExtraIngredients(
            IExtraIngredientRegistration registration) {
        if (Minecraft.getInstance().level != null) {
            ArsNouveauRecipeScanner.refresh(
                    Minecraft.getInstance().level.getRecipeManager());
            registration.addExtraItemStacks(
                    ArsNouveauRecipeBridge.catalystIdentifierJeiStacks(
                            Minecraft.getInstance().level));
        }
    }
}
