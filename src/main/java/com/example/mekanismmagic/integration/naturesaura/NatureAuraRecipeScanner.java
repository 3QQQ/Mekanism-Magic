package com.example.mekanismmagic.integration.naturesaura;

import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** One immutable Nature's Aura recipe snapshot per live recipe manager. */
public final class NatureAuraRecipeScanner {
    private static final Map<RecipeManager, Catalog> CATALOGS =
            new WeakHashMap<>();
    private static long nextVersion;

    private NatureAuraRecipeScanner() {
    }

    public static synchronized List<
            NatureAuraRecipeBridge.AltarRecipeView> scan(
            RecipeManager manager) {
        if (manager == null) {
            return List.of();
        }
        return catalog(manager).recipes();
    }

    public static synchronized Optional<
            NatureAuraRecipeBridge.AltarRecipeView> find(
            RecipeManager manager, ResourceLocation id) {
        if (manager == null || id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(catalog(manager).byId().get(id));
    }

    public static synchronized long version(RecipeManager manager) {
        return manager == null ? 0L : catalog(manager).version();
    }

    /**
     * Concrete catalyst examples exposed by the loaded altar recipes. Item
     * alternatives and tags are expanded, and stacks differing only in count
     * share a kind. Custom ingredients may expose only display examples, so
     * callers must still validate inserted stacks against the live recipes.
     */
    public static synchronized List<ItemStack> catalystKinds(
            RecipeManager manager) {
        return manager == null ? List.of() : catalog(manager).catalystKinds()
                .stream().map(ItemStack::copy).toList();
    }

    public static synchronized int catalystKindCount(
            RecipeManager manager) {
        return manager == null ? 0 : catalog(manager).catalystKinds().size();
    }

    /** Opaque custom ingredients may match items absent from getItems(). */
    public static synchronized boolean hasOpaqueCatalyst(
            RecipeManager manager) {
        return manager != null && catalog(manager).opaqueCatalyst();
    }

    public static synchronized List<OfferingRecipeBridge.OfferingRecipeView>
    scanOfferings(RecipeManager manager) {
        return manager == null ? List.of() : catalog(manager).offerings();
    }

    public static synchronized Optional<OfferingRecipeBridge.OfferingRecipeView>
    findOffering(RecipeManager manager, ResourceLocation id) {
        return manager == null || id == null ? Optional.empty()
                : Optional.ofNullable(catalog(manager).offeringsById().get(id));
    }

    public static synchronized void refresh(RecipeManager manager) {
        if (manager != null) {
            CATALOGS.put(manager, createCatalog(manager));
        }
    }

    public static void logCatalog(MinecraftServer server, Logger logger) {
        List<NatureAuraRecipeBridge.AltarRecipeView> recipes = scan(
                server.getRecipeManager());
        long catalystRecipes = recipes.stream()
                .filter(NatureAuraRecipeBridge.AltarRecipeView
                        ::requiresCatalyst)
                .count();
        logger.info("Nature's Aura recipe scan complete: {} altar recipes, "
                        + "{} with catalyst, {} without catalyst",
                recipes.size(), catalystRecipes,
                recipes.size() - catalystRecipes);
        logger.info("Nature's Aura offering scan complete: {} offering recipes",
                scanOfferings(server.getRecipeManager()).size());
    }

    private static Catalog catalog(RecipeManager manager) {
        return CATALOGS.computeIfAbsent(manager,
                NatureAuraRecipeScanner::createCatalog);
    }

    private static Catalog createCatalog(RecipeManager manager) {
        List<NatureAuraRecipeBridge.AltarRecipeView> recipes =
                NatureAuraRecipeBridge.readAltarRecipes(manager);
        Map<ResourceLocation,
                NatureAuraRecipeBridge.AltarRecipeView> byId =
                new LinkedHashMap<>();
        for (NatureAuraRecipeBridge.AltarRecipeView recipe : recipes) {
            if (byId.put(recipe.id(), recipe) != null) {
                throw new IllegalStateException(
                        "Duplicate Nature's Aura altar recipe id "
                                + recipe.id());
            }
        }
        List<ItemStack> catalystKinds = collectCatalystKinds(recipes);
        boolean opaqueCatalyst = recipes.stream()
                .anyMatch(recipe -> recipe.requiresCatalyst()
                        && !recipe.catalyst().isSimple());
        List<OfferingRecipeBridge.OfferingRecipeView> offerings =
                OfferingRecipeBridge.readRecipes(manager);
        Map<ResourceLocation, OfferingRecipeBridge.OfferingRecipeView>
                offeringsById = new LinkedHashMap<>();
        for (OfferingRecipeBridge.OfferingRecipeView recipe : offerings) {
            if (offeringsById.put(recipe.id(), recipe) != null) {
                throw new IllegalStateException(
                        "Duplicate Nature's Aura offering recipe id "
                                + recipe.id());
            }
        }
        return new Catalog(recipes, Map.copyOf(byId), catalystKinds,
                opaqueCatalyst, offerings,
                Map.copyOf(offeringsById), ++nextVersion);
    }

    static List<ItemStack> collectCatalystKinds(List<
            NatureAuraRecipeBridge.AltarRecipeView> recipes) {
        Set<ItemStack> kinds = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (NatureAuraRecipeBridge.AltarRecipeView recipe : recipes) {
            var catalyst = recipe.catalyst();
            // NeoForge represents an empty tag with a named BARRIER display
            // stack. It is not a real catalyst kind.
            if (catalyst.isEmpty() || catalyst.hasNoItems()) {
                continue;
            }
            for (ItemStack candidate : catalyst.getItems()) {
                if (candidate != null && !candidate.isEmpty()
                        && catalyst.test(candidate)) {
                    kinds.add(candidate.copyWithCount(1));
                }
            }
        }
        return List.copyOf(new ArrayList<>(kinds));
    }

    private record Catalog(
            List<NatureAuraRecipeBridge.AltarRecipeView> recipes,
            Map<ResourceLocation,
                    NatureAuraRecipeBridge.AltarRecipeView> byId,
            List<ItemStack> catalystKinds,
            boolean opaqueCatalyst,
            List<OfferingRecipeBridge.OfferingRecipeView> offerings,
            Map<ResourceLocation, OfferingRecipeBridge.OfferingRecipeView>
                    offeringsById,
            long version) {
    }
}
