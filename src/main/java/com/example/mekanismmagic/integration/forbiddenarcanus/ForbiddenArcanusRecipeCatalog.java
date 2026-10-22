package com.example.mekanismmagic.integration.forbiddenarcanus;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Server-only catalog. Startup and successful global reloads publish snapshots;
 * readers never discover recipes or scan dynamic registries.
 * No Forbidden & Arcanus or Valhelsia types occur in this public contract.
 */
public final class ForbiddenArcanusRecipeCatalog {
    private static final Map<RecipeManager, CachedCatalog> CATALOGS = new WeakHashMap<>();
    private static final Snapshot EMPTY = new Snapshot(0, Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), Map.of(), Map.of());
    private static long revision;

    private ForbiddenArcanusRecipeCatalog() {
    }

    /** Cache lookup only; an uninitialized or replaced server has no snapshot. */
    public static synchronized Snapshot snapshot(MinecraftServer server) {
        CachedCatalog cached = cached(server);
        return cached == null ? EMPTY : cached.snapshot();
    }

    /** Build everything before replacing the previous usable snapshot. */
    public static synchronized Snapshot refresh(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        RegistryAccess registries = server.registryAccess();
        var loaded = ForbiddenArcanus261Bridge.read(server.getRecipeManager(), registries, revision + 1);
        CATALOGS.put(server.getRecipeManager(), new CachedCatalog(new WeakReference<>(registries),
                loaded.snapshot(), loaded.recipes(), loaded.forgeRituals()));
        revision++;
        return loaded.snapshot();
    }

    public static synchronized void clear() {
        CATALOGS.clear();
        revision++;
    }

    public static synchronized long revision() {
        return revision;
    }

    /** Cached material validation, independent of currently available fire or enhancers. */
    public static synchronized boolean isClibanoIngredient(MinecraftServer server, ItemStack stack) {
        CachedCatalog cached = cached(server);
        return cached != null && cached.recipes().isClibanoIngredient(stack);
    }

    /** Qualification only: no inventory edits, fuel use, experience, or random draws. */
    public static synchronized boolean matchesClibano(ServerLevel level, ResourceLocation recipeId,
                                                       ItemStack first, ItemStack second,
                                                       FireType fire, ItemStack enhancer) {
        CachedCatalog cached = cached(level.getServer());
        return cached != null && cached.recipes().matchesClibano(
                recipeId, level, first, second, fire, enhancer);
    }

    /** Native output assembly only. This is not an operation commit or residue roll. */
    public static synchronized Optional<ItemStack> assembleClibano(ServerLevel level,
                                                                   ResourceLocation recipeId,
                                                                   ItemStack first, ItemStack second,
                                                                   FireType fire, ItemStack enhancer) {
        CachedCatalog cached = cached(level.getServer());
        return cached == null ? Optional.empty() : cached.recipes().assembleClibano(
                recipeId, level, first, second, fire, enhancer);
    }

    public static synchronized boolean matchesApplyModifier(ServerLevel level,
                                                             ResourceLocation recipeId,
                                                             ItemStack template, ItemStack base,
                                                             ItemStack addition) {
        CachedCatalog cached = cached(level.getServer());
        return cached != null && cached.recipes().matchesApplyModifier(
                recipeId, level, template, base, addition);
    }

    /** Cached native role predicates only; no assembly, modifier callback or registry scan. */
    public static synchronized boolean isApplyModifierIngredient(MinecraftServer server, ItemStack stack) {
        CachedCatalog cached = cached(server);
        return cached != null && cached.recipes().isApplyModifierIngredient(stack);
    }

    public static synchronized Optional<ItemStack> assembleApplyModifier(ServerLevel level,
                                                                         ResourceLocation recipeId,
                                                                         ItemStack template,
                                                                         ItemStack base,
                                                                         ItemStack addition) {
        CachedCatalog cached = cached(level.getServer());
        return cached == null ? Optional.empty() : cached.recipes().assembleApplyModifier(
                recipeId, level, template, base, addition);
    }

    private static CachedCatalog cached(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        CachedCatalog cached = CATALOGS.get(server.getRecipeManager());
        return cached != null && cached.registries().get() == server.registryAccess() ? cached : null;
    }

    static synchronized HephaestusForge261Bridge.NativeRituals forgeRituals(MinecraftServer server) {
        CachedCatalog cached = cached(server);
        return cached == null ? null : cached.forgeRituals();
    }

    // A strong server/level/RegistryAccess reference in the value could retain the weak map key.
    private record CachedCatalog(WeakReference<RegistryAccess> registries, Snapshot snapshot,
                                 ForbiddenArcanus261Bridge.NativeRecipes recipes,
                                 HephaestusForge261Bridge.NativeRituals forgeRituals) {
    }

    public enum FireType {
        FIRE, SOUL_FIRE, ENCHANTED_FIRE
    }

    public record CookingTimes(int fire, int soulFire, int enchantedFire) {
        public int forFire(FireType type) {
            return switch (type) {
                case FIRE -> fire;
                case SOUL_FIRE -> soulFire;
                case ENCHANTED_FIRE -> enchantedFire;
            };
        }
    }

    /** Display examples only; the native Ingredient remains authoritative for matching. */
    public record IngredientView(List<ItemStack> examples) {
        public IngredientView {
            examples = copies(examples);
        }

        @Override
        public List<ItemStack> examples() {
            return copies(examples);
        }
    }

    public record ResidueChanceView(ResourceLocation type, double chance) {
    }

    public record ClibanoRecipeView(ResourceLocation id, boolean doubleInput,
                                    List<IngredientView> ingredients, CookingTimes cookingTimes,
                                    FireType requiredFire, Optional<ResourceLocation> requiredEnhancer,
                                    Optional<ResidueChanceView> residue, float experience,
                                    ItemStack output) {
        public ClibanoRecipeView {
            ingredients = List.copyOf(ingredients);
            output = output.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        /** Ordinary fire never generates residues, even if the recipe declares a chance. */
        public boolean canProduceResidue(FireType fire) {
            return fire != FireType.FIRE && residue.isPresent();
        }
    }

    public record ApplyModifierRecipeView(ResourceLocation id, IngredientView template,
                                          IngredientView addition, ResourceLocation modifier) {
    }

    /** The four values are requirements, not a mutable storage or a combined magic resource. */
    public record EssenceRequirements(int aureal, int souls, int blood, int experience) {
    }

    public record RitualIngredient(IngredientView ingredient, int amount) {
    }

    public record RitualView(ResourceLocation id, IngredientView mainIngredient,
                             List<RitualIngredient> inputs, EssenceRequirements essences,
                             int tier, boolean exactTier, List<ResourceLocation> enhancers,
                             ResourceLocation magicCircle, int duration, ResourceLocation resultType) {
        public RitualView {
            inputs = List.copyOf(inputs);
            enhancers = List.copyOf(enhancers);
        }
    }

    public record EnhancerView(ResourceLocation id, ResourceLocation displayItem,
                               int clibanoSoulDuration) {
    }

    public record ModifierDefinitionView(ResourceLocation id) {
    }

    public record ResidueView(ResourceLocation id, int requiredAmount, ItemStack output) {
        public ResidueView {
            output = output.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }

    public record MagicCircleView(ResourceLocation id, ResourceLocation innerTexture,
                                  ResourceLocation outerTexture) {
    }

    public record Counts(int clibanoRecipes, int applyModifierRecipes, int rituals,
                         int enhancers, int modifierDefinitions, int residues, int magicCircles) {
    }

    public record Snapshot(long revision, Map<ResourceLocation, ClibanoRecipeView> clibanoById,
                           Map<ResourceLocation, ApplyModifierRecipeView> applyModifierById,
                           Map<ResourceLocation, RitualView> ritualsById,
                           Map<ResourceLocation, EnhancerView> enhancersById,
                           Map<ResourceLocation, ModifierDefinitionView> modifierDefinitionsById,
                           Map<ResourceLocation, ResidueView> residuesById,
                           Map<ResourceLocation, MagicCircleView> magicCirclesById) {
        public Snapshot {
            clibanoById = orderedCopy(clibanoById);
            applyModifierById = orderedCopy(applyModifierById);
            ritualsById = orderedCopy(ritualsById);
            enhancersById = orderedCopy(enhancersById);
            modifierDefinitionsById = orderedCopy(modifierDefinitionsById);
            residuesById = orderedCopy(residuesById);
            magicCirclesById = orderedCopy(magicCirclesById);
        }

        public Counts counts() {
            return new Counts(clibanoById.size(), applyModifierById.size(), ritualsById.size(),
                    enhancersById.size(), modifierDefinitionsById.size(), residuesById.size(),
                    magicCirclesById.size());
        }

        public List<ClibanoRecipeView> clibanoRecipes() {
            return List.copyOf(clibanoById.values());
        }

        public List<ApplyModifierRecipeView> applyModifierRecipes() {
            return List.copyOf(applyModifierById.values());
        }

        public List<RitualView> rituals() {
            return List.copyOf(ritualsById.values());
        }

        public List<EnhancerView> enhancers() {
            return List.copyOf(enhancersById.values());
        }

        public List<ModifierDefinitionView> modifierDefinitions() {
            return List.copyOf(modifierDefinitionsById.values());
        }

        public List<ResidueView> residues() {
            return List.copyOf(residuesById.values());
        }

        public List<MagicCircleView> magicCircles() {
            return List.copyOf(magicCirclesById.values());
        }
    }

    private static List<ItemStack> copies(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static <T> Map<ResourceLocation, T> orderedCopy(Map<ResourceLocation, T> source) {
        Map<ResourceLocation, T> result = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey(
                java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(result);
    }
}
