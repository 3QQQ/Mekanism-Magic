package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.ItemInputGroup;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.integration.common.recipe.ExternalItemPatternRouter;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The ordinary-provider planner must be exact and never alter live slots. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForbiddenExternalPatternPlanGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private ForbiddenExternalPatternPlanGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void forgeRoutesExactMainAndAuxiliaryWithoutTouchingSetup(
            GameTestHelper helper) {
        RitualView ritual = ForbiddenArcanusRecipeCatalog.snapshot(
                helper.getLevel().getServer()).ritualsById().get(
                ResourceLocation.fromNamespaceAndPath("forbidden_arcanus",
                        "quantum_catcher"));
        helper.assertTrue(ritual != null, "Missing native quantum catcher ritual");
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS,
                ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity tile = helper.getBlockEntity(POS);
        CompoundTag saved = tile.saveWithoutMetadata(
                helper.getLevel().registryAccess());
        saved.getCompound(HephaestusForgeProcessorBlockEntity.STATE_TAG)
                .putInt("internal_tier", Math.max(1, ritual.tier()));
        tile.loadWithComponents(saved, helper.getLevel().registryAccess());
        ItemStack main = ritual.mainIngredient().examples().getFirst();
        List<ItemStack> auxiliary = new ArrayList<>();
        List<PatternStack> inputs = new ArrayList<>();
        inputs.add(new PatternStack(main, 1));
        for (var ingredient : ritual.inputs()) {
            ItemStack example = ingredient.ingredient().examples().getFirst();
            auxiliary.add(example.copyWithCount(ingredient.amount()));
            inputs.add(new PatternStack(example, ingredient.amount()));
        }
        while (auxiliary.size() < HephaestusForgeProcessorBlockEntity.AUXILIARY_SLOTS) {
            auxiliary.add(ItemStack.EMPTY);
        }
        var selected = new HephaestusRecipeSelector().select(helper.getLevel(),
                main, auxiliary, tile.getForgeTier(),
                List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                        ItemStack.EMPTY), false);
        helper.assertTrue(selected.isPresent() && !selected.get().upgradesForge(),
                "Fixture did not select a native item ritual");
        ItemStack expected = selected.get().resultPreview();
        Optional<List<ItemInputGroup>> plan =
                tile.mekanismMagicPlanExternalItemInputs(inputs,
                        List.of(new PatternStack(expected, expected.getCount())));
        helper.assertTrue(plan.isPresent() && !plan.get().isEmpty()
                        && plan.get().getFirst().slots().equals(
                        List.of(tile.getMainInputSlot()))
                        && plan.get().getFirst().inputs().getFirst().amount() == 1,
                "Forge plan did not reserve exactly one native main input");
        long planned = plan.get().stream().flatMap(group -> group.inputs().stream())
                .mapToLong(PatternStack::amount).sum();
        long supplied = inputs.stream().mapToLong(PatternStack::amount).sum();
        helper.assertTrue(planned == supplied
                        && tile.getMainInputSlot().isEmpty()
                        && tile.getForgeTier() == Math.max(1, ritual.tier()),
                "Forge planning changed slots or lost an input quantity");
        helper.assertTrue(tile.mekanismMagicPlanExternalItemInputs(inputs,
                List.of(new PatternStack(new ItemStack(Items.DIRT), 1))).isEmpty(),
                "Forge accepted a declared result unlike its native output");
        helper.assertTrue(!ExternalItemPatternRouter.push(tile,
                        tile.getInventorySlots(net.minecraft.core.Direction.UP),
                        inputs, List.of(new PatternStack(new ItemStack(Items.DIRT), 1)))
                        && tile.getMainInputSlot().isEmpty(),
                "Forge router committed an incorrectly declared output");
        helper.assertTrue(ExternalItemPatternRouter.push(tile,
                        tile.getInventorySlots(net.minecraft.core.Direction.UP),
                        inputs, List.of(new PatternStack(expected,
                                expected.getCount()))),
                "Forge router could not commit its exact plan");
        for (ItemInputGroup group : plan.get()) {
            helper.assertTrue(group.slots().size() == 1
                            && ItemStack.isSameItemSameComponents(
                            group.slots().getFirst().getStack(),
                            group.inputs().getFirst().stack())
                            && group.slots().getFirst().getCount()
                            == group.inputs().getFirst().amount(),
                    "Forge router delivered a planned ingredient to the wrong slot");
        }
        helper.assertTrue(tile.getForgeTier() == Math.max(1, ritual.tier()),
                "Forge router altered the internal machine tier");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void clibanoSplitsSameKeyDoubleInputAndRejectsWrongOutput(
            GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of(sameKeyClibanoRecipe(helper)));
            ForbiddenArcanusRecipeCatalog.refresh(helper.getLevel().getServer());
            helper.setBlock(POS, Blocks.AIR);
            helper.setBlock(POS,
                    ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get());
            ClibanoProcessorBlockEntity tile = helper.getBlockEntity(POS);
            List<PatternStack> inputs = List.of(new PatternStack(
                    new ItemStack(Items.RAW_IRON), 2));
            List<PatternStack> outputs = List.of(new PatternStack(
                    new ItemStack(Items.IRON_INGOT), 1));
            Optional<List<ItemInputGroup>> plan =
                    tile.mekanismMagicPlanExternalItemInputs(inputs, outputs);
            helper.assertTrue(plan.isPresent() && plan.get().size() == 2
                            && plan.get().get(0).slots().equals(
                            List.<IInventorySlot>of(tile.getInputSlot(0)))
                            && plan.get().get(1).slots().equals(
                            List.<IInventorySlot>of(tile.getInputSlot(1)))
                            && plan.get().stream().allMatch(group ->
                            group.inputs().getFirst().amount() == 1)
                            && tile.getInputSlot(0).isEmpty()
                            && tile.getInputSlot(1).isEmpty(),
                    "Clibano did not split two identical ingredients without mutation");
            List<PatternStack> wrong = List.of(new PatternStack(
                    new ItemStack(Items.DIRT), 1));
            helper.assertTrue(tile.mekanismMagicPlanExternalItemInputs(inputs,
                    wrong).isEmpty()
                            && !ExternalItemPatternRouter.push(tile,
                            tile.getInventorySlots(net.minecraft.core.Direction.UP),
                            inputs, wrong)
                            && tile.getInputSlot(0).isEmpty()
                            && tile.getInputSlot(1).isEmpty(),
                    "Clibano accepted an incorrect primary output");
            helper.assertTrue(ExternalItemPatternRouter.push(tile,
                            tile.getInventorySlots(net.minecraft.core.Direction.UP),
                            inputs, outputs)
                            && tile.getInputSlot(0).getCount() == 1
                            && tile.getInputSlot(1).getCount() == 1
                            && tile.getFireSlot().isEmpty()
                            && tile.getEnhancerSlot().isEmpty(),
                    "Clibano router did not commit same-key ingredients to both roles");
        } finally {
            manager.replaceRecipes(original);
            ForbiddenArcanusRecipeCatalog.refresh(helper.getLevel().getServer());
        }
        helper.succeed();
    }

    private static RecipeHolder<?> sameKeyClibanoRecipe(GameTestHelper helper) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "forbidden_arcanus:clibano_combustion");
        json.addProperty("category", "misc");
        json.addProperty("cooking_time", 100);
        json.addProperty("experience", 0);
        json.addProperty("fire_type", "fire");
        JsonObject ingredients = new JsonObject();
        JsonObject first = new JsonObject();
        first.addProperty("item", "minecraft:raw_iron");
        ingredients.add("first", first);
        ingredients.add("second", first.deepCopy());
        json.add("ingredients", ingredients);
        var ops = RegistryOps.create(JsonOps.INSTANCE,
                helper.getLevel().registryAccess());
        json.add("result", ItemStack.CODEC.encodeStart(ops,
                new ItemStack(Items.IRON_INGOT)).getOrThrow());
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "clibano_same_key_pattern_fixture"),
                Recipe.CODEC.parse(ops, json).getOrThrow());
    }
}
