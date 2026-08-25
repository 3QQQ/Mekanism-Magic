package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps one indexed view of Ars Nouveau imbuement recipes per recipe manager.
 *
 * The recipe manager is replaced when datapacks are reloaded, so using it as
 * the cache key automatically invalidates the catalog on /reload as well as
 * on a fresh game start.
 */
public final class ArsNouveauRecipeScanner {
    private static final Map<RecipeManager, List<RecipeHolder<ImbuementRecipe>>>
            CATALOGS = new WeakHashMap<>();

    private ArsNouveauRecipeScanner() {
    }

    public static synchronized List<RecipeHolder<ImbuementRecipe>> scan(
            RecipeManager manager) {
        return CATALOGS.computeIfAbsent(manager, current -> List.copyOf(
                current.getAllRecipesFor(RecipeRegistry.IMBUEMENT_TYPE.get())));
    }

    public static void scanAtStartup(MinecraftServer server, Logger logger) {
        int total = scan(server.getRecipeManager()).size();
        long catalystRecipes = scan(server.getRecipeManager()).stream()
                .filter(holder -> holder.value().getPedestalItems().size() == 3)
                .count();
        logger.info(
                "Ars Nouveau recipe scan complete: {} imbuement recipes, "
                        + "{} catalyst identifier recipes",
                total, catalystRecipes);
    }
}
