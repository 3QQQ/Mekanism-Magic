package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Version-specific boundary for Nature's Aura 41.9 recipe implementation
 * classes. Consumers use the immutable local view instead of retaining the
 * optional mod's recipe object.
 */
public final class NatureAuraRecipeBridge {
    private NatureAuraRecipeBridge() {
    }

    static List<AltarRecipeView> readAltarRecipes(RecipeManager manager) {
        if (manager == null) {
            return List.of();
        }
        List<RecipeHolder<AltarRecipe>> recipes = new ArrayList<>(
                manager.getAllRecipesFor(ModRecipes.ALTAR_TYPE));
        recipes.sort(Comparator.comparing(holder -> holder.id().toString()));
        List<AltarRecipeView> views = new ArrayList<>(recipes.size());
        for (RecipeHolder<AltarRecipe> holder : recipes) {
            AltarRecipe recipe = holder.value();
            Ingredient input = recipe.input == null
                    ? Ingredient.EMPTY : recipe.input;
            Ingredient catalyst = recipe.catalyst == null
                    ? Ingredient.EMPTY : recipe.catalyst;
            ItemStack output = recipe.output == null
                    ? ItemStack.EMPTY : recipe.output.copy();
            if (input.isEmpty() || output.isEmpty()
                    || recipe.aura < 0 || recipe.time <= 0) {
                continue;
            }
            views.add(new AltarRecipeView(holder.id(), input, output,
                    catalyst, recipe.aura, recipe.time, null));
        }
        return List.copyOf(views);
    }

    /**
     * Resolves a compact-machine recipe. A previously locked recipe only
     * revalidates its main input, matching the original altar's behavior
     * after its surrounding catalyst block has selected the recipe.
     */
    public static Optional<AltarRecipeView> findAltarRecipe(
            Level level, ItemStack input, ItemStack catalyst,
            @Nullable ResourceLocation lockedRecipe) {
        return findAltarRecipe(level, input,
                catalyst == null ? List.of() : List.of(catalyst),
                lockedRecipe, null);
    }

    public static Optional<AltarRecipeView> findAltarRecipe(
            Level level, ItemStack input, List<ItemStack> catalysts,
            @Nullable ResourceLocation lockedRecipe,
            @Nullable ResourceLocation preferredRecipe) {
        if (level == null || input == null || input.isEmpty()) {
            return Optional.empty();
        }
        RecipeManager manager = level.getRecipeManager();
        if (lockedRecipe != null) {
            Optional<AltarRecipeView> locked =
                    NatureAuraRecipeScanner.find(manager, lockedRecipe)
                            .filter(recipe -> matchesLockedRecipe(
                                    recipe, input));
            if (locked.isPresent()) {
                return locked;
            }
        }

        return selectUnlockedRecipe(
                NatureAuraRecipeScanner.scan(manager), input, catalysts,
                preferredRecipe);
    }

    /** All currently accessible recipes, including explicit alternatives. */
    public static List<AltarRecipeView> matchingAltarRecipes(
            Level level, ItemStack input, List<ItemStack> catalysts) {
        if (level == null || input == null || input.isEmpty()) {
            return List.of();
        }
        return NatureAuraRecipeScanner.scan(level.getRecipeManager())
                .stream().filter(recipe -> recipe.input().test(input)
                        && (!recipe.requiresCatalyst()
                        || hasMatchingCatalyst(recipe, catalysts)))
                .toList();
    }

    public static boolean hasMatchingCatalyst(
            AltarRecipeView recipe, List<ItemStack> catalysts) {
        return recipe != null && catalysts != null
                && (!recipe.catalyst().isSimple()
                || !recipe.catalyst().hasNoItems())
                && catalysts.stream().anyMatch(stack -> stack != null
                        && !stack.isEmpty()
                        && recipe.catalyst().test(stack));
    }

    public static boolean isAltarInput(Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemStack tested = hasPatternRecipeMarker(stack)
                ? clearPatternRecipeMarker(stack) : stack;
        return level == null || NatureAuraRecipeScanner.scan(
                level.getRecipeManager()).stream()
                .anyMatch(recipe -> recipe.input().test(tested));
    }

    public static boolean isAltarCatalyst(Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return level == null || NatureAuraRecipeScanner.scan(
                level.getRecipeManager()).stream()
                .filter(AltarRecipeView::requiresCatalyst)
                .anyMatch(recipe -> (!recipe.catalyst().isSimple()
                        || !recipe.catalyst().hasNoItems())
                        && recipe.catalyst().test(stack));
    }

    public static boolean matchesLockedRecipe(
            AltarRecipeView recipe, ItemStack input) {
        boolean inputMatches = recipe != null && input != null
                && !input.isEmpty() && recipe.input().test(input);
        return NatureAltarSelectionPolicy.lockedRecipeRemainsValid(
                recipe != null, inputMatches);
    }

    static Optional<AltarRecipeView> selectUnlockedRecipe(
            List<AltarRecipeView> recipes, ItemStack input,
            ItemStack catalyst) {
        return selectUnlockedRecipe(recipes, input,
                catalyst == null ? List.of() : List.of(catalyst), null);
    }

    static Optional<AltarRecipeView> selectUnlockedRecipe(
            List<AltarRecipeView> recipes, ItemStack input,
            List<ItemStack> catalysts,
            @Nullable ResourceLocation preferredRecipe) {
        if (recipes == null || input == null || input.isEmpty()) {
            return Optional.empty();
        }
        boolean catalystPresent = catalysts != null
                && catalysts.stream().anyMatch(stack -> stack != null
                && !stack.isEmpty());
        int preferredIndex = -1;
        if (preferredRecipe != null) {
            for (int index = 0; index < recipes.size(); index++) {
                if (preferredRecipe.equals(recipes.get(index).id())) {
                    preferredIndex = index;
                    break;
                }
            }
        }
        int selected = NatureAltarSelectionPolicy.select(recipes.size(),
                catalystPresent,
                index -> recipes.get(index).input().test(input),
                index -> recipes.get(index).requiresCatalyst(),
                index -> hasMatchingCatalyst(recipes.get(index), catalysts),
                preferredIndex);
        return selected < 0 ? Optional.empty()
                : Optional.of(recipes.get(selected));
    }

    static MachineRecipeResult toMachineRecipe(
            AltarRecipeView recipe, int inputSlot) {
        return new MachineRecipeResult(recipe.id(), recipe.output(),
                recipe.duration(), List.of(new InputUse(inputSlot, 1)),
                -1, -1, null, null,
                Map.of(NatureAuraMachineConfig.AURA_RESOURCE,
                        recipe.auraCost()));
    }

    /** Creates the virtual input used to persist this recipe in an AE pattern. */
    public static ItemStack createPatternContext(
            Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return ItemStack.EMPTY;
        }
        Optional<AltarRecipeView> recipe = NatureAuraRecipeScanner.find(
                level.getRecipeManager(), recipeId);
        if (recipe.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack context = new ItemStack(
                NaturesAuraRegistries.NATURE_ALTAR_CONTEXT_ITEM.get());
        CompoundTag data = new CompoundTag();
        data.putString(NatureAltarContextItem.RECIPE_KEY,
                recipeId.toString());
        data.putString(NatureAltarContextItem.RECIPE_SIGNATURE_KEY,
                recipeSignature(recipe.get()));
        representativeChoice(recipe.get().catalyst()).ifPresent(stack ->
                data.putString(NatureAltarContextItem.CATALYST_ITEM_KEY,
                        BuiltInRegistries.ITEM.getKey(stack.getItem())
                                .toString()));
        context.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return context;
    }

    /** Reads a recipe marker from either a context item or a marked input. */
    public static ResourceLocation patternRecipe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        String value = data.getUnsafe().getString(
                NatureAltarContextItem.RECIPE_KEY);
        if (value.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null || parsed.getPath().isBlank()
                ? null : parsed;
    }

    /** True only for the dedicated virtual context item. */
    public static boolean isPatternContext(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.is(NaturesAuraRegistries
                .NATURE_ALTAR_CONTEXT_ITEM.get());
    }

    /**
     * Detects marker presence independently from its value so callers can
     * reject malformed automation input instead of treating it as unmarked.
     */
    public static boolean hasPatternRecipeMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.getUnsafe().contains(
                NatureAltarContextItem.RECIPE_KEY);
    }

    /** Read-only recipe view; avoids copying the overwhelmingly unmarked case. */
    public static ItemStack recipeInputView(ItemStack stack) {
        return hasPatternRecipeMarker(stack)
                ? clearPatternRecipeMarker(stack) : stack;
    }

    /**
     * Legacy low-level writer. Prefer {@link #markPatternInput(ItemStack,
     * ItemStack)} so the current recipe signature is copied as well.
     */
    @Deprecated(forRemoval = false)
    public static ItemStack markPatternInput(
            ItemStack stack, ResourceLocation recipeId) {
        if (stack == null || stack.isEmpty() || recipeId == null) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        ItemStack marked = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, marked, tag ->
                tag.putString(NatureAltarContextItem.RECIPE_KEY,
                        recipeId.toString()));
        return marked;
    }

    /** Copies the validated id and signature from a context onto a real input. */
    public static ItemStack markPatternInput(
            ItemStack stack, ItemStack context) {
        if (stack == null || stack.isEmpty() || !isPatternContext(context)) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        CustomData source = context.get(DataComponents.CUSTOM_DATA);
        if (source == null) {
            return stack.copy();
        }
        CompoundTag sourceTag = source.getUnsafe();
        String recipe = sourceTag.getString(
                NatureAltarContextItem.RECIPE_KEY);
        String signature = sourceTag.getString(
                NatureAltarContextItem.RECIPE_SIGNATURE_KEY);
        if (recipe.isBlank() || signature.isBlank()) {
            return stack.copy();
        }
        ItemStack marked = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, marked, tag -> {
            tag.putString(NatureAltarContextItem.RECIPE_KEY, recipe);
            tag.putString(NatureAltarContextItem.RECIPE_SIGNATURE_KEY,
                    signature);
        });
        return marked;
    }

    /** Returns a copy with all virtual-context metadata removed. */
    public static ItemStack clearPatternRecipeMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        ItemStack cleared = stack.copy();
        CustomData existing = cleared.get(DataComponents.CUSTOM_DATA);
        if (existing == null) {
            return cleared;
        }
        CompoundTag data = existing.copyTag();
        boolean hasContextData = data.contains(
                NatureAltarContextItem.RECIPE_KEY)
                || data.contains(NatureAltarContextItem
                .RECIPE_SIGNATURE_KEY)
                || data.contains(NatureAltarContextItem
                .CATALOG_VERSION_KEY)
                || data.contains(NatureAltarContextItem
                .CATALYST_ITEM_KEY);
        if (!hasContextData) {
            return cleared;
        }
        data.remove(NatureAltarContextItem.RECIPE_KEY);
        data.remove(NatureAltarContextItem.RECIPE_SIGNATURE_KEY);
        data.remove(NatureAltarContextItem.CATALOG_VERSION_KEY);
        data.remove(NatureAltarContextItem.CATALYST_ITEM_KEY);
        if (data.isEmpty()) {
            cleared.remove(DataComponents.CUSTOM_DATA);
        } else {
            cleared.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
        return cleared;
    }

    /**
     * Revalidates a persisted context against the current recipe catalog.
     * Catalog versions are diagnostic only: a data-pack reload keeps a
     * pattern valid when the same recipe id still exists.
     */
    public static boolean patternContextMatchesRecipe(
            Level level, ItemStack context, ResourceLocation recipeId) {
        return isPatternContext(context)
                && patternMarkerMatchesRecipe(level, context, recipeId);
    }

    /** Validates a context marker on either the virtual item or real input. */
    public static boolean patternMarkerMatchesRecipe(
            Level level, ItemStack stack, ResourceLocation recipeId) {
        if (level == null || recipeId == null
                || !hasPatternRecipeMarker(stack)
                || !recipeId.equals(patternRecipe(stack))) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        String storedSignature = data.getUnsafe().getString(
                NatureAltarContextItem.RECIPE_SIGNATURE_KEY);
        return !storedSignature.isBlank()
                && NatureAuraRecipeScanner.find(
                level.getRecipeManager(), recipeId)
                .map(NatureAuraRecipeBridge::recipeSignature)
                .filter(storedSignature::equals)
                .isPresent();
    }

    /** Stable content signature used by JEI subtypes and stale-pattern checks. */
    public static String recipeSignature(AltarRecipeView recipe) {
        return recipe == null ? "" : recipe.signature();
    }

    private static String computeRecipeSignature(
            Ingredient input, ItemStack output, Ingredient catalyst,
            int auraCost, int duration) {
        String payload = ingredientSignature(input) + "->"
                + stackSignature(output) + "|catalyst="
                + ingredientSignature(catalyst) + "|aura="
                + auraCost + "|time=" + duration;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", impossible);
        }
    }

    private static String ingredientSignature(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return "";
        }
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(NatureAuraRecipeBridge::stackSignature)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String stackSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "@" + stack.getCount() + "|"
                + stack.getComponentsPatch();
    }

    public static Optional<ItemStack> representativeChoice(
            Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> stack != null && !stack.isEmpty())
                .min(Comparator.comparing(stack ->
                        BuiltInRegistries.ITEM.getKey(stack.getItem())
                                + "|" + stack.getComponentsPatch()))
                .map(stack -> stack.copyWithCount(1));
    }

    public record AltarRecipeView(
            ResourceLocation id,
            Ingredient input,
            ItemStack output,
            Ingredient catalyst,
            int auraCost,
            int duration,
            String signature) {
        public AltarRecipeView {
            Objects.requireNonNull(id, "id");
            input = input == null ? Ingredient.EMPTY : input;
            output = output == null ? ItemStack.EMPTY : output.copy();
            catalyst = catalyst == null ? Ingredient.EMPTY : catalyst;
            if (auraCost < 0) {
                throw new IllegalArgumentException(
                        "Aura cost cannot be negative");
            }
            if (duration <= 0) {
                throw new IllegalArgumentException(
                        "Recipe duration must be positive");
            }
            signature = computeRecipeSignature(input, output, catalyst,
                    auraCost, duration);
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        public boolean requiresCatalyst() {
            return !catalyst.isEmpty();
        }
    }
}
