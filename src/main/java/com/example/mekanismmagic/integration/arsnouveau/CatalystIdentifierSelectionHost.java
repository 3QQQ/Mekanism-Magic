package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Machine contract used by pattern integrations to select a recipe context. */
public interface CatalystIdentifierSelectionHost {
    Level getLevel();

    boolean selectCatalystIdentifierId(String id);

    default boolean selectCatalystIdentifierForRecipe(
            ResourceLocation recipeId) {
        Level level = getLevel();
        if (level == null || recipeId == null) {
            return false;
        }
        var identifier = ArsNouveauRecipeBridge.createIdentifierForRecipe(
                level, recipeId);
        return !identifier.isEmpty()
                && ArsNouveauRecipeBridge.identifierMatchesRecipe(
                level, identifier, recipeId)
                && selectCatalystIdentifierId(
                CatalystIdentifierItem.catalystId(identifier).toString());
    }
}
