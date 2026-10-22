package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge.SpiritJeiData;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Presentation-only profession groups. These never affect machine recipe selection or pricing. */
public enum SpiritRecipeGroup {
    CRUSHING("spirit_crushing", "crusher", "iron_pickaxe"),
    CRYSTALLIZING("spirit_crystallizing", "crystallizer", "amethyst_shard"),
    SMELTING("spirit_smelting", "smelter", "furnace"),
    OTHERROCK_TRADER("spirit_trade_otherrock", "otherrock_trader", "cobblestone"),
    OTHERSTONE_TRADER("spirit_trade_otherstone", "otherstone_trader", "stone"),
    SAPLING_TRADER("spirit_trade_saplings", "sapling_trader", "oak_sapling"),
    GAMBLER("spirit_gambler", "gambler", "emerald"),
    // Keep the old category ID as the general spirit-fire page, not a duplicate all-recipes page.
    SPIRIT_FIRE("spirit", "spirit_fire", "blaze_powder"),
    OTHER("spirit_other", "other", "book");

    private final String path;
    private final String titleKey;
    private final String iconPath;

    SpiritRecipeGroup(String path, String key, String iconPath) {
        this.path = path;
        this.titleKey = "jei.mekanism_magic.spirit_job." + key;
        this.iconPath = iconPath;
    }
    public String path() { return path; }
    public String titleKey() { return titleKey; }
    public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("mekanism_magic", path); }
    public ResourceLocation iconId() { return ResourceLocation.withDefaultNamespace(iconPath); }

    public static SpiritRecipeGroup classify(SpiritJeiData recipe) {
        return classify(recipe.recipeType(), "spirit_trade".equals(recipe.recipeType())
                ? OccultismRecipeBridge.spiritJobId(recipe.spirit()) : "");
    }

    public static SpiritRecipeGroup classify(String recipeType, String jobId) {
        if (recipeType == null) return OTHER;
        return switch (recipeType) {
            case "crushing" -> CRUSHING;
            case "crystallize" -> CRYSTALLIZING;
            case "smelting", "blasting", "smoking", "campfire_cooking" -> SMELTING;
            case "spirit_fire" -> SPIRIT_FIRE;
            case "spirit_trade" -> switch (OccultismSpiritJobPolicy.normalizeJobId(jobId)) {
                case "occultism:trader_otherrock" -> OTHERROCK_TRADER;
                case "occultism:trader_otherstone" -> OTHERSTONE_TRADER;
                case "occultism:trader_otherworld_saplings" -> SAPLING_TRADER;
                case "occultism:gambler" -> GAMBLER;
                default -> OTHER;
            };
            default -> OTHER;
        };
    }

    /** Every row goes to exactly one page; preserve original row objects, order, amounts and components. */
    public static Map<SpiritRecipeGroup, List<SpiritJeiData>> partition(List<SpiritJeiData> recipes) {
        EnumMap<SpiritRecipeGroup, List<SpiritJeiData>> groups = new EnumMap<>(SpiritRecipeGroup.class);
        for (var group : values()) groups.put(group, new ArrayList<>());
        for (var recipe : recipes) groups.get(classify(recipe)).add(recipe);
        groups.replaceAll((group, rows) -> List.copyOf(rows));
        return Collections.unmodifiableMap(groups);
    }

    /** A native recipe can have four tier-dependent rows; bookmarks must not collapse them. */
    public static ResourceLocation displayId(SpiritJeiData recipe) {
        int tier = OccultismRecipeBridge.spiritTier(recipe.spirit());
        ResourceLocation job = ResourceLocation.tryParse(OccultismRecipeBridge.spiritJobId(recipe.spirit()));
        String jobPath = job == null || job.getPath().isEmpty() ? "unbound" : job.getNamespace() + "/" + job.getPath();
        return ResourceLocation.fromNamespaceAndPath("mekanism_magic", "spirit_view/" + classify(recipe).path
                + "/" + recipe.recipeId().getNamespace() + "/" + recipe.recipeId().getPath()
                + "/tier_" + tier + "/" + jobPath);
    }
}
