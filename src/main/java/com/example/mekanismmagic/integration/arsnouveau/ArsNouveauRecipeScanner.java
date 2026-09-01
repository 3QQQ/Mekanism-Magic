package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps one fingerprinted view of Ars Nouveau imbuement recipes per recipe
 * manager. The fingerprint includes resolved ingredient choices so tag-only
 * datapack changes invalidate the catalog as well.
 */
public final class ArsNouveauRecipeScanner {
    private static final Map<RecipeManager, Catalog>
            CATALOGS = new WeakHashMap<>();

    private ArsNouveauRecipeScanner() {
    }

    public static synchronized List<RecipeHolder<ImbuementRecipe>> scan(
            RecipeManager manager) {
        return CATALOGS.computeIfAbsent(
                manager, ArsNouveauRecipeScanner::createCatalog).recipes();
    }

    /** Constant-time recipe lookup for factory validation hot paths. */
    public static synchronized RecipeHolder<ImbuementRecipe> find(
            RecipeManager manager, ResourceLocation id) {
        if (manager == null || id == null) {
            return null;
        }
        return CATALOGS.computeIfAbsent(
                manager, ArsNouveauRecipeScanner::createCatalog)
                .byId().get(id);
    }

    /** Cached signature used to validate persistent catalyst identifiers. */
    public static synchronized String signature(
            RecipeManager manager, ResourceLocation id) {
        if (manager == null || id == null) {
            return "";
        }
        return CATALOGS.computeIfAbsent(
                manager, ArsNouveauRecipeScanner::createCatalog)
                .signatures().getOrDefault(id, "");
    }

    public static synchronized long version(RecipeManager manager) {
        return CATALOGS.computeIfAbsent(
                manager, ArsNouveauRecipeScanner::createCatalog)
                .fingerprint();
    }

    public static synchronized boolean refresh(RecipeManager manager) {
        Catalog previous = CATALOGS.get(manager);
        Catalog current = createCatalog(manager);
        CATALOGS.put(manager, current);
        return previous == null
                || previous.fingerprint() != current.fingerprint();
    }

    public static void scanAtStartup(MinecraftServer server, Logger logger) {
        int total = scan(server.getRecipeManager()).size();
        long catalystRecipes = scan(server.getRecipeManager()).stream()
                .filter(holder -> {
                    int count = holder.value().getPedestalItems().size();
                    return count > 0 && count <= 9;
                })
                .count();
        int identifiers = ArsNouveauRecipeBridge
                .catalystIdentifierJeiRecipes(server.overworld()).size();
        logger.info(
                "Ars Nouveau recipe scan complete: {} imbuement recipes, "
                        + "{} catalyst recipes, {} catalyst identifiers",
                total, catalystRecipes, identifiers);
    }

    private static Catalog createCatalog(RecipeManager manager) {
        List<RecipeHolder<ImbuementRecipe>> recipes = new ArrayList<>(
                manager.getAllRecipesFor(
                        RecipeRegistry.IMBUEMENT_TYPE.get()));
        recipes.sort(Comparator.comparing(holder -> holder.id().toString()));
        List<RecipeHolder<ImbuementRecipe>> immutable =
                List.copyOf(recipes);
        Map<ResourceLocation, RecipeHolder<ImbuementRecipe>> byId =
                new HashMap<>(immutable.size());
        Map<ResourceLocation, String> signatures =
                new HashMap<>(immutable.size());
        for (RecipeHolder<ImbuementRecipe> recipe : immutable) {
            byId.put(recipe.id(), recipe);
            signatures.put(recipe.id(), recipeSignature(recipe.value()));
        }
        return new Catalog(recipeFingerprint(immutable), immutable,
                Map.copyOf(byId), Map.copyOf(signatures));
    }

    private static long recipeFingerprint(
            List<RecipeHolder<ImbuementRecipe>> recipes) {
        long fingerprint = recipes.size();
        for (RecipeHolder<ImbuementRecipe> holder : recipes) {
            ImbuementRecipe recipe = holder.value();
            fingerprint = 31 * fingerprint + holder.id().hashCode();
            fingerprint = 31 * fingerprint
                    + System.identityHashCode(recipe);
            fingerprint = 31 * fingerprint
                    + ingredientFingerprint(recipe.getInput());
            fingerprint = 31 * fingerprint + recipe.getSource();
            for (Ingredient ingredient : recipe.getPedestalItems()) {
                fingerprint = 31 * fingerprint
                        + ingredientFingerprint(ingredient);
            }
        }
        return fingerprint;
    }

    private static int ingredientFingerprint(Ingredient ingredient) {
        List<String> choices = java.util.Arrays.stream(ingredient.getItems())
                .map(ArsNouveauRecipeScanner::stackKey)
                .sorted()
                .toList();
        return choices.hashCode();
    }

    static String recipeSignature(ImbuementRecipe recipe) {
        List<String> ingredients = recipe.getPedestalItems().stream()
                .map(ArsNouveauRecipeScanner::ingredientSignature)
                .sorted()
                .toList();
        String value = ingredients.size() + "|"
                + String.join(";", ingredients);
        return Integer.toUnsignedString(value.hashCode(), 16);
    }

    private static String ingredientSignature(Ingredient ingredient) {
        return java.util.Arrays.stream(ingredient.getItems())
                .map(ArsNouveauRecipeScanner::stackKey)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String stackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "|" + stack.getComponentsPatch();
    }

    private record Catalog(
            long fingerprint,
            List<RecipeHolder<ImbuementRecipe>> recipes,
            Map<ResourceLocation, RecipeHolder<ImbuementRecipe>> byId,
            Map<ResourceLocation, String> signatures) {
    }
}
