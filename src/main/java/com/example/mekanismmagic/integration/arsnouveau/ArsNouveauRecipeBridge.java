package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.IEnchantingRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Direct recipe bridge for Ars Nouveau's physical crafting machines.
 */
public final class ArsNouveauRecipeBridge {
    private ArsNouveauRecipeBridge() {
    }

    public static Optional<MachineRecipeResult> findCatalystIdentifier(
            Level level, ItemStackHandler inventory, int startSlot,
            int slotCount) {
        if (level == null) {
            return Optional.empty();
        }
        List<ItemStack> samples = new ArrayList<>();
        List<Integer> sampleSlots = new ArrayList<>();
        for (int index = 0; index < slotCount; index++) {
            ItemStack stack = inventory.getStackInSlot(startSlot + index);
            if (!stack.isEmpty()) {
                samples.add(stack.copyWithCount(1));
                sampleSlots.add(index);
            }
        }
        if (samples.size() != 3) {
            return Optional.empty();
        }
        List<ResourceLocation> recipes = new ArrayList<>();
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            if (matchIngredients(holder.value().getPedestalItems(), samples)) {
                recipes.add(holder.id());
            }
        }
        if (recipes.isEmpty()) {
            return Optional.empty();
        }
        samples.sort(java.util.Comparator.comparing(
                ArsNouveauRecipeBridge::sampleKey));
        String fingerprint = samples.stream()
                .map(ArsNouveauRecipeBridge::sampleKey)
                .collect(java.util.stream.Collectors.joining("|"));
        ResourceLocation catalystId = ResourceLocation.fromNamespaceAndPath(
                "mekanism_magic", "catalyst/" + Integer.toUnsignedString(
                        fingerprint.hashCode(), 16));
        ItemStack output = new ItemStack(
                ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
        CompoundTag data = new CompoundTag();
        data.putString("catalyst_id", catalystId.toString());
        ListTag recipeList = new ListTag();
        recipes.forEach(id -> recipeList.add(StringTag.valueOf(id.toString())));
        data.put("recipes", recipeList);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        List<InputUse> inputs = new ArrayList<>();
        for (int index : sampleSlots) {
            inputs.add(new InputUse(startSlot + index, 1));
        }
        return Optional.of(new MachineRecipeResult(
                catalystId, output, 40, inputs, -1, -1, null, null,
                Map.of()));
    }

    public static List<ItemStack> catalystIdentifierJeiStacks(Level level) {
        return catalystIdentifierJeiRecipes(level).stream()
                .map(CatalystIdentifierJeiData::output)
                .map(ItemStack::copy)
                .toList();
    }

    public static List<CatalystIdentifierJeiData>
    catalystIdentifierJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        List<CatalystIdentifierJeiData> result = new ArrayList<>();
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            List<Ingredient> ingredients = holder.value().getPedestalItems();
            if (ingredients.size() != 3) {
                continue;
            }
            ItemStackHandler samples = new ItemStackHandler(3);
            boolean valid = true;
            for (int index = 0; index < 3; index++) {
                ItemStack[] choices = ingredients.get(index).getItems();
                if (choices.length == 0) {
                    valid = false;
                    break;
                }
                samples.setStackInSlot(index, choices[0].copyWithCount(1));
            }
            if (valid) {
                findCatalystIdentifier(level, samples, 0, 3)
                        .map(MachineRecipeResult::output)
                        .ifPresent(stack -> {
                            if (result.stream().noneMatch(existing ->
                                    ItemStack.isSameItemSameComponents(
                                            existing.output(), stack))) {
                                ResourceLocation displayId =
                                        ResourceLocation.fromNamespaceAndPath(
                                                "mekanism_magic",
                                                "catalyst_identifier/"
                                                        + holder.id()
                                                        .getNamespace()
                                                        + "/"
                                                        + holder.id()
                                                        .getPath());
                                result.add(new CatalystIdentifierJeiData(
                                        displayId, List.copyOf(ingredients),
                                        stack.copy()));
                            }
                        });
            }
        }
        return List.copyOf(result);
    }

    public static ResourceLocation catalystIdForRecipe(
            Level level, ResourceLocation recipeId) {
        ItemStack identifier = createIdentifierForRecipe(level, recipeId);
        CustomData data = identifier.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "catalyst/unknown");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(
                data.copyTag().getString("catalyst_id"));
        return parsed == null ? ResourceLocation.fromNamespaceAndPath(
                "mekanism_magic", "catalyst/unknown") : parsed;
    }

    public static ItemStack createIdentifierForRecipe(
            Level level, ResourceLocation recipeId) {
        if (level == null) {
            return new ItemStack(
                    ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
        }
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            if (holder.id().equals(recipeId)) {
                List<Ingredient> ingredients =
                        holder.value().getPedestalItems();
                ItemStackHandler samples = new ItemStackHandler(
                        ingredients.size());
                for (int index = 0; index < ingredients.size(); index++) {
                    ItemStack[] choices = ingredients.get(index).getItems();
                    if (choices.length == 0) {
                        return new ItemStack(ArsNouveauRegistries
                                .CATALYST_IDENTIFIER_ITEM.get());
                    }
                    samples.setStackInSlot(index,
                            choices[0].copyWithCount(1));
                }
                return findCatalystIdentifier(level, samples, 0,
                        ingredients.size())
                        .map(MachineRecipeResult::output)
                        .orElseGet(() -> new ItemStack(ArsNouveauRegistries
                                .CATALYST_IDENTIFIER_ITEM.get()));
            }
        }
        return new ItemStack(
                ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
    }

    public static ItemStack createUnboundIdentifier() {
        return new ItemStack(
                ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
    }

    private static boolean matchIngredients(List<Ingredient> required,
                                            List<ItemStack> samples) {
        if (required.size() != samples.size()) {
            return false;
        }
        boolean[] used = new boolean[samples.size()];
        return matchIngredients(required, 0, samples, used);
    }

    private static boolean matchIngredients(List<Ingredient> required,
                                            int index, List<ItemStack> samples,
                                            boolean[] used) {
        if (index >= required.size()) {
            return true;
        }
        for (int sample = 0; sample < samples.size(); sample++) {
            if (!used[sample] && required.get(index).test(samples.get(sample))) {
                used[sample] = true;
                if (matchIngredients(required, index + 1, samples, used)) {
                    return true;
                }
                used[sample] = false;
            }
        }
        return false;
    }

    private static String sampleKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "|" + stack.getComponentsPatch();
    }

    public record CatalystIdentifierJeiData(
            ResourceLocation id,
            List<Ingredient> catalysts,
            ItemStack output) {
    }

    public static Optional<MachineRecipeResult> findImbuementByIdentifier(
            Level level, ItemStackHandler inventory, int inputSlot,
            ItemStack identifier) {
        if (level == null) {
            return Optional.empty();
        }
        ItemStack reagent = inventory.getStackInSlot(inputSlot);
        if (reagent.isEmpty()
                || !identifier.is(ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get())) {
            return Optional.empty();
        }
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            if (!holder.value().getInput().test(reagent)
                    || !CatalystIdentifierItem.matchesRecipe(
                    identifier, holder.id())) {
                continue;
            }
            ItemStack output =
                    holder.value().getResultItem(level.registryAccess()).copy();
            if (output.isEmpty()) {
                continue;
            }
            return Optional.of(new MachineRecipeResult(
                    holder.id(), output,
                    ArsNouveauMachineConfig.IMBUEMENT_DURATION,
                    List.of(new InputUse(inputSlot, 1)), -1, -1, null, null,
                    Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                            Math.max(0, holder.value().getSource()))));
        }
        return Optional.empty();
    }

    public static Optional<MachineRecipeResult> findImbuementRecipe(
            Level level, ItemStackHandler inventory, int inputSlot,
            int pedestalStart, int pedestalCount) {
        if (level == null) {
            return Optional.empty();
        }
        ItemStack reagent = inventory.getStackInSlot(inputSlot);
        if (reagent.isEmpty()) {
            return Optional.empty();
        }
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            ImbuementRecipe recipe = holder.value();
            if (!recipe.getInput().test(reagent)) {
                continue;
            }
            Optional<List<InputUse>> pedestalUses = matchIngredients(
                    recipe.getPedestalItems(), inventory,
                    pedestalStart, pedestalCount);
            if (pedestalUses.isEmpty()) {
                continue;
            }
            List<InputUse> inputs = new ArrayList<>();
            inputs.add(new InputUse(inputSlot, 1));
            inputs.addAll(pedestalUses.get());
            ItemStack output =
                    recipe.getResultItem(level.registryAccess()).copy();
            if (output.isEmpty()) {
                continue;
            }
            return Optional.of(new MachineRecipeResult(
                    holder.id(), output,
                    ArsNouveauMachineConfig.IMBUEMENT_DURATION,
                    inputs, -1, -1, null, null,
                    Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                            Math.max(0, recipe.getSource()))));
        }
        return Optional.empty();
    }

    public static Optional<MachineRecipeResult> findApparatusRecipe(
            Level level, ItemStackHandler inventory, int reagentSlot,
            int pedestalStart, int pedestalCount) {
        if (level == null) {
            return Optional.empty();
        }
        ItemStack reagent = inventory.getStackInSlot(reagentSlot);
        if (reagent.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> pedestalStacks = new ArrayList<>();
        for (int index = 0; index < pedestalCount; index++) {
            ItemStack stack = inventory.getStackInSlot(pedestalStart + index);
            if (!stack.isEmpty()) {
                pedestalStacks.add(stack.copyWithCount(1));
            }
        }
        ApparatusRecipeInput recipeInput =
                new ApparatusRecipeInput(reagent.copyWithCount(1),
                        pedestalStacks, null);
        for (RecipeHolder<? extends IEnchantingRecipe> holder :
                ArsNouveauAPI.getInstance()
                        .getEnchantingApparatusRecipes(level)) {
            IEnchantingRecipe recipe = holder.value();
            if (!recipe.matches(recipeInput, level)) {
                continue;
            }
            List<Ingredient> pedestalIngredients =
                    recipe instanceof EnchantingApparatusRecipe apparatus
                            ? apparatus.pedestalItems()
                            : List.copyOf(recipe.getIngredients());
            Optional<List<InputUse>> pedestalUses = matchIngredients(
                    pedestalIngredients, inventory,
                    pedestalStart, pedestalCount);
            if (pedestalUses.isEmpty()) {
                continue;
            }
            ItemStack output =
                    recipe.assemble(recipeInput, level.registryAccess()).copy();
            if (output.isEmpty()) {
                continue;
            }
            List<InputUse> inputs = new ArrayList<>();
            inputs.add(new InputUse(reagentSlot, 1));
            inputs.addAll(pedestalUses.get());
            return Optional.of(new MachineRecipeResult(
                    holder.id(), output,
                    ArsNouveauMachineConfig.APPARATUS_DURATION,
                    inputs, -1, -1, null, null,
                    Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                            Math.max(0, recipe.sourceCost()))));
        }
        return Optional.empty();
    }

    private static Optional<List<InputUse>> matchIngredients(
            List<Ingredient> required, ItemStackHandler inventory,
            int startSlot, int slotCount) {
        int[] remaining = new int[slotCount];
        int[] used = new int[slotCount];
        for (int index = 0; index < slotCount; index++) {
            remaining[index] =
                    inventory.getStackInSlot(startSlot + index).getCount();
        }
        if (!matchIngredients(required, 0, inventory, startSlot,
                remaining, used)) {
            return Optional.empty();
        }
        List<InputUse> result = new ArrayList<>();
        for (int index = 0; index < used.length; index++) {
            if (used[index] > 0) {
                result.add(new InputUse(startSlot + index, used[index]));
            }
        }
        return Optional.of(result);
    }

    private static boolean matchIngredients(
            List<Ingredient> required, int ingredientIndex,
            ItemStackHandler inventory, int startSlot,
            int[] remaining, int[] used) {
        if (ingredientIndex >= required.size()) {
            return true;
        }
        Ingredient ingredient = required.get(ingredientIndex);
        for (int index = 0; index < remaining.length; index++) {
            if (remaining[index] <= 0
                    || !ingredient.test(
                    inventory.getStackInSlot(startSlot + index))) {
                continue;
            }
            remaining[index]--;
            used[index]++;
            if (matchIngredients(required, ingredientIndex + 1,
                    inventory, startSlot, remaining, used)) {
                return true;
            }
            remaining[index]++;
            used[index]--;
        }
        return false;
    }
}
