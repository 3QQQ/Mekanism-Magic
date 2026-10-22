package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import de.ellpeck.naturesaura.recipes.AnimalSpawnerRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.integration.common.entity.SimulationEntitySamples;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native recipes produce durable eggs internally; world creation and external structures must never run. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalSpawnerIntegrationGameTests {
    private static final BlockPos MACHINE = new BlockPos(4, 2, 4);

    private AnimalSpawnerIntegrationGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allSeventyTwoRecipesRetainNativeEntityCostsAndIngredients(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var recipes = AnimalSpawnerRecipeBridge.scan(manager);
        helper.assertTrue(recipes.size() == 72, "Expected all 72 native animal spawner recipes");
        for (var view : recipes) {
            var original = manager.getAllRecipesFor(ModRecipes.ANIMAL_SPAWNER_TYPE).stream()
                    .filter(holder -> holder.id().equals(view.id())).findFirst().orElseThrow().value();
            helper.assertTrue(view.entityType() == original.entity && view.auraCost() == original.aura
                            && view.time() == original.time && view.ingredients().size() == original.ingredients.size(),
                    "Entity, cost, time or ingredient count changed: " + view.id());
            List<ItemStack> inputs = representativeInputs(view, 3);
            Collections.reverse(inputs);
            var uses = view.match(inputs).orElseThrow();
            helper.assertTrue(uses.stream().map(use -> use.slot()).distinct().count() == uses.size(),
                    "An ingredient stack was reused: " + view.id());
            var machine = view.toMachineRecipe(uses);
            helper.assertTrue(!machine.output().isEmpty() && machine.output().getCount() == 1 && eggType(machine.output()) == original.entity
                            && machine.resourceCost(NatureAuraMachineConfig.AURA_RESOURCE) == original.aura,
                    "Spawn egg type/count or native Aura cost changed: " + view.id());
            inputs.add(new ItemStack(Items.COBBLESTONE));
            helper.assertTrue(view.match(inputs).isEmpty(), "Extraneous occupied slot matched: " + view.id());
            var replacement = new AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView(view.id(),
                    view.entityType(), view.ingredients(), view.inputAmounts(), view.auraCost() + 1, view.time());
            helper.assertTrue(!view.signature().equals(replacement.signature()),
                    "Same-id data-pack cost replacement kept an old signature");
        }
        var previous = AnimalSpawnerRecipeBridge.scan(manager);
        NatureAuraRecipeScanner.refresh(manager);
        helper.assertTrue(previous != AnimalSpawnerRecipeBridge.scan(manager)
                        && AnimalSpawnerRecipeBridge.scan(manager).size() == 72,
                "Shared data-pack version did not rebuild animal recipes");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void eggUsesExactAuraAndPreservesRemainingComponentsAcrossRestart(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = recipe(helper, "chicken");
        loadInputs(tile, recipe, 2, recipe.auraCost());
        ItemStack named = tile.getIngredientSlots().get(1).getStack().copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("remaining component"));
        tile.getIngredientSlots().get(1).setStack(named);
        tick(tile, 18);
        reload(helper, tile);
        try (var events = new InternalNatureTestSupport.NoWorldEvents(helper.getLevel(), tile.getBlockPos())) {
            tick(tile, recipe.time() - 18 + 1);
            events.assertUnused(helper);
        }
        helper.assertTrue(tile.getNativeOutputSlot().getStack().is(Items.CHICKEN_SPAWN_EGG)
                        && tile.getNativeOutputSlot().getCount() == 1 && tile.getStoredAura() == 0
                        && nearby(helper, EntityType.CHICKEN).isEmpty(), "Completion did not produce one egg for one Aura charge");
        for (int index = 0; index < recipe.ingredients().size(); index++) {
            helper.assertTrue(tile.getIngredientSlots().get(index).getCount() == recipe.inputAmounts().get(index),
                    "Egg production consumed more than one batch");
        }
        helper.assertTrue(Component.literal("remaining component").equals(tile.getIngredientSlots().get(1).getStack().get(DataComponents.CUSTOM_NAME))
                        && !tile.getNativeOutputSlot().getStack().has(DataComponents.CUSTOM_NAME),
                "Input components were lost or copied into the egg result");
        reload(helper, tile);
        long energy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, 40);
        helper.assertTrue(tile.getNativeOutputSlot().getCount() == 1 && tile.getNativeEnergyContainer().getEnergy() == energy
                        && !tile.mekanismMagicSupportsPatternAutomation(), "Restart duplicated the result or underfunded work spent FE");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void solidSurroundingsAndCrowdsNeverTriggerSpawnOrBlockEvents(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = recipe(helper, "chicken");
        loadInputs(tile, recipe, 1, recipe.auraCost());
        for (BlockPos pos : BlockPos.betweenClosed(tile.getBlockPos().offset(-3, -1, -3), tile.getBlockPos().offset(3, 3, 3))) {
            if (!pos.equals(tile.getBlockPos())) helper.getLevel().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
        }
        List<Entity> crowd = new ArrayList<>();
        try {
            for (int index = 0; index < 40; index++) {
                Mob cow = EntityType.COW.create(helper.getLevel());
                cow.setNoAi(true);
                cow.moveTo(tile.getBlockPos().getX() + 0.5, tile.getBlockPos().getY() + 4,
                        tile.getBlockPos().getZ() + 0.5, 0, 0);
                helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not seed crowd fixture");
                crowd.add(cow);
            }
            var world = InternalNatureTestSupport.snapshot(helper.getLevel(), tile.getBlockPos());
            try (var events = new InternalNatureTestSupport.NoWorldEvents(helper.getLevel(), tile.getBlockPos())) {
                tick(tile, recipe.time() + 1);
                events.assertUnused(helper);
            }
            helper.assertTrue(tile.getNativeOutputSlot().getStack().is(Items.CHICKEN_SPAWN_EGG)
                            && !tile.hasMissingStructure() && !tile.hasEntityLimit() && nearby(helper, EntityType.CHICKEN).isEmpty(),
                    "Internal egg production still depended on a base, free space or nearby entity count");
            InternalNatureTestSupport.assertUnchanged(helper, world);
        } finally {
            crowd.forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void blockedOutputAndUnpoweredOrRedstonePausedWorkRemainUnspent(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = recipe(helper, "chicken");
        loadInputs(tile, recipe, 1, recipe.auraCost());
        long energy = tile.getNativeEnergyContainer().getEnergy();
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        tick(tile, 80);
        assertUnspent(helper, tile, recipe);
        helper.assertTrue(tile.hasOutputBlocked() && tile.getProgress() == 0 && tile.getNativeEnergyContainer().getEnergy() == energy,
                "Blocked output spent FE");
        tile.getNativeOutputSlot().setEmpty();
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 30);
        assertUnspent(helper, tile, recipe);
        tile.getNativeEnergyContainer().setEnergy(energy);
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 30);
        assertUnspent(helper, tile, recipe);
        helper.assertTrue(tile.getProgress() == 0 && tile.getNativeEnergyContainer().getEnergy() == energy,
                "Redstone-disabled production advanced or spent FE");
        tile.setControlType(RedstoneControl.DISABLED);
        tick(tile, recipe.time() + 1);
        helper.assertTrue(tile.getNativeOutputSlot().getStack().is(Items.CHICKEN_SPAWN_EGG) && tile.getStoredAura() == 0,
                "Ready egg production did not resume");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void pendingEggSurvivesBlockedDropAndNeverRepaysAura(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = recipe(helper, "chicken");
        loadInputs(tile, recipe, 2, recipe.auraCost());
        tick(tile, recipe.time());
        helper.assertTrue(tile.hasPendingOutput(), "Could not prepare paid egg");
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        long energy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, 30);
        var restored = InternalNatureTestSupport.breakAndReplace(helper, tile);
        helper.assertTrue(restored.hasPendingOutput() && restored.getPendingOutputs().getFirst().is(Items.CHICKEN_SPAWN_EGG)
                        && restored.getStoredAura() == 0, "Paid egg or Aura state was lost in machine item");
        restored.setControlType(RedstoneControl.HIGH);
        restored.getNativeOutputSlot().setEmpty();
        tick(restored, 8);
        helper.assertTrue(!restored.hasPendingOutput() && restored.getNativeOutputSlot().getCount() == 1
                        && restored.getNativeEnergyContainer().getEnergy() == energy && restored.getStoredAura() == 0,
                "Delivery lost the paid egg or charged again");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sameIdRecipeReloadInvalidatesOldProgressTargetAndResourceCost(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var recipe = recipe(helper, "chicken");
        loadInputs(tile, recipe, 1, recipe.auraCost());
        tick(tile, 18);
        helper.assertTrue(tile.getProgress() == 18, "Could not establish partially completed work");
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        try {
            List<RecipeHolder<?>> changed = new ArrayList<>(original);
            changed.removeIf(holder -> holder.id().equals(recipe.id()));
            changed.add(new RecipeHolder<>(recipe.id(), new AnimalSpawnerRecipe(ResourceLocation.withDefaultNamespace("cow"),
                    recipe.auraCost() * 2, recipe.time() * 2, recipe.ingredients())));
            manager.replaceRecipes(changed);
            NatureAuraRecipeScanner.refresh(manager);
            tick(tile, 1);
            helper.assertTrue(tile.getProgress() == 0 && tile.hasNotEnoughAura() && !tile.hasPendingOutput(),
                    "Same-id replacement reused progress or the old cheaper Aura cost");
            helper.assertTrue(tile.storeAuraExact(recipe.auraCost(), false), "Could not fund updated recipe");
            tick(tile, recipe.time() * 2);
            helper.assertTrue(tile.getPendingOutputs().getFirst().is(Items.COW_SPAWN_EGG) && tile.getStoredAura() == 0,
                    "Updated target reused the stale chicken egg or Aura fee");
        } finally {
            manager.replaceRecipes(original);
            NatureAuraRecipeScanner.refresh(manager);
        }
        tick(tile, 1);
        helper.assertTrue(tile.getNativeOutputSlot().getStack().is(Items.COW_SPAWN_EGG), "Reload replaced a paid egg");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void duplicateInputSelectionIsPersistentAndControlsTheEgg(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var frog = recipe(helper, "frog");
        loadInputs(tile, frog, 1, 0);
        tick(tile, 1);
        helper.assertTrue(tile.getMatchingRecipeCount() == 2
                        && ResourceLocation.withDefaultNamespace("frog").equals(tile.getSelectedEntityId()), "Frog/slime ambiguity was hidden");
        tile.cycleMatchingRecipe();
        reload(helper, tile);
        tick(tile, 1);
        helper.assertTrue(ResourceLocation.withDefaultNamespace("slime").equals(tile.getSelectedEntityId()), "Selected slime target was not persistent");
        var slime = recipe(helper, "slime");
        helper.assertTrue(tile.storeAuraExact(slime.auraCost(), false), "Could not fund selected slime");
        tick(tile, slime.time() + 1);
        helper.assertTrue(tile.getNativeOutputSlot().getStack().is(Items.SLIME_SPAWN_EGG)
                        && nearby(helper, EntityType.SLIME).isEmpty(), "Selection did not control the item output");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fallbackEggIsFixedSerializableAndHasNoFakeJobTooltip(GameTestHelper helper) {
        ItemStack egg = AnimalSpawnEggs.forType(EntityType.GIANT);
        helper.assertTrue(SpawnEggItem.byId(EntityType.GIANT) == null && egg.is(NaturesAuraRegistries.ANIMAL_SPAWN_EGG.get())
                        && eggType(egg) == EntityType.GIANT && egg.getCount() == 1, "Missing native egg was not encoded without Occultism registry dependency");
        ItemStack restored = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                (net.minecraft.nbt.CompoundTag) egg.save(helper.getLevel().registryAccess()));
        helper.assertTrue(ItemStack.matches(egg, restored) && eggType(restored) == EntityType.GIANT, "Fallback egg serialization changed its entity type");
        helper.assertTrue(SimulationEntitySamples.read(egg).map(sample -> sample.entityId().equals(ResourceLocation.withDefaultNamespace("giant"))).orElse(false),
                "Fallback egg cannot be used by the existing simulation sample bridge");
        List<Component> lines = new ArrayList<>();
        egg.getItem().appendHoverText(egg, Item.TooltipContext.of(helper.getLevel()), lines, TooltipFlag.NORMAL);
        helper.assertTrue(lines.stream().noneMatch(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                        && contents.getKey().equals("tooltip.mekanism_magic.ritual_spawn_egg.job")),
                "A plain encoded egg displays a phantom minecraft: spirit job");
        helper.assertTrue(AnimalSpawnEggs.forType(EntityType.PLAYER).isEmpty() && AnimalSpawnEggs.forType(null).isEmpty(),
                "Invalid/player type generated a spawn egg");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fallbackEggCanBeUsedByThePlayerWithoutOccultism(GameTestHelper helper) {
        ItemStack egg = AnimalSpawnEggs.forType(EntityType.GIANT);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack previous = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
        var floor = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        List<Entity> spawned = new ArrayList<>();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, egg);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(floor.getCenter(), Direction.UP, floor, false));
            var result = egg.getItem().useOn(context);
            spawned.addAll(helper.getLevel().getEntities((Entity) null, new AABB(floor).inflate(8), entity -> entity.getType() == EntityType.GIANT));
            helper.assertTrue(result.consumesAction() && spawned.size() == 1, "Encoded egg cannot perform an explicit player spawn");
        } finally {
            spawned.forEach(Entity::discard);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void outputIsAppendedAndRemovedTilesCannotProduce(GameTestHelper helper) {
        var tile = placeMachine(helper);
        var slots = tile.getInventorySlots(null);
        helper.assertTrue(slots.indexOf(tile.getIngredientSlots().getFirst()) == 1
                        && slots.indexOf(tile.getIngredientSlots().getLast()) == 6
                        && slots.indexOf(tile.getNativeOutputSlot()) == 7,
                "New egg output renumbered released energy/input slots");
        int outputIndex = slots.indexOf(tile.getNativeOutputSlot());
        helper.assertTrue(tile.insertItem(outputIndex, new ItemStack(Items.CHICKEN_SPAWN_EGG), null, Action.EXECUTE).getCount() == 1,
                "Automation inserted into the output slot");
        var recipe = recipe(helper, "chicken");
        loadInputs(tile, recipe, 1, recipe.auraCost());
        tile.setRemoved();
        long energy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, 60);
        assertUnspent(helper, tile, recipe);
        helper.assertTrue(!tile.hasPendingOutput() && tile.getNativeOutputSlot().isEmpty()
                        && tile.getNativeEnergyContainer().getEnergy() == energy, "Removed machine produced or spent FE");
        helper.succeed();
    }

    private static AnimalSpawnerProcessorBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.setBlock(MACHINE, NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK.get());
        return helper.getBlockEntity(MACHINE);
    }

    private static AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe(GameTestHelper helper, String path) {
        return AnimalSpawnerRecipeBridge.scan(helper.getLevel().getRecipeManager()).stream()
                .filter(view -> view.id().getPath().equals("animal_spawner/" + path)).findFirst().orElseThrow();
    }

    private static List<ItemStack> representativeInputs(AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe, int batches) {
        List<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < recipe.ingredients().size(); index++) result.add(NatureAuraRecipeBridge.representativeChoice(
                recipe.ingredients().get(index)).orElseThrow().copyWithCount(recipe.inputAmounts().get(index) * batches));
        return result;
    }

    private static void loadInputs(AnimalSpawnerProcessorBlockEntity tile, AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe, int batches, int aura) {
        var inputs = representativeInputs(recipe, batches);
        for (int index = 0; index < inputs.size(); index++) tile.getIngredientSlots().get(index).setStack(inputs.get(index));
        tile.getNativeEnergyContainer().setEnergy(AnimalSpawnerProcessorBlockEntity.ENERGY_CAPACITY);
        tile.refreshAuraTypeState();
        if (aura > 0 && !tile.storeAuraExact(aura, false)) throw new IllegalStateException("Unable to fill test Aura");
    }

    private static EntityType<?> eggType(ItemStack egg) {
        if (egg.getItem() instanceof SpawnEggItem nativeEgg) return nativeEgg.getType(egg);
        var id = RitualSpawnEggItem.entityId(egg);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    private static void assertUnspent(GameTestHelper helper, AnimalSpawnerProcessorBlockEntity tile, AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe) {
        helper.assertTrue(tile.getStoredAura() == recipe.auraCost(), "Failed production spent Aura");
        for (int index = 0; index < recipe.ingredients().size(); index++) helper.assertTrue(
                tile.getIngredientSlots().get(index).getCount() == recipe.inputAmounts().get(index), "Failed production spent input");
    }

    private static List<Entity> nearby(GameTestHelper helper, EntityType<?> type) {
        return helper.getLevel().getEntities((Entity) null, new AABB(helper.absolutePos(MACHINE)).inflate(5), entity -> entity.getType() == type);
    }

    private static void reload(GameTestHelper helper, AnimalSpawnerProcessorBlockEntity tile) {
        tile.loadAdditional(tile.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
    }

    private static void tick(AnimalSpawnerProcessorBlockEntity tile, int ticks) {
        for (int tick = 0; tick < ticks; tick++) tile.onUpdateServer();
    }
}
