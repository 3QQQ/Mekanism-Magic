package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.integration.common.recipe.ExternalItemPatternRouter;
import de.ellpeck.naturesaura.recipes.AnimalSpawnerRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** An AE item multiset must still satisfy native one-stack-per-ingredient rules. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalSpawnerExternalPatternGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    private AnimalSpawnerExternalPatternGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sameKeyDatapackIngredientsReachSeparatePhysicalSlots(
            GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        try {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, "animal_spawner_same_key_ae_fixture");
            var fixture = new AnimalSpawnerRecipe(
                    ResourceLocation.withDefaultNamespace("cow"),
                    100, 20,
                    List.of(Ingredient.of(Items.APPLE),
                            Ingredient.of(Items.APPLE)));
            manager.replaceRecipes(List.of(new RecipeHolder<>(id, fixture)));
            NatureAuraRecipeScanner.refresh(manager);
            helper.setBlock(MACHINE, Blocks.AIR);
            helper.setBlock(MACHINE,
                    NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK.get());
            AnimalSpawnerProcessorBlockEntity tile =
                    helper.getBlockEntity(MACHINE);
            var view = AnimalSpawnerRecipeBridge.scan(manager).stream()
                    .filter(recipe -> recipe.id().equals(id))
                    .findFirst().orElseThrow();
            ItemStack apple = new ItemStack(Items.APPLE);
            List<PatternStack> inputs = List.of(new PatternStack(apple, 2));
            List<PatternStack> outputs = List.of(new PatternStack(
                    view.outputEgg(), view.outputEgg().getCount()));
            var planned = tile.mekanismMagicPlanExternalItemInputs(
                    inputs, outputs);
            helper.assertTrue(planned.isPresent() && planned.get().size() == 2
                            && planned.get().get(0).slots().equals(
                            List.of(tile.getIngredientSlots().get(0)))
                            && planned.get().get(1).slots().equals(
                            List.of(tile.getIngredientSlots().get(1)))
                            && planned.get().stream().allMatch(group ->
                            group.inputs().getFirst().amount() == 1)
                            && tile.getIngredientSlots().stream()
                            .allMatch(slot -> slot.isEmpty()),
                    "Repeated native ingredient did not plan as two separate stacks");
            List<PatternStack> wrongOutput = List.of(new PatternStack(
                    new ItemStack(Items.DIRT), 1));
            helper.assertTrue(!ExternalItemPatternRouter.push(tile,
                            tile.getInventorySlots(Direction.UP), inputs,
                            wrongOutput)
                            && tile.getIngredientSlots().stream()
                            .allMatch(slot -> slot.isEmpty()),
                    "Animal spawner committed an output mismatch");
            helper.assertTrue(ExternalItemPatternRouter.push(tile,
                            tile.getInventorySlots(Direction.UP), inputs, outputs),
                    "Animal spawner rejected a valid same-key AE delivery");
            List<ItemStack> physical = tile.getIngredientSlots().stream()
                    .map(slot -> slot.getStack().copy()).toList();
            helper.assertTrue(physical.get(0).is(Items.APPLE)
                            && physical.get(0).getCount() == 1
                            && physical.get(1).is(Items.APPLE)
                            && physical.get(1).getCount() == 1
                            && view.match(physical).isPresent(),
                    "AE delivery did not satisfy native one-slot-per-ingredient matching");
        } finally {
            manager.replaceRecipes(original);
            NatureAuraRecipeScanner.refresh(manager);
        }
        helper.succeed();
    }
}
