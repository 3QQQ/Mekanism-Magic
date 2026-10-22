package com.example.mekanismmagic.integration.naturesaura;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Balances ordinary altar inputs without moving a lane's recipe context.
 * Mekanism's factory sorter only moves item stacks, whereas an altar lane also
 * owns its recipe lock, signature, progress and exact-pattern item count.
 */
public final class NatureAltarFactoryAutoSplit {
    private NatureAltarFactoryAutoSplit() {
    }

    public static boolean balance(Level level,
                                  List<? extends IInventorySlot> inputs,
                                  List<? extends IInventorySlot> outputs,
                                  ItemStack catalyst,
                                  ResourceLocation[] locks,
                                  String[] signatures,
                                  int[] contextRemaining,
                                  int[] progress) {
        return balance(level, inputs, outputs,
                catalyst == null ? List.of() : List.of(catalyst), null,
                locks, signatures, contextRemaining, progress);
    }

    public static boolean balance(Level level,
                                  List<? extends IInventorySlot> inputs,
                                  List<? extends IInventorySlot> outputs,
                                  List<ItemStack> catalysts,
                                  ResourceLocation preferredRecipe,
                                  ResourceLocation[] locks,
                                  String[] signatures,
                                  int[] contextRemaining,
                                  int[] progress) {
        if (level == null || inputs == null || outputs == null
                || locks == null || signatures == null
                || contextRemaining == null || progress == null) {
            return false;
        }
        int lanes = inputs.size();
        if (lanes < 2 || outputs.size() < lanes || locks.length < lanes
                || signatures.length < lanes || contextRemaining.length < lanes
                || progress.length < lanes) {
            return false;
        }
        boolean changed = false;
        boolean[] handled = new boolean[lanes];
        for (int source = 0; source < lanes; source++) {
            if (handled[source]) {
                continue;
            }
            ItemStack template = inputs.get(source).getStack();
            var recipe = ordinaryRecipe(level, template, catalysts,
                    preferredRecipe,
                    locks[source], signatures[source],
                    contextRemaining[source], progress[source]);
            // Native factories can spread a valid input before its catalyst is
            // installed. In that case no recipe is selectable yet, but the
            // plain stack still has no lane-specific context to preserve.
            List<NatureAuraRecipeBridge.AltarRecipeView> possibleRecipes =
                    recipe.isEmpty() && isUnreserved(template, locks[source],
                            signatures[source], contextRemaining[source],
                            progress[source])
                            ? NatureAuraRecipeScanner.scan(
                            level.getRecipeManager()).stream()
                            .filter(candidate -> candidate.input().test(template))
                            .toList() : List.of();
            if (recipe.isPresent()
                    ? !outputAccepts(outputs.get(source), recipe.get().output())
                    : possibleRecipes.isEmpty()
                    || !outputAcceptsAll(outputs.get(source), possibleRecipes)) {
                continue;
            }
            ResourceLocation recipeId = recipe.map(
                    NatureAuraRecipeBridge.AltarRecipeView::id).orElse(null);
            String recipeSignature = recipe.map(
                    NatureAuraRecipeBridge::recipeSignature).orElse("");
            List<Integer> occupied = new ArrayList<>();
            long total = 0;
            for (int lane = source; lane < lanes; lane++) {
                ItemStack input = inputs.get(lane).getStack();
                if (handled[lane] || input.isEmpty()
                        || !ItemStack.isSameItemSameComponents(input, template)) {
                    continue;
                }
                if (recipeId == null) {
                    if (!isUnreserved(input, locks[lane], signatures[lane],
                            contextRemaining[lane], progress[lane])
                            || !outputAcceptsAll(outputs.get(lane),
                            possibleRecipes)) {
                        continue;
                    }
                } else {
                    if (!outputAccepts(outputs.get(lane),
                            recipe.get().output())) {
                        continue;
                    }
                    var selected = ordinaryRecipe(level, input, catalysts,
                            preferredRecipe,
                            locks[lane], signatures[lane],
                            contextRemaining[lane], progress[lane]);
                    if (selected.isEmpty()
                            || !recipeId.equals(selected.get().id())
                            || !recipeSignature.equals(NatureAuraRecipeBridge
                            .recipeSignature(selected.get()))) {
                        continue;
                    }
                }
                occupied.add(lane);
                handled[lane] = true;
                total += input.getCount();
            }
            if (occupied.isEmpty()) {
                continue;
            }
            List<Integer> targets = new ArrayList<>(occupied);
            for (int lane = 0; lane < lanes && targets.size() < total; lane++) {
                if (handled[lane] || !inputs.get(lane).isEmpty()
                        || locks[lane] != null || contextRemaining[lane] != 0
                        || progress[lane] != 0
                        || !(recipeId == null
                        ? outputAcceptsAll(outputs.get(lane), possibleRecipes)
                        : outputAccepts(outputs.get(lane),
                        recipe.get().output()))) {
                    continue;
                }
                ItemStack remainder = inputs.get(lane).insertItem(
                        template.copyWithCount(1), Action.SIMULATE,
                        AutomationType.EXTERNAL);
                if (remainder.isEmpty()) {
                    targets.add(lane);
                    handled[lane] = true;
                }
            }
            if (targets.size() < 2) {
                continue;
            }
            int[] desired = new int[targets.size()];
            int perLane = (int) (total / targets.size());
            int remainder = (int) (total % targets.size());
            boolean fits = true;
            boolean needsChange = false;
            for (int i = 0; i < targets.size(); i++) {
                int lane = targets.get(i);
                desired[i] = perLane + (i < remainder ? 1 : 0);
                if (desired[i] > Math.min(template.getMaxStackSize(),
                        inputs.get(lane).getLimit(template))) {
                    fits = false;
                    break;
                }
                if (inputs.get(lane).getStack().getCount() != desired[i]) {
                    needsChange = true;
                }
            }
            if (!fits || !needsChange) {
                continue;
            }
            ItemStack[] previous = new ItemStack[targets.size()];
            for (int i = 0; i < targets.size(); i++) {
                previous[i] = inputs.get(targets.get(i)).getStack().copy();
            }
            try {
                for (int i = 0; i < targets.size(); i++) {
                    if (previous[i].getCount() != desired[i]) {
                        inputs.get(targets.get(i)).setStack(
                                template.copyWithCount(desired[i]));
                    }
                }
            } catch (RuntimeException | Error failure) {
                for (int i = 0; i < targets.size(); i++) {
                    inputs.get(targets.get(i)).setStack(previous[i]);
                }
                throw failure;
            }
            changed = true;
        }
        return changed;
    }

    private static java.util.Optional<NatureAuraRecipeBridge.AltarRecipeView>
    ordinaryRecipe(Level level, ItemStack input, List<ItemStack> catalysts,
                   ResourceLocation preferredRecipe,
                   ResourceLocation lock, String signature, int context,
                   int progress) {
        if (input.isEmpty() || context != 0
                || NatureAuraRecipeBridge.hasPatternRecipeMarker(input)
                || (lock == null && progress != 0)) {
            return java.util.Optional.empty();
        }
        // An old lock may remain after the shared catalyst changes. A new
        // lane must choose the same recipe on its own or no items may move.
        var unlocked = NatureAuraRecipeBridge.findAltarRecipe(
                level, input, catalysts, null, preferredRecipe);
        if (unlocked.isEmpty()) {
            return unlocked;
        }
        if (lock != null && (!lock.equals(unlocked.get().id())
                || !NatureAuraRecipeBridge.recipeSignature(unlocked.get())
                .equals(signature))) {
            return java.util.Optional.empty();
        }
        return unlocked;
    }

    private static boolean outputAccepts(IInventorySlot slot,
                                         ItemStack result) {
        ItemStack current = slot.getStack();
        return (current.isEmpty() || ItemStack.isSameItemSameComponents(
                current, result)) && (long) current.getCount()
                + result.getCount() <= Math.min(result.getMaxStackSize(),
                slot.getLimit(result));
    }

    private static boolean outputAcceptsAll(IInventorySlot slot,
            List<NatureAuraRecipeBridge.AltarRecipeView> recipes) {
        return recipes.stream().allMatch(recipe ->
                outputAccepts(slot, recipe.output()));
    }

    private static boolean isUnreserved(ItemStack input,
            ResourceLocation lock, String signature, int context,
            int progress) {
        return !input.isEmpty() && lock == null
                && (signature == null || signature.isBlank())
                && context == 0 && progress == 0
                && !NatureAuraRecipeBridge.hasPatternRecipeMarker(input);
    }
}
