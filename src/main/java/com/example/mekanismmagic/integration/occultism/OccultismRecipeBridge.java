package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauIntegration;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Optional integration boundary. Occultism is deliberately not on the compile
 * classpath: this bridge discovers its recipe types and invokes their stable
 * public Recipe API at runtime.
 */
public final class OccultismRecipeBridge {
    private static final String OCCULTISM = "occultism";
    private static final List<TagKey<net.minecraft.world.item.Item>> MINER_ITEM_TAGS =
            List.of(
                    itemTag("miners/basic_resources"),
                    itemTag("miners/ores"),
                    itemTag("miners/deeps"),
                    itemTag("miners/master")
            );
    private static final Set<String> OCCULTISM_CONTAINMENT_PATHS = Set.of(
            "soul_gem",
            "fragile_soul_gem",
            "trinity_gem",
            "magic_lamp_empty"
    );
    private static final Set<String> TRADER_FACTORY_IDS = Set.of(
            "occultism:trader_otherstone",
            "occultism:trader_otherrock",
            "occultism:trader_otherworld_saplings",
            "occultism:gambler",
            "occultism:trader_gem"
    );
    private static final Map<String, Integer> PENTACLE_MODEL_DATA = Map.ofEntries(
            Map.entry("craft_afrit", 3),
            Map.entry("craft_djinni", 4),
            Map.entry("craft_foliot", 5),
            Map.entry("craft_marid", 6),
            Map.entry("possess_afrit", 7),
            Map.entry("possess_djinni", 8),
            Map.entry("possess_foliot", 9),
            Map.entry("summon_afrit", 13),
            Map.entry("summon_djinni", 14),
            Map.entry("summon_foliot", 15),
            Map.entry("summon_marid", 16),
            Map.entry("summon_wild_afrit", 17),
            Map.entry("summon_wild_greater_spirit", 18)
    );
    private static final List<String> RITUAL_CHALK_COLORS = List.of(
            "gold", "purple", "red", "white"
    );
    private static final Map<String, Set<String>> DEFAULT_PENTACLE_CHALK_COLORS =
            Map.ofEntries(
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
                    Map.entry("summon_afrit",
                            Set.of("gray", "lime", "orange", "red", "white")),
                    Map.entry("summon_djinni",
                            Set.of("lime", "light_gray", "white")),
                    Map.entry("summon_foliot", Set.of("white")),
                    Map.entry("summon_marid",
                            Set.of("black", "lime", "orange", "red", "blue", "white")),
                    Map.entry("summon_wild_afrit",
                            Set.of("gray", "lime", "orange", "white")),
                    Map.entry("summon_wild_greater_spirit",
                            Set.of("black", "lime", "orange", "red", "white"))
            );

    private OccultismRecipeBridge() {
    }

    public record InputUse(int slot, int count) {
    }

    public record RecipeResult(ResourceLocation id, ItemStack output, int duration,
                               List<InputUse> inputs, int activationSlot, int sacrificeSlot,
                               String command) {
        public RecipeResult(ResourceLocation id, ItemStack output, int duration,
                            List<InputUse> inputs, int activationSlot, int sacrificeSlot) {
            this(id, output, duration, inputs, activationSlot, sacrificeSlot, "");
        }

        public boolean isCommand() {
            return command != null && !command.isBlank();
        }
    }

    public record RitualProjection(ResourceLocation recipeId, ResourceLocation pentacleId,
                                    Object multiblock) {
    }

    public record PentacleJeiData(ResourceLocation pentacleId,
                                  List<ItemStack> materials,
                                    List<String> chalkColors,
                                    ItemStack output) {
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
        if (stack.isEmpty()) {
            return false;
        }
        // Occultism 1.20.1 does not expose a parent "occultism:miners" tag.
        // Its miner recipes target four leaf tags instead.
        if (MINER_ITEM_TAGS.stream().anyMatch(stack::is)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && OCCULTISM.equals(id.getNamespace())
                && id.getPath().startsWith("miner_");
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
        CompoundTag tag = miner.getTag();
        String tagName = "getMaxMiningTime".equals(method)
                ? "maxMiningTime" : "rollsPerOperation";
        if (tag != null && tag.contains(tagName)) {
            return Math.max(1, tag.getInt(tagName));
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
        List<MinerOutput> candidates = new ArrayList<>();
        long totalWeight = 0;
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            List<Ingredient> recipeIngredients = ingredients(recipe);
            if (recipeIngredients.isEmpty() || !recipeIngredients.get(0).test(miner)) {
                continue;
            }
            ItemStack output = result(level.registryAccess(), recipe);
            int weight = intValue(invoke(recipe, "getWeightedResult").orElse(null),
                    "weight", 0);
            if (output.isEmpty() || weight <= 0) {
                continue;
            }
            candidates.add(new MinerOutput(recipeId(recipe), output, weight));
            totalWeight += weight;
        }
        if (candidates.isEmpty() || totalWeight <= 0) {
            return Optional.empty();
        }
        long roll = Math.floorMod(level.random.nextLong(), totalWeight);
        for (MinerOutput candidate : candidates) {
            roll -= candidate.weight();
            if (roll < 0) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
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
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            List<Ingredient> recipeIngredients = ingredients(recipe);
            if (recipeIngredients.isEmpty() || isEmptyIngredient(recipeIngredients.get(0))) {
                continue;
            }
            ItemStack output = result(level.registryAccess(), recipe);
            int weight = intValue(invoke(recipe, "getWeightedResult").orElse(null),
                    "weight", 0);
            if (!output.isEmpty() && weight > 0) {
                result.add(new MinerJeiData(recipeId(recipe),
                        recipeIngredients.get(0), output, weight));
            }
        }
        return List.copyOf(result);
    }

    public static Optional<RecipeResult> findSpiritRecipe(Level level, ItemStackHandler inventory,
                                                          ItemStack containment) {
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
            for (Recipe<?> recipeHolder : recipes(level, type)) {
                Object recipe = recipeHolder;
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
                    return Optional.of(new RecipeResult(recipeId(recipe), output,
                            recipeDuration(recipe, typeId, settings),
                            List.of(new InputUse(0, operations)), -1, -1));
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<RecipeResult> findRitualRecipe(Level level, ItemStackHandler inventory,
                                                          ItemStack ritualSelector, ItemStack activation,
                                                          ItemStack sacrifice,
                                                          boolean dictionaryEnabled) {
        if (level == null) {
            return Optional.empty();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return Optional.empty();
        }
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            if (!isMachineSafeRitual(recipe)) {
                continue;
            }
            if (!matchesRitualSelector(recipeId(recipe), recipe, ritualSelector)) {
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
                return Optional.of(new RecipeResult(recipeId(recipe), output,
                        Math.max(40, intValue(recipe, "getDuration", 200)),
                        slots.get(),
                        activationSlot, sacrificeSlot, command));
            }
        }
        return Optional.empty();
    }

    /**
     * Supports Occultism's ENTITY_DATA-based soul containers and Ars
     * Nouveau's mob jar data component without hard-linking either mod's
     * implementation classes.
     */
    public static boolean isFilledContainment(ItemStack stack) {
        return isFilledOccultismContainment(stack)
                || ArsNouveauIntegration.isFilledMobJar(stack);
    }

    public static boolean isSpiritSource(ItemStack stack) {
        if (stack.isEmpty() || spiritTier(stack) <= 0) {
            return false;
        }
        // 1.20.1 uses both SpawnEggItem and filled Soul Gem / magic lamp
        // containers. Ars Nouveau jars are handled by isFilledContainment.
        return stack.getItem() instanceof SpawnEggItem
                || isFilledContainment(stack)
                || !spiritItemEntityId(stack).isEmpty();
    }

    private static boolean isFilledOccultismContainment(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag data = entityDataTag(stack);
        if (data == null || data.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && OCCULTISM.equals(itemId.getNamespace())
                && OCCULTISM_CONTAINMENT_PATHS.contains(itemId.getPath())
                && !entityDataId(stack).isEmpty();
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
        return id != null && OCCULTISM.equals(id.getNamespace())
                && (isUniversalChalk(stack)
                || RITUAL_CHALK_COLORS.stream().anyMatch(
                color -> id.getPath().equals("chalk_" + color)));
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
        Object sizeValue = invoke(pentacle, "getSize").orElse(null);
        if (!(sizeValue instanceof net.minecraft.core.Vec3i size)) {
            return availableChalkColors(
                    DEFAULT_PENTACLE_CHALK_COLORS.getOrDefault(
                            pentaclePath, Set.of()));
        }
        Set<String> colors = new LinkedHashSet<>();
        // The all-otherstone/otherrock layer in Occultism's dense preview is
        // only the visual platform. Modonomicon's internal Y orientation can
        // differ between data and runtime, so identify and skip that layer by
        // its contents instead of assuming y == 0 or y == 1.
        for (int y = 0; y < size.getY(); y++) {
            Map<net.minecraft.world.item.Item, Integer> layerCounts =
                    new LinkedHashMap<>();
            boolean hasRealMaterial = false;
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    Object stateValue = invoke(pentacle, "getBlockState",
                            new BlockPos(x, y, z)).orElse(null);
                    if (stateValue instanceof BlockState state) {
                        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        if (id != null && OCCULTISM.equals(id.getNamespace())
                                && id.getPath().startsWith("chalk_glyph_")) {
                            String color = id.getPath().substring("chalk_glyph_".length());
                            if (RITUAL_CHALK_COLORS.contains(color)) {
                                colors.add(color);
                            }
                        }
                    }
                }
            }
        }
        if (!colors.isEmpty()) {
            return colors;
        }
        return availableChalkColors(
                DEFAULT_PENTACLE_CHALK_COLORS.getOrDefault(
                        pentaclePath, Set.of()));
    }

    private static Set<String> availableChalkColors(Set<String> colors) {
        return colors.stream()
                .filter(RITUAL_CHALK_COLORS::contains)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
    }

    public static Optional<RitualProjection> findProjection(Level level, ItemStack ritualDummy) {
        if (level == null || ritualDummy.isEmpty()) {
            return Optional.empty();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return Optional.empty();
        }
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            Object dummy = invoke(recipe, "getRitualDummy").orElse(null);
            if (dummy instanceof ItemStack stack
                    && ItemStack.isSameItem(ritualDummy, stack)) {
                return projection(recipeId(recipe), recipe);
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
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            if (recipeId(recipe).equals(recipeId)) {
                return projection(recipeId(recipe), recipe);
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
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            Object value = invoke(recipe, "getPentacleId").orElse(null);
            if (pentacleId.equals(value)) {
                return projection(recipeId(recipe), recipe);
            }
        }
        return Optional.empty();
    }

    public static List<PentacleJeiData> pentacleJeiRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return List.of();
        }
        Map<String, PentacleJeiData> grouped = new LinkedHashMap<>();
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            Optional<RitualProjection> projection = projection(recipeId(recipe), recipe);
            if (projection.isEmpty()) {
                continue;
            }
            String key = projection.get().pentacleId().toString();
            grouped.putIfAbsent(key, new PentacleJeiData(
                    projection.get().pentacleId(),
                    pentacleMaterialStacks(projection.get().multiblock()),
                    List.copyOf(ritualChalkColors(recipe)),
                    createPentacleMiniRitual(projection.get())));
        }
        return List.copyOf(grouped.values());
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
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            if (!isMachineSafeRitual(recipe)) {
                continue;
            }
            Optional<RitualProjection> projection = projection(recipeId(recipe), recipe);
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
            result.add(new RitualJeiData(recipeId(recipe),
                    projection.get().pentacleId(),
                    List.copyOf(ritualIngredients),
                    activation,
                    sacrificeExamples(recipe),
                    createPentacleMiniRitual(projection.get()),
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
            for (Recipe<?> recipeHolder : recipes(level, type)) {
                Object recipe = recipeHolder;
                Ingredient ingredient = ingredients(recipe).stream().findFirst().orElse(null);
                if (ingredient == null || ingredient.getItems().length == 0) {
                    continue;
                }
                ItemStack input = ingredient.getItems()[0].copy();
                input.setCount(1);
                ItemStack baseOutput = result(level.registryAccess(), recipe);
                if (baseOutput.isEmpty()) {
                    continue;
                }
                if ("spirit_fire".equals(typeId)) {
                    addSpiritJeiRecipe(result, recipeId(recipe), typeId, input,
                            spiritSource("foliot", ""), baseOutput);
                } else if ("spirit_trade".equals(typeId)) {
                    String trader = stringValue(recipe, "getTrader");
                    String entity = trader.contains("gambler") ? "djinni" : "foliot";
                    addSpiritJeiRecipe(result, recipeId(recipe), typeId, input,
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
                        addSpiritJeiRecipe(result, recipeId(recipe), typeId, input,
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
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                OCCULTISM, entityPath);
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
        source.getOrCreateTag().put("EntityTag", entityData);
        return source;
    }

    private static List<ItemStack> pentacleMaterialStacks(Object multiblock) {
        Object sizeValue = invoke(multiblock, "getSize").orElse(null);
        if (!(sizeValue instanceof net.minecraft.core.Vec3i size)) {
            return List.of();
        }
        Map<net.minecraft.world.item.Item, Integer> counts = new LinkedHashMap<>();
        for (int y = 0; y < size.getY(); y++) {
            Map<net.minecraft.world.item.Item, Integer> layerCounts =
                    new LinkedHashMap<>();
            boolean hasProjectionPlatform = false;
            int possiblePlatformStone = 0;
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    Object value = invoke(multiblock, "getBlockState",
                            new BlockPos(x, y, z)).orElse(null);
                    if (!(value instanceof BlockState state)) {
                        continue;
                    }
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (id == null || (OCCULTISM.equals(id.getNamespace())
                            && id.getPath().startsWith("chalk_glyph_"))) {
                        continue;
                    }
                    net.minecraft.world.item.Item item = state.getBlock().asItem();
                    if (item == net.minecraft.world.item.Items.AIR) {
                        continue;
                    }
                    String path = id.getPath();
                    // Otherstone/otherrock is the projection platform, not a
                    // pentacle formation ingredient. It must never appear in
                    // miniature-ritual machine inputs, even on a mixed layer.
                    boolean platformMaterial = OCCULTISM.equals(id.getNamespace())
                            && (path.equals("otherstone") || path.equals("otherrock"));
                    if (platformMaterial) {
                        hasProjectionPlatform = true;
                        continue;
                    }
                    // Occultism 1.20.1's projection base alternates
                    // otherstone and vanilla stone. Delay counting stone
                    // until we know this is not the pure projection layer.
                    if ("minecraft".equals(id.getNamespace())
                            && "stone".equals(path)) {
                        possiblePlatformStone++;
                        continue;
                    }
                    layerCounts.merge(item, 1, Integer::sum);
                }
            }
            if (hasProjectionPlatform && layerCounts.isEmpty()) {
                // Pure projection base: discard both otherstone and its
                // alternating vanilla-stone filler.
                continue;
            }
            if (possiblePlatformStone > 0) {
                layerCounts.merge(Items.STONE, possiblePlatformStone,
                        Integer::sum);
            }
            if (!layerCounts.isEmpty()) {
                layerCounts.forEach((item, count) ->
                        counts.merge(item, count, Integer::sum));
            }
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : counts.entrySet()) {
            int remaining = entry.getValue();
            int max = entry.getKey().getMaxStackSize();
            while (remaining > 0) {
                int count = Math.min(remaining, max);
                stacks.add(new ItemStack(entry.getKey(), count));
                remaining -= count;
            }
        }
        return stacks;
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
        SpawnEggItem egg = SpawnEggItem.byId(candidates.get(0));
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

    public static Optional<RecipeResult> findMiniRitualRecipe(Level level,
                                                              ItemStackHandler inventory) {
        List<RecipeResult> candidates =
                findMiniRitualCandidates(level, inventory);
        return candidates.isEmpty()
                ? Optional.empty() : Optional.of(candidates.get(0));
    }

    public static List<RecipeResult> findMiniRitualCandidates(
            Level level, ItemStackHandler inventory) {
        Map<String, RecipeResult> candidates = new LinkedHashMap<>();
        RecipeType<?> type = recipeType("ritual");
        if (type == null) {
            return List.of();
        }
        for (Recipe<?> recipeHolder : recipes(level, type)) {
            Object recipe = recipeHolder;
            Optional<RitualProjection> projection =
                    projection(recipeId(recipe), recipe);
            if (projection.isEmpty()) {
                continue;
            }
            Optional<List<InputUse>> materialSlots = matchPentacleMaterials(
                    pentacleMaterialStacks(projection.get().multiblock()),
                    inventory);
            if (materialSlots.isEmpty()) {
                continue;
            }
            if (!matchesRitualProjectionChalk(projection.get(), inventory)) {
                continue;
            }
            RecipeResult candidate = new RecipeResult(recipeId(recipe),
                    createPentacleMiniRitual(projection.get()), 100,
                    new ArrayList<>(materialSlots.get()), -1, -1);
            candidates.putIfAbsent(
                    projection.get().pentacleId().toString(), candidate);
        }
        return List.copyOf(candidates.values());
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
                        || !ItemStack.isSameItemSameTags(
                        available, requirement)) {
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

    private static void addInputUse(List<InputUse> matched,
                                    int slot, int count) {
        for (int index = 0; index < matched.size(); index++) {
            InputUse existing = matched.get(index);
            if (existing.slot() == slot) {
                matched.set(index, new InputUse(
                        slot, existing.count() + count));
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
        ItemStack output = new ItemStack(MekanismMagic.MINI_RITUAL.get());
        output.getOrCreateTag().putString(
                "pentacle", projection.pentacleId().toString());
        return output;
    }

    public static void bindMiniRitual(ItemStack stack, RitualProjection projection) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("ritual", projection.recipeId().toString());
        tag.putString("pentacle", projection.pentacleId().toString());
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
        if (ArsNouveauIntegration.emptyMobJar(stack)) {
            inventory.setStackInSlot(slot, stack);
        } else if (isFilledOccultismContainment(stack)) {
            CompoundTag root = stack.getTag();
            if (root != null) {
                root.remove("EntityTag");
            }
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
        CompoundTag data = stack.getTag();
        if (data == null || data.isEmpty()) {
            return 0;
        }
        String pentacle = data.getString("pentacle");
        ResourceLocation id = ResourceLocation.tryParse(pentacle);
        return id == null ? 0 : PENTACLE_MODEL_DATA.getOrDefault(id.getPath(), 0);
    }

    public static Component pentacleDisplayName(ResourceLocation pentacleId) {
        if (pentacleId == null) {
            return Component.translatable(
                    "item.mekanism_magic.mini_ritual.pentacle_only");
        }
        String key = "book.occultism.dictionary_of_spirits.pentacles."
                + pentacleId.getPath() + ".name";
        Component translated = Component.translatable(key);
        if (!translated.getString().equals(key)) {
            return translated;
        }
        String fallbackKey =
                "pentacle.mekanism_magic." + pentacleId.getPath();
        Component fallback = Component.translatable(fallbackKey);
        return fallback.getString().equals(fallbackKey)
                ? Component.literal(pentacleId.getPath()) : fallback;
    }

    private static String entityId(ItemStack stack) {
        if (stack.getItem() instanceof SpawnEggItem egg) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(
                    egg.getType(stack.getTag()));
            return id == null ? "" : id.toString();
        }
        String arsNouveauEntity = ArsNouveauIntegration.entityId(stack);
        if (!arsNouveauEntity.isEmpty()) {
            return arsNouveauEntity;
        }
        String entityData = entityDataId(stack);
        return entityData.isEmpty() ? spiritItemEntityId(stack) : entityData;
    }

    private static String entityDataId(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = entityDataTag(stack);
        if (tag == null) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        return id == null ? "" : id.toString();
    }

    private static String spiritJobId(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = entityDataTag(stack);
        if (tag == null) {
            tag = ArsNouveauIntegration.entityTag(stack);
        }
        if (tag != null) {
            net.minecraft.nbt.CompoundTag spiritJob = tag.getCompound("spiritJob");
            String factoryId = spiritJob.getString("factoryId");
            if (!factoryId.isBlank()) {
                return factoryId;
            }
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !OCCULTISM.equals(id.getNamespace())) {
            return "";
        }
        return switch (id.getPath()) {
            case "book_of_calling_foliot_otherstone_trader" ->
                    "occultism:trader_otherstone";
            case "book_of_calling_foliot_sapling_trader" ->
                    "occultism:trader_otherworld_saplings";
            case "book_of_calling_foliot_gambler" ->
                    "occultism:gambler";
            case "book_of_calling_foliot_gem_trader" ->
                    "occultism:trader_gem";
            default -> "";
        };
    }

    private static String spiritItemEntityId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !OCCULTISM.equals(id.getNamespace())) {
            return "";
        }
        return switch (id.getPath()) {
            case "book_of_binding_bound_foliot",
                    "miner_foliot_unspecialized" -> "occultism:foliot";
            case "book_of_binding_bound_djinni",
                    "miner_djinni_ores" -> "occultism:djinni";
            case "book_of_binding_bound_afrit",
                    "miner_afrit_deeps" -> "occultism:afrit";
            case "book_of_binding_bound_marid",
                    "miner_marid_master" -> "occultism:marid";
            default -> "";
        };
    }

    private static net.minecraft.nbt.CompoundTag entityDataTag(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) {
            return null;
        }
        // Vanilla/Ars-compatible containment and Occultism spawn eggs use
        // EntityTag. Occultism 1.20.1 Soul Gem stores the captured entity at
        // the root key entityData instead.
        if (root.contains("EntityTag")) {
            return root.getCompound("EntityTag").copy();
        }
        if (root.contains("entityData")) {
            return root.getCompound("entityData").copy();
        }
        return null;
    }

    private static String customRitualId(ItemStack stack) {
        CompoundTag data = stack.getTag();
        if (data == null || data.isEmpty()) {
            return "";
        }
        return data.getString("ritual");
    }

    private static String customPentacleId(ItemStack stack) {
        CompoundTag data = stack.getTag();
        if (data == null || data.isEmpty()) {
            return "";
        }
        return data.getString("pentacle");
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
                || ritualType.equals(
                "summon_with_chance_of_chicken_tamed");
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
            output.getOrCreateTag().put("EntityTag", entityData);
        }
        output.setCount(Math.min(count, output.getMaxStackSize()));
        return output;
    }

    private static ItemStack commandResult(Level level, Object recipe) {
        ResourceLocation flameAutomation =
                ResourceLocation.fromNamespaceAndPath(
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
            return egg.getType(stack.getTag());
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
            String recipeClassName = recipe.getClass().getName();
            Class<?> fakeInventory = Class.forName(
                    "com.klikli_dev.occultism.crafting.recipe.ItemStackFakeInventory");
            Object recipeInput = fakeInventory.getConstructor(ItemStack.class)
                    .newInstance(input.copy());
            Method matches = findMethod(recipe, "matches",
                    fakeInventory, Level.class);
            boolean tieredRecipe = recipeClassName.contains("CrushingRecipe")
                    || recipeClassName.contains("CrystallizeRecipe");
            if (!tieredRecipe && matches != null
                    && (boolean) matches.invoke(recipe, recipeInput, level)) {
                return Optional.of(new Match());
            }
            if ("spirit_trade".equals(typeId)) {
                // 1.20.1 SpiritTradeRecipe validates its shapeless ingredient
                // list directly. Newer versions expose TraderRecipeInput;
                // support both without linking either implementation.
                Method validArray = findMethod(recipe, "isValid",
                        ItemStack[].class);
                if (validArray != null && (boolean) validArray.invoke(
                        recipe, (Object) new ItemStack[]{input.copy()})) {
                    return Optional.of(new Match());
                }
                Method validList = findMethod(recipe, "isValid", List.class);
                if (validList != null && (boolean) validList.invoke(
                        recipe, List.of(input.copy()))) {
                    return Optional.of(new Match());
                }
                try {
                    Class<?> traderInput = Class.forName(
                            "com.klikli_dev.occultism.crafting.recipe.TraderRecipeInput");
                    Constructor<?> constructor = traderInput.getConstructor(
                            ItemStack.class, String.class);
                    String trader = stringValue(recipe, "getTrader");
                    if (!trader.isBlank() && canUseTrader(sourceJob, trader)) {
                        Object trade = constructor.newInstance(input.copy(), trader);
                        Method tradeMatches = findMethod(recipe, "matches",
                                trade.getClass(), Level.class);
                        if (tradeMatches != null && (boolean) tradeMatches.invoke(
                                recipe, trade, level)) {
                            return Optional.of(new Match());
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                    // Forge 1.20.1 uses the isValid overloads above.
                }
            }
            // Occultism 1.20.1 crushing recipes require the tier-aware
            // ItemStackFakeInventory subtype. A missing class used to make
            // every crushing recipe silently fail here.
            if (tieredRecipe) {
                Class<?> tiered = Class.forName(
                        "com.klikli_dev.occultism.crafting.recipe."
                                + "TieredItemStackFakeInventory");
                Constructor<?> constructor = tiered.getConstructor(ItemStack.class, int.class);
                Object tieredInput = constructor.newInstance(input.copy(), spiritTier);
                Method tieredMatches = findMethod(recipe, "matches",
                        fakeInventory, Level.class);
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

    private static ItemStack result(RegistryAccess registries, Object recipe) {
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
        return BuiltInRegistries.RECIPE_TYPE.get(
                ResourceLocation.fromNamespaceAndPath(OCCULTISM, path));
    }

    private static TagKey<net.minecraft.world.item.Item> itemTag(String path) {
        return TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(OCCULTISM, path));
    }

    private static ResourceLocation recipeId(Object recipe) {
        return invoke(recipe, "getId")
                .filter(ResourceLocation.class::isInstance)
                .map(ResourceLocation.class::cast)
                .orElse(ResourceLocation.fromNamespaceAndPath(
                        "mekanism_magic", "runtime_occultism_recipe"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Recipe<?>> recipes(Level level, RecipeType<?> type) {
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    private static Method findMethod(Object target, String name, Class<?>... parameterTypes) {
        try {
            return target.getClass().getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
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
