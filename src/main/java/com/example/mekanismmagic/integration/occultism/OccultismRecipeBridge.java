package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.BoundedSlotMatcher;
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
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

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
    private static final Set<String> COOKING_RECIPE_TYPES = Set.of(
            "smelting", "blasting", "smoking", "campfire_cooking");
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
            RECIPE_MARKER_DYES = List.of(
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
    private static final Map<RecipeManager, RitualCatalog> RITUAL_CATALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<RecipeManager, MinerCatalog> MINER_CATALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, Method> MINER_PROPERTY_METHODS =
            new HashMap<>();
    private static final Set<ResourceLocation> WARNED_RITUAL_TYPES =
            ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_RITUAL_ADAPTER_FAILURES =
            ConcurrentHashMap.newKeySet();
    private static final ResourceLocation SPIRIT_NAME_COMPONENT =
            ResourceLocation.fromNamespaceAndPath(OCCULTISM,
                    "spirit_name");
    private static final AtomicLong RECIPE_REVISION = new AtomicLong();
    private static final AtomicLong SPIRIT_CONFIG_REVISION = new AtomicLong();
    private static final AtomicLong SPIRIT_PROCESSING_REVISION =
            new AtomicLong();
    private static final int MAX_RITUAL_AUTOMATION_UNITS =
            (NativeMagicMachineBlockEntity.INPUT_SLOTS + 2) * 64;

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

    private record RitualEntry(ResourceLocation recipeId, Object recipe,
                               Optional<OccultismRitualMachineMode> mode,
                               Optional<RitualProjection> projection) {
    }

    private record RitualCatalog(
            long revision,
            List<RitualEntry> entries,
            Map<ResourceLocation, RitualEntry> byRecipeId,
            Map<ResourceLocation, List<RitualEntry>> byPentacleId,
            Map<net.minecraft.world.item.Item, List<RitualEntry>> byDummyItem) {
    }

    private record PentacleCatalog(long recipeRevision,
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
                                List<MinerCandidate> candidates,
                                List<MinerSelection> selections) {
    }

    private record MinerSelection(ItemStack miner,
                                  List<MinerCandidate> candidates,
                                  long totalWeight) {
    }

    public record RitualJeiData(ResourceLocation recipeId,
                                ResourceLocation pentacleId,
                                List<Ingredient> ingredients,
                                Ingredient activation,
                                List<ItemStack> sacrifices,
                                ItemStack selector,
                                ItemStack output) {
    }

    public enum RitualAutomationRole {
        ACTIVATION,
        SACRIFICE,
        MATERIAL
    }

    public record RitualAutomationInput(ItemStack stack, int amount) {
        public RitualAutomationInput {
            stack = stack == null ? ItemStack.EMPTY
                    : stack.copyWithCount(1);
            amount = Math.max(0, amount);
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }

    public record RitualAutomationAllocation(
            int inputIndex, RitualAutomationRole role, int amount) {
    }

    public record RitualAutomationPlan(
            ResourceLocation recipeId,
            List<RitualAutomationAllocation> allocations,
            ItemStack output, int copies) {
        public RitualAutomationPlan {
            allocations = allocations == null ? List.of()
                    : List.copyOf(allocations);
            output = output == null ? ItemStack.EMPTY : output.copy();
            copies = Math.max(1, copies);
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }

    public record SpiritJeiData(ResourceLocation recipeId, String recipeType,
                                Ingredient input, ItemStack spirit,
                                ItemStack output, int weight) {
    }

    /** Runtime spirit-machine result plus whether this input has >1 draw. */
    public record SpiritMachineRecipe(MachineRecipeResult recipe,
                                      boolean randomTrade) {
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
            Method getter = minerPropertyMethod(method);
            Object value = getter.invoke(null, miner);
            return value instanceof Number number
                    ? Math.max(1, number.intValue()) : fallback;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static Method minerPropertyMethod(String name)
            throws ReflectiveOperationException {
        synchronized (MINER_PROPERTY_METHODS) {
            Method cached = MINER_PROPERTY_METHODS.get(name);
            if (cached != null) {
                return cached;
            }
            Class<?> mineshaft = Class.forName(
                    "com.klikli_dev.occultism.common.blockentity."
                            + "DimensionalMineshaftBlockEntity");
            Method resolved = mineshaft.getMethod(name, ItemStack.class);
            MINER_PROPERTY_METHODS.put(name, resolved);
            return resolved;
        }
    }

    public static Optional<MinerOutput> findMinerOutput(Level level, ItemStack miner) {
        List<MinerOutput> outputs = rollMinerOutputs(level, miner, 1);
        return outputs.isEmpty()
                ? Optional.empty() : Optional.of(outputs.getFirst());
    }

    /**
     * Rolls several dimensional-miner outputs from one prepared candidate
     * table. A high-speed miner must not rebuild and refilter the complete
     * Occultism recipe catalog separately for every roll.
     */
    public static List<MinerOutput> rollMinerOutputs(
            Level level, ItemStack miner, int rolls) {
        if (level == null || !isMinerItem(miner)) {
            return List.of();
        }
        int requestedRolls = Math.max(0, rolls);
        if (requestedRolls == 0) {
            return List.of();
        }
        RecipeType<?> type = recipeType("miner");
        if (type == null) {
            return List.of();
        }
        MinerSelection selection = minerSelection(level, type, miner);
        if (selection.candidates().isEmpty()
                || selection.totalWeight() <= 0) {
            return List.of();
        }
        List<MinerOutput> outputs = new ArrayList<>(requestedRolls);
        for (int index = 0; index < requestedRolls; index++) {
            long roll = Math.floorMod(
                    level.random.nextLong(), selection.totalWeight());
            MinerCandidate selected = selection.candidates().getLast();
            for (MinerCandidate candidate : selection.candidates()) {
                roll -= candidate.weight();
                if (roll < 0) {
                    selected = candidate;
                    break;
                }
            }
            outputs.add(new MinerOutput(selected.recipeId(),
                    selected.output().copy(), selected.weight()));
        }
        return List.copyOf(outputs);
    }

    private static MinerSelection minerSelection(
            Level level, RecipeType<?> type, ItemStack miner) {
        List<MinerCandidate> candidates = minerCandidates(level, type);
        MinerCatalog catalog = MINER_CATALOGS.get(
                level.getRecipeManager());
        if (catalog != null) {
            synchronized (catalog.selections()) {
                for (MinerSelection selection : catalog.selections()) {
                    if (ItemStack.isSameItemSameComponents(
                            selection.miner(), miner)) {
                        return selection;
                    }
                }
            }
        }
        long totalWeight = 0L;
        List<MinerCandidate> matching = new ArrayList<>();
        for (MinerCandidate candidate : candidates) {
            if (candidate.ingredient().test(miner)) {
                matching.add(candidate);
                totalWeight += candidate.weight();
            }
        }
        MinerSelection created = new MinerSelection(
                miner.copyWithCount(1), List.copyOf(matching), totalWeight);
        if (catalog != null) {
            synchronized (catalog.selections()) {
                // Miner variants are normally a tiny fixed set. Keep a hard
                // ceiling for unusual component-heavy integration packs.
                if (catalog.selections().size() >= 32) {
                    catalog.selections().removeFirst();
                }
                catalog.selections().add(created);
            }
        }
        return created;
    }

    private static List<MinerCandidate> minerCandidates(Level level,
                                                         RecipeType<?> type) {
        RecipeManager recipeManager = level.getRecipeManager();
        MinerCatalog cached = MINER_CATALOGS.get(recipeManager);
        if (cached != null) {
            return cached.candidates();
        }
        List<RecipeHolder<?>> holders = recipes(level, type);
        long fingerprint = recipeFingerprint(holders);
        List<MinerCandidate> candidates = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            Object recipe = holder.value();
            List<Ingredient> recipeIngredients = ingredients(recipe);
            Ingredient ingredient = recipeIngredients.isEmpty()
                    ? null : recipeIngredients.getFirst();
            if (ingredient == null || ingredient.isEmpty()
                    || !isExecutableMinerRecipe(holder.id(), ingredient)) {
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
                new MinerCatalog(fingerprint, immutable,
                        new ArrayList<>()));
        return immutable;
    }

    /** Clears catalogs after a server recipe/datapack synchronization. */
    public static void invalidateRecipeCaches() {
        PENTACLE_CATALOGS.clear();
        RITUAL_CATALOGS.clear();
        MINER_CATALOGS.clear();
        incrementRevision(RECIPE_REVISION);
        incrementRevision(SPIRIT_PROCESSING_REVISION);
        requestJeiRuntimeRefresh();
    }

    public static long recipeRevision() {
        return RECIPE_REVISION.get();
    }

    /** Called for an Occultism server-config reload. */
    public static void invalidateSpiritJobConfig() {
        incrementRevision(SPIRIT_CONFIG_REVISION);
        incrementRevision(SPIRIT_PROCESSING_REVISION);
        requestJeiRuntimeRefresh();
    }

    /** Invalidates ritual timing cached by active machines. */
    public static void invalidateRitualConfig() {
        incrementRevision(RECIPE_REVISION);
        requestJeiRuntimeRefresh();
    }

    public static long spiritJobConfigRevision() {
        return SPIRIT_CONFIG_REVISION.get();
    }

    public static long spiritProcessingRevision() {
        return SPIRIT_PROCESSING_REVISION.get();
    }

    private static void incrementRevision(AtomicLong revision) {
        revision.updateAndGet(current -> current == Long.MAX_VALUE
                ? 1L : current + 1L);
    }

    /**
     * JEI has a runtime mutation API but no common-side reload event. Keep the
     * bridge server-safe and ask the optional client plugin to reconcile its
     * displayed snapshots only when this JVM actually owns a client.
     */
    private static void requestJeiRuntimeRefresh() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class.forName("com.example.mekanismmagic.integration.jei."
                            + "MekanismMagicJeiPlugin")
                    .getMethod("requestOccultismRuntimeRefresh")
                    .invoke(null);
        } catch (ClassNotFoundException ignored) {
            // JEI is optional.
        } catch (NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException | LinkageError failure) {
            MekanismMagic.LOGGER.warn(
                    "Could not refresh Occultism recipes in the active JEI "
                            + "runtime", failure);
        }
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
            if (recipeIngredients.isEmpty()
                    || isEmptyIngredient(recipeIngredients.getFirst())
                    || !isExecutableMinerRecipe(holder.id(),
                    recipeIngredients.getFirst())) {
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

    private static boolean isExecutableMinerRecipe(
            ResourceLocation recipeId, Ingredient ingredient) {
        if (recipeId == null || ingredient == null
                || recipeId.getPath().startsWith("miner/debug_")) {
            return false;
        }
        for (ItemStack candidate : ingredient.getItems()) {
            if (isMinerItem(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<MachineRecipeResult> findSpiritRecipe(
            Level level, ItemStackHandler inventory, ItemStack containment) {
        return findSpiritRecipe(level, inventory, containment, 0L);
    }

    public static Optional<MachineRecipeResult> findSpiritRecipe(
            Level level, ItemStackHandler inventory, ItemStack containment,
            long selectionSeed) {
        return findSpiritMachineRecipe(level, inventory, containment,
                selectionSeed).map(SpiritMachineRecipe::recipe);
    }

    public static Optional<SpiritMachineRecipe> findSpiritMachineRecipe(
            Level level, ItemStackHandler inventory, ItemStack containment,
            long selectionSeed) {
        if (level == null) {
            return Optional.empty();
        }
        int sourceTier = spiritTier(containment);
        if (sourceTier <= 0) {
            return Optional.empty();
        }
        ItemStack input = inventory.getStackInSlot(0);
        String sourceJob = OccultismSpiritJobPolicy.normalizeJobId(
                spiritJobId(containment));
        List<String> recipeOrder = OccultismSpiritJobPolicy.recipeOrder(
                sourceJob, sourceTier);
        for (String typeId : recipeOrder) {
            if (!OccultismSpiritJobPolicy.permits(
                    sourceJob, sourceTier, typeId)) {
                continue;
            }
            if ("spirit_trade".equals(typeId)) {
                Optional<SpiritMachineRecipe> trade = findSpiritTradeRecipe(
                        level, input, sourceTier, sourceJob,
                        selectionSeed, containment);
                if (trade.isPresent()) {
                    return trade;
                }
                continue;
            }
            RecipeType<?> type = recipeType(typeId);
            if (type == null) {
                continue;
            }
            OccultismSpiritJobConfig.WorkerSettings settings =
                    OccultismSpiritJobConfig.settings(typeId, sourceTier);
            for (RecipeHolder<?> holder : matchingSingleRecipes(
                    level, type, typeId, input,
                    settings.recipeTier())) {
                Object recipe = holder.value();
                Optional<Match> match = matchSingleRecipe(level, recipe,
                        input, sourceTier, settings.recipeTier(),
                        sourceJob, typeId);
                if (match.isPresent()) {
                    ItemStack baseOutput = singleRecipeResult(level, recipe,
                            input, typeId, settings.recipeTier());
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
                    return Optional.of(new SpiritMachineRecipe(
                            new MachineRecipeResult(holder.id(), output,
                                    recipeDuration(recipe, typeId, settings),
                                    List.of(new InputUse(0, operations)), -1),
                            false));
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> matchingSingleRecipes(
            Level level, RecipeType<?> type, String typeId,
            ItemStack input, int recipeTier) {
        if (level == null || type == null || input.isEmpty()) {
            return List.of();
        }
        try {
            Object rawInput;
            if ("crushing".equals(typeId)
                    || "crystallize".equals(typeId)) {
                Class<?> tiered = Class.forName(
                        "com.klikli_dev.occultism.crafting.recipe."
                                + "TieredSingleRecipeInput");
                rawInput = tiered.getConstructor(ItemStack.class, int.class)
                        .newInstance(input.copy(), recipeTier);
            } else {
                rawInput = new SingleRecipeInput(input.copy());
            }
            if (rawInput instanceof RecipeInput recipeInput) {
                Optional<RecipeHolder<?>> match = (Optional) level
                        .getRecipeManager().getRecipeFor(
                                (RecipeType) type, recipeInput, level);
                if (match.isPresent()) {
                    return List.of(match.get());
                }
                return List.of();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to the compatibility scan below.
        }
        return recipes(level, type);
    }

    private static Optional<SpiritMachineRecipe> findSpiritTradeRecipe(
            Level level, ItemStack input, int sourceTier,
            String sourceJob, long selectionSeed,
            ItemStack containment) {
        RecipeType<?> type = recipeType("spirit_trade");
        if (type == null || input.isEmpty()) {
            return Optional.empty();
        }
        List<WeightedTradeCandidate> candidates = weightedTradeCandidates(
                level, type, input, sourceTier, sourceJob);
        OccultismSpiritJobConfig.TraderSettings traderSettings =
                traderSettings(containment, sourceJob);
        if (traderSettings.operationCount() <= 0) {
            return Optional.empty();
        }
        boolean randomTrade = OccultismSpiritJobPolicy
                .requiresRandomTrade(candidates.size());
        WeightedTradeCandidate selected = selectWeightedTrade(
                candidates, stableTradeSeed(selectionSeed, sourceJob));
        if (selected == null) {
            return Optional.empty();
        }
        OccultismSpiritJobConfig.WorkerSettings settings =
                new OccultismSpiritJobConfig.WorkerSettings(
                        sourceTier, 1.0F, 1.0F,
                        randomTrade ? 1
                                : traderSettings.operationCount());
        int operations = spiritOperations(null, "spirit_trade", input,
                selected.output(), settings);
        if (operations <= 0) {
            return Optional.empty();
        }
        ItemStack output = spiritOutput(null, "spirit_trade",
                selected.output(), settings, operations);
        int duration = randomTrade
                ? OccultismSpiritJobPolicy.singleTradeDuration(
                traderSettings.operationTicks(),
                traderSettings.operationCount())
                : traderSettings.operationTicks();
        return Optional.of(new SpiritMachineRecipe(
                new MachineRecipeResult(selected.recipeId(), output,
                        duration, List.of(new InputUse(0, operations)), -1),
                randomTrade));
    }

    private static List<WeightedTradeCandidate> weightedTradeCandidates(
            Level level, RecipeType<?> type, ItemStack input,
            int sourceTier, String sourceJob) {
        List<WeightedTradeCandidate> candidates = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes(level, type)) {
            Object recipe = holder.value();
            if (matchSingleRecipe(level, recipe, input, sourceTier,
                    sourceTier, sourceJob,
                    "spirit_trade").isEmpty()) {
                continue;
            }
            ItemStack output = result(level.registryAccess(), recipe);
            int weight = weightedTradeWeight(recipe);
            if (!output.isEmpty() && weight > 0) {
                candidates.add(new WeightedTradeCandidate(
                        holder.id(), output, weight));
            }
        }
        candidates.sort(java.util.Comparator.comparing(candidate ->
                candidate.recipeId().toString()));
        return List.copyOf(candidates);
    }

    private static OccultismSpiritJobConfig.TraderSettings traderSettings(
            ItemStack containment, String sourceJob) {
        OccultismSpiritJobConfig.TraderSettings configured =
                OccultismSpiritJobConfig.traderSettings(sourceJob);
        CompoundTag job = spiritEntityData(containment)
                .map(tag -> tag.getCompound("spiritJob"))
                .orElseGet(CompoundTag::new);
        int ticks = job.contains("timeToConvert", Tag.TAG_INT)
                ? Math.max(1, job.getInt("timeToConvert"))
                : configured.operationTicks();
        int operations = job.contains("maxTradesPerRound", Tag.TAG_INT)
                ? Math.max(0, job.getInt("maxTradesPerRound"))
                : configured.operationCount();
        return new OccultismSpiritJobConfig.TraderSettings(
                ticks, operations);
    }

    private static RitualCatalog ritualCatalog(Level level) {
        RecipeManager manager = level.getRecipeManager();
        long revision = recipeRevision();
        RitualCatalog cached = RITUAL_CATALOGS.get(manager);
        if (cached != null && cached.revision() == revision) {
            return cached;
        }
        synchronized (RITUAL_CATALOGS) {
            cached = RITUAL_CATALOGS.get(manager);
            if (cached != null && cached.revision() == revision) {
                return cached;
            }
            RecipeType<?> type = recipeType("ritual");
            if (type == null) {
                RitualCatalog empty = new RitualCatalog(revision,
                        List.of(), Map.of(), Map.of(), Map.of());
                RITUAL_CATALOGS.put(manager, empty);
                return empty;
            }

            List<RitualEntry> entries = new ArrayList<>();
            Map<ResourceLocation, RitualEntry> byRecipeId =
                    new LinkedHashMap<>();
            Map<ResourceLocation, List<RitualEntry>> byPentacleId =
                    new LinkedHashMap<>();
            Map<net.minecraft.world.item.Item, List<RitualEntry>> byDummyItem =
                    new LinkedHashMap<>();
            for (RecipeHolder<?> holder : recipes(level, type)) {
                Object recipe = holder.value();
                Optional<RitualProjection> projection =
                        projection(holder.id(), recipe);
                RitualEntry entry = new RitualEntry(holder.id(), recipe,
                        machineMode(recipe), projection);
                entries.add(entry);
                byRecipeId.put(holder.id(), entry);
                projection.ifPresent(value -> byPentacleId
                        .computeIfAbsent(value.pentacleId(), ignored ->
                                new ArrayList<>())
                        .add(entry));
                Object dummy = invoke(recipe, "getRitualDummy")
                        .orElse(null);
                if (dummy instanceof ItemStack stack && !stack.isEmpty()) {
                    byDummyItem.computeIfAbsent(stack.getItem(), ignored ->
                                    new ArrayList<>())
                            .add(entry);
                }
            }
            RitualCatalog built = new RitualCatalog(revision,
                    List.copyOf(entries), Map.copyOf(byRecipeId),
                    immutableGroupedMap(byPentacleId),
                    immutableGroupedMap(byDummyItem));
            RITUAL_CATALOGS.put(manager, built);
            return built;
        }
    }

    private static <K> Map<K, List<RitualEntry>> immutableGroupedMap(
            Map<K, List<RitualEntry>> source) {
        Map<K, List<RitualEntry>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    /**
     * Resolves selector scope without walking the complete recipe catalog.
     * A recipe-bound miniature is an exact O(1) lookup. Pentacle-only,
     * ultimate and original dummy selectors intentionally return every
     * candidate in their scope so the caller can preserve ambiguity checks.
     */
    private static List<RitualEntry> indexedRitualCandidates(
            Level level, ItemStack selector) {
        if (!isRitualSelector(selector)) {
            return List.of();
        }
        RitualCatalog catalog = ritualCatalog(level);
        if (isUltimateMiniRitual(selector)) {
            return catalog.entries();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                selector.getItem());
        if (itemId != null
                && MekanismMagic.MOD_ID.equals(itemId.getNamespace())
                && "mini_ritual".equals(itemId.getPath())) {
            String exactValue = customRitualId(selector);
            if (!exactValue.isEmpty()) {
                ResourceLocation exactId = ResourceLocation.tryParse(
                        exactValue);
                RitualEntry exact = exactId == null ? null
                        : catalog.byRecipeId().get(exactId);
                return exact == null ? List.of() : List.of(exact);
            }
            ResourceLocation pentacleId = ResourceLocation.tryParse(
                    customPentacleId(selector));
            return pentacleId == null ? List.of()
                    : catalog.byPentacleId().getOrDefault(
                            pentacleId, List.of());
        }
        return catalog.byDummyItem().getOrDefault(
                selector.getItem(), List.of());
    }

    public static Optional<MachineRecipeResult> findRitualRecipe(
            Level level, ItemStackHandler inventory,
            ItemStack ritualSelector, ItemStack activation,
            ItemStack sacrifice, boolean dictionaryEnabled) {
        if (level == null) {
            return Optional.empty();
        }
        for (RitualEntry entry : indexedRitualCandidates(
                level, ritualSelector)) {
            Object recipe = entry.recipe();
            Optional<OccultismRitualMachineMode> mode = entry.mode();
            if (mode.isEmpty()) {
                continue;
            }
            if (!matchesActivation(recipe, activation, dictionaryEnabled)) {
                continue;
            }
            int sacrificeSlot = -1;
            if (booleanValue(recipe, "requiresSacrifice")) {
                if (!matchesSacrifice(recipe, sacrifice)) {
                    continue;
                }
                if (!canStoreSacrificeRemainder(
                        inventory, sacrifice)) {
                    continue;
                }
                sacrificeSlot = NativeMagicMachineBlockEntity.SACRIFICE_SLOT;
            }
            Optional<RitualInputMatch> inputMatch = matchRitualIngredients(
                    recipe, inventory);
            if (inputMatch.isEmpty()) {
                continue;
            }
            int activationSlot = activationRequired(recipe)
                    ? NativeMagicMachineBlockEntity.ACTIVATION_SLOT : -1;
            String command = stringValue(recipe, "getCommand");
            ItemStack primaryInput = inputMatch.get().primarySlot() < 0
                    ? ItemStack.EMPTY : inventory.getStackInSlot(
                    inputMatch.get().primarySlot());
            ItemStack output = machineRitualOutput(level, recipe,
                    mode.get(), activation, primaryInput, false);
            if (!output.isEmpty()) {
                RecipeCompletion completion = command.isBlank()
                        ? RecipeCompletion.NONE
                        : (serverLevel, position) -> executeCommandRitual(
                        serverLevel, position, command);
                return Optional.of(new MachineRecipeResult(
                        entry.recipeId(), output,
                        OccultismRitualMachineMode.durationTicks(
                                intValue(recipe, "getDuration", 200),
                                OccultismRitualConfig.durationMultiplier()),
                        inputMatch.get().uses(), activationSlot,
                        sacrificeSlot,
                        completion, sacrificeSlot < 0
                        ? SpecialInputHandler.NONE : SACRIFICE_HANDLER));
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves an unordered AE input batch against the ritual selected in the
     * machine, assigning every requested item to exactly one semantic role.
     * Ambiguous selectors or surplus inputs fail before any port is mutated.
     */
    public static Optional<RitualAutomationPlan> planRitualAutomation(
            Level level, ItemStack ritualSelector,
            List<RitualAutomationInput> inputs,
            boolean dictionaryEnabled) {
        if (level == null || !isRitualSelector(ritualSelector)
                || inputs == null || inputs.isEmpty()) {
            return Optional.empty();
        }
        RitualAutomationPlan selected = null;
        for (RitualEntry entry : indexedRitualCandidates(
                level, ritualSelector)) {
            Object recipe = entry.recipe();
            Optional<OccultismRitualMachineMode> mode = entry.mode();
            if (mode.isEmpty()) {
                continue;
            }
            Optional<RitualAutomationPlan> candidate =
                    planRitualAutomation(entry.recipeId(), recipe,
                            mode.get(), level, inputs,
                            dictionaryEnabled);
            if (candidate.isEmpty()) {
                continue;
            }
            if (selected != null) {
                // A pentacle-only or ultimate selector must not let AE choose
                // silently between two recipes with the same input shape.
                return Optional.empty();
            }
            selected = candidate.get();
        }
        return Optional.ofNullable(selected);
    }

    private static Optional<RitualAutomationPlan> planRitualAutomation(
            ResourceLocation recipeId, Object recipe,
            OccultismRitualMachineMode mode, Level level,
            List<RitualAutomationInput> inputs,
            boolean dictionaryEnabled) {
        List<RitualAutomationRequirement> requirements =
                new ArrayList<>();
        Object activationValue = invoke(
                recipe, "getActivationItem").orElse(null);
        if (activationValue instanceof Ingredient activation
                && !activation.isEmpty()) {
            requirements.add(new RitualAutomationRequirement(
                    RitualAutomationRole.ACTIVATION,
                    stack -> activation.test(stack)
                            || dictionaryEnabled
                            && matchesUncraftedBook(activation, stack)));
        }
        if (booleanValue(recipe, "requiresSacrifice")) {
            requirements.add(new RitualAutomationRequirement(
                    RitualAutomationRole.SACRIFICE,
                    stack -> matchesSacrifice(recipe, stack)));
        }
        for (Ingredient ingredient : ingredients(recipe)) {
            if (isEmptyIngredient(ingredient)) {
                return Optional.empty();
            }
            requirements.add(new RitualAutomationRequirement(
                    RitualAutomationRole.MATERIAL,
                    ingredient::test));
        }

        int[] capacities = new int[inputs.size()];
        long requested = 0;
        for (int input = 0; input < inputs.size(); input++) {
            RitualAutomationInput candidate = inputs.get(input);
            if (candidate == null || candidate.stack().isEmpty()
                    || candidate.amount() <= 0) {
                return Optional.empty();
            }
            capacities[input] = candidate.amount();
            requested += candidate.amount();
        }
        if (requirements.isEmpty() || requested <= 0
                || requested > MAX_RITUAL_AUTOMATION_UNITS
                || requested % requirements.size() != 0) {
            return Optional.empty();
        }
        int copies = (int) (requested / requirements.size());
        if (copies > 1) {
            List<RitualAutomationRequirement> oneCopy =
                    List.copyOf(requirements);
            requirements = new ArrayList<>((int) requested);
            for (int copy = 0; copy < copies; copy++) {
                requirements.addAll(oneCopy);
            }
        }

        boolean[][] compatibility = new boolean[requirements.size()]
                [inputs.size()];
        for (int requirement = 0;
             requirement < requirements.size(); requirement++) {
            Predicate<ItemStack> matcher =
                    requirements.get(requirement).matcher();
            for (int input = 0; input < inputs.size(); input++) {
                compatibility[requirement][input] = matcher.test(
                        inputs.get(input).stack());
            }
        }
        Optional<BoundedSlotMatcher.Assignment> assignment =
                BoundedSlotMatcher.assign(compatibility, capacities);
        if (assignment.isEmpty()) {
            return Optional.empty();
        }
        int[] used = assignment.get().slotUse();
        for (int input = 0; input < capacities.length; input++) {
            if (used[input] != capacities[input]) {
                return Optional.empty();
            }
        }

        int[][] roleCounts = new int[inputs.size()]
                [RitualAutomationRole.values().length];
        int activationInput = -1;
        int primaryInput = -1;
        for (int requirement = 0;
             requirement < requirements.size(); requirement++) {
            int input = assignment.get().ingredientSlot(requirement);
            if (input < 0) {
                return Optional.empty();
            }
            RitualAutomationRole role =
                    requirements.get(requirement).role();
            roleCounts[input][role.ordinal()]++;
            if (role == RitualAutomationRole.ACTIVATION
                    && activationInput < 0) {
                activationInput = input;
            } else if (role == RitualAutomationRole.MATERIAL
                    && primaryInput < 0) {
                primaryInput = input;
            }
        }
        List<RitualAutomationAllocation> allocations =
                new ArrayList<>();
        for (int input = 0; input < roleCounts.length; input++) {
            for (RitualAutomationRole role
                    : RitualAutomationRole.values()) {
                int amount = roleCounts[input][role.ordinal()];
                if (amount > 0) {
                    allocations.add(new RitualAutomationAllocation(
                            input, role, amount));
                }
            }
        }
        ItemStack activationStack = activationInput < 0
                ? ItemStack.EMPTY : inputs.get(activationInput).stack();
        if (requiresStableSpiritName(mode)
                && !hasSpiritName(activationStack)) {
            return Optional.empty();
        }
        ItemStack primaryStack = primaryInput < 0
                ? ItemStack.EMPTY : inputs.get(primaryInput).stack();
        ItemStack output = machineRitualOutput(level, recipe, mode,
                activationStack, primaryStack, false);
        if (output.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RitualAutomationPlan(
                recipeId, allocations, output, copies));
    }

    private static boolean requiresStableSpiritName(
            OccultismRitualMachineMode mode) {
        return mode == OccultismRitualMachineMode.NAMED_ITEM
                || mode == OccultismRitualMachineMode.MINER_ITEM;
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
        return stack.getItem() instanceof SpawnEggItem
                || stack.getItem() instanceof RitualSpawnEggItem
                || isFilledContainment(stack);
    }

    public static boolean isRitualSelector(ItemStack stack) {
        if (MiniPentacleDeployment.isDeployed(stack)) {
            return false;
        }
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
        return ritualCatalog(level).byDummyItem()
                .getOrDefault(ritualDummy.getItem(), List.of()).stream()
                .map(RitualEntry::projection)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static Optional<RitualProjection> findProjection(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }
        RitualEntry entry = ritualCatalog(level).byRecipeId().get(recipeId);
        return entry == null ? Optional.empty() : entry.projection();
    }

    public static Optional<RitualProjection> findProjectionByPentacle(
            Level level, ResourceLocation pentacleId) {
        if (level == null || pentacleId == null) {
            return Optional.empty();
        }
        return ritualCatalog(level).byPentacleId()
                .getOrDefault(pentacleId, List.of()).stream()
                .map(RitualEntry::projection)
                .flatMap(Optional::stream)
                .findFirst();
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
        List<RitualJeiData> result = new ArrayList<>();
        for (RitualEntry entry : ritualCatalog(level).entries()) {
            Object recipe = entry.recipe();
            Optional<OccultismRitualMachineMode> mode = entry.mode();
            if (mode.isEmpty()) {
                continue;
            }
            Optional<RitualProjection> projection = entry.projection();
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
            ItemStack output = machineRitualOutput(level, recipe,
                    mode.get(), ItemStack.EMPTY, ItemStack.EMPTY, true);
            if (output.isEmpty()) {
                continue;
            }
            Ingredient activation = invoke(recipe, "getActivationItem")
                    .filter(Ingredient.class::isInstance)
                    .map(Ingredient.class::cast)
                    .orElse(null);
            result.add(new RitualJeiData(entry.recipeId(),
                    projection.get().pentacleId(),
                    List.copyOf(ritualIngredients),
                    activation,
                    sacrificeExamples(recipe),
                    createMiniRitual(projection.get()),
                    output));
        }
        return result;
    }

    public static void logRitualCoverage(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        RitualCatalog catalog = ritualCatalog(level);
        if (catalog.entries().isEmpty()
                && recipeType("ritual") == null) {
            MekanismMagic.LOGGER.warn(
                    "Occultism ritual recipe type is unavailable");
            return;
        }
        int total = catalog.entries().size();
        int covered = ritualJeiRecipes(level).size();
        if (covered == total) {
            MekanismMagic.LOGGER.info(
                    "Occultism ritual definitions mapped for machine/JEI: "
                            + "{}/{}",
                    covered, total);
        } else {
            MekanismMagic.LOGGER.warn(
                    "Occultism ritual definition mapping is incomplete: "
                            + "{}/{} recipes", covered, total);
        }
    }

    public static void logSpiritJobCoverage(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        RitualCatalog catalog = ritualCatalog(level);
        if (catalog.entries().isEmpty()
                && recipeType("ritual") == null) {
            return;
        }
        int total = 0;
        int mapped = 0;
        for (RitualEntry entry : catalog.entries()) {
            Object recipe = entry.recipe();
            if (entry.mode().orElse(null)
                    != OccultismRitualMachineMode.JOB_ENTITY) {
                continue;
            }
            total++;
            Object jobValue = invoke(recipe, "getSpiritJobType")
                    .orElse(null);
            EntityType<?> entity = invoke(recipe, "getEntityToSummon")
                    .filter(EntityType.class::isInstance)
                    .map(EntityType.class::cast).orElse(null);
            ResourceLocation entityId = entity == null ? null
                    : BuiltInRegistries.ENTITY_TYPE.getKey(entity);
            if (jobValue instanceof ResourceLocation jobId
                    && entityId != null
                    && OccultismSpiritJobPolicy.profile(jobId.toString())
                    .filter(profile -> profile.entityTier()
                            == OccultismSpiritJobPolicy.entityTier(
                            entityId.getPath())).isPresent()) {
                mapped++;
            }
        }
        if (mapped == total
                && mapped == OccultismSpiritJobPolicy.knownJobCount()) {
            MekanismMagic.LOGGER.info(
                    "Occultism SpiritJob IDs/entity tiers recognized: {}/{}",
                    mapped, total);
        } else {
            MekanismMagic.LOGGER.warn(
                    "Occultism SpiritJob mapping is incomplete or changed: "
                            + "{}/{} ritual jobs; policy contains {} jobs",
                    mapped, total,
                    OccultismSpiritJobPolicy.knownJobCount());
        }
        int executable = OccultismSpiritJobPolicy.executableJobCount();
        int worldTasks = OccultismSpiritJobPolicy
                .recognizedWorldTaskCount();
        if (executable == 16 && worldTasks == 10) {
            MekanismMagic.LOGGER.info(
                    "Occultism processing/trade jobs executable by machines: "
                            + "{}/{}; WORLD_TASK jobs recognized-only: {}",
                    executable, 16, worldTasks);
        } else {
            MekanismMagic.LOGGER.warn(
                    "Occultism SpiritJob policy counts changed: {} executable, "
                            + "{} WORLD_TASK recognized-only",
                    executable, worldTasks);
        }
    }

    public static List<SpiritJeiData> spiritJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        List<SpiritJeiData> result = new ArrayList<>();
        for (String typeId : List.of("spirit_fire", "crushing",
                "crystallize", "smelting", "blasting", "smoking",
                "campfire_cooking", "spirit_trade")) {
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
                Ingredient displayIngredient = executableSpiritIngredient(
                        level, typeId, holder.id(), ingredient);
                ItemStack representative = representativeSpiritInput(
                        displayIngredient);
                if (representative.isEmpty()) {
                    continue;
                }
                if ("spirit_fire".equals(typeId)) {
                    ItemStack baseOutput = singleRecipeResult(level, recipe,
                            representative, typeId, 1);
                    for (int tier = 1; tier <= 4; tier++) {
                        addSpiritJeiRecipe(result, holder.id(), typeId,
                                ingredient, spiritSource(
                                        spiritEntityForTier(tier), ""),
                                baseOutput, 0);
                    }
                } else if ("spirit_trade".equals(typeId)) {
                    String trader = OccultismSpiritJobPolicy.normalizeJobId(
                            stringValue(recipe, "getTrader"));
                    Optional<OccultismSpiritJobPolicy.Profile> profile =
                            OccultismSpiritJobPolicy.profile(trader)
                                    .filter(value -> value.mode()
                                            == OccultismSpiritJobPolicy
                                            .WorkMode.TRADE);
                    ItemStack baseOutput = result(
                            level.registryAccess(), recipe);
                    if (profile.isPresent() && !baseOutput.isEmpty()) {
                        addSpiritJeiRecipe(result, holder.id(), typeId,
                                ingredient, spiritSource(
                                        spiritEntityForTier(profile.get()
                                                .entityTier()), trader),
                                baseOutput,
                                tradeRecipeCanBeRandom(level, trader,
                                        ingredient)
                                        ? weightedTradeWeight(recipe) : 0);
                    }
                } else {
                    for (int tier = 1; tier <= 4; tier++) {
                        OccultismSpiritJobConfig.WorkerSettings settings =
                                OccultismSpiritJobConfig.settings(typeId, tier);
                        if (!recipeTierAllowed(recipe, typeId,
                                settings.recipeTier())) {
                            continue;
                        }
                        ItemStack baseOutput = singleRecipeResult(level,
                                recipe, representative, typeId,
                                settings.recipeTier());
                        if (baseOutput.isEmpty()) {
                            continue;
                        }
                        ItemStack output = spiritOutput(recipe, typeId, baseOutput,
                                settings, 1);
                        addSpiritJeiRecipe(result, holder.id(), typeId,
                                displayIngredient,
                                spiritSource(spiritEntityForTier(tier),
                                        OccultismSpiritJobPolicy.jobFor(
                                                typeId, tier)), output, 0);
                    }
                }
            }
        }
        return result;
    }

    private static void addSpiritJeiRecipe(List<SpiritJeiData> result,
                                           ResourceLocation id, String type,
                                           Ingredient input, ItemStack spirit,
                                           ItemStack output, int weight) {
        if (!spirit.isEmpty() && !output.isEmpty()) {
            result.add(new SpiritJeiData(id, type, input,
                    spirit.copy(), output.copy(), Math.max(0, weight)));
        }
    }

    private static ItemStack spiritSource(String entityPath, String job) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(OCCULTISM, entityPath);
        EntityType<?> entity = BuiltInRegistries.ENTITY_TYPE.get(id);
        SpawnEggItem egg = SpawnEggItem.byId(entity);
        CompoundTag entityData = new CompoundTag();
        entityData.putString("id", id.toString());
        if (!job.isBlank()) {
            CompoundTag spiritJob = new CompoundTag();
            spiritJob.putString("factoryId", job);
            entityData.put("spiritJob", spiritJob);
        }
        ResourceLocation jobId = ResourceLocation.tryParse(job);
        ItemStack source;
        if (jobId != null) {
            source = RitualSpawnEggItem.withJobInitialization(
                    RitualSpawnEggItem.forEntity(entity, entityData), jobId);
        } else if (egg == null) {
            source = RitualSpawnEggItem.forEntity(entity, entityData);
        } else {
            source = new ItemStack(egg);
            source.set(DataComponents.ENTITY_DATA,
                    CustomData.of(entityData));
        }
        if (jobId != null) {
            // JEI catalysts for several jobs share the same bound spirit
            // model. Give the synthetic display stack the native Occultism
            // job name so players can distinguish the required profession.
            source.set(DataComponents.CUSTOM_NAME,
                    Component.translatable("job.occultism."
                            + jobId.getPath()));
        }
        return source;
    }

    private static String spiritEntityForTier(int tier) {
        return switch (tier) {
            case 1 -> "foliot";
            case 2 -> "djinni";
            case 3 -> "afrit";
            case 4 -> "marid";
            default -> "";
        };
    }

    private static boolean recipeTierAllowed(
            Object recipe, String typeId, int recipeTier) {
        if (!"crushing".equals(typeId)
                && !"crystallize".equals(typeId)) {
            return true;
        }
        int minimum = intValue(recipe, "getMinTier", -1);
        int maximum = intValue(recipe, "getMaxTier", -1);
        return (minimum < 0 || recipeTier >= minimum)
                && (maximum < 0 || recipeTier <= maximum);
    }

    private static Ingredient executableSpiritIngredient(
            Level level, String typeId, ResourceLocation recipeId,
            Ingredient ingredient) {
        if (!COOKING_RECIPE_TYPES.contains(typeId)) {
            return ingredient;
        }
        List<ItemStack> executable = new ArrayList<>();
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty()
                    && isPreferredCookingRecipe(level, typeId,
                    recipeId, candidate)
                    && executable.stream().noneMatch(existing ->
                    ItemStack.isSameItemSameComponents(existing,
                            candidate))) {
                executable.add(candidate.copyWithCount(1));
            }
        }
        return executable.isEmpty() ? Ingredient.EMPTY
                : Ingredient.of(executable.stream());
    }

    private static ItemStack representativeSpiritInput(
            Ingredient ingredient) {
        if (ingredient == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty()) {
                return candidate.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean tradeRecipeCanBeRandom(
            Level level, String trader, Ingredient ingredient) {
        Optional<OccultismSpiritJobPolicy.Profile> profile =
                OccultismSpiritJobPolicy.profile(trader);
        RecipeType<?> type = recipeType("spirit_trade");
        if (level == null || profile.isEmpty() || type == null
                || ingredient == null) {
            return false;
        }
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty()
                    && OccultismSpiritJobPolicy.requiresRandomTrade(
                    weightedTradeCandidates(level, type,
                            candidate.copyWithCount(1),
                            profile.get().entityTier(), trader).size())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isPreferredCookingRecipe(
            Level level, String candidateType,
            ResourceLocation candidateId, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(
                input.copyWithCount(1));
        for (String typeId : List.of("smelting", "blasting",
                "smoking", "campfire_cooking")) {
            RecipeType<?> type = recipeType(typeId);
            if (type == null) {
                continue;
            }
            Optional<RecipeHolder<?>> matched = (Optional) level
                    .getRecipeManager().getRecipeFor((RecipeType) type,
                            recipeInput, level);
            if (matched.isPresent()) {
                return typeId.equals(candidateType)
                        && matched.get().id().equals(candidateId);
            }
        }
        return false;
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
        RitualCatalog rituals = ritualCatalog(level);
        long revision = rituals.revision();
        RecipeManager recipeManager = level.getRecipeManager();
        PentacleCatalog cached = PENTACLE_CATALOGS.get(recipeManager);
        if (cached != null && cached.recipeRevision() == revision) {
            return cached.definitions();
        }

        Map<ResourceLocation, PentacleDefinition> grouped =
                new LinkedHashMap<>();
        for (RitualEntry entry : rituals.entries()) {
            Optional<RitualProjection> projection = entry.projection();
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
                new PentacleCatalog(revision, definitions));
        return definitions;
    }

    private static List<PentacleDefinition> addUniqueRecipeMarkers(
            List<PentacleDefinition> definitions) {
        List<PentacleDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(java.util.Comparator.comparing(
                definition -> definition.pentacleId().toString()));
        Map<ResourceLocation, List<ItemStack>> markerSets =
                new LinkedHashMap<>();
        for (int index = 0; index < sorted.size(); index++) {
            markerSets.put(sorted.get(index).pentacleId(),
                    uniqueRecipeMarker(index));
        }
        List<PentacleDefinition> differentiated =
                new ArrayList<>(definitions.size());
        for (PentacleDefinition definition : definitions) {
            List<ItemStack> marker = markerSets.get(
                    definition.pentacleId());
            List<ItemStack> materials =
                    new ArrayList<>(definition.materials().size()
                            + marker.size());
            // Put the marker first so it remains visible even for large JEI
            // recipes that fill all sixteen displayed input positions.
            materials.addAll(copyStacks(marker));
            materials.addAll(copyStacks(definition.materials()));
            differentiated.add(new PentacleDefinition(
                    definition.recipeId(), definition.pentacleId(),
                    List.copyOf(materials), definition.chalkColors(),
                    definition.output()));
        }
        return List.copyOf(differentiated);
    }

    /**
     * Assigns a unique non-empty combination of the sixteen vanilla dyes.
     * The first sixteen recipes retain one dye each; further recipes use
     * deterministic two-dye, then three-dye combinations. Unlike using a
     * larger count of the same dye, this remains unique when automation sends
     * stacked inputs for several consecutive crafts.
     */
    private static List<ItemStack> uniqueRecipeMarker(int markerIndex) {
        if (markerIndex < 0) {
            throw new IllegalArgumentException("Negative marker index");
        }
        long rank = markerIndex;
        int dyeCount = RECIPE_MARKER_DYES.size();
        for (int size = 1; size <= dyeCount; size++) {
            long combinations = combinationCount(dyeCount, size);
            if (rank >= combinations) {
                rank -= combinations;
                continue;
            }
            List<ItemStack> marker = new ArrayList<>(size);
            int firstCandidate = 0;
            for (int position = 0; position < size; position++) {
                int remaining = size - position - 1;
                for (int candidate = firstCandidate;
                     candidate < dyeCount; candidate++) {
                    long suffixes = combinationCount(
                            dyeCount - candidate - 1, remaining);
                    if (rank >= suffixes) {
                        rank -= suffixes;
                        continue;
                    }
                    marker.add(new ItemStack(
                            RECIPE_MARKER_DYES.get(candidate)));
                    firstCandidate = candidate + 1;
                    break;
                }
            }
            return List.copyOf(marker);
        }
        throw new IllegalStateException(
                "More miniature pentacles than unique dye combinations");
    }

    private static long combinationCount(int total, int selected) {
        if (selected < 0 || selected > total) {
            return 0;
        }
        selected = Math.min(selected, total - selected);
        long result = 1L;
        for (int index = 1; index <= selected; index++) {
            result = result * (total - selected + index) / index;
        }
        return result;
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
        return candidates.size() == 1
                ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    public static List<MachineRecipeResult> findMiniRitualCandidates(
            Level level, ItemStackHandler inventory) {
        List<MachineRecipeResult> candidates = new ArrayList<>();
        for (PentacleDefinition definition : pentacleDefinitions(level)) {
            if (!matchesDyeSignature(definition.materials(), inventory)) {
                continue;
            }
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

    private static boolean matchesDyeSignature(
            List<ItemStack> required, ItemStackHandler inventory) {
        Set<net.minecraft.world.item.Item> expected = new LinkedHashSet<>();
        for (ItemStack stack : required) {
            if (stack.getItem() instanceof net.minecraft.world.item.DyeItem) {
                expected.add(stack.getItem());
            }
        }
        Set<net.minecraft.world.item.Item> actual = new LinkedHashSet<>();
        for (int slot = 0;
             slot < NativeMagicMachineBlockEntity.INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.getItem() instanceof net.minecraft.world.item.DyeItem) {
                actual.add(stack.getItem());
            }
        }
        return !expected.isEmpty() && expected.equals(actual);
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
        if (stack.isEmpty()
                || stack.getItem() != MekanismMagic.MINI_RITUAL.get()
                || MiniPentacleDeployment.isDeployed(stack)) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        String pentacle = data.getUnsafe().getString("pentacle");
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
        ItemStack single = stack.copyWithCount(1);
        if (EntityContainerRegistry.empty(single)) {
            ItemStack consumed = inventory.extractItem(slot, 1, false);
            if (consumed.isEmpty()) {
                return false;
            }
            ItemStack remainder = inventory.insertItem(
                    NativeMagicMachineBlockEntity.RITUAL_REMAINDER_SLOT,
                    single, false);
            return remainder.isEmpty();
        }
        return !inventory.extractItem(slot, 1, false).isEmpty();
    }

    private static boolean canStoreSacrificeRemainder(
            ItemStackHandler inventory, ItemStack sacrifice) {
        ItemStack remainder = sacrifice.copyWithCount(1);
        if (!EntityContainerRegistry.empty(remainder)) {
            return true;
        }
        return inventory.insertItem(
                NativeMagicMachineBlockEntity.RITUAL_REMAINDER_SLOT,
                remainder, true).isEmpty();
    }

    public static String spiritType(ItemStack stack) {
        String id = entityId(stack);
        return id.isEmpty() ? "" : id;
    }

    public static boolean isTraderSpirit(ItemStack stack) {
        return OccultismSpiritJobPolicy.isTrader(spiritJobId(stack));
    }

    public static boolean isRandomTradeSpirit(ItemStack stack) {
        return OccultismSpiritJobPolicy.isGambler(spiritJobId(stack));
    }

    /** Creates a persisted, server-only salt so clients cannot predict draws. */
    public static long createSpiritTradeSalt() {
        long salt = java.util.concurrent.ThreadLocalRandom.current()
                .nextLong();
        return salt == 0L ? 0x9E3779B97F4A7C15L : salt;
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
        return OccultismSpiritJobPolicy.entityTier(entityId.getPath());
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
        String pentacle = data.getUnsafe().getString("pentacle");
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
        if (stack.getItem() instanceof RitualSpawnEggItem) {
            ResourceLocation id = RitualSpawnEggItem.entityId(stack);
            return id == null ? "" : id.toString();
        }
        return EntityContainerRegistry.capturedEntity(stack)
                .map(CapturedEntity::entityId)
                .map(ResourceLocation::toString)
                .orElse("");
    }

    private static String spiritJobId(ItemStack stack) {
        return spiritEntityData(stack)
                .map(tag -> tag.getCompound("spiritJob")
                        .getString("factoryId"))
                .orElse("");
    }

    private static Optional<CompoundTag> spiritEntityData(ItemStack stack) {
        Optional<CompoundTag> captured = EntityContainerRegistry
                .capturedEntity(stack).map(CapturedEntity::entityData);
        if (captured.isPresent()) {
            return captured;
        }
        if (!(stack.getItem() instanceof SpawnEggItem)
                && !(stack.getItem() instanceof RitualSpawnEggItem)) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.ENTITY_DATA);
        return data == null || data.isEmpty()
                ? Optional.empty() : Optional.of(data.copyTag());
    }

    private static String customRitualId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return "";
        }
        return data.getUnsafe().getString("ritual");
    }

    private static String customPentacleId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return "";
        }
        return data.getUnsafe().getString("pentacle");
    }

    private static Optional<OccultismRitualMachineMode> machineMode(
            Object recipe) {
        Object typeValue = invoke(recipe, "getRitualType").orElse(null);
        if (!(typeValue instanceof ResourceLocation type)) {
            return Optional.empty();
        }
        Optional<OccultismRitualMachineMode> mode =
                OccultismRitualMachineMode.from(type);
        if (mode.isEmpty() && WARNED_RITUAL_TYPES.add(type)) {
            MekanismMagic.LOGGER.warn(
                    "Skipping unsupported Occultism ritual type {}. "
                            + "Register a machine output adapter before "
                            + "exposing it in JEI or automation.",
                    type);
        }
        return mode;
    }

    private static ItemStack machineRitualOutput(
            Level level, Object recipe,
            OccultismRitualMachineMode mode,
            ItemStack activation, ItemStack primaryInput,
            boolean preview) {
        try {
            String command = stringValue(recipe, "getCommand");
            if (!command.isBlank()) {
                return commandResult(level, recipe);
            }
            ItemStack base = result(level.registryAccess(), recipe);
            return switch (mode) {
                case STATIC_ITEM -> base;
                case ENTITY -> spawnEggResult(level, recipe,
                        activation, false);
                case JOB_ENTITY -> spawnEggResult(level, recipe,
                        activation, true, true, true);
                case OWNED_ENTITY -> spawnEggResult(level, recipe,
                        activation, true);
                case CHANCE_ENTITY -> chanceSpawnEggResult(
                        level, recipe, activation);
                case NAMED_ITEM -> preview ? base
                        : copyBoundSpiritName(base, activation, true);
                case REPAIR_ITEM -> preview ? base
                        : repairedActivation(activation);
                case RESURRECT_ENTITY -> preview ? base
                        : resurrectedEntityResult(activation);
                case MINER_ITEM -> preview ? base
                        : initializedMinerResult(level, base, activation);
                case UPGRADE_ITEM -> preview ? base
                        : upgradedResult(base, activation, primaryInput);
            };
        } catch (ReflectiveOperationException | RuntimeException
                 | LinkageError failure) {
            String warning = mode + ":" + recipe.getClass().getName();
            if (WARNED_RITUAL_ADAPTER_FAILURES.add(warning)) {
                MekanismMagic.LOGGER.warn(
                        "Failed to adapt Occultism ritual output for {}",
                        mode, failure);
            }
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack repairedActivation(ItemStack activation) {
        if (activation.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack output = activation.copyWithCount(1);
        output.setDamageValue(0);
        return output;
    }

    private static ItemStack initializedMinerResult(
            Level level, ItemStack base, ItemStack activation)
            throws ReflectiveOperationException {
        if (base.isEmpty() || activation.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack output = base.copy();
        output.getItem().onCraftedBy(output, level, null);
        return copyBoundSpiritName(output, activation, true);
    }

    private static ItemStack upgradedResult(
            ItemStack base, ItemStack activation,
            ItemStack primaryInput) throws ReflectiveOperationException {
        if (base.isEmpty() || primaryInput.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack output = base.copy();
        net.minecraft.world.item.Rarity resultRarity = output.getRarity();
        output.applyComponents(primaryInput.getComponents());
        Integer maximumDamage = output.get(DataComponents.MAX_DAMAGE);
        if (maximumDamage != null) {
            output.set(DataComponents.MAX_DAMAGE, maximumDamage);
        }
        if (hasSpiritName(activation)) {
            output = copyBoundSpiritName(output, activation, false);
        }
        output.set(DataComponents.RARITY, resultRarity);
        return output;
    }

    private static ItemStack copyBoundSpiritName(
            ItemStack output, ItemStack activation,
            boolean generateIfMissing) throws ReflectiveOperationException {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String name = boundSpiritName(activation, generateIfMissing);
        if (name.isBlank()) {
            return ItemStack.EMPTY;
        }
        Class<?> utility = Class.forName(
                "com.klikli_dev.occultism.util.ItemNBTUtil");
        Method setter = utility.getMethod(
                "setBoundSpiritName", ItemStack.class, String.class);
        setter.invoke(null, output, name);
        return output;
    }

    private static String boundSpiritName(
            ItemStack activation, boolean generateIfMissing)
            throws ReflectiveOperationException {
        if (activation.isEmpty()
                || !generateIfMissing && !hasSpiritName(activation)) {
            return "";
        }
        Class<?> utility = Class.forName(
                "com.klikli_dev.occultism.util.ItemNBTUtil");
        Method getter = utility.getMethod(
                "getBoundSpiritName", ItemStack.class);
        Object value = getter.invoke(null,
                activation.copyWithCount(1));
        return value instanceof String name ? name : "";
    }

    private static boolean hasSpiritName(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        net.minecraft.core.component.DataComponentType<?> component =
                BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                        SPIRIT_NAME_COMPONENT);
        return component != null && stack.has(component);
    }

    private static ItemStack resurrectedEntityResult(
            ItemStack activation) {
        if (activation.isEmpty()) {
            return ItemStack.EMPTY;
        }
        CustomData data = activation.get(DataComponents.ENTITY_DATA);
        if (data == null || data.isEmpty()) {
            return ItemStack.EMPTY;
        }
        CompoundTag entityData = data.copyTag();
        ResourceLocation entityId = ResourceLocation.tryParse(
                entityData.getString("id"));
        if (entityId == null
                || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
            return ItemStack.EMPTY;
        }
        EntityType<?> entityType =
                BuiltInRegistries.ENTITY_TYPE.get(entityId);
        entityData.remove("Pos");
        return RitualSpawnEggItem.forResurrectedEntity(
                entityType, entityData);
    }

    private static ItemStack chanceSpawnEggResult(
            Level level, Object recipe, ItemStack activation)
            throws ReflectiveOperationException {
        EntityType<?> type = invoke(recipe, "getEntityToSummon")
                .filter(EntityType.class::isInstance)
                .map(EntityType.class::cast)
                .orElse(null);
        if (type == null) {
            return ItemStack.EMPTY;
        }
        ItemStack output = RitualSpawnEggItem
                .forOwnedEntityWithChickenFallback(
                type, ritualEntityData(level, recipe, activation), 3);
        return hasSpiritName(activation) ? output
                : RitualSpawnEggItem.withDeferredSpiritName(output);
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

    private static ItemStack spawnEggResult(
            Level level, Object recipe, ItemStack activation,
            boolean assignOwner) throws ReflectiveOperationException {
        return spawnEggResult(level, recipe, activation,
                assignOwner, false, false);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack spawnEggResult(
            Level level, Object recipe, ItemStack activation,
            boolean assignOwner, boolean includeCallingBook,
            boolean initializeSpiritJob)
            throws ReflectiveOperationException {
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

        boolean deferredName = !hasSpiritName(activation);
        CompoundTag entityData = ritualEntityData(
                level, recipe, activation);
        int count = Math.max(1, intValue(recipe, "getSummonNumber", 1));
        ItemStack output;
        if (entityTag != null) {
            // Keep the tag on the generic egg and defer random selection until
            // the player uses it. A recipe NBT id would otherwise make the egg
            // resolve as a fixed entity and defeat tag-based randomness.
            entityData.remove("id");
            output = assignOwner
                    ? RitualSpawnEggItem.forOwnedTag(entityTag, entityData)
                    : RitualSpawnEggItem.forTag(entityTag, entityData);
        } else {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (entityId != null) {
                entityData.putString("id", entityId.toString());
            }
            if (assignOwner) {
                output = RitualSpawnEggItem.forOwnedEntity(
                        type, entityData);
            } else if (deferredName) {
                output = RitualSpawnEggItem.forEntity(
                        type, entityData);
            } else {
                SpawnEggItem egg = SpawnEggItem.byId(type);
                ItemStack recipeResult = result(
                        level.registryAccess(), recipe);
                output = recipeResult.getItem() instanceof SpawnEggItem
                        ? recipeResult.copy() : egg == null
                        ? RitualSpawnEggItem.forEntity(type, entityData)
                        : new ItemStack(egg);
            }
            if (!assignOwner) {
                output.set(DataComponents.ENTITY_DATA,
                        CustomData.of(entityData));
            }
        }
        if (includeCallingBook) {
            ItemStack callingBook = callingBookResult(
                    level, recipe, activation);
            if (!callingBook.isEmpty()) {
                output = RitualSpawnEggItem.withCallingBook(
                        output, callingBook, level.registryAccess());
            }
        }
        if (initializeSpiritJob) {
            ResourceLocation jobId = ResourceLocation.tryParse(
                    entityData.getCompound("spiritJob")
                            .getString("factoryId"));
            if (jobId == null) {
                return ItemStack.EMPTY;
            }
            output = RitualSpawnEggItem.withJobInitialization(
                    output, jobId);
        }
        if (deferredName) {
            output = RitualSpawnEggItem.withDeferredSpiritName(output);
        }
        output.setCount(Math.min(count, output.getMaxStackSize()));
        return output;
    }

    private static ItemStack callingBookResult(
            Level level, Object recipe, ItemStack activation) {
        ItemStack book = result(level.registryAccess(), recipe);
        if (book.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                book.getItem());
        if (!itemId.getNamespace().equals(OCCULTISM)
                || !itemId.getPath().startsWith("book_of_calling_")) {
            return ItemStack.EMPTY;
        }
        book = book.copyWithCount(1);
        // This mirrors SummonRitual#getBookOfCallingBound: custom activation
        // components (notably the generated spirit name) carry onto the
        // calling book before it is bound to the spawned worker.
        if (!activation.isEmpty()
                && !activation.isComponentsPatchEmpty()) {
            book.applyComponents(activation.getComponents());
        }
        return book;
    }

    private static CompoundTag ritualEntityData(
            Level level, Object recipe, ItemStack activation)
            throws ReflectiveOperationException {
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
        int maximumAge = intValue(recipe, "getSpiritMaxAge", -1);
        if (maximumAge >= 0) {
            entityData.putInt("spiritMaxAge", maximumAge);
        }
        String spiritName = boundSpiritName(activation, false);
        if (!spiritName.isBlank()) {
            entityData.putString("CustomName",
                    Component.Serializer.toJson(
                            Component.literal(spiritName),
                            level.registryAccess()));
        }
        return entityData;
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

    private static Optional<RitualInputMatch> matchRitualIngredients(
            Object recipe, ItemStackHandler inventory) {
        List<Ingredient> ingredients = ingredients(recipe);
        int slotCount = NativeMagicMachineBlockEntity.INPUT_SLOTS;
        int[] capacities = new int[slotCount];
        boolean[][] compatibility = new boolean[ingredients.size()][slotCount];
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            capacities[slot] = stack.getCount();
            if (stack.isEmpty()) {
                continue;
            }
            for (int ingredient = 0; ingredient < ingredients.size();
                 ingredient++) {
                compatibility[ingredient][slot] =
                        ingredients.get(ingredient).test(stack);
            }
        }
        Optional<BoundedSlotMatcher.Assignment> assignment =
                BoundedSlotMatcher.assign(
                compatibility, capacities);
        if (assignment.isEmpty()) {
            return Optional.empty();
        }
        int[] used = assignment.get().slotUse();
        List<InputUse> matched = new ArrayList<>();
        for (int slot = 0; slot < used.length; slot++) {
            if (used[slot] > 0) {
                matched.add(new InputUse(slot, used[slot]));
            }
        }
        return Optional.of(new RitualInputMatch(List.copyOf(matched),
                assignment.get().ingredientSlot(0)));
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
                                                     ItemStack input,
                                                     int entityTier,
                                                     int recipeTier,
                                                     String sourceJob, String typeId) {
        if (input.isEmpty()) {
            return Optional.empty();
        }
        if (!OccultismSpiritJobPolicy.permits(
                sourceJob, entityTier, typeId)) {
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
            // The registered recipe type, not an implementation-class name,
            // defines whether the tiered Occultism input contract applies.
            // This keeps code-defined/datapack extension recipes compatible.
            if ("crushing".equals(typeId)
                    || "crystallize".equals(typeId)) {
                Class<?> tiered = Class.forName(
                        "com.klikli_dev.occultism.crafting.recipe.TieredSingleRecipeInput");
                Constructor<?> constructor = tiered.getConstructor(ItemStack.class, int.class);
                Object tieredInput = constructor.newInstance(
                        input.copy(), recipeTier);
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
        return !sourceJob.isBlank()
                && OccultismSpiritJobPolicy.normalizeJobId(sourceJob)
                .equals(trader);
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

    private static ItemStack singleRecipeResult(
            Level level, Object recipe, ItemStack input,
            String typeId, int recipeTier) {
        if (level == null || recipe == null || input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            Object recipeInput;
            if ("crushing".equals(typeId)
                    || "crystallize".equals(typeId)) {
                Class<?> tiered = Class.forName(
                        "com.klikli_dev.occultism.crafting.recipe."
                                + "TieredSingleRecipeInput");
                recipeInput = tiered.getConstructor(
                        ItemStack.class, int.class).newInstance(
                        input.copy(), recipeTier);
            } else {
                recipeInput = new SingleRecipeInput(
                        input.copy());
            }
            Object assembled = invoke(recipe, "assemble", recipeInput,
                    level.registryAccess()).orElse(null);
            if (assembled instanceof ItemStack output
                    && !output.isEmpty()) {
                return output.copy();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall through to the stable preview result.
        }
        return result(level.registryAccess(), recipe);
    }

    private static int weightedTradeWeight(Object recipe) {
        Object weighted = invoke(recipe, "getWeightedResult").orElse(null);
        return weighted == null ? 0
                : Math.max(0, intValue(weighted, "weight", 0));
    }

    private static WeightedTradeCandidate selectWeightedTrade(
            List<WeightedTradeCandidate> candidates, long seed) {
        if (candidates.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (WeightedTradeCandidate candidate : candidates) {
            try {
                totalWeight = Math.addExact(totalWeight,
                        Math.max(0, candidate.weight()));
            } catch (ArithmeticException overflow) {
                return null;
            }
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = net.minecraft.util.RandomSource.create(seed)
                .nextInt(totalWeight);
        int[] weights = candidates.stream()
                .mapToInt(WeightedTradeCandidate::weight).toArray();
        int selected = OccultismSpiritJobPolicy.weightedIndex(
                weights, roll);
        return selected < 0 ? null : candidates.get(selected);
    }

    private static long stableTradeSeed(long selectionSeed,
                                        String sourceJob) {
        long value = selectionSeed;
        value ^= (long) sourceJob.hashCode() * 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static int spiritOperations(Object recipe, String typeId,
                                        ItemStack input, ItemStack baseOutput,
                                        OccultismSpiritJobConfig.WorkerSettings settings) {
        int requested = switch (typeId) {
            case "spirit_fire" -> input.getCount();
            case "crushing", "crystallize", "smelting", "blasting",
                 "smoking", "campfire_cooking", "spirit_trade" ->
                    settings.operationCount();
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
            case "smelting", "blasting", "smoking",
                 "campfire_cooking" -> "getCookingTime";
            default -> "";
        };
        int baseDuration = getter.isBlank() ? 80 : Math.max(1, intValue(recipe, getter, 80));
        if ("crushing".equals(typeId) || "crystallize".equals(typeId)
                || COOKING_RECIPE_TYPES.contains(typeId)) {
            return Math.max(1, (int) Math.ceil(baseDuration * settings.timeMultiplier()));
        }
        return baseDuration;
    }

    private static RecipeType<?> recipeType(String path) {
        String namespace = COOKING_RECIPE_TYPES.contains(path)
                ? "minecraft" : OCCULTISM;
        return BuiltInRegistries.RECIPE_TYPE.get(
                ResourceLocation.fromNamespaceAndPath(namespace, path));
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
            if (!candidate.getName().equals(method)
                    || candidate.getParameterCount() != args.length
                    || !parametersAccept(candidate.getParameterTypes(), args)) {
                continue;
            }
            try {
                return Optional.ofNullable(candidate.invoke(target, args));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A bridge/overload may reject the runtime subtype. Continue
                // to the next compatible public overload before giving up.
            }
        }
        return Optional.empty();
    }

    private static boolean parametersAccept(
            Class<?>[] parameters, Object[] arguments) {
        for (int index = 0; index < parameters.length; index++) {
            Object argument = arguments[index];
            if (argument == null) {
                if (parameters[index].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> parameter = parameters[index].isPrimitive()
                    ? primitiveWrapper(parameters[index])
                    : parameters[index];
            if (!parameter.isInstance(argument)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> primitiveWrapper(Class<?> primitive) {
        if (primitive == boolean.class) {
            return Boolean.class;
        } else if (primitive == byte.class) {
            return Byte.class;
        } else if (primitive == short.class) {
            return Short.class;
        } else if (primitive == int.class) {
            return Integer.class;
        } else if (primitive == long.class) {
            return Long.class;
        } else if (primitive == float.class) {
            return Float.class;
        } else if (primitive == double.class) {
            return Double.class;
        } else if (primitive == char.class) {
            return Character.class;
        }
        return primitive;
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

    private record RitualInputMatch(List<InputUse> uses,
                                    int primarySlot) {
    }

    private record RitualAutomationRequirement(
            RitualAutomationRole role,
            Predicate<ItemStack> matcher) {
    }

    private record WeightedTradeCandidate(
            ResourceLocation recipeId, ItemStack output, int weight) {
        private WeightedTradeCandidate {
            output = output.copy();
            weight = Math.max(0, weight);
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }

    private record Match() {
    }
}
