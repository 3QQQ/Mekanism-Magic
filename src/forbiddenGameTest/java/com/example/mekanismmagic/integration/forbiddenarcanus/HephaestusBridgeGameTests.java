package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusRecipeSelector.Selection;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Actual native registry/handler oracles for retained create-item and resource behavior. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HephaestusBridgeGameTests {
    private static final String NATIVE = "com.stal111.forbidden_arcanus.";

    private HephaestusBridgeGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allTenCreateRecipesUseNativeResultsAndRealMaterialAmounts(GameTestHelper helper)
            throws ReflectiveOperationException {
        var level = helper.getLevel();
        var views = HephaestusForge261Bridge.itemRecipes(level.getServer()).stream()
                .filter(recipe -> recipe.view().resultType().equals(id("create_item"))).toList();
        helper.assertTrue(views.size() == 10, "Supported catalog must retain all ten native create-item recipes");
        for (var recipe : views) {
            Fixture fixture = fixture(helper, recipe.view());
            var selector = new HephaestusRecipeSelector();
            var selection = selector.select(level, fixture.main(), fixture.auxiliary(), fixture.tier(),
                    fixture.enhancers(), false).orElseThrow();
            helper.assertTrue(selection.id().equals(recipe.id())
                            && selection.inputs().getFirst().slot() == 0 && selection.inputs().getFirst().count() == 1
                            && selection.inputs().stream().mapToInt(use -> use.count()).sum()
                            == 1 + recipe.view().inputs().stream().mapToInt(input -> input.amount()).sum(),
                    "Native counted ingredients were lost for " + recipe.id());
            int[] balances = HephaestusForge261Bridge.capacities(fixture.tier());
            int[] beforeBalances = balances.clone();
            var beforeItems = copies(fixture.auxiliary());
            var job = HephaestusForge261Bridge.prepareJob(level, selection, fixture.main(), fixture.auxiliary(),
                    fixture.tier(), fixture.enhancers(), balances, false).orElseThrow();
            ItemStack nativeResult = nativeResult(helper, recipe.id(), fixture.main());
            helper.assertTrue(ItemStack.matches(job.result(), nativeResult)
                            && ItemStack.matches(selection.resultPreview(), nativeResult)
                            && Arrays.equals(job.debit(), selection.rawCosts()) && job.duration() == recipe.view().duration(),
                    "Prepared job differs from native result, debit or duration for " + recipe.id());
            helper.assertTrue(fixture.main().getCount() == 3 && Arrays.equals(balances, beforeBalances)
                            && sameItems(beforeItems, fixture.auxiliary()),
                    "Preparation mutated real items or essence balances");
            ItemStack escaped = job.result();
            escaped.setCount(0);
            job.debit()[0] = -1;
            helper.assertTrue(ItemStack.matches(job.result(), nativeResult) && job.debit()[0] >= 0,
                    "Prepared job exposed a mutable output or debit array");
        }
        int foreignForgeTransitions = 0;
        for (RitualView unsupported : ForbiddenArcanusRecipeCatalog.snapshot(level.getServer()).rituals()) {
            if (unsupported.resultType().equals(id("upgrade_tier"))) {
                if (unsupported.id().equals(id("package_forge"))) {
                    foreignForgeTransitions++;
                    helper.assertTrue(ModCompatibility.loaded("packaged_faa")
                                    && HephaestusForge261Bridge.itemRecipes(level.getServer()).stream()
                                    .noneMatch(view -> view.id().equals(unsupported.id()))
                                    && HephaestusForge261Bridge.upgradeRecipes(level.getServer()).stream()
                                    .noneMatch(view -> view.id().equals(unsupported.id())),
                            "PackagedFAA world-forge replacement entered our machine");
                    continue;
                }
                Fixture fixture = fixture(helper, unsupported);
                helper.assertTrue(HephaestusForge261Bridge.itemRecipes(level.getServer()).stream()
                                .noneMatch(view -> view.id().equals(unsupported.id()))
                                && HephaestusForge261Bridge.upgradeRecipes(level.getServer()).stream()
                                .anyMatch(view -> view.id().equals(unsupported.id())),
                        "Tier upgrade was not separated from ordinary item results: " + unsupported.id());
                var upgrade = new HephaestusRecipeSelector().select(level, fixture.main(), fixture.auxiliary(),
                        fixture.tier(), fixture.enhancers(), false).orElseThrow();
                helper.assertTrue(upgrade.upgradesForge()
                                && upgrade.upgradeTier() == fixture.tier() + 1
                                && upgrade.resultPreview().isEmpty(),
                        "Native tier-upgrade context was not represented as machine state");
                helper.assertTrue(new HephaestusRecipeSelector().select(level, fixture.main(), fixture.auxiliary(),
                                upgrade.upgradeTier(), fixture.enhancers(), false).isEmpty(),
                        "Exact-tier upgrade requirements were bypassed: " + unsupported.id());
            }
        }
        helper.assertTrue(foreignForgeTransitions
                        == (ModCompatibility.loaded("packaged_faa") ? 1 : 0),
                "Unexpected packaged world-forge transition count");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void internalTierEnhancerUnlocksAndUnpaidCacheAreStrict(GameTestHelper helper) {
        var level = helper.getLevel();
        int[][] expected = {{1000, 10, 10000, 900}, {3000, 50, 15000, 1350}, {5000, 100, 30000, 2500},
                {10000, 500, 50000, 5000}, {20000, 1000, 100000, 7500}};
        for (int tier = 1; tier <= 5; tier++) {
            ItemStack core = item("hephaestus_forge_tier_" + tier);
            helper.assertTrue(HephaestusForge261Bridge.forgeTier(core) == tier
                            && Arrays.equals(HephaestusForge261Bridge.capacities(tier), expected[tier - 1])
                            && HephaestusForge261Bridge.allowedEnhancerCount(tier) == Math.min(tier, 4),
                    "Internal tier did not retain native capacities/enhancer unlocks");
        }
        ItemStack spoof = new ItemStack(Items.STICK);
        spoof.set(DataComponents.CUSTOM_NAME, Component.literal("Hephaestus Forge Tier 5"));
        helper.assertTrue(HephaestusForge261Bridge.forgeTier(spoof) == 0
                        && Arrays.equals(HephaestusForge261Bridge.capacities(0), new int[4]),
                "A legacy item name spoof qualified as a native tier");
        Fixture eternal = fixture(helper, ritual(helper, "eternal_stella"));
        helper.assertTrue(new HephaestusRecipeSelector().select(level, eternal.main(), eternal.auxiliary(),
                        1, eternal.enhancers(), true).isEmpty(),
                "Creative magic bypassed the internal forge tier");
        Fixture smelter = fixture(helper, ritual(helper, "smelter_prism"));
        List<ItemStack> enhancers = emptySlots(4);
        enhancers.set(1, item("elementarium"));
        var selector = new HephaestusRecipeSelector();
        helper.assertTrue(selector.select(level, smelter.main(), smelter.auxiliary(), smelter.tier(),
                        enhancers, false).isEmpty(),
                "A tier-one locked enhancer slot supplied the required elementarium");
        enhancers.set(0, item("elementarium"));
        var selected = selector.select(level, smelter.main(), smelter.auxiliary(), smelter.tier(),
                enhancers, false).orElseThrow();
        long scans = selector.candidateScanCount();
        for (int tick = 0; tick < 100; tick++) {
            helper.assertTrue(selector.select(level, smelter.main(), smelter.auxiliary(), smelter.tier(),
                            enhancers, false).orElseThrow() == selected,
                    "Stable unpaid lookup recreated a selection");
        }
        helper.assertTrue(selector.candidateScanCount() == scans, "Stable unpaid ticks rescanned native rituals");
        String context = HephaestusForge261Bridge.currentContextSignature(level, smelter.tier(), enhancers);
        enhancers.set(1, item("crimson_stone"));
        helper.assertTrue(HephaestusForge261Bridge.currentContextSignature(level, smelter.tier(), enhancers).equals(context),
                "A locked enhancer slot changed the paid context");
        enhancers.set(0, item("crimson_stone"));
        helper.assertTrue(!HephaestusForge261Bridge.currentContextSignature(level, smelter.tier(), enhancers).equals(context),
                "An enabled enhancer change did not change the paid context");
        helper.assertTrue(HephaestusForge261Bridge.currentContextSignature(level, 0, enhancers).isEmpty(),
                "Invalid internal tier retained paid-job authority");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void deterministicEssenceHandlersPreservePartialContainersAndNeverDisenchant(GameTestHelper helper)
            throws ReflectiveOperationException {
        var level = helper.getLevel();
        String[] sources = {"aureal_bottle", "soul", "xpetrified_orb"};
        int[] channels = {0, 1, 3};
        int[] values = {35, 1, 91};
        for (int index = 0; index < sources.length; index++) {
            ItemStack source = item(sources[index]);
            var transfer = HephaestusForge261Bridge.prepareEssenceInput(level, source,
                    new int[]{1000, 1000, 1000, 1000}).orElseThrow();
            helper.assertTrue(transfer.channel() == channels[index] && transfer.amount() == values[index]
                            && transfer.remainder().isEmpty() && source.getCount() == 1,
                    "Fixed native ESSENCE_VALUE input was not consumed exactly once on a copy");
        }
        ItemStack bottle = item("aureal_bottle");
        helper.assertTrue(HephaestusForge261Bridge.prepareEssenceInput(level, bottle,
                        new int[]{34, 0, 0, 0}).isEmpty() && bottle.getCount() == 1,
                "A fixed-value bottle was consumed without room for the complete value");
        ItemStack tube = nativeStorageItem("blood_test_tube", "BLOOD", 7, 3000);
        ItemStack before = tube.copy();
        var partial = HephaestusForge261Bridge.prepareEssenceInput(level, tube,
                new int[]{0, 0, 3, 0}).orElseThrow();
        ItemStack nativeRemainder = nativeFinishStorage(tube.copy(), 3);
        helper.assertTrue(partial.channel() == 2 && partial.amount() == 3
                        && ItemStack.matches(partial.remainder(), nativeRemainder) && ItemStack.matches(before, tube)
                        && HephaestusForge261Bridge.isEssenceInput(partial.remainder()),
                "Partial extraction changed the real container or differs from native finishInput");
        var emptied = HephaestusForge261Bridge.prepareEssenceInput(level, partial.remainder(),
                new int[]{0, 0, 100, 0}).orElseThrow();
        helper.assertTrue(emptied.amount() == 4 && emptied.remainder().is(item("test_tube").getItem())
                        && !HephaestusForge261Bridge.isEssenceInput(emptied.remainder()),
                "An emptied blood container did not return the native EMPTY_ITEM");
        helper.assertTrue(HephaestusForge261Bridge.prepareEssenceInput(level, tube.copyWithCount(2),
                        new int[]{0, 0, 100, 0}).isEmpty(), "The bridge drained an entire stacked container source");
        ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
        enchanted.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING), 3);
        ItemStack enchantedBefore = enchanted.copy();
        helper.assertTrue(!HephaestusForge261Bridge.isEssenceInput(enchanted)
                        && HephaestusForge261Bridge.prepareEssenceInput(level, enchanted,
                        new int[]{1000, 1000, 1000, 1000}).isEmpty()
                        && ItemStack.matches(enchantedBefore, enchanted),
                "The first release fell back to destructive/random enchantment extraction");
        ItemStack enchantedStorage = nativeStorageItem("essence_utrem_jar", "EXPERIENCE", 200, 10000);
        enchantedStorage.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING), 1);
        helper.assertTrue(nativeFirstInput(helper, enchantedStorage, "EXPERIENCE").getClass()
                        .getSimpleName().equals("ExtractEnchantmentsInput")
                        && !HephaestusForge261Bridge.isEssenceInput(enchantedStorage)
                        && HephaestusForge261Bridge.prepareEssenceInput(level, enchantedStorage,
                        new int[]{1000, 1000, 1000, 1000}).isEmpty(),
                "Unsupported first native handler was skipped in favor of essence storage");
        ItemStack ambiguous = nativeStorageItem("blood_test_tube", "BLOOD", 200, 3000);
        DataComponentType<?> essenceValue = nativeComponent("ESSENCE_VALUE");
        DataComponentType<?> essenceStorage = nativeComponent("ESSENCE_STORAGE");
        helper.assertTrue(id("essence_data").equals(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(essenceValue)),
                "Unexpected registry identity for the native ESSENCE_VALUE component");
        copyComponent(bottle, ambiguous, essenceValue);
        helper.assertTrue(call(ambiguous.get(essenceValue), "type").toString().equals("AUREAL")
                        && call(call(ambiguous.get(essenceStorage), "value"), "type").toString().equals("BLOOD"),
                "Ambiguous-input fixture did not contain two different actual essence components");
        helper.assertTrue(nativeFirstInput(helper, ambiguous, "AUREAL") != null
                        && nativeFirstInput(helper, ambiguous, "BLOOD") != null,
                "Ambiguous-input fixture did not expose both original native channel paths");
        ItemStack ambiguousBefore = ambiguous.copy();
        helper.assertTrue(!HephaestusForge261Bridge.isEssenceInput(ambiguous)
                        && HephaestusForge261Bridge.prepareEssenceInput(level, ambiguous,
                        new int[]{1000, 1000, 1000, 1000}).isEmpty()
                        && ItemStack.matches(ambiguous, ambiguousBefore),
                "Unified essence input chose one of two different native resource paths");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeModifierGateAndRawReductionRemainDistinct(GameTestHelper helper)
            throws ReflectiveOperationException {
        var level = helper.getLevel();
        Fixture fixture = fixture(helper, ritual(helper, "quantum_catcher"));
        List<ItemStack> enhancers = emptySlots(4);
        enhancers.set(0, item("crimson_stone"));
        Selection selected = new HephaestusRecipeSelector().select(level, fixture.main(), fixture.auxiliary(),
                fixture.tier(), enhancers, false).orElseThrow();
        int[] nativeGate = nativeGate(helper, selected.id(), enhancers.getFirst());
        helper.assertTrue(Arrays.equals(selected.gateCosts(), nativeGate)
                        && selected.gateCosts()[1] < selected.rawCosts()[1],
                "The native crimson-stone qualification discount disappeared");
        int[] atGate = nativeGate.clone();
        var low = HephaestusForge261Bridge.prepareJob(level, selected, fixture.main(), fixture.auxiliary(),
                fixture.tier(), enhancers, atGate, false).orElseThrow();
        helper.assertTrue(Arrays.equals(low.debit(), nativeDebit(helper, selected.id(), atGate)),
                "Low-bank actual debit differs from native clamped raw reduction");
        int[] full = HephaestusForge261Bridge.capacities(fixture.tier());
        var high = HephaestusForge261Bridge.prepareJob(level, selected, fixture.main(), fixture.auxiliary(),
                fixture.tier(), enhancers, full, false).orElseThrow();
        helper.assertTrue(Arrays.equals(high.debit(), nativeDebit(helper, selected.id(), full))
                        && high.debit()[1] == selected.rawCosts()[1],
                "A full bank was incorrectly charged the fixed discounted price");
        int[] insufficient = atGate.clone();
        insufficient[0]--;
        helper.assertTrue(!HephaestusForge261Bridge.canPay(selected, insufficient, false)
                        && HephaestusForge261Bridge.prepareJob(level, selected, fixture.main(), fixture.auxiliary(),
                        fixture.tier(), enhancers, insufficient, false).isEmpty(),
                "Payment bypassed one of the four native resource gates");
        var creative = new HephaestusRecipeSelector().select(level, fixture.main(), fixture.auxiliary(),
                fixture.tier(), enhancers, true).orElseThrow();
        var free = HephaestusForge261Bridge.prepareJob(level, creative, fixture.main(), fixture.auxiliary(),
                fixture.tier(), enhancers, new int[4], true).orElseThrow();
        helper.assertTrue(Arrays.equals(creative.gateCosts(), new int[4]) && Arrays.equals(free.debit(), new int[4])
                        && creative.inputs().getFirst().count() == 1,
                "Creative magic changed the non-essence material obligations");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, RitualView view) {
        ItemStack main = view.mainIngredient().examples().getFirst().copyWithCount(3);
        List<ItemStack> auxiliary = emptySlots(8);
        for (int row = 0; row < view.inputs().size(); row++) {
            var input = view.inputs().get(row);
            auxiliary.set(7 - row, input.ingredient().examples().getFirst().copyWithCount(input.amount() + 2));
        }
        List<ItemStack> enhancers = emptySlots(4);
        var catalog = ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer());
        for (int index = 0; index < view.enhancers().size(); index++) {
            enhancers.set(index, BuiltInRegistries.ITEM.get(catalog.enhancersById()
                    .get(view.enhancers().get(index)).displayItem()).getDefaultInstance());
        }
        return new Fixture(main, auxiliary, Math.max(1, view.tier()), enhancers);
    }

    private static RitualView ritual(GameTestHelper helper, String path) {
        return ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer()).ritualsById().get(id(path));
    }

    @SuppressWarnings("unchecked")
    private static Object nativeRitual(GameTestHelper helper, ResourceLocation id) throws ReflectiveOperationException {
        ResourceKey<Registry<Object>> key = (ResourceKey<Registry<Object>>) Class.forName(NATIVE + "core.registry.FARegistries")
                .getField("RITUAL").get(null);
        return helper.getLevel().registryAccess().registryOrThrow(key).get(id);
    }

    private static ItemStack nativeResult(GameTestHelper helper, ResourceLocation id, ItemStack main)
            throws ReflectiveOperationException {
        Object result = call(nativeRitual(helper, id), "result");
        return (ItemStack) result.getClass().getMethod("getResultItem", ItemStack.class).invoke(result, main.copy());
    }

    private static int[] nativeGate(GameTestHelper helper, ResourceLocation id, ItemStack enhancer)
            throws ReflectiveOperationException {
        Object requirements = call(nativeRitual(helper, id), "requirements");
        Object raw = call(requirements, "essences");
        Class<?> enhancerHelper = Class.forName(NATIVE + "common.item.enhancer.EnhancerHelper");
        var found = (java.util.Optional<?>) enhancerHelper.getMethod("getEnhancer",
                net.minecraft.core.HolderLookup.Provider.class, ItemStack.class)
                .invoke(null, helper.getLevel().registryAccess(), enhancer);
        Class<?> targetClass = Class.forName(NATIVE + "common.item.enhancer.EnhancerTarget");
        Object target = targetClass.getField("HEPHAESTUS_FORGE").get(null);
        Object definition = found.orElseThrow();
        var effects = (Stream<?>) definition.getClass().getMethod("getEffects", targetClass).invoke(definition, target);
        Class<?> modifier = Class.forName(NATIVE + "common.block.entity.forge.essence.EssenceModifier");
        List<?> modifiers = effects.filter(modifier::isInstance).toList();
        return nativeAmounts(raw.getClass().getMethod("applyModifiers", List.class).invoke(raw, modifiers));
    }

    private static int[] nativeDebit(GameTestHelper helper, ResourceLocation id, int[] balances)
            throws ReflectiveOperationException {
        Object raw = call(call(nativeRitual(helper, id), "requirements"), "essences");
        Object before = raw.getClass().getConstructor(int.class, int.class, int.class, int.class)
                .newInstance(balances[0], balances[1], balances[2], balances[3]);
        Object storage = call(before, "mutable");
        storage.getClass().getMethod("reduce", raw.getClass()).invoke(storage, raw);
        int[] after = nativeAmounts(call(storage, "immutable"));
        for (int channel = 0; channel < 4; channel++) {
            after[channel] = balances[channel] - after[channel];
        }
        return after;
    }

    private static int[] nativeAmounts(Object definition) throws ReflectiveOperationException {
        return new int[]{(Integer) call(definition, "aureal"), (Integer) call(definition, "souls"),
                (Integer) call(definition, "blood"), (Integer) call(definition, "experience")};
    }

    private static ItemStack nativeStorageItem(String item, String channel, int amount, int limit)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(NATIVE + "common.block.entity.forge.essence.EssenceType");
        Method create = Class.forName(NATIVE + "common.essence.EssenceHelper")
                .getMethod("createStorageItem", Item.class, type, int.class, int.class);
        return (ItemStack) create.invoke(null, item(item).getItem(), type.getField(channel).get(null), amount, limit);
    }

    private static ItemStack nativeFinishStorage(ItemStack source, int amount) throws ReflectiveOperationException {
        Holder<?> holder = (Holder<?>) Class.forName(NATIVE + "core.init.other.ModForgeInputTypes")
                .getField("ESSENCE_STORAGE").get(null);
        Object handler = holder.value();
        return (ItemStack) handler.getClass().getMethod("finishInput", ItemStack.class, int.class)
                .invoke(handler, source, amount);
    }

    @SuppressWarnings("unchecked")
    private static Object nativeFirstInput(GameTestHelper helper, ItemStack stack, String channel)
            throws ReflectiveOperationException {
        ResourceKey<Registry<Object>> key = (ResourceKey<Registry<Object>>) Class.forName(NATIVE + "core.registry.FARegistries")
                .getField("FORGE_INPUT").get(null);
        Class<?> type = Class.forName(NATIVE + "common.block.entity.forge.essence.EssenceType");
        Object essenceType = type.getField(channel).get(null);
        for (var holder : helper.getLevel().registryAccess().registryOrThrow(key).holders().toList()) {
            Object input = holder.value();
            if ((Boolean) input.getClass().getMethod("canInput", type, ItemStack.class)
                    .invoke(input, essenceType, stack.copy())) {
                return input;
            }
        }
        return null;
    }

    private static <T> void copyComponent(ItemStack source, ItemStack target, DataComponentType<T> type) {
        Objects.requireNonNull(type, "Missing native component type in fixture");
        target.set(type, Objects.requireNonNull(source.get(type), "Missing native component value in fixture"));
    }

    private static DataComponentType<?> nativeComponent(String field) throws ReflectiveOperationException {
        var entry = (java.util.function.Supplier<?>) Class.forName(NATIVE + "core.init.ModDataComponents")
                .getField(field).get(null);
        return (DataComponentType<?>) entry.get();
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static List<ItemStack> emptySlots(int count) {
        return new ArrayList<>(Collections.nCopies(count, ItemStack.EMPTY));
    }

    private static List<ItemStack> copies(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static boolean sameItems(List<ItemStack> first, List<ItemStack> second) {
        for (int slot = 0; slot < first.size(); slot++) {
            if (!ItemStack.matches(first.get(slot), second.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack item(String path) {
        return BuiltInRegistries.ITEM.getOptional(id(path)).orElseThrow().getDefaultInstance();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path);
    }

    private record Fixture(ItemStack main, List<ItemStack> auxiliary, int tier,
                           List<ItemStack> enhancers) {
    }
}
