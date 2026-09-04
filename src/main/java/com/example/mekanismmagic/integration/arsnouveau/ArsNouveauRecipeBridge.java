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
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Direct recipe bridge for Ars Nouveau's physical crafting machines.
 */
public final class ArsNouveauRecipeBridge {
    private static final int MAX_CATALYST_SAMPLES = 9;
    private static final Map<RecipeManager, JeiCatalog> JEI_CATALOGS =
            new WeakHashMap<>();

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
        if (samples.isEmpty() || samples.size() > MAX_CATALYST_SAMPLES) {
            return Optional.empty();
        }
        List<RecipeHolder<ImbuementRecipe>> recipes = new ArrayList<>();
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            if (matchIngredients(holder.value().getPedestalItems(), samples)) {
                recipes.add(holder);
            }
        }
        if (recipes.isEmpty()) {
            return Optional.empty();
        }
        // The identifier is a recipe identity, not an identity of the
        // concrete tag alternatives that happened to be inserted. AE may
        // substitute any valid Ingredient choice, so sample-based IDs made
        // an otherwise identical craft produce different component data.
        ResourceLocation catalystId = catalystIdForRecipes(recipes);
        ItemStack output = createBoundIdentifier(level, catalystId, recipes);
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
        return level == null ? List.of()
                : jeiCatalog(level).catalystRecipes();
    }

    private static List<CatalystIdentifierJeiData>
    buildCatalystIdentifierJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        List<CatalystIdentifierJeiData> result = new ArrayList<>();
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            List<Ingredient> ingredients = holder.value().getPedestalItems();
            if (ingredients.isEmpty()
                    || ingredients.size() > MAX_CATALYST_SAMPLES) {
                continue;
            }
            ItemStackHandler samples = new ItemStackHandler(
                    ingredients.size());
            boolean valid = true;
            for (int index = 0; index < ingredients.size(); index++) {
                ItemStack choice = representativeChoice(
                        ingredients.get(index));
                if (choice.isEmpty()) {
                    valid = false;
                    break;
                }
                samples.setStackInSlot(index, choice);
            }
            if (valid) {
                findCatalystIdentifier(level, samples, 0,
                                ingredients.size())
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
                                        identifierRecipeIds(stack),
                                        stack.copy()));
                            }
                        });
            }
        }
        return List.copyOf(result);
    }

    /**
     * Builds the machine-facing imbuement view used by JEI and pattern
     * terminals. Unlike Ars Nouveau's physical imbuement view, the pedestal
     * ingredients are represented by the persistent catalyst identifier that
     * the processor and factories actually select.
     */
    public static List<IdentifierImbuementJeiData>
    identifierImbuementJeiRecipes(Level level) {
        return level == null ? List.of()
                : jeiCatalog(level).imbuementRecipes();
    }

    private static List<IdentifierImbuementJeiData>
    buildIdentifierImbuementJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        List<IdentifierImbuementJeiData> result = new ArrayList<>();
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            ImbuementRecipe recipe = holder.value();
            int catalystCount = recipe.getPedestalItems().size();
            if (catalystCount > MAX_CATALYST_SAMPLES) {
                continue;
            }
            ItemStack output = recipe.getResultItem(
                    level.registryAccess()).copy();
            ItemStack identifier = createPatternIdentifierForRecipe(
                    level, holder.id());
            if (output.isEmpty()
                    || !identifier.is(ArsNouveauRegistries
                    .CATALYST_IDENTIFIER_ITEM.get())
                    || !CatalystIdentifierItem.matchesRecipe(
                    identifier, holder.id())) {
                continue;
            }
            ResourceLocation displayId =
                    ResourceLocation.fromNamespaceAndPath(
                            "mekanism_magic",
                            "identifier_imbuement/"
                                    + holder.id().getNamespace() + "/"
                                    + holder.id().getPath());
            result.add(new IdentifierImbuementJeiData(
                    displayId, recipe.getInput(), identifier.copy(),
                    output, Math.max(0, recipe.getSource())));
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
                data.getUnsafe().getString("catalyst_id"));
        return parsed == null ? ResourceLocation.fromNamespaceAndPath(
                "mekanism_magic", "catalyst/unknown") : parsed;
    }

    public static ItemStack createIdentifierForRecipe(
            Level level, ResourceLocation recipeId) {
        if (level == null) {
            return new ItemStack(
                    ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
        }
        RecipeHolder<ImbuementRecipe> holder = ArsNouveauRecipeScanner.find(
                level.getRecipeManager(), recipeId);
        if (holder == null) {
            return new ItemStack(
                    ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
        }
        List<Ingredient> ingredients = holder.value().getPedestalItems();
        if (ingredients.isEmpty()) {
            ResourceLocation virtualId = ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "catalyst/virtual/"
                            + recipeId.getNamespace() + "/"
                            + recipeId.getPath());
            return createBoundIdentifier(level, virtualId, List.of(holder));
        }
        if (ingredients.size() > MAX_CATALYST_SAMPLES) {
            return createUnboundIdentifier();
        }
        ItemStackHandler samples = new ItemStackHandler(ingredients.size());
        for (int index = 0; index < ingredients.size(); index++) {
            ItemStack choice = representativeChoice(ingredients.get(index));
            if (choice.isEmpty()) {
                return new ItemStack(
                        ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
            }
            samples.setStackInSlot(index, choice);
        }
        return findCatalystIdentifier(level, samples, 0, ingredients.size())
                .map(MachineRecipeResult::output)
                .orElseGet(() -> new ItemStack(
                        ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get()));
    }

    public static ItemStack createPatternIdentifierForRecipe(
            Level level, ResourceLocation recipeId) {
        ItemStack identifier = createIdentifierForRecipe(level, recipeId);
        if (level == null || identifier.isEmpty()) {
            return identifier;
        }
        RecipeHolder<ImbuementRecipe> holder = ArsNouveauRecipeScanner.find(
                level.getRecipeManager(), recipeId);
        if (holder != null) {
            CustomData.update(DataComponents.CUSTOM_DATA, identifier, tag -> {
                tag.putString("pattern_recipe", recipeId.toString());
                putDisplayItem(level, tag, holder);
            });
        }
        return identifier;
    }

    /** Reads the transient recipe context carried by an AE-dispatched input. */
    public static ResourceLocation patternRecipe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : ResourceLocation.tryParse(
                data.getUnsafe().getString("pattern_recipe"));
    }

    public static boolean hasPatternRecipeMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null
                && data.getUnsafe().contains("pattern_recipe");
    }

    /** Read-only recipe view with only our transient AE marker removed. */
    public static ItemStack recipeInputView(ItemStack stack) {
        return hasPatternRecipeMarker(stack)
                ? clearPatternRecipeMarker(stack) : stack;
    }

    public static ItemStack clearPatternRecipeMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        ItemStack cleared = stack.copy();
        CustomData existing = cleared.get(DataComponents.CUSTOM_DATA);
        if (existing == null
                || !existing.getUnsafe().contains("pattern_recipe")) {
            return cleared;
        }
        CompoundTag data = existing.copyTag();
        data.remove("pattern_recipe");
        if (data.isEmpty()) {
            cleared.remove(DataComponents.CUSTOM_DATA);
        } else {
            cleared.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
        return cleared;
    }

    /**
     * Copies a real processing input and attaches the virtual catalyst
     * context. The item is consumed normally; the catalyst identifier itself
     * remains pattern metadata and is never physically dispatched.
     */
    public static ItemStack markPatternInput(
            ItemStack stack, ResourceLocation recipeId) {
        if (stack == null || stack.isEmpty() || recipeId == null) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        ItemStack marked = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, marked, tag ->
                tag.putString("pattern_recipe", recipeId.toString()));
        return marked;
    }

    public static int catalystIdentifierJeiIndex(
            Level level, String catalystId) {
        if (level == null || catalystId == null || catalystId.isBlank()) {
            return -1;
        }
        return jeiCatalog(level).indexByCatalystId()
                .getOrDefault(catalystId, -1);
    }

    public static ItemStack catalystIdentifierJeiStack(
            Level level, int index) {
        List<CatalystIdentifierJeiData> recipes =
                catalystIdentifierJeiRecipes(level);
        return index < 0 || index >= recipes.size()
                ? ItemStack.EMPTY : recipes.get(index).output().copy();
    }

    public static String catalystIdentifierJeiId(Level level, int index) {
        if (level == null) {
            return "";
        }
        List<String> ids = jeiCatalog(level).catalystIds();
        return index < 0 || index >= ids.size() ? "" : ids.get(index);
    }

    public static ItemStack catalystIdentifierJeiStack(
            Level level, String catalystId) {
        return catalystIdentifierJeiStack(level,
                catalystIdentifierJeiIndex(level, catalystId));
    }

    public static boolean requiresCatalystIdentifier(
            Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return true;
        }
        RecipeHolder<ImbuementRecipe> holder = ArsNouveauRecipeScanner.find(
                level.getRecipeManager(), recipeId);
        return holder == null
                || !holder.value().getPedestalItems().isEmpty();
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

    private static ResourceLocation catalystIdForRecipes(
            List<RecipeHolder<ImbuementRecipe>> recipes) {
        String fingerprint = sortedRecipes(recipes).stream()
                .map(holder -> holder.id() + "#"
                        + ArsNouveauRecipeScanner.recipeSignature(
                        holder.value()))
                .collect(java.util.stream.Collectors.joining("|"));
        return ResourceLocation.fromNamespaceAndPath(
                "mekanism_magic", "catalyst/" + Integer.toUnsignedString(
                        fingerprint.hashCode(), 16));
    }

    private static ResourceLocation legacyCatalystId(
            List<Ingredient> ingredients) {
        String fingerprint = ingredients.stream()
                .map(ArsNouveauRecipeBridge::representativeChoice)
                .filter(stack -> !stack.isEmpty())
                .map(ArsNouveauRecipeBridge::sampleKey)
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));
        return ResourceLocation.fromNamespaceAndPath(
                "mekanism_magic", "catalyst/" + Integer.toUnsignedString(
                        fingerprint.hashCode(), 16));
    }

    private static List<RecipeHolder<ImbuementRecipe>> sortedRecipes(
            List<RecipeHolder<ImbuementRecipe>> recipes) {
        List<RecipeHolder<ImbuementRecipe>> sorted =
                new ArrayList<>(recipes);
        sorted.sort(java.util.Comparator.comparing(
                holder -> holder.id().toString()));
        return sorted;
    }

    private static ItemStack createBoundIdentifier(
            Level level, ResourceLocation catalystId,
            List<RecipeHolder<ImbuementRecipe>> recipes) {
        ItemStack output = new ItemStack(
                ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get());
        CompoundTag data = new CompoundTag();
        data.putString("catalyst_id", catalystId.toString());
        ListTag recipeList = new ListTag();
        CompoundTag signatures = new CompoundTag();
        List<RecipeHolder<ImbuementRecipe>> sorted =
                sortedRecipes(recipes);
        sorted.forEach(holder -> {
            recipeList.add(StringTag.valueOf(holder.id().toString()));
            signatures.putString(holder.id().toString(),
                    ArsNouveauRecipeScanner.recipeSignature(holder.value()));
        });
        data.put("recipes", recipeList);
        data.put("recipe_signatures", signatures);
        sorted.stream().findFirst().ifPresent(holder ->
                putDisplayItem(level, data, holder));
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return output;
    }

    private static void putDisplayItem(
            Level level, CompoundTag data,
            RecipeHolder<ImbuementRecipe> holder) {
        ItemStack display = holder.value().getResultItem(
                level.registryAccess());
        if (!display.isEmpty()) {
            data.putString("display_item", BuiltInRegistries.ITEM
                    .getKey(display.getItem()).toString());
        }
    }

    private static List<ResourceLocation> identifierRecipeIds(
            ItemStack identifier) {
        CustomData data = identifier.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        List<ResourceLocation> result = new ArrayList<>();
        ListTag recipes = data.getUnsafe().getList("recipes", 8);
        for (int index = 0; index < recipes.size(); index++) {
            ResourceLocation id = ResourceLocation.tryParse(
                    recipes.getString(index));
            if (id != null) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    public static ItemStack representativeChoice(Ingredient ingredient) {
        return java.util.Arrays.stream(ingredient.getItems())
                .min(java.util.Comparator.comparing(
                        ArsNouveauRecipeBridge::sampleKey))
                .map(stack -> stack.copyWithCount(1))
                .orElse(ItemStack.EMPTY);
    }

    public static boolean identifierMatchesRecipe(
            Level level, ItemStack identifier, ResourceLocation recipeId) {
        if (level == null || identifier.isEmpty() || recipeId == null) {
            return false;
        }
        String signature = ArsNouveauRecipeScanner.signature(
                level.getRecipeManager(), recipeId);
        return !signature.isEmpty()
                && identifierMatchesRecipe(identifier, recipeId, signature);
    }

    private static boolean identifierMatchesRecipe(
            ItemStack identifier, RecipeHolder<ImbuementRecipe> holder) {
        return identifierMatchesRecipe(identifier, holder.id(),
                ArsNouveauRecipeScanner.recipeSignature(holder.value()));
    }

    private static boolean identifierMatchesRecipe(
            ItemStack identifier, ResourceLocation recipeId,
            String expectedSignature) {
        if (!CatalystIdentifierItem.matchesRecipe(identifier, recipeId)) {
            return false;
        }
        CustomData customData = identifier.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return false;
        }
        CompoundTag signatures = customData.getUnsafe()
                .getCompound("recipe_signatures");
        return expectedSignature.equals(
                signatures.getString(recipeId.toString()));
    }

    private static synchronized JeiCatalog jeiCatalog(Level level) {
        RecipeManager manager = level.getRecipeManager();
        long version = ArsNouveauRecipeScanner.version(manager);
        JeiCatalog cached = JEI_CATALOGS.get(manager);
        if (cached == null || cached.version() != version) {
            cached = buildJeiCatalog(level, version);
            JEI_CATALOGS.put(manager, cached);
        }
        return cached;
    }

    private static JeiCatalog buildJeiCatalog(Level level, long version) {
        List<CatalystIdentifierJeiData> catalystRecipes =
                buildCatalystIdentifierJeiRecipes(level);
        Map<String, Integer> indexByCatalystId = new HashMap<>();
        List<String> catalystIds = new ArrayList<>(
                catalystRecipes.size());
        for (int index = 0; index < catalystRecipes.size(); index++) {
            CatalystIdentifierJeiData recipe = catalystRecipes.get(index);
            String currentId = CatalystIdentifierItem.catalystId(
                    recipe.output()).toString();
            catalystIds.add(currentId);
            indexByCatalystId.putIfAbsent(currentId, index);
            indexByCatalystId.putIfAbsent(
                    legacyCatalystId(recipe.catalysts()).toString(), index);
        }
        return new JeiCatalog(version, catalystRecipes,
                buildIdentifierImbuementJeiRecipes(level),
                List.copyOf(catalystIds), Map.copyOf(indexByCatalystId));
    }

    private record JeiCatalog(
            long version,
            List<CatalystIdentifierJeiData> catalystRecipes,
            List<IdentifierImbuementJeiData> imbuementRecipes,
            List<String> catalystIds,
            Map<String, Integer> indexByCatalystId) {
    }

    public record CatalystIdentifierJeiData(
            ResourceLocation id,
            List<Ingredient> catalysts,
            List<ResourceLocation> recipeIds,
            ItemStack output) {
    }

    public record IdentifierImbuementJeiData(
            ResourceLocation id,
            Ingredient input,
            ItemStack identifier,
            ItemStack output,
            int sourceCost) {
    }

    public static Optional<MachineRecipeResult> findImbuementByIdentifier(
            Level level, ItemStackHandler inventory, int inputSlot,
            ItemStack identifier) {
        if (level == null) {
            return Optional.empty();
        }
        ItemStack reagent = recipeInputView(
                inventory.getStackInSlot(inputSlot));
        if (reagent.isEmpty()) {
            return Optional.empty();
        }
        if (identifier.is(
                ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get())) {
            for (RecipeHolder<ImbuementRecipe> holder :
                    ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
                if (holder.value().getPedestalItems().isEmpty()
                        || !holder.value().getInput().test(reagent)
                        || !identifierMatchesRecipe(identifier, holder)) {
                    continue;
                }
                Optional<MachineRecipeResult> result =
                        createImbuementResult(level, inputSlot, holder);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            if (!holder.value().getPedestalItems().isEmpty()
                    || !holder.value().getInput().test(reagent)) {
                continue;
            }
            Optional<MachineRecipeResult> result =
                    createImbuementResult(level, inputSlot, holder);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private static Optional<MachineRecipeResult> createImbuementResult(
            Level level, int inputSlot,
            RecipeHolder<ImbuementRecipe> holder) {
        ItemStack output = holder.value().getResultItem(
                level.registryAccess()).copy();
        if (output.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MachineRecipeResult(
                holder.id(), output,
                ArsNouveauMachineConfig.IMBUEMENT_DURATION,
                List.of(new InputUse(inputSlot, 1)), -1, -1, null, null,
                Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                        Math.max(0, holder.value().getSource()))));
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
            Level level, ItemStackHandler inventory, int inputStart,
            int inputCount) {
        if (level == null) {
            return Optional.empty();
        }
        for (RecipeHolder<? extends IEnchantingRecipe> holder :
                ArsNouveauAPI.getInstance()
                        .getEnchantingApparatusRecipes(level)) {
            IEnchantingRecipe recipe = holder.value();
            List<Ingredient> pedestalIngredients =
                    recipe instanceof EnchantingApparatusRecipe apparatus
                            ? apparatus.pedestalItems()
                            : List.copyOf(recipe.getIngredients());
            // Ars recipes distinguish a reagent from unordered pedestal
            // ingredients. Try every occupied machine input as that reagent,
            // reserving one item from the candidate slot before matching the
            // remaining ingredients. This also handles repeated ingredients
            // stored together in one stack.
            for (int offset = 0; offset < inputCount; offset++) {
                int reagentSlot = inputStart + offset;
                ItemStack reagent = inventory.getStackInSlot(reagentSlot);
                if (reagent.isEmpty()) {
                    continue;
                }
                Optional<MatchedIngredients> pedestalMatch =
                        matchIngredientStacks(
                                pedestalIngredients, inventory,
                                inputStart, inputCount, reagentSlot);
                if (pedestalMatch.isEmpty()) {
                    continue;
                }
                ApparatusRecipeInput recipeInput = new ApparatusRecipeInput(
                        reagent.copyWithCount(1),
                        pedestalMatch.get().stacks(), null);
                if (!recipe.matches(recipeInput, level)) {
                    continue;
                }
                ItemStack output = recipe.assemble(
                        recipeInput, level.registryAccess()).copy();
                if (output.isEmpty()) {
                    continue;
                }
                List<InputUse> inputs = mergeInputUses(
                        reagentSlot, pedestalMatch.get().uses());
                return Optional.of(new MachineRecipeResult(
                        holder.id(), output,
                        ArsNouveauMachineConfig.APPARATUS_DURATION,
                        inputs, -1, -1, null, null,
                        Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                                ArsNouveauMachineConfig.apparatusSourceCost(
                                        recipe.sourceCost()))));
            }
        }
        return Optional.empty();
    }

    private static List<InputUse> mergeInputUses(
            int reagentSlot, List<InputUse> pedestalUses) {
        Map<Integer, Integer> counts = new java.util.TreeMap<>();
        counts.put(reagentSlot, 1);
        for (InputUse use : pedestalUses) {
            counts.merge(use.slot(), use.count(), Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new InputUse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Optional<List<InputUse>> matchIngredients(
            List<Ingredient> required, ItemStackHandler inventory,
            int startSlot, int slotCount) {
        return matchIngredientStacks(required, inventory, startSlot,
                slotCount).map(MatchedIngredients::uses);
    }

    private static Optional<MatchedIngredients> matchIngredientStacks(
            List<Ingredient> required, ItemStackHandler inventory,
            int startSlot, int slotCount) {
        return matchIngredientStacks(required, inventory, startSlot,
                slotCount, -1);
    }

    private static Optional<MatchedIngredients> matchIngredientStacks(
            List<Ingredient> required, ItemStackHandler inventory,
            int startSlot, int slotCount, int reservedSlot) {
        int[] remaining = new int[slotCount];
        int[] used = new int[slotCount];
        int[] matchedSlots = new int[required.size()];
        java.util.Arrays.fill(matchedSlots, -1);
        for (int index = 0; index < slotCount; index++) {
            remaining[index] =
                    inventory.getStackInSlot(startSlot + index).getCount();
        }
        if (reservedSlot >= startSlot
                && reservedSlot < startSlot + slotCount) {
            remaining[reservedSlot - startSlot]--;
        }
        if (!matchIngredients(required, 0, inventory, startSlot,
                remaining, used, matchedSlots)) {
            return Optional.empty();
        }
        List<InputUse> uses = new ArrayList<>();
        for (int index = 0; index < used.length; index++) {
            if (used[index] > 0) {
                uses.add(new InputUse(startSlot + index, used[index]));
            }
        }
        List<ItemStack> stacks = new ArrayList<>(matchedSlots.length);
        for (int matchedSlot : matchedSlots) {
            stacks.add(inventory.getStackInSlot(startSlot + matchedSlot)
                    .copyWithCount(1));
        }
        return Optional.of(new MatchedIngredients(
                List.copyOf(uses), List.copyOf(stacks)));
    }

    private static boolean matchIngredients(
            List<Ingredient> required, int ingredientIndex,
            ItemStackHandler inventory, int startSlot,
            int[] remaining, int[] used, int[] matchedSlots) {
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
            matchedSlots[ingredientIndex] = index;
            if (matchIngredients(required, ingredientIndex + 1,
                    inventory, startSlot, remaining, used,
                    matchedSlots)) {
                return true;
            }
            remaining[index]++;
            used[index]--;
            matchedSlots[ingredientIndex] = -1;
        }
        return false;
    }

    private record MatchedIngredients(
            List<InputUse> uses, List<ItemStack> stacks) {
    }
}
