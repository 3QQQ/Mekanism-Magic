package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Real 2.6.1 recipes and dynamic registries; isolated fixtures use its registered native codec. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForbiddenCatalogGameTests {
    private static final ResourceLocation IRON = id("clibano_combustion/iron_ingot_from_clibano_combusting_raw_iron");
    private static final ResourceLocation ALLOY = id("clibano_combustion/obsidiansteel_ingot_from_clibano_combustion");
    private static final ResourceLocation ETERNAL = id("smithing/apply_eternal_modifier");

    private ForbiddenCatalogGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void serverStartupPublishesAllNativeCatalogs(GameTestHelper helper) {
        requireMods(helper);
        ResourceLocation retired = ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "item_modifier_processor");
        helper.assertFalse(BuiltInRegistries.BLOCK.containsKey(retired)
                        || BuiltInRegistries.ITEM.containsKey(retired),
                "Canceled Arcane Augmentation Table registered a block or item");
        var snapshot = ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer());
        helper.assertTrue(snapshot.revision() > 0, "Forbidden catalog startup hook did not publish a snapshot");
        boolean packaged = ModList.get().isLoaded("packaged_faa");
        helper.assertTrue(snapshot.counts().equals(new ForbiddenArcanusRecipeCatalog.Counts(
                15, 6, packaged ? 23 : 22, 7, 6, 11, 3)),
                "Unexpected native catalog counts: " + snapshot.counts());
        ResourceLocation foreign = ResourceLocation.fromNamespaceAndPath(
                "forbidden_arcanus", "package_forge");
        helper.assertTrue(snapshot.ritualsById().containsKey(foreign)
                        == packaged,
                "PackagedFAA world-forge ritual was absent or leaked into the base catalog");
        for (Map<ResourceLocation, ?> index : indexes(snapshot)) {
            var ids = index.keySet().stream().map(ResourceLocation::toString).toList();
            helper.assertTrue(ids.equals(ids.stream().sorted().toList()), "Catalog IDs are not sorted");
        }
        helper.assertTrue(snapshot.clibanoRecipes().stream().filter(
                ForbiddenArcanusRecipeCatalog.ClibanoRecipeView::doubleInput).count() == 1,
                "Native Clibano single/double distinction was lost");
        var alloy = snapshot.clibanoById().get(ALLOY);
        helper.assertTrue(alloy.requiredEnhancer().orElseThrow().equals(id("artisan_relic"))
                        && alloy.residue().orElseThrow().type().equals(id("copper")),
                "Alloy context must preserve the native artisan relic and copper residue");
        var deorum = snapshot.residuesById().get(id("deorum"));
        helper.assertTrue(deorum.requiredAmount() == 1 && deorum.output().getCount() == 2
                        && deorum.output().is(item("deorum_nugget").getItem()),
                "Deorum is one residue unit to two nuggets, not nine units to a block");
        helper.assertTrue(snapshot.rituals().stream()
                        .filter(ritual -> !ritual.id().equals(foreign))
                        .allMatch(ritual -> ritual.essences().aureal() > 0
                                && ritual.essences().souls() > 0
                                && ritual.essences().blood() > 0)
                        && (!packaged || snapshot.ritualsById().get(foreign)
                        .essences().equals(new ForbiddenArcanusRecipeCatalog
                                .EssenceRequirements(114, 0, 0, 514))),
                "The four ritual essence requirements were collapsed or dropped");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void resetRefreshAndReturnedCopiesCannotMutateSnapshots(GameTestHelper helper) {
        requireMods(helper);
        var server = helper.getLevel().getServer();
        var old = ForbiddenArcanusRecipeCatalog.snapshot(server);
        long oldRevision = old.revision();
        var expected = old.counts();
        ForbiddenArcanusRecipeCatalog.clear();
        long clearedRevision = ForbiddenArcanusRecipeCatalog.revision();
        helper.assertTrue(ForbiddenArcanusRecipeCatalog.snapshot(server).clibanoRecipes().isEmpty()
                        && ForbiddenArcanusRecipeCatalog.revision() == clearedRevision,
                "Snapshot lookup scanned or repopulated the cleared catalog");
        var refreshed = ForbiddenArcanusRecipeCatalog.refresh(server);
        helper.assertTrue(refreshed != old && refreshed.revision() > oldRevision
                        && refreshed.counts().equals(expected) && old.revision() == oldRevision
                        && old.counts().equals(expected), "Refresh modified a previously published snapshot");
        helper.assertTrue(ForbiddenArcanusRecipeCatalog.snapshot(server) == refreshed,
                "Stable cache lookup rebuilt the snapshot");
        for (Map<ResourceLocation, ?> index : indexes(refreshed)) {
            assertUnmodifiable(helper, index::clear);
        }
        assertUnmodifiable(helper, refreshed.clibanoRecipes()::clear);
        assertUnmodifiable(helper, refreshed.rituals()::clear);
        var iron = refreshed.clibanoById().get(IRON);
        assertUnmodifiable(helper, iron.ingredients()::clear);
        assertUnmodifiable(helper, iron.ingredients().getFirst().examples()::clear);
        ItemStack output = iron.output();
        output.setCount(49);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Mutated reader"));
        iron.ingredients().getFirst().examples().forEach(stack -> stack.setCount(0));
        helper.assertTrue(iron.output().getCount() == 1 && !iron.output().has(DataComponents.CUSTOM_NAME)
                        && iron.ingredients().getFirst().examples().stream().noneMatch(ItemStack::isEmpty),
                "Mutable stack accessor leaked into the catalog");
        var residue = refreshed.residuesById().get(id("deorum"));
        residue.output().setCount(0);
        helper.assertTrue(residue.output().getCount() == 2, "Residue output accessor leaked a mutable stack");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeSingleDoubleAndEnhancerMatchingRemainAuthoritative(GameTestHelper helper) {
        requireMods(helper);
        var level = helper.getLevel();
        ItemStack iron = new ItemStack(Items.RAW_IRON, 12);
        ItemStack obsidian = new ItemStack(Items.OBSIDIAN, 8);
        ItemStack artisan = item("artisan_relic");
        helper.assertTrue(ForbiddenArcanusRecipeCatalog.matchesClibano(level, IRON,
                        iron, ItemStack.EMPTY, FireType.FIRE, ItemStack.EMPTY)
                        && ForbiddenArcanusRecipeCatalog.matchesClibano(level, IRON,
                        ItemStack.EMPTY, iron, FireType.FIRE, ItemStack.EMPTY),
                "Native single-input matching failed when the occupied slot moved");
        helper.assertTrue(!ForbiddenArcanusRecipeCatalog.matchesClibano(level, IRON,
                        iron, obsidian, FireType.FIRE, ItemStack.EMPTY),
                "A single recipe accepted two occupied input slots");
        for (FireType fire : FireType.values()) {
            helper.assertTrue(ForbiddenArcanusRecipeCatalog.matchesClibano(level, ALLOY,
                            iron, obsidian, fire, artisan)
                            && ForbiddenArcanusRecipeCatalog.matchesClibano(level, ALLOY,
                            obsidian, iron, fire, artisan), "Double ingredients were made order-sensitive");
        }
        ItemStack namedStick = new ItemStack(Items.STICK);
        namedStick.set(DataComponents.CUSTOM_NAME, Component.literal("Artisan Relic"));
        for (ItemStack wrong : List.of(ItemStack.EMPTY, item("crimson_stone"), namedStick)) {
            helper.assertTrue(!ForbiddenArcanusRecipeCatalog.matchesClibano(level, ALLOY,
                            iron, obsidian, FireType.ENCHANTED_FIRE, wrong),
                    "Alloy bypassed native enhancer Holder identity");
        }
        ItemStack componentRemoved = artisan.copy();
        componentRemoved.remove(BuiltInRegistries.DATA_COMPONENT_TYPE.get(id("enhancer")));
        helper.assertTrue(!ForbiddenArcanusRecipeCatalog.matchesClibano(level, ALLOY,
                        iron, obsidian, FireType.FIRE, componentRemoved),
                "Enhancer was accepted by item ID after its native component was removed");
        helper.assertTrue(ForbiddenArcanus261Bridge.isEnhancerItem(level.registryAccess(), artisan)
                        && !ForbiddenArcanus261Bridge.isEnhancerItem(level.registryAccess(), componentRemoved)
                        && !ForbiddenArcanus261Bridge.isEnhancerItem(level.registryAccess(), namedStick),
                "Enhancer slot validation differs from the native component identity");
        helper.assertTrue(ForbiddenArcanus261Bridge.enhancerId(level.registryAccess(), artisan)
                        .orElseThrow().equals(id("artisan_relic")), "Native enhancer component did not resolve");
        var result = ForbiddenArcanusRecipeCatalog.assembleClibano(level, ALLOY,
                obsidian, iron, FireType.FIRE, artisan).orElseThrow();
        helper.assertTrue(result.is(item("obsidiansteel_ingot").getItem()) && result.getCount() == 1
                        && iron.getCount() == 12 && obsidian.getCount() == 8 && artisan.getCount() == 1,
                "Read-only native assembly consumed or changed inputs");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeFireFixturesPreserveThresholdsTimesAndOutputComponents(GameTestHelper helper) {
        requireMods(helper);
        var level = helper.getLevel();
        helper.assertTrue(ForbiddenArcanus261Bridge.fireType(item("soul")) == FireType.SOUL_FIRE
                        && ForbiddenArcanus261Bridge.fireType(item("corrupt_soul")) == FireType.SOUL_FIRE
                        && ForbiddenArcanus261Bridge.fireType(item("enchanted_soul")) == FireType.ENCHANTED_FIRE
                        && ForbiddenArcanus261Bridge.fireType(ItemStack.EMPTY) == FireType.FIRE,
                "Native fire-medium tags were not used");
        helper.assertTrue(ForbiddenArcanus261Bridge.soulDuration(level.registryAccess(), ItemStack.EMPTY) == 2700
                        && ForbiddenArcanus261Bridge.soulDuration(level.registryAccess(), item("crimson_stone")) == 3510,
                "Native soul duration or crimson-stone effect was omitted");
        ItemStack expectedOutput = new ItemStack(Items.DIAMOND_PICKAXE);
        expectedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("Native assembled output"));
        expectedOutput.setDamageValue(17);
        var isolated = new RecipeManager(level.registryAccess());
        List<RecipeHolder<?>> fixtures = new ArrayList<>();
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        for (FireType required : FireType.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "forbidden_arcanus:clibano_combustion");
            json.addProperty("category", "misc");
            json.addProperty("cooking_time", 100);
            json.addProperty("experience", 0.75);
            json.addProperty("fire_type", required.name().toLowerCase(Locale.ROOT));
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", "minecraft:raw_iron");
            json.add("ingredients", ingredient);
            json.add("result", ItemStack.CODEC.encodeStart(ops, expectedOutput).getOrThrow());
            ResourceLocation fixtureId = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                    "forbidden_fire_" + required.name().toLowerCase(Locale.ROOT));
            fixtures.add(new RecipeHolder<>(fixtureId, Recipe.CODEC.parse(ops, json).getOrThrow()));
        }
        isolated.replaceRecipes(fixtures);
        var loaded = ForbiddenArcanus261Bridge.read(isolated, level.registryAccess(), 1);
        for (int requiredIndex = 0; requiredIndex < fixtures.size(); requiredIndex++) {
            ResourceLocation fixtureId = fixtures.get(requiredIndex).id();
            var view = loaded.snapshot().clibanoById().get(fixtureId);
            helper.assertTrue(view.cookingTimes().equals(new ForbiddenArcanusRecipeCatalog.CookingTimes(100, 66, 40))
                            && view.experience() == 0.75F, "Native cooking-time or experience context changed");
            for (FireType available : FireType.values()) {
                boolean expected = available.ordinal() >= requiredIndex;
                helper.assertTrue(loaded.recipes().matchesClibano(fixtureId, level,
                                new ItemStack(Items.RAW_IRON), ItemStack.EMPTY, available, ItemStack.EMPTY) == expected,
                        "Native fire threshold bypassed for " + fixtureId + " using " + available);
                var assembled = loaded.recipes().assembleClibano(fixtureId, level,
                        new ItemStack(Items.RAW_IRON), ItemStack.EMPTY, available, ItemStack.EMPTY);
                helper.assertTrue(assembled.isPresent() == expected, "Assembly bypassed fire qualification");
                if (expected) {
                    helper.assertTrue(ItemStack.matches(assembled.orElseThrow(), expectedOutput),
                            "Native assemble lost output data components");
                    assembled.orElseThrow().setDamageValue(50);
                    helper.assertTrue(ItemStack.matches(view.output(), expectedOutput),
                            "Assembled output leaked into the immutable catalog");
                }
            }
        }
        var iron = ForbiddenArcanusRecipeCatalog.snapshot(level.getServer()).clibanoById().get(IRON);
        helper.assertTrue(!iron.canProduceResidue(FireType.FIRE)
                        && iron.canProduceResidue(FireType.SOUL_FIRE)
                        && iron.canProduceResidue(FireType.ENCHANTED_FIRE),
                "Ordinary fire must not advertise residue production");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void modifierAssemblyMatchesNativeRecipeWithoutChangingTheTarget(GameTestHelper helper) {
        requireMods(helper);
        var level = helper.getLevel();
        ItemStack base = new ItemStack(Items.DIAMOND_SWORD);
        base.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved sword"));
        base.setDamageValue(13);
        ItemStack before = base.copy();
        ItemStack template = item("apply_modifier_smithing_template");
        ItemStack addition = item("eternal_stella");
        var nativeRecipe = (SmithingRecipe) level.getRecipeManager().byKey(ETERNAL).orElseThrow().value();
        var input = new SmithingRecipeInput(template.copy(), base.copy(), addition.copy());
        helper.assertTrue(nativeRecipe.matches(input, level), "Eternal fixture is not valid for the native recipe");
        var expected = nativeRecipe.assemble(input, level.registryAccess());
        var actual = ForbiddenArcanusRecipeCatalog.assembleApplyModifier(
                level, ETERNAL, template, base, addition).orElseThrow();
        helper.assertTrue(ItemStack.matches(expected, actual) && ItemStack.matches(before, base)
                        && actual.getHoverName().equals(expected.getHoverName()),
                "Modifier bridge differs from native assembly or mutated its input");
        boolean repeatedNative = nativeRecipe.matches(new SmithingRecipeInput(template, actual, addition), level);
        helper.assertTrue(!repeatedNative && !ForbiddenArcanusRecipeCatalog.matchesApplyModifier(
                        level, ETERNAL, template, actual, addition),
                "The modifier bridge allowed repeated application");
        helper.succeed();
    }

    private static List<Map<ResourceLocation, ?>> indexes(ForbiddenArcanusRecipeCatalog.Snapshot snapshot) {
        return List.of(snapshot.clibanoById(), snapshot.applyModifierById(), snapshot.ritualsById(),
                snapshot.enhancersById(), snapshot.modifierDefinitionsById(), snapshot.residuesById(),
                snapshot.magicCirclesById());
    }

    private static void assertUnmodifiable(GameTestHelper helper, Runnable mutation) {
        boolean rejected = false;
        try {
            mutation.run();
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Catalog exposed a mutable collection");
    }

    private static void requireMods(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("forbidden_arcanus") && ModList.get().isLoaded("valhelsia_core"),
                "This dedicated GameTest run requires real Forbidden & Arcanus and Valhelsia Core");
    }

    private static ItemStack item(String path) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id(path)));
        if (stack.isEmpty()) {
            throw new IllegalStateException("Missing real Forbidden & Arcanus item " + path);
        }
        return stack;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path);
    }
}
