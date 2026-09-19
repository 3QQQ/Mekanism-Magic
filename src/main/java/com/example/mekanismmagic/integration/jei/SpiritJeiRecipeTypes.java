package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge.SpiritJeiData;
import com.example.mekanismmagic.integration.occultism.SpiritRecipeGroup;
import mezz.jei.api.recipe.RecipeType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** JEI IDs and Mekanism recipe buttons share SpiritRecipeGroup's stable paths. */
public final class SpiritJeiRecipeTypes {
    private static final Map<SpiritRecipeGroup, RecipeType<SpiritJeiData>> TYPES = createTypes();
    private SpiritJeiRecipeTypes() { }
    private static Map<SpiritRecipeGroup, RecipeType<SpiritJeiData>> createTypes() {
        EnumMap<SpiritRecipeGroup, RecipeType<SpiritJeiData>> types = new EnumMap<>(SpiritRecipeGroup.class);
        for (var group : SpiritRecipeGroup.values()) types.put(group, new RecipeType<>(group.id(), SpiritJeiData.class));
        return Collections.unmodifiableMap(types);
    }
    public static RecipeType<SpiritJeiData> type(SpiritRecipeGroup group) { return TYPES.get(group); }
    public static RecipeType<?>[] all() { return TYPES.values().toArray(RecipeType[]::new); }
}
