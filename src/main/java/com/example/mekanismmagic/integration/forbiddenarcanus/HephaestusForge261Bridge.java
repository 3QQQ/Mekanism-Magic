package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.integration.common.recipe.BoundedSlotMatcher;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusRecipeSelector.Selection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stal111.forbidden_arcanus.common.block.HephaestusForgeBlock;
import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeLevel;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceModifier;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesDefinition;
import com.stal111.forbidden_arcanus.common.block.entity.forge.input.EssenceDataInput;
import com.stal111.forbidden_arcanus.common.block.entity.forge.input.EssenceStorageInput;
import com.stal111.forbidden_arcanus.common.block.entity.forge.input.HephaestusForgeInput;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.CreateItemResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.TransmuteInputResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.UpgradeTierResult;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerDefinition;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerHelper;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerTarget;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exact 2.6.1 forge ABI. Four-channel arrays always use Aureal, Souls, Blood, Experience.
 * This boundary never starts a native world ritual or scans entities for essence.
 */
public final class HephaestusForge261Bridge {
    public static final int CHANNEL_COUNT = 4;
    public static final int AUXILIARY_SLOTS = 8;
    public static final int ENHANCER_SLOTS = 4;
    // This is the frozen mod registry, not a per-world dynamic registry. Preserve
    // native registration order, including unsupported handlers that win a match.
    private static final List<HephaestusForgeInput> INPUT_HANDLERS =
            FARegistries.FORGE_INPUT_REGISTRY.holders().map(Holder::value).toList();

    private HephaestusForge261Bridge() {
    }

    /** Remote registries omit magic circles/effects. Only synchronized recipe facts are read here. */
    static List<ForbiddenRecipeDisplayCatalog.RitualDisplay> readDisplayRituals(RegistryAccess registries,
                List<ItemStack> candidates, Map<ResourceLocation, List<ItemStack>> enhancerExamples) {
        List<ForbiddenRecipeDisplayCatalog.RitualDisplay> result = new ArrayList<>();
        registries.registryOrThrow(FARegistries.RITUAL).holders()
                .sorted(Comparator.comparing(holder -> holder.key().location().toString())).forEach(holder -> {
                    Ritual ritual = holder.value();
                    if (!displaySupported(ritual)) return;
                    int upgradeTier = ritual.result() instanceof UpgradeTierResult upgrade ? upgrade.resultTier() : 0;
                    List<Integer> supportedTiers = java.util.stream.IntStream.rangeClosed(1, 5)
                            .filter(tier -> displayTierMatches(ritual, tier)).boxed().toList();
                    if (supportedTiers.isEmpty()) return;
                    var requirements = ritual.requirements();
                    var essences = requirements.essences();
                    List<ResourceLocation> enhancerIds = requirements.enhancers().stream()
                            .map(ForbiddenArcanus261Bridge::referenceId).sorted(Comparator.comparing(ResourceLocation::toString)).toList();
                    List<List<ItemStack>> enhancers = enhancerIds.stream()
                            .map(id -> enhancerExamples.getOrDefault(id, List.of())).toList();
                    RitualView view = new RitualView(holder.key().location(), ForbiddenArcanus261Bridge.ingredient(ritual.mainIngredient()),
                            ritual.inputs().stream().map(input -> new ForbiddenArcanusRecipeCatalog.RitualIngredient(
                                    ForbiddenArcanus261Bridge.ingredient(input.ingredient()), input.amount())).toList(),
                            new ForbiddenArcanusRecipeCatalog.EssenceRequirements(essences.aureal(), essences.souls(),
                                    essences.blood(), essences.experience()), requirements.tier().tier(), requirements.tier().matchExact(),
                            enhancerIds, ritual.magicCircleType() == null ? null : ForbiddenArcanus261Bridge.referenceId(ritual.magicCircleType()),
                            ritual.duration(), FARegistries.RITUAL_RESULT_TYPE_REGISTRY.getKey(ritual.result().getType()));
                    List<ForbiddenRecipeDisplayCatalog.TransformExample> examples = new ArrayList<>();
                    for (ItemStack main : ritual.mainIngredient().getItems()) {
                        if (main.isEmpty() || !ritual.mainIngredient().test(main.copyWithCount(1))) continue;
                        ItemStack output = upgradeTier > 0 ? ItemStack.EMPTY : resultFor(ritual, main);
                        if (upgradeTier > 0 || !output.isEmpty()) {
                            examples.add(new ForbiddenRecipeDisplayCatalog.TransformExample(main, output));
                        }
                    }
                    result.add(new ForbiddenRecipeDisplayCatalog.RitualDisplay(view, examples, supportedTiers,
                            enhancers, upgradeTier, ForbiddenRecipeDisplayCatalog.signature(registries,
                            view, examples, supportedTiers, enhancers, upgradeTier)));
                });
        return List.copyOf(result);
    }

    /** Component-sensitive ordinary item preview; tier is internal machine state. */
    public static Optional<ItemStack> previewRitual(RegistryAccess registries, ResourceLocation id,
                                                    ItemStack main, int tier) {
        Ritual ritual = registries.registryOrThrow(FARegistries.RITUAL).get(id);
        if (ritual == null || ritual.result() instanceof UpgradeTierResult
                || !displaySupported(ritual) || !displayTierMatches(ritual, tier)
                || main.isEmpty() || !ritual.mainIngredient().test(main.copyWithCount(1))) return Optional.empty();
        ItemStack result = resultFor(ritual, main);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.copy());
    }

    /** A tier ritual changes the machine, not an item returned to inventory or AE. */
    public static java.util.OptionalInt previewUpgradeTier(RegistryAccess registries, ResourceLocation id, int tier) {
        Ritual ritual = registries.registryOrThrow(FARegistries.RITUAL).get(id);
        return ritual != null && ritual.result() instanceof UpgradeTierResult upgrade
                && displaySupported(ritual) && displayTierMatches(ritual, tier)
                ? java.util.OptionalInt.of(upgrade.resultTier()) : java.util.OptionalInt.empty();
    }

    /** Exact consumed auxiliary roles; tier and enhancers are retained machine context. */
    public static boolean matchesDisplayedAuxiliary(RegistryAccess registries, ResourceLocation id,
                                                    List<ItemStack> auxiliary) {
        Ritual ritual = registries.registryOrThrow(FARegistries.RITUAL).get(id);
        if (ritual == null || !displaySupported(ritual) || auxiliary.size() != ritual.inputs().size()) return false;
        for (int i = 0; i < auxiliary.size(); i++) {
            ItemStack stack = auxiliary.get(i);
            var required = ritual.inputs().get(i);
            if (stack.isEmpty() || stack.getCount() != required.amount()
                    || !required.ingredient().test(stack.copyWithCount(1))) return false;
        }
        return true;
    }

    private static boolean displaySupported(Ritual ritual) {
        long units = ritual.inputs().stream().mapToLong(input -> input.amount()).sum();
        return hasSupportedResult(ritual) && units >= 0 && units <= AUXILIARY_SLOTS
                && ritual.inputs().stream().allMatch(input -> input.amount() > 0);
    }

    private static boolean displayTierMatches(Ritual ritual, int tier) {
        return validTier(tier) && ritual.requirements().tier().test(tier)
                && ritual.requirements().enhancers().size() <= allowedEnhancerCount(tier)
                && (!(ritual.result() instanceof UpgradeTierResult upgrade) || upgrade.resultTier() == tier + 1);
    }

    /** Legacy core migration only; new machines never need this physical item. */
    public static int forgeTier(ItemStack core) {
        if (!core.isEmpty() && core.getItem() instanceof BlockItem item
                && item.getBlock() instanceof HephaestusForgeBlock block
                && core.is(block.asItem()) && block.getLevel().getBlock() == block) {
            return block.getLevel().getAsInt();
        }
        return 0;
    }

    public static int[] capacities(int tier) {
        return !validTier(tier) ? new int[CHANNEL_COUNT]
                : amounts(HephaestusForgeLevel.values()[tier - 1].getMaxEssences());
    }

    public static int allowedEnhancerCount(int tier) {
        return validTier(tier) ? Math.min(tier, ENHANCER_SLOTS) : 0;
    }

    private static boolean validTier(int tier) { return tier >= 1 && tier <= 5; }

    public static boolean isEssenceInput(ItemStack stack) {
        return inputHandler(stack).isPresent();
    }

    /** One retained native component-bearing sample; stack count never multiplies its yield. */
    public static Optional<EssenceSample> previewRetainedEssenceSample(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ItemStack single = stack.copyWithCount(1);
        var handler = inputHandler(single);
        if (handler.isEmpty()) return Optional.empty();
        EssenceType type;
        int amount;
        if (handler.get().getClass() == EssenceDataInput.class) {
            var data = single.get(ModDataComponents.ESSENCE_VALUE);
            if (data == null) return Optional.empty();
            type = data.type();
            amount = data.amount();
        } else if (handler.get().getClass() == EssenceStorageInput.class) {
            var stored = single.get(ModDataComponents.ESSENCE_STORAGE);
            if (stored == null) return Optional.empty();
            type = stored.value().type();
            amount = stored.value().amount();
        } else {
            return Optional.empty();
        }
        return type != null && amount > 0 && type.ordinal() < CHANNEL_COUNT
                ? Optional.of(new EssenceSample(type.ordinal(), amount)) : Optional.empty();
    }

    /**
     * Deterministic only: the two exact native handlers below do not sample random values.
     * The physical working slot owns one container; callers must split a stacked source first.
     */
    public static Optional<EssenceTransfer> prepareEssenceInput(ServerLevel level, ItemStack single,
                                                               int[] room) {
        if (single.getCount() != 1 || !validAmounts(room)) {
            return Optional.empty();
        }
        var handler = inputHandler(single);
        if (handler.isEmpty()) {
            return Optional.empty();
        }
        ItemStack working = single.copy();
        var offered = handler.get().getInputValue(working, level.getRandom());
        int channel = offered.type().ordinal();
        int amount = offered.amount();
        if (amount <= 0 || room[channel] <= 0) {
            return Optional.empty();
        }
        if (handler.get().getClass() == EssenceStorageInput.class) {
            amount = Math.min(amount, room[channel]);
        } else if (amount > room[channel]) {
            return Optional.empty();
        }
        ItemStack remainder = handler.get().finishInput(working, amount);
        return Optional.of(new EssenceTransfer(channel, amount, remainder));
    }

    private static Optional<HephaestusForgeInput> inputHandler(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        // Inspect the two actual components before choosing a native handler.
        // ESSENCE_VALUE's registry name in 2.6.1 is essence_data, not essence_value.
        var data = Optional.ofNullable(stack.get(ModDataComponents.ESSENCE_VALUE));
        var stored = Optional.ofNullable(stack.get(ModDataComponents.ESSENCE_STORAGE));
        if (data.isPresent() && stored.isPresent() && data.get().type() != stored.get().value().type()) {
            return Optional.empty();
        }
        HephaestusForgeInput selected = null;
        for (EssenceType channel : EssenceType.values()) {
            for (HephaestusForgeInput handler : INPUT_HANDLERS) {
                if (handler.canInput(channel, stack.copy())) {
                    // The unified feed has no channel selector. Multiple native input
                    // paths are ambiguous even if one path is currently unsupported.
                    if (selected != null) {
                        return Optional.empty();
                    }
                    selected = handler;
                    break;
                }
            }
        }
        if (selected == null) {
            return Optional.empty();
        }
        if (selected.getClass() == EssenceDataInput.class) {
            return data.isPresent() && data.get().amount() > 0 ? Optional.of(selected) : Optional.empty();
        }
        if (selected.getClass() == EssenceStorageInput.class) {
            return stored.isPresent() && stored.get().value().amount() > 0
                    ? Optional.of(selected) : Optional.empty();
        }
        // Do not fall through a native extract_enchantments match to essence_storage.
        return Optional.empty();
    }

    /** Audited, side-effect-free item outputs. Core upgrades are a separate transaction kind. */
    public static List<RitualRecipeView> itemRecipes(MinecraftServer server) {
        NativeRituals nativeRituals = ForbiddenArcanusRecipeCatalog.forgeRituals(server);
        return nativeRituals == null ? List.of() : nativeRituals.views;
    }

    public static List<RitualRecipeView> upgradeRecipes(MinecraftServer server) {
        NativeRituals nativeRituals = ForbiddenArcanusRecipeCatalog.forgeRituals(server);
        return nativeRituals == null ? List.of() : nativeRituals.upgradeViews;
    }

    public static boolean isRitualMainInput(MinecraftServer server, ItemStack stack) {
        NativeRituals nativeRituals = ForbiddenArcanusRecipeCatalog.forgeRituals(server);
        return nativeRituals != null && !stack.isEmpty()
                && nativeRituals.mainIngredients.stream().anyMatch(ingredient -> ingredient.test(stack));
    }

    public static boolean isRitualAuxInput(MinecraftServer server, ItemStack stack) {
        NativeRituals nativeRituals = ForbiddenArcanusRecipeCatalog.forgeRituals(server);
        return nativeRituals != null && !stack.isEmpty()
                && nativeRituals.auxIngredients.stream().anyMatch(ingredient -> ingredient.test(stack));
    }

    /** Internal tier and enabled valid enhancers identify a paid job's context. */
    public static String currentContextSignature(ServerLevel level, int tier, List<ItemStack> enhancers) {
        if (!validTier(tier) || enhancers.size() != ENHANCER_SLOTS) {
            return "";
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        JsonObject data = new JsonObject();
        data.addProperty("tier", tier);
        JsonArray slots = new JsonArray();
        for (int slot = 0; slot < allowedEnhancerCount(tier); slot++) {
            ItemStack stack = enhancers.get(slot);
            slots.add(encode(ops, EnhancerHelper.getEnhancerHolder(level.registryAccess(), stack).isPresent()
                    ? stack.copyWithCount(1) : ItemStack.EMPTY));
        }
        data.add("enhancers", slots);
        return fingerprint(data);
    }

    static Optional<Selection> select(ServerLevel level, RitualRecipeView view, ItemStack main,
                                       List<ItemStack> auxiliary, int tier, List<ItemStack> enhancers,
                                       boolean creativeMagic) {
        NativeRituals nativeRituals = ForbiddenArcanusRecipeCatalog.forgeRituals(level.getServer());
        if (nativeRituals == null || nativeRituals.byId.get(view.id()) != view) {
            return Optional.empty();
        }
        Ritual ritual = nativeRituals.recipes.get(view.id()).value();
        var matching = match(level, ritual, main, auxiliary, tier, enhancers);
        if (matching.isEmpty()) {
            return Optional.empty();
        }
        // A transmutation inherits the input's component PATCH over the target's
        // defaults. Its real output, not the target's default stack, decides if
        // the output slot has room. Both whitelisted native implementations are
        // deterministic and side-effect free when given an owned single copy.
        ItemStack preview = resultFor(ritual, main);
        if (preview.isEmpty() && view.upgradeTier() == 0) {
            return Optional.empty();
        }
        String context = currentContextSignature(level, tier, enhancers);
        int[] raw = amounts(ritual.requirements().essences());
        int[] gate = creativeMagic ? new int[CHANNEL_COUNT] : gateCosts(level, ritual, tier, enhancers);
        return Optional.of(new Selection(view.id(), view.view(), matching.get(), preview,
                gate, raw, context, view.id() + "/" + context + "/" + creativeMagic + "/" + view.upgradeTier(),
                view.upgradeTier()));
    }

    public static boolean canPay(Selection selection, int[] balances, boolean creativeMagic) {
        if (!validAmounts(balances)) {
            return false;
        }
        if (creativeMagic) {
            return true;
        }
        int[] gate = selection.gateCosts();
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            if (balances[channel] < gate[channel]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Final preparation only: no real slot is touched. The caller owns item
     * result or internal tier transition and essence debit in one paid job.
     */
    public static Optional<PreparedJob> prepareJob(ServerLevel level, Selection selection, ItemStack main,
                                                    List<ItemStack> auxiliary, int tier,
                                                    List<ItemStack> enhancers,
                                                    int[] balances, boolean creativeMagic) {
        NativeRituals nativeRituals = ForbiddenArcanusRecipeCatalog.forgeRituals(level.getServer());
        if (nativeRituals == null || !validAmounts(balances)) {
            return Optional.empty();
        }
        var view = nativeRituals.byId.get(selection.id());
        if (view == null || view.view() != selection.view() || view.upgradeTier() != selection.upgradeTier()
                || !selection.contextSignature().equals(currentContextSignature(level, tier, enhancers))) {
            return Optional.empty();
        }
        Ritual ritual = nativeRituals.recipes.get(selection.id()).value();
        var matching = match(level, ritual, main, auxiliary, tier, enhancers);
        if (matching.isEmpty() || !matching.get().equals(selection.inputs())) {
            return Optional.empty();
        }
        int[] debit = new int[CHANNEL_COUNT];
        if (!creativeMagic) {
            var before = definition(balances);
            var gate = definition(gateCosts(level, ritual, tier, enhancers));
            if (!before.hasMoreThan(gate)) {
                return Optional.empty();
            }
            // Native 2.6.1 checks modified costs but reduces RAW costs, clamping each
            // balance to zero. Preserve its actual min(balance, raw) debit on a copy.
            var after = before.mutable();
            after.reduce(ritual.requirements().essences());
            int[] remaining = amounts(after.immutable());
            for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
                debit[channel] = balances[channel] - remaining[channel];
            }
        }
        ItemStack result = resultFor(ritual, main);
        // A tier upgrade is machine state, never a craftable or returnable item.
        // Ordinary transforms remain component-sensitive at payment time.
        if (view.upgradeTier() > 0
                ? !result.isEmpty() || !selection.resultPreview().isEmpty()
                : result.isEmpty() || !ItemStack.matches(result, selection.resultPreview())) {
            return Optional.empty();
        }
        return Optional.of(new PreparedJob(result, debit,
                ritual.duration(), selection.contextSignature(), view.upgradeTier()));
    }

    private static Optional<List<InputUse>> match(ServerLevel level, Ritual ritual, ItemStack main,
                                                  List<ItemStack> auxiliary, int tier,
                                                  List<ItemStack> enhancers) {
        if (!validTier(tier) || main.isEmpty() || auxiliary.size() != AUXILIARY_SLOTS
                || enhancers.size() != ENHANCER_SLOTS || !hasSupportedResult(ritual)
                || !ritual.requirements().checkRequirements(tier, effectiveEnhancers(level, tier, enhancers))) {
            return Optional.empty();
        }
        // Native JSONs require an exact old tier. Also reject custom attempts
        // to skip, repeat, downgrade or exceed the five-level machine progression.
        if (ritual.result() instanceof UpgradeTierResult upgrade && upgrade.resultTier() != tier + 1) {
            return Optional.empty();
        }
        long units = ritual.inputs().stream().mapToLong(input -> input.amount()).sum();
        if (units < 0 || units > AUXILIARY_SLOTS || ritual.inputs().stream().anyMatch(input -> input.amount() <= 0)) {
            return Optional.empty();
        }
        boolean[][] compatible = new boolean[(int) units][AUXILIARY_SLOTS];
        int[] capacities = new int[AUXILIARY_SLOTS];
        for (int slot = 0; slot < AUXILIARY_SLOTS; slot++) {
            capacities[slot] = auxiliary.get(slot).getCount();
        }
        int row = 0;
        for (var input : ritual.inputs()) {
            for (int unit = 0; unit < input.amount(); unit++, row++) {
                for (int slot = 0; slot < AUXILIARY_SLOTS; slot++) {
                    compatible[row][slot] = input.ingredient().test(auxiliary.get(slot).copyWithCount(1));
                }
            }
        }
        var assigned = BoundedSlotMatcher.assign(compatible, capacities);
        if (assigned.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> orderedUnits = new ArrayList<>();
        for (int slot : assigned.get().ingredientSlots()) {
            orderedUnits.add(auxiliary.get(slot).copyWithCount(1));
        }
        // The native predicate counts collection entries, not ItemStack counts.
        if (!ritual.checkIngredients(orderedUnits, main.copyWithCount(1))) {
            return Optional.empty();
        }
        List<InputUse> uses = new ArrayList<>();
        uses.add(new InputUse(0, 1));
        int[] used = assigned.get().slotUse();
        for (int slot = 0; slot < used.length; slot++) {
            if (used[slot] > 0) {
                uses.add(new InputUse(slot + 1, used[slot]));
            }
        }
        return Optional.of(List.copyOf(uses));
    }

    private static boolean hasSupportedResult(Ritual ritual) {
        // Two native records are pure item results. UpgradeTierResult maps
        // to internal machine tier, NEVER executes the native world block replacement.
        return ritual.result() instanceof CreateItemResult || ritual.result() instanceof TransmuteInputResult
                || ritual.result() instanceof UpgradeTierResult upgrade && upgrade.resultTier() >= 2 && upgrade.resultTier() <= 5;
    }

    private static ItemStack resultFor(Ritual ritual, ItemStack main) {
        return ritual.result() instanceof UpgradeTierResult ? ItemStack.EMPTY
                : ritual.result().getResultItem(main.copyWithCount(1));
    }

    private static HolderSet<EnhancerDefinition> effectiveEnhancers(ServerLevel level, int tier,
                                                                   List<ItemStack> enhancers) {
        List<Holder<EnhancerDefinition>> result = new ArrayList<>();
        for (int slot = 0; slot < Math.min(allowedEnhancerCount(tier), enhancers.size()); slot++) {
            EnhancerHelper.getEnhancerHolder(level.registryAccess(), enhancers.get(slot)).ifPresent(result::add);
        }
        return HolderSet.direct(result);
    }

    private static int[] gateCosts(ServerLevel level, Ritual ritual, int tier, List<ItemStack> enhancers) {
        List<EssenceModifier> modifiers = effectiveEnhancers(level, tier, enhancers).stream()
                .flatMap(holder -> holder.value().getEffects(EnhancerTarget.HEPHAESTUS_FORGE))
                .filter(EssenceModifier.class::isInstance).map(EssenceModifier.class::cast).toList();
        return amounts(ritual.requirements().essences().applyModifiers(modifiers));
    }

    private static boolean validAmounts(int[] values) {
        if (values == null || values.length != CHANNEL_COUNT) {
            return false;
        }
        for (int value : values) {
            if (value < 0) {
                return false;
            }
        }
        return true;
    }

    private static EssencesDefinition definition(int[] amounts) {
        return new EssencesDefinition(amounts[0], amounts[1], amounts[2], amounts[3]);
    }

    private static int[] amounts(EssencesDefinition definition) {
        return new int[]{definition.aureal(), definition.souls(), definition.blood(), definition.experience()};
    }

    private static JsonElement encode(RegistryOps<JsonElement> ops, ItemStack stack) {
        return ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).getOrThrow();
    }

    private static String fingerprint(JsonElement data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(data).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static JsonElement canonical(JsonElement data) {
        if (data.isJsonObject()) {
            JsonObject result = new JsonObject();
            data.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.add(entry.getKey(), canonical(entry.getValue())));
            return result;
        }
        if (data.isJsonArray()) {
            JsonArray result = new JsonArray();
            data.getAsJsonArray().forEach(value -> result.add(canonical(value)));
            return result;
        }
        return data;
    }

    public record EssenceTransfer(int channel, int amount, ItemStack remainder) {
        public EssenceTransfer {
            remainder = remainder.copy();
        }

        @Override public ItemStack remainder() { return remainder.copy(); }
    }

    public record EssenceSample(int channel, int amount) { }

    /** No static output stack: ordinary transforms need owned input components. */
    public record RitualRecipeView(RitualView view, int upgradeTier) {
        public ResourceLocation id() { return view.id(); }
    }

    public record PreparedJob(ItemStack result, int[] debit,
                              int duration, String contextSignature, int upgradeTier) {
        public PreparedJob {
            result = result.copy();
            debit = debit.clone();
        }

        @Override public ItemStack result() { return result.copy(); }
        @Override public int[] debit() { return debit.clone(); }
        public boolean upgradesForge() { return upgradeTier > 0; }
    }

    /** Immutable raw references share the existing catalog's weak-key lifecycle; no world is retained. */
    static final class NativeRituals {
        private final Map<ResourceLocation, Holder<Ritual>> recipes;
        private final Map<ResourceLocation, RitualRecipeView> byId;
        private final List<RitualRecipeView> views;
        private final List<RitualRecipeView> upgradeViews;
        private final List<Ingredient> mainIngredients;
        private final List<Ingredient> auxIngredients;

        NativeRituals(Map<ResourceLocation, Holder<Ritual>> all, Map<ResourceLocation, RitualView> descriptors) {
            Map<ResourceLocation, Holder<Ritual>> eligible = new LinkedHashMap<>();
            Map<ResourceLocation, RitualRecipeView> previews = new LinkedHashMap<>();
            all.entrySet().stream().sorted(Map.Entry.comparingByKey(
                    java.util.Comparator.comparing(ResourceLocation::toString))).forEach(entry -> {
                if (hasSupportedResult(entry.getValue().value())) {
                    eligible.put(entry.getKey(), entry.getValue());
                    int upgradeTier = entry.getValue().value().result() instanceof UpgradeTierResult upgrade ? upgrade.resultTier() : 0;
                    previews.put(entry.getKey(), new RitualRecipeView(descriptors.get(entry.getKey()), upgradeTier));
                }
            });
            recipes = Collections.unmodifiableMap(eligible);
            byId = Collections.unmodifiableMap(previews);
            views = previews.values().stream().filter(view -> view.upgradeTier() == 0).toList();
            upgradeViews = previews.values().stream().filter(view -> view.upgradeTier() > 0).toList();
            mainIngredients = eligible.values().stream().map(holder -> holder.value().mainIngredient()).toList();
            auxIngredients = eligible.values().stream().flatMap(holder -> holder.value().inputs().stream())
                    .map(input -> input.ingredient()).toList();
        }
    }
}
