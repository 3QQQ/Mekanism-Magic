package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.common.recipe.RecipeCompletion;
import com.example.mekanismmagic.integration.common.recipe.SpecialInputHandler;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Optional integration boundary. Occultism is deliberately not on the compile
 * classpath: this bridge discovers its recipe types and invokes their stable
 * public Recipe API at runtime.
 */
public final class OccultismRecipeBridge {
    private static final String OCCULTISM = "occultism";
    private static final TagKey<net.minecraft.world.item.Item> MINER_ITEM_TAG =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(OCCULTISM, "miners"));
    private static final Set<String> TRADER_FACTORY_IDS = Set.of(
            "occultism:trader_otherstone",
            "occultism:trader_otherrock",
            "occultism:trader_otherworld_saplings",
            "occultism:gambler",
            "occultism:trader_gem"
    );
    private static final Map<String, Integer> PENTACLE_MODEL_DATA = Map.ofEntries(
            Map.entry("contact_eldritch_spirit", 1),
            Map.entry("contact_wild_spirit", 2),
            Map.entry("craft_afrit", 3),
            Map.entry("craft_djinni", 4),
            Map.entry("craft_foliot", 5),
            Map.entry("craft_marid", 6),
            Map.entry("possess_afrit", 7),
            Map.entry("possess_djinni", 8),
            Map.entry("possess_foliot", 9),
            Map.entry("possess_marid", 10),
            Map.entry("possess_unbound_afrit", 11),
            Map.entry("resurrect_spirit", 12),
            Map.entry("summon_afrit", 13),
            Map.entry("summon_djinni", 14),
            Map.entry("summon_foliot", 15),
            Map.entry("summon_marid", 16),
            Map.entry("summon_unbound_afrit", 17),
            Map.entry("summon_unbound_marid", 18)
    );
    private static final List<String> RITUAL_CHALK_COLORS = List.of(
            "black", "blue", "brown", "cyan",
            "gray", "green", "light_blue", "light_gray",
            "lime", "magenta", "orange", "pink",
            "purple", "red", "white", "gold"
    );
    private static final List<net.minecraft.world.item.Item>
            DUPLICATE_RECIPE_MARKERS = List.of(
            Items.WHITE_DYE, Items.ORANGE_DYE, Items.MAGENTA_DYE,
            Items.LIGHT_BLUE_DYE, Items.YELLOW_DYE, Items.LIME_DYE,
            Items.PINK_DYE, Items.GRAY_DYE, Items.LIGHT_GRAY_DYE,
            Items.CYAN_DYE, Items.PURPLE_DYE, Items.BLUE_DYE,
            Items.BROWN_DYE, Items.GREEN_DYE, Items.RED_DYE,
            Items.BLACK_DYE);
    private static final Map<String, Set<String>> DEFAULT_PENTACLE_CHALK_COLORS =
            Map.ofEntries(
                    Map.entry("contact_eldritch_spirit",
                            Set.of("light_blue", "brown", "cyan", "green", "magenta", "pink")),
                    Map.entry("contact_wild_spirit",
                            Set.of("light_blue", "green", "pink")),
                    Map.entry("craft_afrit",
                            Set.of("gray", "lime", "orange", "red", "white", "purple")),
                    Map.entry("craft_djinni",
                            Set.of("lime", "light_gray", "white", "purple")),
                    Map.entry("craft_foliot",
                            Set.of("white", "purple")),
                    Map.entry("craft_marid",
                            Set.of("black", "lime", "orange", "red", "blue", "white", "purple")),
                    Map.entry("possess_afrit",
                            Set.of("gray", "lime", "orange", "red", "white", "gold")),
                    Map.entry("possess_djinni",
                            Set.of("lime", "light_gray", "white", "gold")),
                    Map.entry("possess_foliot",
                            Set.of("white", "gold")),
                    Map.entry("possess_marid",
                            Set.of("black", "lime", "orange", "red", "blue", "white", "gold")),
                    Map.entry("possess_unbound_afrit",
                            Set.of("gray", "lime", "orange", "white", "gold")),
                    Map.entry("resurrect_spirit", Set.of("white")),
                    Map.entry("summon_afrit",
                            Set.of("gray", "lime", "orange", "red", "white")),
                    Map.entry("summon_djinni",
                            Set.of("lime", "light_gray", "white")),
                    Map.entry("summon_foliot", Set.of("white")),
                    Map.entry("summon_marid",
                            Set.of("black", "lime", "orange", "red", "blue", "white")),
                    Map.entry("summon_unbound_afrit",
                            Set.of("gray", "lime", "orange", "white")),
                    Map.entry("summon_unbound_marid",
                            Set.of("black", "lime", "orange", "red", "white"))
            );
    private static final Map<RecipeManager, PentacleCatalog> PENTACLE_CATALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<RecipeManager, MinerCatalog> MINER_CATALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private OccultismRecipeBridge() {
    }

    private static final SpecialInputHandler SACRIFICE_HANDLER =
            new SpecialInputHandler() {
        @Override
        public boolean matches(ItemStack stack) {
            return isSacrificeItem(stack);
        }

        @Override
        public boolean consume(ItemStackHandler inventory, int slot) {
            return consumeSacrifice(inventory, slot);
        }
    };

    public record RitualProjection(ResourceLocation recipeId, ResourceLocation pentacleId,
                                    Object multiblock) {
    }

    public record PentacleJeiData(ResourceLocation pentacleId,
                                  List<ItemStack> materials,
                                  List<String> chalkColors,
                                  ItemStack output) {
    }

    private record PentacleDefinition(ResourceLocation recipeId,
                                      ResourceLocation pentacleId,
                                      List<ItemStack> materials,
                                      Set<String> chalkColors,
                                      ItemStack output) {
    }

    private record PentacleCatalog(long recipeFingerprint,
                                   List<PentacleDefinition> definitions) {
    }

    private record PentacleScan(List<ItemStack> materials,
                                Set<String> chalkColors) {
    }

    private record MinerCandidate(Ingredient ingredient,
                                  ResourceLocation recipeId,
                                  ItemStack output,
                                  int weight) {
    }

    private record MinerCatalog(long recipeFingerprint,
                                List<MinerCandidate> candidates) {
    }

    public record RitualJeiData(ResourceLocation recipeId,
                                ResourceLocation pentacleId,
                                List<Ingredient> ingredients,
                                Ingredient activation,
                                List<ItemStack> sacrifices,
                                ItemStack selector,
                                ItemStack output) {
    }

    public record SpiritJeiData(ResourceLocation recipeId, String recipeType,
                                ItemStack input, ItemStack spirit,
                                ItemStack output) {
    }

    public record MinerOutput(ResourceLocation recipeId, ItemStack output, int weight) {
    }

    public record MinerJeiData(ResourceLocation recipeId, Ingredient input,
                               ItemStack output, int weight) {
    }

    public static boolean isMinerItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(MINER_ITEM_TAG);
    }

    public static int minerDuration(ItemStack miner) {
        return minerProperty(miner, "getMaxMiningTime", 400);
    }

    public static int minerRollsPerOperation(ItemStack miner) {
        return minerProperty(miner, "getRollsPerOperation", 1);
    }

    private static int minerProperty(ItemStack miner, String method, int fallback) {
        if (!isMinerItem(miner)) {
            return fallback;
        }
        try {
            Class<?> mineshaft = Class.forName(
                    "com.klikli_dev.occultism.common.blockentity."
                            + "DimensionalMineshaftBlockEntity");
            Method getter = mineshaft.getMethod(method, ItemStack.class);
            Object value = getter.invoke(null, miner);
            return value instanceof Number number
                    ? Math.max(1, number.intValue()) : fallback;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    public static Optional<MinerOutput> findMinerOutput(Level level, ItemStack miner) {
        if (level == null || !isMinerItem(miner)) {
            return Optional.empty();
        }
        RecipeType<?> type = recipeType("miner");
        if (type == null) {
            return Optional.empty();
        }
        List<MinerCandidate> candidates =
                minerCandidates(level, type);
        long totalWeight = 0;
        List<MinerCandidate> matching = new ArrayList<>();
        for (MinerCandidate candidate : candidates) {
            if (!candidate.ingredient().test(miner)) {
                continue;
            }
            matching.add(candidate);
            totalWeight += candidate.weight();
        }
        if (matching.isEmpty() || totalWeight <= 0) {
            return Optional.empty();
        }
        long roll = Math.floorMod(level.random.nextLong(), totalWeight);
        for (MinerCandidate candidate : matching) {
            roll -= candidate.weight();
            if (roll < 0) {
                return Optional.of(new MinerOutput(candidate.recipeId(),
                        candidate.output().copy(), candidate.weight()));
            }
        }
        MinerCandidate candidate = matching.getLast();
        return Optional.of(new MinerOutput(candidate.recipeId(),
                candidate.output().copy(), candidate.weight()));
    }

    private static List<MinerCandidate> minerCandidates(Level level,
                                                         RecipeType<?> type) {
        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<?>> holders = recipes(level, type);
        long fingerprint = recipeFingerprint(holders);
        MinerCatalog cached = MINER_CATALOGS.get(recipeManager);
        if (cached != null && cached.recipeFingerprint() == fingerprint) {
            return cached.candidates();
        }
        List<MinerCandidate> candidates = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            Object recipe = holder.value();
            List<Ingredient> recipeIngredients = ingredients(recipe);
            Ingredient ingredient = recipeIngredients.isEmpty()
                    ? null : recipeIngredients.getFirst();
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            ItemStack output = result(level.registryAccess(), recipe);
            int weight = intValue(invoke(recipe, "getWeightedResult").orElse(null),
                    "weight", 0);
            if (output.isEmpty() || weight <= 0) {
                continue;
            }
            candidates.add(new MinerCandidate(ingredient, holder.id(),
                    output.copy(), weight));
        }
        List<MinerCandidate> immutable = List.copyOf(candidates);
        MINER_CATALOGS.put(recipeManager,
                new MinerCatalog(fingerprint, immutable));
        return immutable;
    }

    public static List<MinerJeiData> minerJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        RecipeType<?> type = recipeType("miner");
        if (type == null) {
            return List.of();
        }
        List<MinerJeiData> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes(level, type)) {
            Object recipe = holder.value();
            List<Ingredient> recipeIngredients = ingredients(recipe);
            if (recipeIngredients.isEmpty() || isEmptyIngredient(recipeIngredients.getFirst())) {
                continue;
            }
            ItemStack output = result(level.registryAccess(), recipe);
            int weight = intValue(invoke(recipe, "getWeightedResult").orElse(null),
                    "weight", 0);
            if (!output.isEmpty() && weight > 0) {
                result.add(new MinerJeiData(holder.id(),
                        recipeIngredients.getFirst(), output, weight));
            }
        }
        return List.copyOf(result);
    }

    public static Optional<MachineRecipeResult> findSpiritRecipe(
            Level level, ItemStackHandler inventory, ItemStack containment) {
        if (level == null) {
            return Optional.empty();
        }
        int sourceTier = spiritTier(containment);
        if (sourceTier <= 0) {
            return Optional.empty();
        }
        ItemStack input = inventory.getStackInSlot(0);
        String sourceJob = spiritJobId(containment);
        // Spirit Fire itself has no trader requirement. A bound trader checks
        // its exact trades first and then falls back to Spirit Fire. Ordinary
        // worker spirits never enter the trade recipe type.
        List<String> recipeOrder = isTraderSpirit(containment)
                ? List.of("spirit_trade", "spirit_fire")
                : List.of("spirit_fire", "crushing", "crystallize");
        for (String typeId : recipeOrder) {
            RecipeType<?> type = recipeType(typeId);
            if (type == null) {
                continue;
            }
            OccultismSpiritJobConfig.WorkerSettings settings =
                    OccultismSpiritJobConfig.settings(typeId, sourceTier);
            for (RecipeHolder<?> holder : recipes(level, type)) {
                Object recipe = holder.value();
                Optional<Match> match = matchSingleRecipe(level, recipe,
                        input, settings.recipeTier(), sourceJob, typeId);
                if (match.isPresent()) {
                    ItemStack baseOutput = result(level.registryAccess(), recipe);
                    if (baseOutput.isEmpty()) {
                        continue;
                    }
                    int operations = spiritOperations(recipe, typeId, input,
                            baseOutput, settings);
                    if (operations <= 0) {
                        continue;
                    }
                    ItemStack output = spiritOutput(recipe, typeId, baseOutput,
                            settings, operations);
                    return Optional.of(new MachineRecipeResult(holder.id(), output,
                            recipeDuration(recipe, typeId, settings),
                            List.of(new InputUse(0, operations)), -1));
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<MachineRecipeResult> findRitualRecipe(
            Level level, ItemStackHandler inventory,
            ItemStack ritualSelector, ItemStack activation,
            ItemStack sacrifice, boolean dictionaryEnabled) {
        if (level == null) {
            return Optional.empty();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return Optional.empty();
        }
        for (RecipeHolder<?> holder : recipes(level, type)) {
            Object recipe = holder.value();
            if (!isMachineSafeRitual(recipe)) {
                continue;
            }
            if (!matchesRitualSelector(holder.id(), recipe, ritualSelector)) {
                continue;
            }
            if (!matchesActivation(recipe, activation, dictionaryEnabled)) {
                continue;
            }
            Optional<List<InputUse>> slots = matchRitualIngredients(recipe, inventory);
            if (slots.isEmpty()) {
                continue;
            }
            int sacrificeSlot = -1;
            if (booleanValue(recipe, "requiresSacrifice")) {
                if (!matchesSacrifice(recipe, sacrifice)) {
                    continue;
                }
                sacrificeSlot = NativeMagicMachineBlockEntity.SACRIFICE_SLOT;
            }
            int activationSlot = activationRequired(recipe)
                    ? NativeMagicMachineBlockEntity.ACTIVATION_SLOT : -1;
            boolean summonsEntity = invoke(recipe, "getEntityToSummon").orElse(null) != null
                    || invoke(recipe, "getEntityTagToSummon").orElse(null) != null;
            String command = stringValue(recipe, "getCommand");
            ItemStack output = command.isBlank()
                    ? summonsEntity
                    ? spawnEggResult(level, recipe)
                    : result(level.registryAccess(), recipe)
                    : commandResult(level, recipe);
            if (summonsEntity && output.isEmpty()) {
                continue;
            }
            if (!command.isBlank() && output.isEmpty()) {
                continue;
            }
            if (!output.isEmpty()) {
                RecipeCompletion completion = command.isBlank()
                        ? RecipeCompletion.NONE
                        : (serverLevel, position) -> executeCommandRitual(
                        serverLevel, position, command);
                return Optional.of(new MachineRecipeResult(holder.id(), output,
                        Math.max(40, intValue(recipe, "getDuration", 200)),
                        slots.get(), activationSlot, sacrificeSlot,
                        completion, sacrificeSlot < 0
                        ? SpecialInputHandler.NONE : SACRIFICE_HANDLER));
            }
        }
        return Optional.empty();
    }

    /**
     * Delegates reusable captured-entity items to the common integration
     * registry, so this recipe bridge does not know which optional mod owns
     * the container.
     */
    public static boolean isFilledContainment(ItemStack stack) {
        return EntityContainerRegistry.isFilled(stack);
    }

    public static boolean isSpiritSource(ItemStack stack) {
        if (stack.isEmpty() || spiritTier(stack) <= 0) {
            return false;
        }
        return stack.getItem() instanceof SpawnEggItem || isFilledContainment(stack);
    }

    public static boolean isRitualSelector(ItemStack stack) {
        if (isUltimateMiniRitual(stack)) {
            return true;
        }
        if (isRitualProjectionItem(stack)) {
            return true;
        }
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        return "mekanism_magic".equals(id.getNamespace()) && "mini_ritual".equals(id.getPath())
                && (!customRitualId(stack).isEmpty()
                || !customPentacleId(stack).isEmpty());
    }

    public static boolean isUltimateMiniRitual(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "mekanism_magic".equals(id.getNamespace())
                && "ultimate_mini_ritual".equals(id.getPath());
    }

    public static boolean isRitualProjectionItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && OCCULTISM.equals(id.getNamespace())
                && (id.getPath().startsWith("ritual_dummy_")
                || id.getPath().startsWith("ritual_dummy/"));
    }

    public static boolean isDictionaryOfSpirits(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && OCCULTISM.equals(id.getNamespace())
                && "dictionary_of_spirits".equals(id.getPath());
    }

    public static List<String> ritualChalkColors() {
        return RITUAL_CHALK_COLORS;
    }

    public static boolean isChalkForColor(ItemStack stack, String color) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isUniversalChalk(stack)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && OCCULTISM.equals(id.getNamespace())
                && id.getPath().equals("chalk_" + color);
    }

    public static boolean isAnyChalk(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !OCCULTISM.equals(id.getNamespace())) {
            return false;
        }
        return isUniversalChalk(stack) || RITUAL_CHALK_COLORS.stream()
                .anyMatch(color -> id.getPath().equals("chalk_" + color));
    }

    public static boolean isUniversalChalk(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && OCCULTISM.equals(id.getNamespace())
                && (id.getPath().equals("chalk_rainbow")
                || id.getPath().equals("chalk_void"));
    }

    public static boolean matchesRitualProjectionChalk(RitualProjection projection,
                                                       ItemStackHandler inventory) {
        return matchesChalkColors(ritualChalkColors(projection.multiblock(),
                projection.pentacleId().getPath()), inventory);
    }

    private static boolean matchesChalkColors(Set<String> required,
                                              ItemStackHandler inventory) {
        if (required.isEmpty()) {
            return true;
        }
        for (int slot = NativeMagicMachineBlockEntity.CHALK_SLOT_START;
             slot < NativeMagicMachineBlockEntity.CHALK_SLOT_START
                     + NativeMagicMachineBlockEntity.CHALK_SLOT_COUNT; slot++) {
            if (isUniversalChalk(inventory.getStackInSlot(slot))) {
                return true;
            }
        }
        return required.stream().allMatch(color -> {
            for (int slot = NativeMagicMachineBlockEntity.CHALK_SLOT_START;
                 slot < NativeMagicMachineBlockEntity.CHALK_SLOT_START
                         + NativeMagicMachineBlockEntity.CHALK_SLOT_COUNT; slot++) {
                if (isChalkForColor(inventory.getStackInSlot(slot), color)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static Set<String> ritualChalkColors(Object recipe) {
        Object pentacle = invoke(recipe, "getPentacle").orElse(null);
        Object pentacleId = invoke(recipe, "getPentacleId").orElse(null);
        String pentaclePath = pentacleId instanceof ResourceLocation id
                ? id.getPath() : "";
        return ritualChalkColors(pentacle, pentaclePath);
    }

    private static Set<String> ritualChalkColors(Object pentacle,
                                                 String pentaclePath) {
        return scanPentacle(pentacle, pentaclePath).chalkColors();
    }

    public static Optional<RitualProjection> findProjection(Level level, ItemStack ritualDummy) {
        if (level == null || ritualDummy.isEmpty()) {
            return Optional.empty();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return Optional.empty();
        }
        for (RecipeHolder<?> holder : recipes(level, type)) {
            Object recipe = holder.value();
            Object dummy = invoke(recipe, "getRitualDummy").orElse(null);
            if (dummy instanceof ItemStack stack
                    && ItemStack.isSameItem(ritualDummy, stack)) {
                return projection(holder.id(), recipe);
            }
        }
        return Optional.empty();
    }

    public static Optional<RitualProjection> findProjection(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return Optional.empty();
        }
        for (RecipeHolder<?> holder : recipes(level, type)) {
            if (holder.id().equals(recipeId)) {
                return projection(holder.id(), holder.value());
            }
        }
        return Optional.empty();
    }

    public static Optional<RitualProjection> findProjectionByPentacle(
            Level level, ResourceLocation pentacleId) {
        if (level == null || pentacleId == null) {
            return Optional.empty();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return Optional.empty();
        }
        for (RecipeHolder<?> holder : recipes(level, type)) {
            Object recipe = holder.value();
            Object value = invoke(recipe, "getPentacleId").orElse(null);
            if (pentacleId.equals(value)) {
                return projection(holder.id(), recipe);
            }
        }
        return Optional.empty();
    }

    public static List<PentacleJeiData> pentacleJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        List<PentacleJeiData> result = new ArrayList<>();
        for (PentacleDefinition definition : pentacleDefinitions(level)) {
            result.add(new PentacleJeiData(
                    definition.pentacleId(),
                    copyStacks(definition.materials()),
                    List.copyOf(definition.chalkColors()),
                    definition.output().copy()));
        }
        return List.copyOf(result);
    }

    public static List<RitualJeiData> ritualJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return List.of();
        }
        List<RitualJeiData> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes(level, type)) {
            Object recipe = holder.value();
            if (!isMachineSafeRitual(recipe)) {
                continue;
            }
            Optional<RitualProjection> projection = projection(holder.id(), recipe);
            if (projection.isEmpty()) {
                continue;
            }
            List<Ingredient> ritualIngredients = ingredients(recipe);
            if (ritualIngredients.stream().anyMatch(OccultismRecipeBridge::isEmptyIngredient)) {
                // Optional integrations can leave an unresolved item/tag
                // ingredient. Do not publish a JEI entry that cannot render or
                // be transferred safely.
                continue;
            }
            boolean summons = invoke(recipe, "getEntityToSummon").orElse(null) != null
                    || invoke(recipe, "getEntityTagToSummon").orElse(null) != null;
            String command = stringValue(recipe, "getCommand");
            ItemStack output = command.isBlank()
                    ? summons
                    ? spawnEggResult(level, recipe)
                    : result(level.registryAccess(), recipe)
                    : commandResult(level, recipe);
            if (output.isEmpty()) {
                continue;
            }
            Ingredient activation = invoke(recipe, "getActivationItem")
                    .filter(Ingredient.class::isInstance)
                    .map(Ingredient.class::cast)
                    .orElse(null);
            result.add(new RitualJeiData(holder.id(),
                    projection.get().pentacleId(),
                    List.copyOf(ritualIngredients),
                    activation,
                    sacrificeExamples(recipe),
                    createMiniRitual(projection.get()),
                    output));
        }
        return result;
    }

    public static List<SpiritJeiData> spiritJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        List<SpiritJeiData> result = new ArrayList<>();
        for (String typeId : List.of("spirit_fire", "crushing",
                "crystallize", "spirit_trade")) {
            RecipeType<?> type = recipeType(typeId);
            if (type == null) {
                continue;
            }
            for (RecipeHolder<?> holder : recipes(level, type)) {
                Object recipe = holder.value();
                Ingredient ingredient = ingredients(recipe).stream().findFirst().orElse(null);
                if (ingredient == null || ingredient.getItems().length == 0) {
                    continue;
                }
                ItemStack input = ingredient.getItems()[0].copyWithCount(1);
                ItemStack baseOutput = result(level.registryAccess(), recipe);
                if (baseOutput.isEmpty()) {
                    continue;
                }
                if ("spirit_fire".equals(typeId)) {
                    addSpiritJeiRecipe(result, holder.id(), typeId, input,
                            spiritSource("foliot", ""), baseOutput);
                } else if ("spirit_trade".equals(typeId)) {
                    String trader = stringValue(recipe, "getTrader");
                    String entity = trader.contains("gambler") ? "djinni" : "foliot";
                    addSpiritJeiRecipe(result, holder.id(), typeId, input,
                            spiritSource(entity, trader), baseOutput);
                } else {
                    for (int tier = 1; tier <= 4; tier++) {
                        OccultismSpiritJobConfig.WorkerSettings settings =
                                OccultismSpiritJobConfig.settings(typeId, tier);
                        ItemStack output = spiritOutput(recipe, typeId, baseOutput,
                                settings, 1);
                        String entity = switch (tier) {
                            case 1 -> "foliot";
                            case 2 -> "djinni";
                            case 3 -> "afrit";
                            default -> "marid";
                        };
                        addSpiritJeiRecipe(result, holder.id(), typeId, input,
                                spiritSource(entity, ""), output);
                    }
                }
            }
        }
        return result;
    }

    private static void addSpiritJeiRecipe(List<SpiritJeiData> result,
                                           ResourceLocation id, String type,
                                           ItemStack input, ItemStack spirit,
                                           ItemStack output) {
        result.add(new SpiritJeiData(id, type, input.copy(),
                spirit.copy(), output.copy()));
    }

    private static ItemStack spiritSource(String entityPath, String job) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(OCCULTISM, entityPath);
        EntityType<?> entity = BuiltInRegistries.ENTITY_TYPE.get(id);
        SpawnEggItem egg = SpawnEggItem.byId(entity);
        if (egg == null) {
            return ItemStack.EMPTY;
        }
        ItemStack source = new ItemStack(egg);
        CompoundTag entityData = new CompoundTag();
        entityData.putString("id", id.toString());
        if (!job.isBlank()) {
            CompoundTag spiritJob = new CompoundTag();
            spiritJob.putString("factoryId", job);
            entityData.put("spiritJob", spiritJob);
        }
        source.set(DataComponents.ENTITY_DATA, CustomData.of(entityData));
        return source;
    }

    private static List<ItemStack> pentacleMaterialStacks(Object multiblock) {
        return scanPentacle(multiblock, "").materials();
    }

    private static PentacleScan scanPentacle(Object multiblock,
                                             String pentaclePath) {
        Object sizeValue = invoke(multiblock, "getSize").orElse(null);
        if (!(sizeValue instanceof net.minecraft.core.Vec3i size)) {
            return new PentacleScan(List.of(),
                    DEFAULT_PENTACLE_CHALK_COLORS.getOrDefault(
                            pentaclePath, Set.of()));
        }
        Map<net.minecraft.world.item.Item, Integer> counts = new LinkedHashMap<>();
        Set<String> colors = new LinkedHashSet<>();
        Method getBlockState = findMethod(
                multiblock, "getBlockState", BlockPos.class);
        for (int y = 0; y < size.getY(); y++) {
            Map<net.minecraft.world.item.Item, Integer> layerCounts =
                    new LinkedHashMap<>();
            boolean hasRealMaterial = false;
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockPos position = new BlockPos(x, y, z);
                    Object value = getBlockState == null
                            ? invoke(multiblock, "getBlockState", position)
                            .orElse(null)
                            : invokeMethod(getBlockState, multiblock, position)
                            .orElse(null);
                    if (!(value instanceof BlockState state)) {
                        continue;
                    }
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (id == null) {
                        continue;
                    }
                    if (OCCULTISM.equals(id.getNamespace())
                            && id.getPath().startsWith("chalk_glyph_")) {
                        String color = id.getPath().substring(
                                "chalk_glyph_".length());
                        if (RITUAL_CHALK_COLORS.contains(color)) {
                            colors.add(color);
                        }
                        continue;
                    }
                    net.minecraft.world.item.Item item = state.getBlock().asItem();
                    if (item != net.minecraft.world.item.Items.AIR) {
                        String path = id.getPath();
                        boolean platformMaterial = OCCULTISM.equals(id.getNamespace())
                                && (path.equals("otherstone") || path.equals("otherrock"));
                        if (!platformMaterial) {
                            hasRealMaterial = true;
                        }
                        layerCounts.merge(item, 1, Integer::sum);
                    }
                }
            }
            if (hasRealMaterial) {
                layerCounts.forEach((item, count) ->
                        counts.merge(item, count, Integer::sum));
            }
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : counts.entrySet()) {
            int remaining = entry.getValue();
            int max = entry.getKey().getDefaultMaxStackSize();
            while (remaining > 0) {
                int count = Math.min(remaining, max);
                stacks.add(new ItemStack(entry.getKey(), count));
                remaining -= count;
            }
        }
        Set<String> requiredColors = colors.isEmpty()
                ? DEFAULT_PENTACLE_CHALK_COLORS.getOrDefault(
                pentaclePath, Set.of())
                : Collections.unmodifiableSet(colors);
        return new PentacleScan(List.copyOf(stacks), requiredColors);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> sacrificeExamples(Object recipe) {
        Object value = invoke(recipe, "getEntityToSacrifice").orElse(null);
        if (!(value instanceof TagKey<?> rawTag)) {
            return List.of();
        }
        List<EntityType<?>> candidates = BuiltInRegistries.ENTITY_TYPE
                .getTag((TagKey<EntityType<?>>) rawTag)
                .stream()
                .flatMap(named -> named.stream())
                .map(Holder::value)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        SpawnEggItem egg = SpawnEggItem.byId(candidates.getFirst());
        return egg == null ? List.of() : List.of(new ItemStack(egg));
    }

    private static Optional<RitualProjection> projection(ResourceLocation recipeId, Object recipe) {
        Object pentacleId = invoke(recipe, "getPentacleId").orElse(null);
        Object multiblock = invoke(recipe, "getPentacle").orElse(null);
        if (pentacleId instanceof ResourceLocation id && multiblock != null) {
            return Optional.of(new RitualProjection(recipeId, id, multiblock));
        }
        return Optional.empty();
    }

    private static List<PentacleDefinition> pentacleDefinitions(Level level) {
        if (level == null) {
            return List.of();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return List.of();
        }
        List<RecipeHolder<?>> ritualRecipes = recipes(level, type);
        long fingerprint = recipeFingerprint(ritualRecipes);
        RecipeManager recipeManager = level.getRecipeManager();
        PentacleCatalog cached = PENTACLE_CATALOGS.get(recipeManager);
        if (cached != null && cached.recipeFingerprint() == fingerprint) {
            return cached.definitions();
        }

        Map<ResourceLocation, PentacleDefinition> grouped =
                new LinkedHashMap<>();
        for (RecipeHolder<?> holder : ritualRecipes) {
            Optional<RitualProjection> projection =
                    projection(holder.id(), holder.value());
            if (projection.isEmpty()
                    || grouped.containsKey(projection.get().pentacleId())) {
                continue;
            }
            RitualProjection value = projection.get();
            PentacleScan scan = scanPentacle(
                    value.multiblock(), value.pentacleId().getPath());
            grouped.put(value.pentacleId(), new PentacleDefinition(
                    value.recipeId(), value.pentacleId(),
                    scan.materials(), scan.chalkColors(),
                    createPentacleMiniRitual(value)));
        }
        List<PentacleDefinition> definitions =
                addUniqueRecipeMarkers(List.copyOf(grouped.values()));
        PENTACLE_CATALOGS.put(recipeManager,
                new PentacleCatalog(fingerprint, definitions));
        return definitions;
    }

    private static List<PentacleDefinition> addUniqueRecipeMarkers(
            List<PentacleDefinition> definitions) {
        List<PentacleDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(java.util.Comparator.comparing(
                definition -> definition.pentacleId().toString()));
        Map<ResourceLocation, ItemStack> markers = new LinkedHashMap<>();
        for (int index = 0; index < sorted.size(); index++) {
            net.minecraft.world.item.Item marker =
                    DUPLICATE_RECIPE_MARKERS.get(
                            index % DUPLICATE_RECIPE_MARKERS.size());
            int count = index / DUPLICATE_RECIPE_MARKERS.size() + 1;
            markers.put(sorted.get(index).pentacleId(),
                    new ItemStack(marker, count));
        }
        List<PentacleDefinition> differentiated =
                new ArrayList<>(definitions.size());
        for (PentacleDefinition definition : definitions) {
            ItemStack marker = markers.get(definition.pentacleId());
            List<ItemStack> materials =
                    new ArrayList<>(definition.materials().size() + 1);
            // Put the marker first so it remains visible even for large JEI
            // recipes that fill all sixteen displayed input positions.
            materials.add(marker);
            materials.addAll(copyStacks(definition.materials()));
            differentiated.add(new PentacleDefinition(
                    definition.recipeId(), definition.pentacleId(),
                    List.copyOf(materials), definition.chalkColors(),
                    definition.output()));
        }
        return List.copyOf(differentiated);
    }

    private static long recipeFingerprint(
            List<RecipeHolder<?>> recipes) {
        long fingerprint = recipes.size();
        for (RecipeHolder<?> holder : recipes) {
            fingerprint = 31 * fingerprint + holder.id().hashCode();
            fingerprint = 31 * fingerprint
                    + System.identityHashCode(holder.value());
        }
        return fingerprint;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    public static Optional<MachineRecipeResult> findMiniRitualRecipe(
            Level level, ItemStackHandler inventory) {
        List<MachineRecipeResult> candidates =
                findMiniRitualCandidates(level, inventory);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.getFirst());
    }

    public static List<MachineRecipeResult> findMiniRitualCandidates(
            Level level, ItemStackHandler inventory) {
        List<MachineRecipeResult> candidates = new ArrayList<>();
        for (PentacleDefinition definition : pentacleDefinitions(level)) {
            Optional<List<InputUse>> materialSlots = matchPentacleMaterials(
                    definition.materials(), inventory);
            if (materialSlots.isEmpty()) {
                continue;
            }
            if (!matchesChalkColors(definition.chalkColors(), inventory)) {
                continue;
            }
            candidates.add(new MachineRecipeResult(
                    definition.recipeId(), definition.output().copy(),
                    100, materialSlots.get(), -1));
        }
        return List.copyOf(candidates);
    }

    /**
     * Returns the stable pentacle catalog used by the mini-ritual assembler.
     * Unlike recipe matching, this does not require materials or chalk to be
     * present, so a machine can be configured before it is supplied.
     */
    public static List<ResourceLocation> miniRitualPentacleIds(Level level) {
        return pentacleDefinitions(level).stream()
                .map(PentacleDefinition::pentacleId)
                .toList();
    }

    public static Optional<ResourceLocation> miniRitualPentacle(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != MekanismMagic.MINI_RITUAL.get()) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        String pentacle = data.copyTag().getString("pentacle");
        ResourceLocation id = ResourceLocation.tryParse(pentacle);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    private static Optional<List<InputUse>> matchPentacleMaterials(
            List<ItemStack> required, ItemStackHandler inventory) {
        int[] remaining = new int[NativeMagicMachineBlockEntity.INPUT_SLOTS];
        for (int slot = 0; slot < remaining.length; slot++) {
            remaining[slot] = inventory.getStackInSlot(slot).getCount();
        }
        List<InputUse> matched = new ArrayList<>();
        for (ItemStack requirement : required) {
            int needed = requirement.getCount();
            for (int slot = 0; slot < remaining.length && needed > 0; slot++) {
                ItemStack available = inventory.getStackInSlot(slot);
                if (remaining[slot] <= 0
                        || !ItemStack.isSameItemSameComponents(available, requirement)) {
                    continue;
                }
                int used = Math.min(needed, remaining[slot]);
                remaining[slot] -= used;
                needed -= used;
                addInputUse(matched, slot, used);
            }
            if (needed > 0) {
                return Optional.empty();
            }
        }
        return Optional.of(matched);
    }

    private static void addInputUse(List<InputUse> matched, int slot, int count) {
        for (int index = 0; index < matched.size(); index++) {
            InputUse existing = matched.get(index);
            if (existing.slot() == slot) {
                matched.set(index, new InputUse(slot, existing.count() + count));
                return;
            }
        }
        matched.add(new InputUse(slot, count));
    }

    public static ItemStack createMiniRitual(RitualProjection projection) {
        ItemStack output = new ItemStack(MekanismMagic.MINI_RITUAL.get());
        bindMiniRitual(output, projection);
        return output;
    }

    public static ItemStack createPentacleMiniRitual(RitualProjection projection) {
        return createPentacleMiniRitual(projection.pentacleId());
    }

    public static ItemStack createPentacleMiniRitual(
            ResourceLocation pentacleId) {
        ItemStack output = new ItemStack(MekanismMagic.MINI_RITUAL.get());
        CustomData.update(DataComponents.CUSTOM_DATA, output, tag ->
                tag.putString("pentacle", pentacleId.toString()));
        return output;
    }

    public static void bindMiniRitual(ItemStack stack, RitualProjection projection) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("ritual", projection.recipeId().toString());
            tag.putString("pentacle", projection.pentacleId().toString());
        });
    }

    public static boolean isSacrificeItem(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof SpawnEggItem
                || stack.getItem() instanceof RitualSpawnEggItem
                || isFilledContainment(stack));
    }

    public static boolean isActivationItem(ItemStack stack) {
        return !stack.isEmpty();
    }

    public static boolean consumeSacrifice(ItemStackHandler inventory, int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!isSacrificeItem(stack)) {
            return false;
        }
        if (EntityContainerRegistry.empty(stack)) {
            inventory.setStackInSlot(slot, stack);
        } else {
            inventory.extractItem(slot, 1, false);
        }
        return true;
    }

    public static String spiritType(ItemStack stack) {
        String id = entityId(stack);
        return id.isEmpty() ? "" : id;
    }

    public static boolean isTraderSpirit(ItemStack stack) {
        return TRADER_FACTORY_IDS.contains(spiritJobId(stack));
    }

    public static int spiritTier(ItemStack stack) {
        String id = spiritType(stack);
        if (id.isEmpty()) {
            return 0;
        }
        ResourceLocation entityId = ResourceLocation.tryParse(id);
        if (entityId == null || !OCCULTISM.equals(entityId.getNamespace())) {
            return 0;
        }
        return switch (entityId.getPath()) {
            case "foliot" -> 1;
            case "djinni" -> 2;
            case "afrit", "afrit_wild" -> 3;
            case "marid", "marid_unbound" -> 4;
            default -> 0;
        };
    }

    public static String spiritDisplayName(ItemStack stack) {
        int tier = spiritTier(stack);
        String id = spiritType(stack);
        if (tier <= 0 || id.isEmpty()) {
            return "";
        }
        return switch (tier) {
            case 1 -> "Foliot";
            case 2 -> "Djinni";
            case 3 -> "Afrit";
            case 4 -> "Marid";
            default -> id;
        };
    }

    /**
     * Returns the stable client-side model variant for a bound miniature.
     * The complete ritual recipe id remains in CUSTOM_DATA for server-side
     * matching; the model only needs to represent its pentacle projection.
     */
    public static int miniRitualModelData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return 0;
        }
        String pentacle = data.copyTag().getString("pentacle");
        ResourceLocation id = ResourceLocation.tryParse(pentacle);
        return id == null ? 0 : PENTACLE_MODEL_DATA.getOrDefault(id.getPath(), 0);
    }

    public static net.minecraft.network.chat.Component pentacleDisplayName(
            ResourceLocation pentacleId) {
        if (pentacleId == null) {
            return net.minecraft.network.chat.Component.translatable(
                    "item.mekanism_magic.mini_ritual.pentacle_only");
        }
        String key = "book.occultism.dictionary_of_spirits.pentacles."
                + pentacleId.getPath() + ".name";
        net.minecraft.network.chat.MutableComponent translated =
                net.minecraft.network.chat.Component.translatable(key);
        if (!translated.getString().equals(key)) {
            return translated;
        }
        String fallbackKey = "pentacle.mekanism_magic." + pentacleId.getPath();
        net.minecraft.network.chat.MutableComponent fallback =
                net.minecraft.network.chat.Component.translatable(fallbackKey);
        return fallback.getString().equals(fallbackKey)
                ? net.minecraft.network.chat.Component.literal(pentacleId.getPath())
                : fallback;
    }

    private static String entityId(ItemStack stack) {
        if (stack.getItem() instanceof SpawnEggItem egg) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(egg.getType(stack));
            return id == null ? "" : id.toString();
        }
        return EntityContainerRegistry.capturedEntity(stack)
                .map(CapturedEntity::entityId)
                .map(ResourceLocation::toString)
                .orElse("");
    }

    private static String spiritJobId(ItemStack stack) {
        return EntityContainerRegistry.capturedEntity(stack)
                .map(CapturedEntity::entityData)
                .map(tag -> tag.getCompound("spiritJob")
                        .getString("factoryId"))
                .orElse("");
    }

    private static String customRitualId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return "";
        }
        return data.copyTag().getString("ritual");
    }

    private static String customPentacleId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return "";
        }
        return data.copyTag().getString("pentacle");
    }

    private static boolean isMachineSafeRitual(Object recipe) {
        Object typeValue = invoke(recipe, "getRitualType").orElse(null);
        if (typeValue instanceof ResourceLocation type
                && isSpecialEffectRitual(type.getPath())) {
            return false;
        }
        return true;
    }

    private static boolean isSpecialEffectRitual(String ritualType) {
        return ritualType.equals("repair")
                || ritualType.equals("resurrect_familiar")
                || ritualType.equals("craft_with_spirit_name")
                || ritualType.equals("summon_with_chance_of_chicken_tamed");
    }

    private static boolean matchesActivation(Object recipe, ItemStack activation,
                                             boolean dictionaryEnabled) {
        Object value = invoke(recipe, "getActivationItem").orElse(null);
        if (!(value instanceof Ingredient ingredient) || ingredient.isEmpty()) {
            return true;
        }
        return ingredient.test(activation)
                || dictionaryEnabled && matchesUncraftedBook(ingredient, activation);
    }

    private static boolean matchesUncraftedBook(Ingredient ingredient, ItemStack activation) {
        if (activation.isEmpty()) {
            return false;
        }
        ResourceLocation activationId = BuiltInRegistries.ITEM.getKey(activation.getItem());
        if (activationId == null || !OCCULTISM.equals(activationId.getNamespace())) {
            return false;
        }
        for (ItemStack candidate : ingredient.getItems()) {
            ResourceLocation requiredId = BuiltInRegistries.ITEM.getKey(candidate.getItem());
            if (requiredId == null || !OCCULTISM.equals(requiredId.getNamespace())) {
                continue;
            }
            String prefix = "book_of_binding_bound_";
            if (!requiredId.getPath().startsWith(prefix)) {
                continue;
            }
            String spirit = requiredId.getPath().substring(prefix.length());
            if (activationId.getPath().equals("book_of_binding_" + spirit)) {
                return true;
            }
        }
        return false;
    }

    private static boolean activationRequired(Object recipe) {
        Object value = invoke(recipe, "getActivationItem").orElse(null);
        return value instanceof Ingredient ingredient && !ingredient.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static ItemStack spawnEggResult(Level level, Object recipe) {
        EntityType<?> type = invoke(recipe, "getEntityToSummon")
                .filter(EntityType.class::isInstance)
                .map(EntityType.class::cast)
                .orElse(null);
        TagKey<EntityType<?>> entityTag = null;
        if (type == null) {
            Object tagValue = invoke(recipe, "getEntityTagToSummon").orElse(null);
            if (tagValue instanceof TagKey<?> rawTag) {
                entityTag = (TagKey<EntityType<?>>) rawTag;
            }
        }
        if (type == null && entityTag == null) {
            return ItemStack.EMPTY;
        }

        CompoundTag entityData = invoke(recipe, "getEntityNbt")
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .map(CompoundTag::copy)
                .orElseGet(CompoundTag::new);
        Object jobValue = invoke(recipe, "getSpiritJobType").orElse(null);
        if (jobValue instanceof ResourceLocation jobId) {
            CompoundTag spiritJob = entityData.getCompound("spiritJob");
            spiritJob.putString("factoryId", jobId.toString());
            entityData.put("spiritJob", spiritJob);
        }
        int count = Math.max(1, intValue(recipe, "getSummonNumber", 1));
        ItemStack output;
        if (entityTag != null) {
            // Keep the tag on the generic egg and defer random selection until
            // the player uses it. A recipe NBT id would otherwise make the egg
            // resolve as a fixed entity and defeat tag-based randomness.
            entityData.remove("id");
            output = RitualSpawnEggItem.forTag(entityTag, entityData);
        } else {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (entityId != null) {
                entityData.putString("id", entityId.toString());
            }
            SpawnEggItem egg = SpawnEggItem.byId(type);
            ItemStack recipeResult = result(level.registryAccess(), recipe);
            output = recipeResult.getItem() instanceof SpawnEggItem
                    ? recipeResult.copy() : egg == null
                    ? RitualSpawnEggItem.forEntity(type, entityData)
                    : new ItemStack(egg);
            output.set(DataComponents.ENTITY_DATA, CustomData.of(entityData));
        }
        output.setCount(Math.min(count, output.getMaxStackSize()));
        return output;
    }

    private static ItemStack commandResult(Level level, Object recipe) {
        ResourceLocation flameAutomation = ResourceLocation.fromNamespaceAndPath(
                OCCULTISM, "flame_automation");
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(flameAutomation);
        if (item != Items.AIR) {
            return new ItemStack(item);
        }
        return result(level.registryAccess(), recipe);
    }

    public static boolean executeCommandRitual(ServerLevel level, BlockPos pos,
                                                String command) {
        if (level == null || level.getServer() == null
                || command == null || command.isBlank()) {
            return true;
        }
        try {
            Component name = Component.literal("@");
            CommandSourceStack source = new CommandSourceStack(
                    CommandSource.NULL,
                    Vec3.atCenterOf(pos),
                    Vec2.ZERO,
                    level,
                    2,
                    name.getString(),
                    name,
                    level.getServer(),
                    null);
            level.getServer().getCommands().performPrefixedCommand(source, command);
            return true;
        } catch (Throwable ignored) {
            // A malformed or disallowed datapack command must not crash the
            // server. The machine simply leaves its inputs untouched.
            return false;
        }
    }

    private static Optional<List<InputUse>> matchRitualIngredients(Object recipe,
                                                                   ItemStackHandler inventory) {
        List<Ingredient> ingredients = ingredients(recipe);
        int[] remaining = new int[NativeMagicMachineBlockEntity.INPUT_SLOTS];
        int[] used = new int[NativeMagicMachineBlockEntity.INPUT_SLOTS];
        for (int slot = 0; slot < remaining.length; slot++) {
            remaining[slot] = inventory.getStackInSlot(slot).getCount();
        }
        if (!matchRitualIngredients(ingredients, 0, inventory, remaining, used)) {
            return Optional.empty();
        }
        List<InputUse> matched = new ArrayList<>();
        for (int slot = 0; slot < used.length; slot++) {
            if (used[slot] > 0) {
                matched.add(new InputUse(slot, used[slot]));
            }
        }
        return Optional.of(matched);
    }

    private static boolean matchRitualIngredients(List<Ingredient> ingredients, int index,
                                                  ItemStackHandler inventory, int[] remaining,
                                                  int[] used) {
        if (index >= ingredients.size()) {
            return true;
        }
        Ingredient ingredient = ingredients.get(index);
        for (int slot = 0; slot < remaining.length; slot++) {
            if (remaining[slot] <= 0
                    || !ingredient.test(inventory.getStackInSlot(slot))) {
                continue;
            }
            remaining[slot]--;
            used[slot]++;
            if (matchRitualIngredients(ingredients, index + 1,
                    inventory, remaining, used)) {
                return true;
            }
            used[slot]--;
            remaining[slot]++;
        }
        return false;
    }

    private static boolean matchesRitualSelector(ResourceLocation recipeId, Object recipe,
                                                  ItemStack selector) {
        if (!isRitualSelector(selector)) {
            return false;
        }
        if (isUltimateMiniRitual(selector)) {
            return true;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(selector.getItem());
        if (itemId != null && "mekanism_magic".equals(itemId.getNamespace())
                && "mini_ritual".equals(itemId.getPath())) {
            String ritualId = customRitualId(selector);
            if (!ritualId.isEmpty()) {
                return recipeId.toString().equals(ritualId);
            }
            String pentacleId = customPentacleId(selector);
            Object recipePentacle = invoke(recipe, "getPentacleId").orElse(null);
            return recipePentacle instanceof ResourceLocation id
                    && id.toString().equals(pentacleId);
        }
        Object dummy = invoke(recipe, "getRitualDummy").orElse(null);
        return dummy instanceof ItemStack ritualDummy
                && ItemStack.isSameItem(selector, ritualDummy);
    }

    @SuppressWarnings("unchecked")
    private static boolean matchesSacrifice(Object recipe, ItemStack sacrifice) {
        if (!isSacrificeItem(sacrifice)) {
            return false;
        }
        Object tagValue = invoke(recipe, "getEntityToSacrifice").orElse(null);
        if (!(tagValue instanceof TagKey<?> tag)) {
            return false;
        }
        ResourceLocation customTag = RitualSpawnEggItem.entityTag(sacrifice);
        if (customTag != null && customTag.equals(tag.location())) {
            return true;
        }
        EntityType<?> entityType = entityType(sacrifice);
        return entityType != null && entityType.is((TagKey<EntityType<?>>) tag);
    }

    private static EntityType<?> entityType(ItemStack stack) {
        if (stack.getItem() instanceof SpawnEggItem egg) {
            return egg.getType(stack);
        }
        String id = entityId(stack);
        ResourceLocation entityId = ResourceLocation.tryParse(id);
        return entityId == null ? null : BuiltInRegistries.ENTITY_TYPE.get(entityId);
    }

    private static Optional<Match> matchSingleRecipe(Level level, Object recipe,
                                                     ItemStack input, int spiritTier,
                                                     String sourceJob, String typeId) {
        if (input.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object recipeInput = new SingleRecipeInput(input.copy());
            Method matches = findMethod(recipe, "matches", recipeInput.getClass(), Level.class);
            if (matches != null && (boolean) matches.invoke(recipe, recipeInput, level)) {
                return Optional.of(new Match());
            }
            if ("spirit_trade".equals(typeId)) {
                Class<?> traderInput = Class.forName(
                        "com.klikli_dev.occultism.crafting.recipe.TraderRecipeInput");
                Constructor<?> constructor = traderInput.getConstructor(ItemStack.class, String.class);
                String trader = stringValue(recipe, "getTrader");
                if (trader.isBlank() || !canUseTrader(sourceJob, trader)) {
                    return Optional.empty();
                }
                Object trade = constructor.newInstance(input.copy(), trader);
                Method tradeMatches = findMethod(recipe, "matches", trade.getClass(), Level.class);
                if (tradeMatches != null && (boolean) tradeMatches.invoke(recipe, trade, level)) {
                    return Optional.of(new Match());
                }
            }
            // Occultism's crushing/crystallize recipes use (ItemStack, tier).
            String name = recipe.getClass().getName();
            if (name.contains("CrushingRecipe") || name.contains("CrystallizeRecipe")) {
                Class<?> tiered = Class.forName(
                        "com.klikli_dev.occultism.crafting.recipe.TieredSingleRecipeInput");
                Constructor<?> constructor = tiered.getConstructor(ItemStack.class, int.class);
                Object tieredInput = constructor.newInstance(input.copy(), spiritTier);
                Method tieredMatches = findMethod(recipe, "matches", tiered, Level.class);
                if (tieredMatches != null && (boolean) tieredMatches.invoke(recipe, tieredInput, level)) {
                    return Optional.of(new Match());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A missing optional class or a recipe with a newer signature is
            // simply skipped; the addon must remain loadable without it.
        }
        return Optional.empty();
    }

    private static boolean canUseTrader(String sourceJob, String trader) {
        // Entity type alone is insufficient: all Foliots and Djinn share their
        // base entity types. Only the bound spirit job identifies a trader.
        return !sourceJob.isBlank() && sourceJob.equals(trader);
    }

    private static List<Ingredient> ingredients(Object recipe) {
        Object value = invoke(recipe, "getIngredients").orElse(null);
        List<Ingredient> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Ingredient ingredient) {
                    result.add(ingredient);
                }
            }
        }
        // Occultism's item_to_use is a player interaction in the original
        // ritual. For the machine it becomes an ordinary consumable input so
        // all item-use rituals remain deterministic and automatable.
        Object itemToUse = invoke(recipe, "getItemToUse").orElse(null);
        if (itemToUse instanceof Ingredient ingredient && !ingredient.isEmpty()) {
            result.add(ingredient);
        }
        return result;
    }

    private static boolean isEmptyIngredient(Ingredient ingredient) {
        return ingredient == null || ingredient.isEmpty()
                || ingredient.getItems().length == 0;
    }

    private static ItemStack result(HolderLookup.Provider registries, Object recipe) {
        Object result = invoke(recipe, "getResultItem", registries).orElse(null);
        return result instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
    }

    private static int spiritOperations(Object recipe, String typeId,
                                        ItemStack input, ItemStack baseOutput,
                                        OccultismSpiritJobConfig.WorkerSettings settings) {
        int requested = switch (typeId) {
            case "spirit_fire" -> input.getCount();
            case "crushing", "crystallize" -> settings.operationCount();
            default -> 1;
        };
        requested = Math.min(input.getCount(), Math.max(0, requested));
        if (requested <= 1) {
            return requested;
        }
        int maxStackSize = baseOutput.getMaxStackSize();
        for (int operations = requested; operations >= 1; operations--) {
            int outputCount = switch (typeId) {
                case "spirit_fire" -> operations;
                case "crushing", "crystallize" ->
                        scaledWorkerCount(baseOutput.getCount(), operations,
                                workerOutputMultiplier(recipe, typeId, settings));
                default -> baseOutput.getCount() * operations;
            };
            if (outputCount <= maxStackSize) {
                return operations;
            }
        }
        return 1;
    }

    private static ItemStack spiritOutput(Object recipe, String typeId, ItemStack baseOutput,
                                          OccultismSpiritJobConfig.WorkerSettings settings,
                                          int operations) {
        ItemStack output = baseOutput.copy();
        if ("spirit_fire".equals(typeId)) {
            // SpiritFireRecipe#assemble mirrors the input stack count.
            output.setCount(operations);
        } else if ("crushing".equals(typeId) || "crystallize".equals(typeId)) {
            float multiplier = workerOutputMultiplier(recipe, typeId, settings);
            output.setCount(scaledWorkerCount(baseOutput.getCount(), operations, multiplier));
        } else if (operations > 1) {
            output.setCount(baseOutput.getCount() * operations);
        }
        return output;
    }

    private static float workerOutputMultiplier(Object recipe, String typeId,
                                                OccultismSpiritJobConfig.WorkerSettings settings) {
        String ignoreGetter = "crushing".equals(typeId)
                ? "getIgnoreCrushingMultiplier"
                : "getIgnoreCrystallizeMultiplier";
        return booleanValue(recipe, ignoreGetter) ? 1.0F : settings.outputMultiplier();
    }

    private static int scaledWorkerCount(int baseCount, int operations, float multiplier) {
        // Match Occultism's ItemStack#setCount((int) (base * operations * multiplier)).
        return (int) (baseCount * operations * multiplier);
    }

    private static int recipeDuration(Object recipe, String typeId,
                                      OccultismSpiritJobConfig.WorkerSettings settings) {
        String getter = switch (typeId) {
            case "crushing" -> "getCrushingTime";
            case "crystallize" -> "getCrystallizeTime";
            default -> "";
        };
        int baseDuration = getter.isBlank() ? 80 : Math.max(1, intValue(recipe, getter, 80));
        if ("crushing".equals(typeId) || "crystallize".equals(typeId)) {
            return Math.max(1, (int) Math.ceil(baseDuration * settings.timeMultiplier()));
        }
        return baseDuration;
    }

    private static RecipeType<?> recipeType(String path) {
        return BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.fromNamespaceAndPath(OCCULTISM, path));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(Level level, RecipeType<?> type) {
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    private static Method findMethod(Object target, String name, Class<?>... parameterTypes) {
        try {
            return target.getClass().getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Optional<Object> invokeMethod(Method method,
                                                 Object target,
                                                 Object... args) {
        if (method == null || target == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(method.invoke(target, args));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> invoke(Object target, String method, Object... args) {
        if (target == null) {
            return Optional.empty();
        }
        for (Method candidate : target.getClass().getMethods()) {
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != args.length) {
                continue;
            }
            try {
                return Optional.ofNullable(candidate.invoke(target, args));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean booleanValue(Object target, String method) {
        return invoke(target, method).filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false);
    }

    private static int intValue(Object target, String method, int fallback) {
        return invoke(target, method).filter(Number.class::isInstance)
                .map(Number.class::cast).map(Number::intValue).orElse(fallback);
    }

    private static String stringValue(Object target, String method) {
        return invoke(target, method).map(Object::toString).orElse("");
    }

    private record Match() {
    }
}
