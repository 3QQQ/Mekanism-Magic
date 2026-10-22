package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Live recipe-manager coverage for data-pack-defined altar catalyst kinds. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NatureAltarCatalystCatalogGameTests {
    private NatureAltarCatalystCatalogGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            batch = "natureCatalystCatalog")
    public static void catalystKindsExpandTagsAlternativesAndRefresh(
            GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var nativeRecipes = NatureAuraRecipeScanner.scan(manager).stream()
                .filter(recipe -> recipe.id().getNamespace()
                        .equals("naturesaura"))
                .toList();
        helper.assertTrue(NatureAuraRecipeScanner.collectCatalystKinds(
                        nativeRecipes).size() == 2,
                "Nature's Aura 41.9 no longer exposes exactly two native catalyst kinds");

        List<ItemStack> baseline = NatureAuraRecipeScanner.catalystKinds(
                manager);
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        Ingredient choices = Ingredient.of(Items.DIAMOND, Items.EMERALD);
        Ingredient tag = Ingredient.of(ItemTags.PLANKS);
        Ingredient emptyTag = Ingredient.of(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                        "missing_test_catalysts")));
        helper.assertTrue(!tag.hasNoItems() && emptyTag.hasNoItems(),
                "Catalyst tag fixtures did not resolve as expected");

        List<RecipeHolder<?>> changed = new ArrayList<>(original);
        changed.add(testRecipe("catalyst_choices", choices));
        changed.add(testRecipe("catalyst_tag", tag));
        changed.add(testRecipe("catalyst_empty_tag", emptyTag));
        try {
            long oldVersion = NatureAuraRecipeScanner.version(manager);
            manager.replaceRecipes(changed);
            NatureAuraRecipeScanner.refresh(manager);
            helper.assertTrue(NatureAuraRecipeScanner.version(manager)
                            > oldVersion,
                    "Catalyst catalog did not advance on recipe reload");
            var emptyTagRecipe = NatureAuraRecipeScanner.find(manager,
                    ResourceLocation.fromNamespaceAndPath(
                            MekanismMagic.MOD_ID,
                            "catalyst_empty_tag")).orElseThrow();
            helper.assertTrue(!NatureAuraRecipeBridge.hasMatchingCatalyst(
                            emptyTagRecipe,
                            List.of(new ItemStack(Items.BARRIER))),
                    "An empty catalyst tag accepted the display BARRIER as a real catalyst");

            List<ItemStack> actual = NatureAuraRecipeScanner.catalystKinds(
                    manager);
            Set<ItemStack> expected = ItemStackLinkedSet
                    .createTypeAndComponentsSet();
            baseline.forEach(stack -> expected.add(stack.copyWithCount(1)));
            for (Ingredient ingredient : List.of(choices, tag)) {
                Arrays.stream(ingredient.getItems())
                        .filter(ingredient::test)
                        .forEach(stack -> expected.add(
                                stack.copyWithCount(1)));
            }
            helper.assertTrue(actual.size() == expected.size()
                            && expected.stream().allMatch(stack ->
                            containsKind(actual, stack)),
                    "Catalyst catalog omitted tag/OR alternatives, duplicated a kind, or counted an empty-tag barrier");

            actual.getFirst().setCount(17);
            helper.assertTrue(NatureAuraRecipeScanner.catalystKinds(manager)
                            .getFirst().getCount() == 1,
                    "Caller mutated the cached catalyst catalog");
        } finally {
            manager.replaceRecipes(original);
            NatureAuraRecipeScanner.refresh(manager);
        }
        List<ItemStack> restored = NatureAuraRecipeScanner.catalystKinds(
                manager);
        helper.assertTrue(restored.size() == baseline.size()
                        && baseline.stream().allMatch(stack ->
                        containsKind(restored, stack)),
                "Removed data-pack catalysts remained in the refreshed catalog");
        helper.succeed();
    }

    private static RecipeHolder<AltarRecipe> testRecipe(
            String path, Ingredient catalyst) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, path), new AltarRecipe(
                Ingredient.of(Items.COBBLESTONE),
                new ItemStack(Items.STONE), catalyst, 1, 20));
    }

    private static boolean containsKind(List<ItemStack> stacks,
                                        ItemStack wanted) {
        return stacks.stream().anyMatch(stack ->
                ItemStack.isSameItemSameComponents(stack, wanted));
    }
}
