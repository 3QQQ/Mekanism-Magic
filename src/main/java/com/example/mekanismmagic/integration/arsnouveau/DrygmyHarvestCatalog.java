package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Data-pack-defined, bounded live harvests; no live entity interaction or ticking. */
final class DrygmyHarvestCatalog extends SimpleJsonResourceReloadListener {
    static final DrygmyHarvestCatalog INSTANCE = new DrygmyHarvestCatalog();
    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
    private static final Set<String> KINDS = Set.of("item", "loot_table", "fluid",
            "goat_horn", "mooshroom_stew", "frog_lights", "turtle_egg", "sniffer_egg",
            "colored_wool", "colored_dye", "cat_gift", "villager_gift");
    private static final Set<String> SIMPLE_KINDS = Set.of("item", "loot_table", "fluid");
    private static final int MAX_RULES = 256;
    private static volatile List<Rule> rules = List.of();
    private static volatile long revision;

    private DrygmyHarvestCatalog() {
        super(new Gson(), "drygmy_harvest");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        List<Rule> loaded = new ArrayList<>();
        WARNED.clear();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (loaded.size() >= MAX_RULES) {
                warn(entry.getKey(), "rule limit exceeded");
                return;
            }
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                if (!flag(json, "enabled", true)) {
                    return;
                }
                for (String mod : strings(json, "required_mods")) {
                    if (!ModCompatibility.loaded(mod)) {
                        return;
                    }
                }
                loaded.add(parse(entry.getKey(), json));
            } catch (Exception failure) {
                warn(entry.getKey(), "invalid definition: " + failure.getMessage());
            }
        });
        rules = List.copyOf(loaded);
        revision++;
        MekanismMagic.LOGGER.info("Loaded {} Drygmy live-harvest rules", loaded.size());
    }

    static long revision() {
        return revision;
    }

    static int ruleCount() {
        return rules.size();
    }

    static Set<ResourceLocation> ruleIds() {
        return Set.copyOf(rules.stream().map(Rule::id).toList());
    }

    static Harvest collect(ServerLevel level, List<LivingEntity> entities,
                           List<ItemStack> ordinaryCandidates, int operationMultiplier,
                           boolean includeFluids) {
        int multiplier = Math.clamp(operationMultiplier, 1, 256);
        List<ItemStack> items = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        Map<LivingEntity, CompoundTag> snapshots = new IdentityHashMap<>();
        for (Rule rule : rules) {
            if (rule.kind().equals("fluid") && !includeFluids) {
                continue;
            }
            Set<HarvestKey> seen = new HashSet<>();
            for (LivingEntity entity : entities) {
                try {
                    ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    if (!entity.isAlive() || !rule.entities().contains(type)
                            || rule.adult() && entity.isBaby()
                            || rule.baby() && !entity.isBaby()) {
                        continue;
                    }
                    CompoundTag data = snapshots.computeIfAbsent(entity, key -> {
                        CompoundTag tag = new CompoundTag();
                        key.saveWithoutId(tag);
                        return tag;
                    });
                    if (!NbtUtils.compareNbt(rule.nbt(), data, true)) {
                        continue;
                    }
                    boolean nativeKind = !SIMPLE_KINDS.contains(rule.kind());
                    Optional<String> variant = nativeKind
                            ? DrygmyVanillaHarvest.variantKey(rule.kind(), level, entity, entities)
                            : Optional.of("");
                    if (variant.isEmpty()) {
                        continue;
                    }
                    CompoundTag variantData = new CompoundTag();
                    for (String key : rule.variantKeys()) {
                        Tag value = path(data, key);
                        if (value != null) {
                            variantData.put(key, value.copy());
                        }
                    }
                    if (!seen.add(new HarvestKey(type, variant.get(), variantData))
                            || rule.chance() < 1 && level.random.nextDouble() >= rule.chance()) {
                        continue;
                    }
                    if (rule.kind().equals("fluid")) {
                        resolveFluid(rule).ifPresent(fluid -> {
                            long amount = (long) rule.amount() * multiplier;
                            addFluid(fluids, fluid.copyWithAmount((int) Math.min(Integer.MAX_VALUE, amount)));
                        });
                        continue;
                    }
                    List<ItemStack> generated;
                    if (rule.kind().equals("item")) {
                        var item = BuiltInRegistries.ITEM.getOptional(rule.item()).orElse(null);
                        if (item == null) {
                            warn(rule.id(), "output item is not registered");
                            continue;
                        }
                        generated = List.of(new ItemStack(item,
                                rule.minCount() + level.random.nextInt(rule.maxCount() - rule.minCount() + 1)));
                    } else if (rule.kind().equals("loot_table")) {
                        generated = sampleTable(level, entity, rule.lootTable());
                    } else {
                        generated = DrygmyVanillaHarvest.collect(rule.kind(), level, entity, entities);
                    }
                    for (ItemStack stack : generated) {
                        if (!DrygmyLootPolicy.shouldKeep(stack)) {
                            continue;
                        }
                        // Already-supported death loot keeps its existing selection rate.
                        // A shear/live rule must not add a second copy of the same variant.
                        if (ordinaryCandidates.stream().anyMatch(candidate ->
                                ItemStack.isSameItemSameComponents(candidate, stack))) {
                            continue;
                        }
                        int unscaled = nativeKind && rule.overrideCount()
                                ? rule.minCount() + level.random.nextInt(rule.maxCount() - rule.minCount() + 1)
                                : Math.min(stack.getCount(), 64);
                        int count = (int) Math.min(Integer.MAX_VALUE, (long) unscaled * multiplier);
                        addItem(items, stack.copyWithCount(count));
                    }
                } catch (RuntimeException failure) {
                    warn(rule.id(), "could not evaluate rule: " + failure.getClass().getSimpleName());
                }
            }
        }
        return new Harvest(List.copyOf(items), List.copyOf(fluids));
    }

    static List<ItemStack> sampleTable(ServerLevel level, LivingEntity entity, ResourceLocation id) {
        var table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        var allowed = table.getParamSet().getAllowed();
        LootParams.Builder builder = new LootParams.Builder(level);
        if (allowed.contains(LootContextParams.ORIGIN)) {
            builder.withParameter(LootContextParams.ORIGIN, entity.position());
        }
        if (allowed.contains(LootContextParams.THIS_ENTITY)) {
            builder.withParameter(LootContextParams.THIS_ENTITY, entity);
        }
        if (allowed.contains(LootContextParams.TOOL)) {
            builder.withParameter(LootContextParams.TOOL, new ItemStack(Items.SHEARS));
        }
        if (allowed.contains(LootContextParams.DAMAGE_SOURCE)) {
            builder.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
        }
        return table.getRandomItems(builder.create(table.getParamSet()));
    }

    private static Optional<FluidStack> resolveFluid(Rule rule) {
        if (rule.fluidContainer() != null) {
            var item = BuiltInRegistries.ITEM.getOptional(rule.fluidContainer()).orElse(null);
            if (item != null) {
                Optional<FluidStack> fluid = FluidUtil.getFluidContained(item.getDefaultInstance());
                if (fluid.isPresent() && !fluid.get().isEmpty()) {
                    return Optional.of(fluid.get().copyWithAmount(1));
                }
            }
        }
        if (rule.fluid() != null) {
            return BuiltInRegistries.FLUID.getOptional(rule.fluid())
                    .map(DrygmyHarvestCatalog::sourceFluid).filter(fluid ->
                            fluid != net.minecraft.world.level.material.Fluids.EMPTY)
                    .map(fluid -> new FluidStack(fluid, 1));
        }
        if (rule.fluidTag() == null) {
            return Optional.empty();
        }
        Set<Fluid> sources = new HashSet<>();
        BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, rule.fluidTag()))
                .ifPresent(tag -> tag.forEach(holder -> sources.add(sourceFluid(holder.value()))));
        return sources.stream().filter(fluid -> fluid != net.minecraft.world.level.material.Fluids.EMPTY)
                .sorted(Comparator.comparing(fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()))
                .findFirst().map(fluid -> new FluidStack(fluid, 1));
    }

    private static Fluid sourceFluid(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    private static Rule parse(ResourceLocation id, JsonObject json) throws Exception {
        Set<ResourceLocation> entities = new HashSet<>();
        for (String entity : strings(json, "entities")) {
            entities.add(ResourceLocation.parse(entity));
        }
        if (entities.isEmpty()) {
            throw new IllegalArgumentException("entities must be explicit");
        }
        String kind = json.get("kind").getAsString();
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("unsupported harvest kind");
        }
        int min = 1;
        int max = 1;
        if (json.has("count")) {
            JsonElement count = json.get("count");
            if (count.isJsonObject()) {
                min = count.getAsJsonObject().get("min").getAsInt();
                max = count.getAsJsonObject().get("max").getAsInt();
            } else {
                min = max = count.getAsInt();
            }
        }
        double chance = json.has("chance") ? json.get("chance").getAsDouble() : 1;
        int amount = json.has("amount") ? json.get("amount").getAsInt() : 1000;
        if (min < 1 || max < min || max > 64 || !Double.isFinite(chance)
                || chance < 0 || chance > 1 || amount < 1 || amount > 16000) {
            throw new IllegalArgumentException("count/chance/amount out of bounds");
        }
        ResourceLocation item = location(json, "item");
        ResourceLocation table = location(json, "loot_table");
        ResourceLocation fluid = location(json, "fluid");
        ResourceLocation fluidTag = location(json, "fluid_tag");
        ResourceLocation fluidContainer = location(json, "fluid_container");
        if (kind.equals("item") && item == null || kind.equals("loot_table") && table == null
                || kind.equals("fluid") && fluid == null && fluidTag == null && fluidContainer == null) {
            throw new IllegalArgumentException("missing output definition");
        }
        CompoundTag predicate = new CompoundTag();
        if (json.has("nbt")) {
            JsonElement nbt = json.get("nbt");
            if (!nbt.isJsonObject() && !(nbt.isJsonPrimitive() && nbt.getAsJsonPrimitive().isString())) {
                throw new IllegalArgumentException("nbt must be an object or SNBT compound string");
            }
            // SNBT strings preserve byte/short/long predicates such as Color:9b.
            predicate = TagParser.parseTag(nbt.isJsonObject() ? nbt.toString() : nbt.getAsString());
        }
        boolean adult = flag(json, "adult", false);
        boolean baby = flag(json, "baby", false);
        if (adult && baby) {
            throw new IllegalArgumentException("conflicting age conditions");
        }
        return new Rule(id, Set.copyOf(entities), kind, item, table, min, max, json.has("count"), chance,
                adult, baby, predicate, strings(json, "variant_keys"), fluid, fluidTag, fluidContainer, amount);
    }

    private static List<String> strings(JsonObject object, String key) {
        if (!object.has(key)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement value : object.getAsJsonArray(key)) {
            values.add(value.getAsString());
        }
        return List.copyOf(values);
    }

    private static ResourceLocation location(JsonObject object, String key) {
        return object.has(key) ? ResourceLocation.parse(object.get(key).getAsString()) : null;
    }

    private static boolean flag(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    private static Tag path(CompoundTag root, String path) {
        Tag value = root;
        for (String part : path.split("\\.")) {
            if (!(value instanceof CompoundTag compound)) {
                return null;
            }
            value = compound.get(part);
        }
        return value;
    }

    private static void addItem(List<ItemStack> values, ItemStack stack) {
        for (ItemStack value : values) {
            if (ItemStack.isSameItemSameComponents(value, stack)) {
                value.setCount((int) Math.min(Integer.MAX_VALUE, (long) value.getCount() + stack.getCount()));
                return;
            }
        }
        values.add(stack);
    }

    private static void addFluid(List<FluidStack> values, FluidStack stack) {
        for (FluidStack value : values) {
            if (FluidStack.isSameFluidSameComponents(value, stack)) {
                value.setAmount((int) Math.min(Integer.MAX_VALUE, (long) value.getAmount() + stack.getAmount()));
                return;
            }
        }
        values.add(stack);
    }

    private static void warn(ResourceLocation id, String reason) {
        if (WARNED.add(id)) {
            MekanismMagic.LOGGER.warn("Drygmy harvest {} skipped: {}", id, reason);
        }
    }

    record Harvest(List<ItemStack> items, List<FluidStack> fluids) {
    }

    private record HarvestKey(ResourceLocation type, String variant, CompoundTag data) {
    }

    private record Rule(ResourceLocation id, Set<ResourceLocation> entities, String kind,
                        ResourceLocation item, ResourceLocation lootTable, int minCount, int maxCount, boolean overrideCount,
                        double chance, boolean adult, boolean baby, CompoundTag nbt, List<String> variantKeys,
                        ResourceLocation fluid, ResourceLocation fluidTag, ResourceLocation fluidContainer, int amount) {
    }
}
