package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.common.recipe.BoundedSlotMatcher;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
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
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/** 41.9's eight single-item stands, distinct sapling, output components and time. */
public final class TreeRitualRecipeBridge {
    public static final int MATERIAL_SLOTS = 8;
    public static final int GOLD_POWDER_PER_OPERATION = 16;
    private static final Map<RecipeManager, Catalog> CATALOGS = new WeakHashMap<>();

    private TreeRitualRecipeBridge() {
    }

    public static List<TreeRitualRecipeView> readRecipes(RecipeManager manager) {
        if (manager == null) {
            return List.of();
        }
        List<RecipeHolder<TreeRitualRecipe>> recipes = new ArrayList<>(
                manager.getAllRecipesFor(ModRecipes.TREE_RITUAL_TYPE));
        recipes.sort(Comparator.comparing(holder -> holder.id().toString()));
        List<TreeRitualRecipeView> result = new ArrayList<>();
        for (var holder : recipes) {
            var recipe = holder.value();
            if (recipe.saplingType == null || recipe.saplingType.isEmpty()
                    || recipe.output == null || recipe.output.isEmpty()
                    || recipe.time <= 0 || recipe.ingredients == null
                    || recipe.ingredients.isEmpty()
                    || recipe.ingredients.size() > MATERIAL_SLOTS
                    || recipe.ingredients.stream().anyMatch(
                    ingredient -> ingredient == null || ingredient.isEmpty())) {
                continue;
            }
            result.add(new TreeRitualRecipeView(holder.id(), recipe.saplingType,
                    recipe.ingredients, recipe.output, recipe.time));
        }
        return List.copyOf(result);
    }

    public static synchronized List<TreeRitualRecipeView> scan(RecipeManager manager) {
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

    static boolean isMaterial(Level level, ItemStack stack) {
        return stack != null && !stack.isEmpty() && (level == null
                || scan(level.getRecipeManager()).stream().anyMatch(recipe ->
                recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))));
    }

    static boolean isSapling(Level level, ItemStack stack) {
        return stack != null && !stack.isEmpty() && (level == null
                || scan(level.getRecipeManager()).stream().anyMatch(
                recipe -> recipe.sapling().test(stack)));
    }

    public static Optional<Match> findRecipe(Level level, List<ItemStack> materials,
                                             ItemStack sapling) {
        if (level == null || sapling == null || sapling.isEmpty()) {
            return Optional.empty();
        }
        for (var recipe : scan(level.getRecipeManager())) {
            var uses = recipe.matchMaterials(materials, sapling);
            if (uses.isPresent()) {
                return Optional.of(new Match(recipe, uses.get()));
            }
        }
        return Optional.empty();
    }

    public record Match(TreeRitualRecipeView recipe, List<InputUse> inputs) {
        public Match {
            inputs = List.copyOf(inputs);
        }

        /** One internal operation consumes the recipe materials, one sapling and the original 16 gold powder. */
        public MachineRecipeResult machineRecipe() {
            List<InputUse> allInputs = new ArrayList<>(inputs);
            allInputs.add(new InputUse(TreeRitualProcessorBlockEntity.SAPLING_SLOT, 1));
            allInputs.add(new InputUse(TreeRitualProcessorBlockEntity.GOLD_POWDER_SLOT, GOLD_POWDER_PER_OPERATION));
            return new MachineRecipeResult(recipe.id(), recipe.output(),
                    recipe.time(), allInputs, -1);
        }
    }

    public record TreeRitualRecipeView(ResourceLocation id, Ingredient sapling,
                                       List<Ingredient> ingredients, ItemStack output,
                                       int time, String signature) {
        public TreeRitualRecipeView(ResourceLocation id, Ingredient sapling,
                                   List<Ingredient> ingredients, ItemStack output,
                                   int time) {
            this(id, sapling, ingredients, output, time, "");
        }

        public TreeRitualRecipeView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sapling, "sapling");
            Objects.requireNonNull(output, "output");
            ingredients = List.copyOf(ingredients);
            if (sapling.isEmpty() || output.isEmpty() || time <= 0
                    || ingredients.isEmpty() || ingredients.size() > MATERIAL_SLOTS
                    || ingredients.stream().anyMatch(Ingredient::isEmpty)) {
                throw new IllegalArgumentException("Invalid tree ritual recipe");
            }
            output = output.copy();
            signature = signatureOf(sapling, ingredients, output, time);
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        public Optional<List<InputUse>> matchMaterials(List<ItemStack> materials,
                                                       ItemStack saplingStack) {
            if (materials == null || materials.size() > MATERIAL_SLOTS
                    || saplingStack == null || saplingStack.isEmpty()
                    || !sapling.test(saplingStack)) {
                return Optional.empty();
            }
            boolean[][] compatible = new boolean[ingredients.size()][materials.size()];
            int[] capacities = new int[materials.size()];
            for (int slot = 0; slot < materials.size(); slot++) {
                ItemStack stack = materials.get(slot);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                capacities[slot] = stack.getCount();
                for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
                    compatible[ingredient][slot] = ingredients.get(ingredient).test(stack);
                }
            }
            return BoundedSlotMatcher.match(compatible, capacities).map(consumed -> {
                List<InputUse> uses = new ArrayList<>();
                for (int slot = 0; slot < consumed.length; slot++) {
                    if (consumed[slot] > 0) {
                        uses.add(new InputUse(slot, consumed[slot]));
                    }
                }
                return List.copyOf(uses);
            });
        }
    }

    private static String signatureOf(Ingredient sapling, List<Ingredient> ingredients,
                                      ItemStack output, int time) {
        String value = ingredientSignature(sapling) + "|" + ingredients.stream()
                .map(TreeRitualRecipeBridge::ingredientSignature).sorted()
                .collect(Collectors.joining(";")) + "|" + stackSignature(output) + "|" + time;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String ingredientSignature(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems()).map(TreeRitualRecipeBridge::stackSignature)
                .sorted().collect(Collectors.joining(","));
    }

    private static String stackSignature(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "@" + stack.getCount()
                + "|" + stack.getComponentsPatch();
    }

    private record Catalog(long version, List<TreeRitualRecipeView> recipes) {
    }
}
