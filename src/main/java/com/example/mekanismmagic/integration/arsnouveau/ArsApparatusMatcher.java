package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.BoundedSlotMatcher;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Complete, bounded and component-aware matching for the nine unordered inputs. */
final class ArsApparatusMatcher {
    private static final int MAX_SEARCH_NODES = 8192;

    private ArsApparatusMatcher() {
    }

    static Optional<MachineRecipeResult> find(Level level, ItemStackHandler inventory,
                                               int start, int count) {
        if (level == null || start < 0 || count <= 0 || count > 9
                || start + count > inventory.getSlots()) {
            return Optional.empty();
        }
        List<Group> groups = groups(inventory, start, count);
        if (groups.isEmpty()) return Optional.empty();
        List<RecipeHolder<? extends IEnchantingRecipe>> recipes = new ArrayList<>();
        ArsNouveauAPI.getInstance().getEnchantingApparatusRecipes(level).forEach(recipes::add);
        recipes.removeIf(holder -> ArsApparatusRecipeViews.pedestalIngredients(holder.value()).size() > 8);
        recipes.sort(Comparator.<RecipeHolder<? extends IEnchantingRecipe>>comparingInt(
                holder -> ArsApparatusRecipeViews.pedestalIngredients(holder.value()).size()).reversed()
                .thenComparing(holder -> holder.id().toString()));
        MachineRecipeResult selected = null;
        int selectedUnits = -1;
        boolean ambiguous = false;
        for (var holder : recipes) {
            List<Ingredient> ingredients = ArsApparatusRecipeViews.pedestalIngredients(holder.value());
            int units = ingredients.size() + 1;
            if (selected != null && units < selectedUnits) break;
            if (groups.size() > units) continue;
            Search search;
            try {
                search = new Search(level, inventory, holder, ingredients, groups);
                search.run();
            } catch (RuntimeException unsupportedContext) {
                return Optional.empty();
            }
            // An incomplete search cannot prove a unique result.
            if (search.exhausted) return Optional.empty();
            if (search.selected == null) continue;
            if (selected == null) {
                selected = search.selected;
                selectedUnits = units;
                ambiguous = search.ambiguous;
            } else {
                // Different equally-specific recipes are not interchangeable,
                // even if one happens to have the same displayed result.
                ambiguous = true;
            }
        }
        return selected == null || ambiguous ? Optional.empty() : Optional.of(selected);
    }

    private static List<Group> groups(ItemStackHandler inventory, int start, int count) {
        List<Group> groups = new ArrayList<>();
        for (int slot = start; slot < start + count; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            Group group = groups.stream().filter(candidate ->
                    ItemStack.isSameItemSameComponents(candidate.stack, stack)).findFirst().orElse(null);
            if (group == null) {
                group = new Group(stack.copyWithCount(1));
                groups.add(group);
            }
            group.slots.add(slot);
            group.capacity = Math.min(9, group.capacity + Math.min(9, stack.getCount()));
        }
        return groups;
    }

    private static final class Group {
        final ItemStack stack;
        final List<Integer> slots = new ArrayList<>();
        int capacity;
        Group(ItemStack stack) { this.stack = stack; }
    }

    private static final class Search {
        final Level level;
        final ItemStackHandler inventory;
        final RecipeHolder<? extends IEnchantingRecipe> holder;
        final List<Ingredient> ingredients;
        final List<Group> groups;
        final boolean[][] compatible;
        final int[] remaining;
        final int[] used;
        final int[] assignment;
        int reagent;
        int nodes;
        boolean exhausted;
        MachineRecipeResult selected;
        boolean selectedHasComponents;
        boolean ambiguous;

        Search(Level level, ItemStackHandler inventory, RecipeHolder<? extends IEnchantingRecipe> holder,
               List<Ingredient> ingredients, List<Group> groups) {
            this.level = level;
            this.inventory = inventory;
            this.holder = holder;
            this.ingredients = ingredients;
            this.groups = groups;
            remaining = new int[groups.size()];
            used = new int[groups.size()];
            assignment = new int[ingredients.size()];
            compatible = new boolean[ingredients.size()][groups.size()];
            for (int row = 0; row < ingredients.size(); row++) {
                for (int group = 0; group < groups.size(); group++) {
                    compatible[row][group] = ingredients.get(row).test(groups.get(group).stack);
                }
            }
        }

        void run() {
            for (reagent = 0; reagent < groups.size() && !exhausted; reagent++) {
                for (int group = 0; group < groups.size(); group++) {
                    remaining[group] = groups.get(group).capacity - (group == reagent ? 1 : 0);
                    used[group] = group == reagent ? 1 : 0;
                }
                // Cheap feasibility check before considering native, component-sensitive rules.
                if (BoundedSlotMatcher.match(compatible, remaining).isPresent()) visit(0);
            }
        }

        void visit(int row) {
            if (++nodes > MAX_SEARCH_NODES) {
                exhausted = true;
                return;
            }
            if (row == ingredients.size()) {
                for (int amount : used) {
                    if (amount == 0) return; // Do not craft a material subset.
                }
                List<ItemStack> exact = new ArrayList<>();
                exact.add(groups.get(reagent).stack.copy());
                for (int group : assignment) exact.add(groups.get(group).stack.copy());
                ArsApparatusRecipeViews.assembleExact(level, holder.value(), exact).ifPresent(this::accept);
                return;
            }
            int minimum = 0;
            for (int earlier = row - 1; earlier >= 0; earlier--) {
                if (ingredients.get(row).equals(ingredients.get(earlier))) {
                    minimum = assignment[earlier];
                    break;
                }
            }
            for (int group = minimum; group < groups.size() && !exhausted; group++) {
                if (remaining[group] <= 0 || !compatible[row][group]) continue;
                remaining[group]--;
                used[group]++;
                assignment[row] = group;
                visit(row + 1);
                used[group]--;
                remaining[group]++;
            }
        }

        void accept(ItemStack output) {
            List<InputUse> inputs = new ArrayList<>();
            for (int group = 0; group < groups.size(); group++) {
                int needed = used[group];
                for (int slot : groups.get(group).slots) {
                    int taken = Math.min(needed, inventory.getStackInSlot(slot).getCount());
                    if (taken > 0) inputs.add(new InputUse(slot, taken));
                    needed -= taken;
                    if (needed == 0) break;
                }
            }
            inputs.sort(Comparator.comparingInt(InputUse::slot));
            MachineRecipeResult candidate = new MachineRecipeResult(holder.id(), output.copy(),
                    ArsNouveauMachineConfig.APPARATUS_DURATION, List.copyOf(inputs), -1, -1, null, null,
                    Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                            ArsNouveauMachineConfig.apparatusSourceCost(holder.value().sourceCost())));
            boolean hasComponents = !groups.get(reagent).stack.getComponentsPatch().isEmpty();
            if (selected == null || hasComponents && !selectedHasComponents) {
                selected = candidate;
                selectedHasComponents = hasComponents;
                ambiguous = false;
            } else if (hasComponents == selectedHasComponents
                    && !ItemStack.matches(selected.output(), output)) {
                ambiguous = true;
            }
        }
    }
}
