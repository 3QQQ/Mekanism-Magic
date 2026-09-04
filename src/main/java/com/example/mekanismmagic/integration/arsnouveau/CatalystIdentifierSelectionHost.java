package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Machine contract used by pattern integrations to select a recipe context. */
public interface CatalystIdentifierSelectionHost {
    Level getLevel();

    boolean selectCatalystIdentifierId(String id);

    /** Current physical or virtual catalyst selection. */
    ItemStack selectedCatalystIdentifier();

    /**
     * Transactional preflight for a context change. Factories override this
     * to keep a shared catalyst stable while any lane still owns work.
     */
    default boolean canSelectCatalystIdentifierId(String id) {
        return id != null && !id.isBlank();
    }

    /** Resolves and validates a recipe selection without mutating the host. */
    default String catalystIdentifierIdForRecipe(
            ResourceLocation recipeId) {
        Level level = getLevel();
        if (level == null || recipeId == null) {
            return "";
        }
        var identifier = ArsNouveauRecipeBridge.createIdentifierForRecipe(
                level, recipeId);
        return !identifier.isEmpty()
                && ArsNouveauRecipeBridge.identifierMatchesRecipe(
                level, identifier, recipeId)
                ? CatalystIdentifierItem.catalystId(identifier).toString()
                : "";
    }

    default boolean selectCatalystIdentifierForRecipe(
            ResourceLocation recipeId) {
        String id = catalystIdentifierIdForRecipe(recipeId);
        return !id.isEmpty() && selectCatalystIdentifierId(id);
    }
}
