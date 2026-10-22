package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.registry.PerkRegistry;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ArmorUpgradeRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantmentRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ReactiveEnchantmentRecipe;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Shared exact-input assembly for the apparatus JEI view and AE patterns. */
public final class ArsApparatusRecipeViews {
    private ArsApparatusRecipeViews() {
    }

    public static List<ArsApparatusJeiData> recipes(Level level) {
        if (level == null) {
            return List.of();
        }
        return ArsNouveauAPI.getInstance().getEnchantingApparatusRecipes(level)
                .stream()
                .filter(holder -> pedestalIngredients(holder.value()).size()
                        <= EnchantingApparatusProcessorBlockEntity.PEDESTAL_SLOT_COUNT)
                .map(holder -> new ArsApparatusJeiData(holder.id(), holder.value()))
                .toList();
    }

    /** Prevents an open JEI page from encoding a recipe replaced by a reload. */
    public static boolean isCurrent(Level level, ArsApparatusJeiData view) {
        return currentRecipe(level, view).filter(recipe -> recipe == view.recipe())
                .isPresent();
    }

    /**
     * The first input is the reagent; every remaining entry is one pedestal
     * ingredient. Entries must each contain exactly one item, as displayed in
     * the machine's JEI slots. Resolve the current recipe by ID on every call;
     * no output, component patch, or recipe-manager cache survives a reload.
     */
    public static Optional<ItemStack> resolveExact(
            Level level, ArsApparatusJeiData view, List<ItemStack> exactInputs) {
        return currentRecipe(level, view)
                .flatMap(recipe -> assembleExact(level, recipe, exactInputs))
                .filter(output -> matchesMachineSelection(level, view,
                        exactInputs, output));
    }

    public static Optional<ItemStack> resolveExact(
            Level level, RecipeHolder<? extends IEnchantingRecipe> holder,
            List<ItemStack> exactInputs) {
        return resolveExact(level, new ArsApparatusJeiData(holder.id(),
                holder.value()), exactInputs);
    }

    /**
     * Uses Ars' own matching and assembly rules for ordinary apparatus recipes,
     * enchantments, armor tiers and addon implementations. In particular, armor
     * assembly mutates its input stack, so both matching and assembly receive
     * fresh copies. Never replace the result with getResultItem or strip data.
     */
    public static Optional<ItemStack> assembleExact(
            Level level, IEnchantingRecipe recipe, List<ItemStack> exactInputs) {
        if (level == null || exactInputs == null
                || exactInputs.size() != pedestalIngredients(recipe).size() + 1
                || exactInputs.size() > 9
                || exactInputs.stream().anyMatch(stack -> stack == null
                || stack.isEmpty() || stack.getCount() != 1)) {
            return Optional.empty();
        }
        try {
            if (!recipe.matches(copyInput(exactInputs), level)) {
                return Optional.empty();
            }
            ItemStack output = recipe.assemble(copyInput(exactInputs),
                    level.registryAccess());
            return output.isEmpty() ? Optional.empty() : Optional.of(output.copy());
        } catch (RuntimeException unsupportedRecipeContext) {
            // Addon recipes may require the physical apparatus/player context.
            // A static placeholder would encode an output we cannot promise.
            return Optional.empty();
        }
    }

    public static List<Ingredient> pedestalIngredients(IEnchantingRecipe recipe) {
        return recipe instanceof EnchantingApparatusRecipe apparatus
                ? List.copyOf(apparatus.pedestalItems())
                : List.copyOf(recipe.getIngredients());
    }

    /**
     * Validate the complete unordered declaration with the machine's own
     * lookup, including exact components and full consumption of every input.
     * This also rejects an old static JEI output after input ghosts are edited.
     */
    public static boolean matchesPattern(Level level, List<PatternStack> inputs,
                                         List<PatternStack> outputs) {
        if (level == null || inputs == null || inputs.isEmpty()
                || inputs.size() > 9 || outputs == null || outputs.size() != 1) {
            return false;
        }
        ItemStackHandler inventory = new ItemStackHandler(9);
        int total = 0;
        for (int index = 0; index < inputs.size(); index++) {
            PatternStack input = inputs.get(index);
            if (input == null || input.stack().isEmpty()
                    || input.amount() <= 0 || input.amount() > 9) {
                return false;
            }
            total += (int) input.amount();
            if (total > 9) {
                return false;
            }
            inventory.setStackInSlot(index,
                    input.stack().copyWithCount((int) input.amount()));
        }
        PatternStack declaredOutput = outputs.getFirst();
        if (declaredOutput == null || declaredOutput.stack().isEmpty()
                || declaredOutput.amount() <= 0) {
            return false;
        }
        try {
            return ArsNouveauRecipeBridge.findApparatusRecipe(level, inventory,
                    0, inventory.getSlots()).filter(result -> {
                        int[] consumed = new int[inventory.getSlots()];
                        for (InputUse use : result.inputs()) {
                            if (use.slot() < 0 || use.slot() >= consumed.length
                                    || use.count() <= 0) {
                                return false;
                            }
                            consumed[use.slot()] += use.count();
                        }
                        for (int slot = 0; slot < consumed.length; slot++) {
                            if (consumed[slot]
                                    != inventory.getStackInSlot(slot).getCount()) {
                                return false;
                            }
                        }
                        return declaredOutput.amount() == result.output().getCount()
                                && ItemStack.isSameItemSameComponents(
                                declaredOutput.stack(), result.output());
                    }).isPresent();
        } catch (RuntimeException unsupportedRecipeContext) {
            return false;
        }
    }


    /** Concrete input selection shared by JEI and runtime focus regression tests. */
    public record InputSelection(List<ItemStack> reagents, List<ItemStack> pedestals, boolean valid) {
    }

    public static InputSelection selectInputs(Level level, IEnchantingRecipe recipe,
                                               List<ItemStack> focusedInputs) {
        List<Ingredient> ingredients = pedestalIngredients(recipe);
        List<ItemStack> pedestals = new ArrayList<>(ingredients.stream()
                .map(ingredient -> Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                        .filter(ingredient::test).findFirst().map(stack -> stack.copyWithCount(1))
                        .orElse(ItemStack.EMPTY)).toList());
        List<ItemStack> reagents = new ArrayList<>();
        boolean[] assigned = new boolean[ingredients.size()];
        boolean valid = ingredients.size() <= 8 && focusedInputs.size() <= 9;
        for (ItemStack focus : focusedInputs) {
            if (focus == null || focus.isEmpty()) continue;
            boolean pedestalFocus = ingredients.stream().anyMatch(ingredient -> ingredient.test(focus))
                    && (recipe instanceof ReactiveEnchantmentRecipe || !isReagentType(level, recipe, focus));
            if (!pedestalFocus) {
                reagents.add(focus.copyWithCount(1));
                continue;
            }
            int target = -1;
            boolean duplicate = false;
            for (int index = 0; index < ingredients.size(); index++) {
                if (!ingredients.get(index).test(focus)) continue;
                if (!assigned[index] && target < 0) target = index;
                if (assigned[index] && ItemStack.isSameItemSameComponents(pedestals.get(index), focus)) {
                    duplicate = true;
                }
            }
            if (duplicate) continue;
            if (target < 0) {
                valid = false; // Multiple different component values for one pedestal are ambiguous.
            } else {
                pedestals.set(target, focus.copyWithCount(1));
                assigned[target] = true;
            }
        }
        if (reagents.isEmpty()) reagents.addAll(defaultReagents(level, recipe));
        return new InputSelection(List.copyOf(reagents), List.copyOf(pedestals), valid);
    }

    /** Use the same exact assembly as the JEI page before exposing it in a lookup. */
    public static boolean hasResolvedPreview(Level level, ArsApparatusJeiData view,
                                             List<ItemStack> focusedInputs) {
        if (level == null || view == null || focusedInputs == null
                || !isCurrent(level, view)) {
            return false;
        }
        InputSelection selection = selectInputs(level, view.recipe(), focusedInputs);
        if (!selection.valid()) {
            return false;
        }
        for (ItemStack reagent : selection.reagents()) {
            List<ItemStack> exactInputs = new ArrayList<>(selection.pedestals().size() + 1);
            exactInputs.add(reagent.copyWithCount(1));
            exactInputs.addAll(selection.pedestals());
            // Most higher-tier defaults fail Ars' own match; avoid a full
            // machine lookup for each one while JEI builds its recipe index.
            if (assembleExact(level, view.recipe(), exactInputs).isPresent()
                    && resolveExact(level, view, exactInputs).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isReagentType(Level level, IEnchantingRecipe recipe, ItemStack focus) {
        if (recipe instanceof ArmorUpgradeRecipe) {
            return focus.has(DataComponentRegistry.ARMOR_PERKS.get())
                    || PerkRegistry.getPerkProviderItems().contains(focus.getItem());
        }
        if (recipe instanceof EnchantmentRecipe enchantment) {
            return focus.is(Items.BOOK) || focus.is(Items.ENCHANTED_BOOK)
                    || level != null && enchantment.holderFor(level).value().canEnchant(focus);
        }
        if (recipe instanceof EnchantingApparatusRecipe apparatus) {
            return apparatus.reagent().test(focus) || Arrays.stream(apparatus.reagent().getItems())
                    .anyMatch(candidate -> ItemStack.isSameItem(candidate, focus));
        }
        return true;
    }

    /** Bounded unmodified defaults; never invent enchantments or armor tiers. */
    public static List<ItemStack> defaultReagents(Level level,
                                                 IEnchantingRecipe recipe) {
        if (recipe instanceof ArmorUpgradeRecipe) {
            return PerkRegistry.getPerkProviderItems().stream()
                    .limit(16)
                    .map(Item::getDefaultInstance).map(ItemStack::copy).toList();
        }
        if (recipe instanceof EnchantmentRecipe enchantment && level != null) {
            var holder = enchantment.holderFor(level);
            return List.of(Items.BOOK, Items.ENCHANTED_BOOK,
                            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE,
                            Items.IRON_SHOVEL, Items.IRON_HOE, Items.BOW,
                            Items.CROSSBOW, Items.TRIDENT, Items.FISHING_ROD,
                            Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                            Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.ELYTRA)
                    .stream()
                    .filter(item -> item == Items.BOOK || item == Items.ENCHANTED_BOOK
                            || holder.value().canEnchant(item.getDefaultInstance()))
                    .map(Item::getDefaultInstance).map(ItemStack::copy).toList();
        }
        if (recipe instanceof EnchantingApparatusRecipe apparatus) {
            return Arrays.stream(apparatus.reagent().getItems())
                    .filter(stack -> !stack.isEmpty())
                    .limit(16)
                    .map(stack -> stack.copyWithCount(1)).toList();
        }
        return List.of();
    }

    private static Optional<IEnchantingRecipe> currentRecipe(
            Level level, ArsApparatusJeiData view) {
        if (level == null || view == null) {
            return Optional.empty();
        }
        return ArsNouveauAPI.getInstance().getEnchantingApparatusRecipes(level)
                .stream().filter(holder -> holder.id().equals(view.id()))
                .map(holder -> (IEnchantingRecipe) holder.value()).findFirst();
    }

    private static ApparatusRecipeInput copyInput(List<ItemStack> exactInputs) {
        List<ItemStack> pedestals = new ArrayList<>(exactInputs.size() - 1);
        for (int index = 1; index < exactInputs.size(); index++) {
            pedestals.add(exactInputs.get(index).copy());
        }
        return new ApparatusRecipeInput(exactInputs.getFirst().copy(), pedestals,
                null);
    }

    private static boolean matchesMachineSelection(
            Level level, ArsApparatusJeiData view, List<ItemStack> exactInputs,
            ItemStack expected) {
        ItemStackHandler inventory = new ItemStackHandler(9);
        for (int index = 0; index < exactInputs.size(); index++) {
            inventory.setStackInSlot(index, exactInputs.get(index).copy());
        }
        try {
            return ArsNouveauRecipeBridge.findApparatusRecipe(level, inventory,
                    0, inventory.getSlots()).filter(result ->
                    result.id().equals(view.id())
                            && result.output().getCount() == expected.getCount()
                            && ItemStack.isSameItemSameComponents(
                            result.output(), expected)).isPresent();
        } catch (RuntimeException unsupportedRecipeContext) {
            return false;
        }
    }
}
