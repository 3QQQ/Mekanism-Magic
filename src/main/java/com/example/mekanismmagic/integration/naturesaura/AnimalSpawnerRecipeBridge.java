package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.BoundedSlotMatcher;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import de.ellpeck.naturesaura.Helper;
import de.ellpeck.naturesaura.recipes.AnimalSpawnerRecipe;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/** 41.9 describes one entity without result NBT; the machine represents that fixed type as one spawn egg. */
public final class AnimalSpawnerRecipeBridge {
    public static final int INPUT_SLOT_COUNT = 6;
    private static final Map<RecipeManager, Catalog> CATALOGS = new WeakHashMap<>();

    private AnimalSpawnerRecipeBridge() {
    }

    public static List<AnimalSpawnerRecipeView> readRecipes(RecipeManager manager) {
        if (manager == null) {
            return List.of();
        }
        List<AnimalSpawnerRecipeView> recipes = new ArrayList<>();
        for (var holder : manager.getAllRecipesFor(ModRecipes.ANIMAL_SPAWNER_TYPE)) {
            AnimalSpawnerRecipe recipe = holder.value();
            if (!AnimalSpawnEggs.supported(recipe.entity) || recipe.ingredients == null
                    || recipe.ingredients.isEmpty() || recipe.ingredients.size() > INPUT_SLOT_COUNT
                    || recipe.aura < 0 || recipe.time <= 0
                    || recipe.ingredients.stream().anyMatch(ingredient -> ingredient == null
                    || ingredient.isEmpty() || Helper.getIngredientAmount(ingredient) <= 0)) {
                continue;
            }
            recipes.add(new AnimalSpawnerRecipeView(holder.id(), recipe.entity,
                    recipe.ingredients, recipe.ingredients.stream()
                    .map(Helper::getIngredientAmount).toList(), recipe.aura, recipe.time));
        }
        recipes.sort(Comparator.comparing(view -> view.id().toString()));
        return List.copyOf(recipes);
    }

    public static synchronized List<AnimalSpawnerRecipeView> scan(RecipeManager manager) {
        if (manager == null) {
            return List.of();
        }
        long version = NatureAuraRecipeScanner.version(manager);
        Catalog catalog = CATALOGS.get(manager);
        if (catalog == null || catalog.version() != version) {
            catalog = new Catalog(version, readRecipes(manager));
            CATALOGS.put(manager, catalog);
        }
        return catalog.recipes();
    }

    static boolean isInput(Level level, ItemStack stack) {
        return stack != null && !stack.isEmpty() && (level == null
                || scan(level.getRecipeManager()).stream().anyMatch(recipe ->
                recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))));
    }

    public static List<AnimalSpawnerRecipeView> matching(RecipeManager manager,
                                                         List<ItemStack> stacks) {
        return scan(manager).stream().filter(recipe -> recipe.match(stacks).isPresent()).toList();
    }

    public record AnimalSpawnerRecipeView(ResourceLocation id, EntityType<?> entityType,
                                         List<Ingredient> ingredients, List<Integer> inputAmounts,
                                         int auraCost, int time, String signature) {
        public AnimalSpawnerRecipeView(ResourceLocation id, EntityType<?> type,
                                       List<Ingredient> ingredients, List<Integer> amounts,
                                       int aura, int time) {
            this(id, type, ingredients, amounts, aura, time, "");
        }

        public AnimalSpawnerRecipeView {
            if (id == null || entityType == null || ingredients == null || inputAmounts == null
                    || ingredients.isEmpty() || ingredients.size() > INPUT_SLOT_COUNT
                    || ingredients.size() != inputAmounts.size() || auraCost < 0 || time <= 0
                    || ingredients.stream().anyMatch(ingredient -> ingredient == null || ingredient.isEmpty())
                    || inputAmounts.stream().anyMatch(amount -> amount == null || amount <= 0)) {
                throw new IllegalArgumentException("Invalid animal spawner recipe");
            }
            ingredients = List.copyOf(ingredients);
            inputAmounts = List.copyOf(inputAmounts);
            signature = signatureOf(entityType, ingredients, inputAmounts, auraCost, time);
        }

        public ResourceLocation entityId() {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        }

        public ItemStack outputEgg() { return AnimalSpawnEggs.forType(entityType); }

        /** One occupied slot per native dropped ingredient stack; extra stock stays in place. */
        public Optional<List<InputUse>> match(List<ItemStack> stacks) {
            if (stacks == null || stacks.size() > INPUT_SLOT_COUNT
                    || stacks.stream().filter(stack -> stack != null && !stack.isEmpty()).count()
                    != ingredients.size()) {
                return Optional.empty();
            }
            boolean[][] compatibility = new boolean[ingredients.size()][stacks.size()];
            int[] capacities = new int[stacks.size()];
            for (int slot = 0; slot < stacks.size(); slot++) {
                ItemStack stack = stacks.get(slot);
                if (stack != null && !stack.isEmpty()) {
                    capacities[slot] = 1;
                    for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
                        compatibility[ingredient][slot] = stack.getCount() >= inputAmounts.get(ingredient)
                                && ingredients.get(ingredient).test(stack);
                    }
                }
            }
            var assignment = BoundedSlotMatcher.assign(compatibility, capacities);
            if (assignment.isEmpty()) {
                return Optional.empty();
            }
            int[] assigned = assignment.get().ingredientSlots();
            List<InputUse> uses = new ArrayList<>(assigned.length);
            for (int ingredient = 0; ingredient < assigned.length; ingredient++) {
                uses.add(new InputUse(assigned[ingredient], inputAmounts.get(ingredient)));
            }
            return Optional.of(List.copyOf(uses));
        }

        public MachineRecipeResult toMachineRecipe(List<InputUse> inputs) {
            return new MachineRecipeResult(id, outputEgg(), time, inputs, -1, -1,
                    null, null, Map.of(NatureAuraMachineConfig.AURA_RESOURCE, auraCost));
        }
    }

    private static String signatureOf(EntityType<?> type, List<Ingredient> ingredients,
                                      List<Integer> amounts, int aura, int time) {
        StringBuilder value = new StringBuilder(BuiltInRegistries.ENTITY_TYPE.getKey(type)
                + "|aura=" + aura + "|time=" + time);
        for (int index = 0; index < ingredients.size(); index++) {
            value.append("|ingredient=").append(Arrays.stream(ingredients.get(index).getItems())
                    .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()) + "@"
                            + stack.getCount() + "|" + stack.getComponentsPatch())
                    .sorted().collect(Collectors.joining(",")))
                    .append("@amount=").append(amounts.get(index));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Catalog(long version, List<AnimalSpawnerRecipeView> recipes) {
    }
}
