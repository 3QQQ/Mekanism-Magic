package com.example.mekanismmagic.api;

/**
 * Exposes the server-synchronised entity represented by a machine's current
 * recipe. The entity is display-only and is never inserted into the level.
 */
public interface IRecipeEntityDisplay {
    RecipeEntityDisplayState mekanismMagicRecipeEntityDisplay();
}
