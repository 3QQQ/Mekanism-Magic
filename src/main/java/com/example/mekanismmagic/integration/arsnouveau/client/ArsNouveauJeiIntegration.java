package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusJeiData;
import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusRecipeViews;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge.CatalystIdentifierJeiData;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge.IdentifierImbuementJeiData;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Machine-facing Ars Nouveau views with exact inputs and actual assembly.
 */
public final class ArsNouveauJeiIntegration {
    public static final RecipeType<ArsApparatusJeiData> APPARATUS_RECIPE_TYPE =
            RecipeType.create("mekanism_magic", "apparatus",
                    ArsApparatusJeiData.class);
    private static List<ArsApparatusJeiData> registeredApparatus = List.of();
    private static volatile List<ArsApparatusJeiData> focusedOnlyApparatus = List.of();
    private static List<RegisteredRecipe<CatalystIdentifierJeiData>> registeredCatalysts = List.of();
    private static List<RegisteredRecipe<IdentifierImbuementJeiData>> registeredImbuement = List.of();
    private static List<ItemStack> registeredIdentifierStacks = List.of();
    private static IIngredientManager ingredientManager;
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
                new ArsApparatusJeiCategory(
                        registration.getJeiHelpers().getGuiHelper()),
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

    public static void registerAdvanced(IAdvancedRegistration registration) {
        registration.addTypedRecipeManagerPlugin(APPARATUS_RECIPE_TYPE,
                new FocusedApparatusRecipes());
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        ArsApparatusJeiReagents.clear();
        registeredApparatus = List.of();
        focusedOnlyApparatus = List.of();
        registeredCatalysts = List.of();
        registeredImbuement = List.of();
        registeredIdentifierStacks = List.of();
        ingredientManager = registration.getIngredientManager();
        if (Minecraft.getInstance().level != null) {
            ApparatusListings apparatus = apparatusListings(Minecraft.getInstance().level);
            registeredApparatus = apparatus.browsable();
            focusedOnlyApparatus = apparatus.focusedOnly();
            registration.addRecipes(APPARATUS_RECIPE_TYPE, registeredApparatus);
            ArsNouveauRecipeScanner.refresh(
                    Minecraft.getInstance().level.getRecipeManager());
            registeredCatalysts = remember(freezeCatalysts(ArsNouveauRecipeBridge.catalystIdentifierJeiRecipes(
                    Minecraft.getInstance().level)), ArsNouveauJeiIntegration::catalystKey);
            registeredImbuement = remember(freezeImbuement(ArsNouveauRecipeBridge.identifierImbuementJeiRecipes(
                    Minecraft.getInstance().level)), ArsNouveauJeiIntegration::imbuementKey);
            if (!registeredCatalysts.isEmpty()) registration.addRecipes(CATALYST_IDENTIFIER_RECIPE_TYPE, recipes(registeredCatalysts));
            if (!registeredImbuement.isEmpty()) registration.addRecipes(IDENTIFIER_IMBUEMENT_RECIPE_TYPE, recipes(registeredImbuement));
            registeredIdentifierStacks = recipes(registeredCatalysts).stream().map(recipe -> recipe.output().copy()).toList();
            if (!registeredIdentifierStacks.isEmpty()) {
                registration.addItemStackInfo(registeredIdentifierStacks,
                        net.minecraft.network.chat.Component.translatable("jei.mekanism_magic.catalyst_identifier.info"));
                ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, registeredIdentifierStacks);
            }
        }
    }

    /** Release the disposed JEI runtime and all recipe/ingredient snapshots without mutating it. */
    public static void onRuntimeUnavailable() {
        registeredApparatus = List.of();
        focusedOnlyApparatus = List.of();
        registeredCatalysts = List.of();
        registeredImbuement = List.of();
        registeredIdentifierStacks = List.of();
        ingredientManager = null;
        ArsApparatusJeiReagents.clear();
    }

    /** Existing reflective callback refreshes all three Ars pages, never from a tick scan. */
    public static void refreshApparatusRecipes(IRecipeManager manager) {
        ArsApparatusJeiReagents.clear();
        if (Minecraft.getInstance().level == null) {
            return;
        }
        ApparatusListings apparatus = apparatusListings(Minecraft.getInstance().level);
        List<ArsApparatusJeiData> fresh = apparatus.browsable();
        if (!fresh.equals(registeredApparatus)) {
            if (!registeredApparatus.isEmpty()) {
                manager.hideRecipes(APPARATUS_RECIPE_TYPE, registeredApparatus);
            }
            if (!fresh.isEmpty()) {
                manager.addRecipes(APPARATUS_RECIPE_TYPE, fresh);
                manager.unhideRecipes(APPARATUS_RECIPE_TYPE, fresh);
            }
            registeredApparatus = List.copyOf(fresh);
        }
        focusedOnlyApparatus = apparatus.focusedOnly();
        refreshImbuementRecipes(manager,
                ArsNouveauRecipeBridge.catalystIdentifierJeiRecipes(Minecraft.getInstance().level),
                ArsNouveauRecipeBridge.identifierImbuementJeiRecipes(Minecraft.getInstance().level));
    }

    private static ApparatusListings apparatusListings(Level level) {
        List<ArsApparatusJeiData> browsable = new ArrayList<>();
        List<ArsApparatusJeiData> focusedOnly = new ArrayList<>();
        for (ArsApparatusJeiData view : ArsApparatusRecipeViews.recipes(level)) {
            if (ArsApparatusRecipeViews.hasResolvedPreview(level, view, List.of())) {
                browsable.add(view);
            } else {
                // JEI's hidden-recipe set also filters advanced lookups. A
                // former browse entry must not equal its focused-only view.
                focusedOnly.add(new ArsApparatusJeiData(view.id(), view.recipe(), true));
            }
        }
        return new ApparatusListings(List.copyOf(browsable), List.copyOf(focusedOnly));
    }

    private record ApparatusListings(List<ArsApparatusJeiData> browsable,
                                     List<ArsApparatusJeiData> focusedOnly) {
    }

    /** No blank browse pages; an actual item focus may reveal an exact craft. */
    private static final class FocusedApparatusRecipes
            implements ISimpleRecipeManagerPlugin<ArsApparatusJeiData> {
        @Override
        public boolean isHandledInput(ITypedIngredient<?> input) {
            return input != null && Minecraft.getInstance().level != null
                    && !focusedOnlyApparatus.isEmpty()
                    && input.getItemStack().filter(stack -> !stack.isEmpty()).isPresent();
        }

        @Override
        public boolean isHandledOutput(ITypedIngredient<?> output) {
            return false;
        }

        @Override
        public List<ArsApparatusJeiData> getRecipesForInput(ITypedIngredient<?> input) {
            if (!isHandledInput(input)) {
                return List.of();
            }
            Level level = Minecraft.getInstance().level;
            ItemStack focusedStack = input.getItemStack().orElse(ItemStack.EMPTY);
            return focusedOnlyApparatus.stream()
                    .filter(view -> ArsApparatusRecipeViews.hasResolvedPreview(level,
                            view, List.of(focusedStack)))
                    .toList();
        }

        @Override
        public List<ArsApparatusJeiData> getRecipesForOutput(ITypedIngredient<?> output) {
            return List.of();
        }

        @Override
        public List<ArsApparatusJeiData> getAllRecipes() {
            return List.of();
        }
    }

    /** Detached boundary also used by the API contract test without a Minecraft client. */
    static void refreshImbuementRecipes(IRecipeManager manager, List<CatalystIdentifierJeiData> catalysts,
                                        List<IdentifierImbuementJeiData> imbuement) {
        registeredCatalysts = reconcile(manager, CATALYST_IDENTIFIER_RECIPE_TYPE, registeredCatalysts,
                freezeCatalysts(catalysts), ArsNouveauJeiIntegration::catalystKey);
        registeredImbuement = reconcile(manager, IDENTIFIER_IMBUEMENT_RECIPE_TYPE, registeredImbuement,
                freezeImbuement(imbuement), ArsNouveauJeiIntegration::imbuementKey);
        List<ItemStack> freshIdentifiers = recipes(registeredCatalysts).stream().map(recipe -> recipe.output().copy()).toList();
        if (ingredientManager != null) {
            List<ItemStack> removed = registeredIdentifierStacks.stream().filter(old -> freshIdentifiers.stream()
                    .noneMatch(current -> ItemStack.matches(old, current))).toList();
            List<ItemStack> added = freshIdentifiers.stream().filter(current -> registeredIdentifierStacks.stream()
                    .noneMatch(old -> ItemStack.matches(old, current))).toList();
            if (!removed.isEmpty()) ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, removed);
            if (!added.isEmpty()) ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, added);
        }
        registeredIdentifierStacks = freshIdentifiers;
    }

    private static <T> List<RegisteredRecipe<T>> remember(List<T> fresh, Function<T, DisplayKey> key) {
        return fresh.stream().map(recipe -> new RegisteredRecipe<>(recipe, key.apply(recipe))).toList();
    }

    private static <T> List<T> recipes(List<RegisteredRecipe<T>> registered) {
        return registered.stream().map(RegisteredRecipe::recipe).toList();
    }

    private static <T> List<RegisteredRecipe<T>> reconcile(IRecipeManager manager, RecipeType<T> type,
            List<RegisteredRecipe<T>> previous, List<T> fresh, Function<T, DisplayKey> key) {
        Map<ResourceLocation, RegisteredRecipe<T>> remaining = new LinkedHashMap<>();
        previous.forEach(recipe -> remaining.put(recipe.key().id(), recipe));
        Set<ResourceLocation> seen = new HashSet<>();
        List<RegisteredRecipe<T>> registered = new ArrayList<>();
        List<T> added = new ArrayList<>(), removed = new ArrayList<>();
        for (T recipe : fresh) {
            DisplayKey snapshot = key.apply(recipe);
            if (!seen.add(snapshot.id())) throw new IllegalArgumentException("Duplicate Ars JEI recipe: " + snapshot.id());
            RegisteredRecipe<T> old = remaining.remove(snapshot.id());
            if (old != null && old.key().same(snapshot)) {
                registered.add(old);
            } else {
                if (old != null) removed.add(old.recipe());
                registered.add(new RegisteredRecipe<>(recipe, snapshot));
                added.add(recipe);
            }
        }
        remaining.values().forEach(old -> removed.add(old.recipe()));
        if (!removed.isEmpty()) manager.hideRecipes(type, removed);
        if (!added.isEmpty()) {
            manager.addRecipes(type, added);
            manager.unhideRecipes(type, added);
        }
        return List.copyOf(registered);
    }

    private static List<CatalystIdentifierJeiData> freezeCatalysts(List<CatalystIdentifierJeiData> recipes) {
        return recipes.stream().map(recipe -> new CatalystIdentifierJeiData(recipe.id(), List.copyOf(recipe.catalysts()),
                List.copyOf(recipe.recipeIds()), recipe.output().copy())).toList();
    }

    private static List<IdentifierImbuementJeiData> freezeImbuement(List<IdentifierImbuementJeiData> recipes) {
        return recipes.stream().map(recipe -> new IdentifierImbuementJeiData(recipe.id(), recipe.input(),
                recipe.identifier().copy(), recipe.output().copy(), recipe.sourceCost())).toList();
    }

    private static DisplayKey catalystKey(CatalystIdentifierJeiData recipe) {
        return new DisplayKey(recipe.id(), recipe.catalysts().stream().map(ArsNouveauJeiIntegration::choices).toList(),
                List.copyOf(recipe.recipeIds()), ItemStack.EMPTY, recipe.output().copy(), 0);
    }

    private static DisplayKey imbuementKey(IdentifierImbuementJeiData recipe) {
        return new DisplayKey(recipe.id(), List.of(choices(recipe.input())), List.of(), recipe.identifier().copy(),
                recipe.output().copy(), recipe.sourceCost());
    }

    private static List<ItemStack> choices(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems()).map(ItemStack::copy).toList();
    }

    private record RegisteredRecipe<T>(T recipe, DisplayKey key) { }

    /** Copy resolved choices and full item components so tag/component-only reloads are visible. */
    private record DisplayKey(ResourceLocation id, List<List<ItemStack>> ingredients, List<ResourceLocation> recipeIds,
                              ItemStack identifier, ItemStack output, int sourceCost) {
        private boolean same(DisplayKey other) {
            if (!id.equals(other.id) || !recipeIds.equals(other.recipeIds) || sourceCost != other.sourceCost
                    || !ItemStack.matches(identifier, other.identifier) || !ItemStack.matches(output, other.output)
                    || ingredients.size() != other.ingredients.size()) return false;
            for (int index = 0; index < ingredients.size(); index++) {
                if (!ItemStack.listMatches(ingredients.get(index), other.ingredients.get(index))) return false;
            }
            return true;
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
                APPARATUS_RECIPE_TYPE);
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
