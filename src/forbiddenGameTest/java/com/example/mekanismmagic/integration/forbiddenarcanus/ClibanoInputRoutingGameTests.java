package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/** Exercises insertion through the actual default sided capability, not direct slot setters. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClibanoInputRoutingGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private ClibanoInputRoutingGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void defaultTopPortRoutesMaterialsSoulAndEnhancerToTheirDeclaredSlots(GameTestHelper helper) {
        var tile = place(helper);
        var config = tile.getConfig().getConfig(TransmissionType.ITEM);
        helper.assertTrue(config.getDataType(RelativeSide.TOP) == DataType.INPUT_OUTPUT
                        && tile.getInventorySlots(Direction.UP).containsAll(List.of(tile.getInputSlot(0),
                        tile.getInputSlot(1), tile.getFireSlot(), tile.getEnhancerSlot())),
                "Default top port does not expose the declared processing and context slots");
        helper.assertTrue(config.getSlotInfo(DataType.EXTRA) instanceof InventorySlotInfo extra
                        && extra.getSlots().containsAll(List.of(tile.getFireSlot(), tile.getEnhancerSlot())),
                "The dedicated EXTRA context category was lost");
        IItemHandler handler = topHandler(helper);
        ItemStack iron = new ItemStack(Items.RAW_IRON, 12);
        ItemStack soul = item("soul").copyWithCount(3);
        ItemStack artisan = item("artisan_relic");
        for (ItemStack input : List.of(iron, soul, artisan)) {
            helper.assertTrue(insert(handler, input, true).isEmpty(),
                    "Default top port simulation rejected a valid material/context stack: " + input);
        }
        helper.assertTrue(tile.getInputSlot(0).isEmpty() && tile.getInputSlot(1).isEmpty()
                        && tile.getFireSlot().isEmpty() && tile.getEnhancerSlot().isEmpty(),
                "Simulated insertion changed machine inventory");
        for (ItemStack input : List.of(iron, soul, artisan)) {
            helper.assertTrue(insert(handler, input, false).isEmpty(),
                    "Default top port rejected a valid material/context stack: " + input);
        }
        helper.assertTrue(ItemStack.matches(tile.getInputSlot(0).getStack(), iron)
                        && tile.getInputSlot(1).isEmpty()
                        && ItemStack.matches(tile.getFireSlot().getStack(), soul)
                        && ItemStack.matches(tile.getEnhancerSlot().getStack(), artisan),
                "The top port routed a soul or enhancer into ordinary material inputs");
        ItemStack junk = new ItemStack(Items.DIRT, 6);
        helper.assertTrue(ItemStack.matches(insert(handler, junk, true), junk)
                        && ItemStack.matches(insert(handler, junk, false), junk)
                        && ItemStack.matches(tile.getInputSlot(0).getStack(), iron) && tile.getInputSlot(1).isEmpty(),
                "Ordinary non-ingredient items entered an input slot");
        helper.assertTrue(iron.getCount() == 12 && soul.getCount() == 3 && artisan.getCount() == 1,
                "Capability insertion mutated the caller's supplied stack objects");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void cachedIngredientValidationReloadsAndAllowsSoulsUsedAsRealMaterials(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var manager = level.getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        ResourceLocation fixtureId = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                "clibano_soul_ingredient_fixture");
        ItemStack soul = item("soul");
        try {
            helper.assertTrue(ForbiddenArcanusRecipeCatalog.isClibanoIngredient(server, new ItemStack(Items.RAW_IRON))
                            && !ForbiddenArcanusRecipeCatalog.isClibanoIngredient(server, soul),
                    "Unexpected native ingredient baseline");
            manager.replaceRecipes(List.of(soulFixture(helper, fixtureId)));
            // The manager has changed, but only explicit refresh may replace cached predicates.
            helper.assertTrue(ForbiddenArcanusRecipeCatalog.isClibanoIngredient(server, new ItemStack(Items.RAW_IRON))
                            && !ForbiddenArcanusRecipeCatalog.isClibanoIngredient(server, soul),
                    "Slot validation rescanned RecipeManager outside catalog refresh");
            ForbiddenArcanusRecipeCatalog.refresh(server);
            helper.assertTrue(ForbiddenArcanusRecipeCatalog.isClibanoIngredient(server, soul)
                            && !ForbiddenArcanusRecipeCatalog.isClibanoIngredient(server, new ItemStack(Items.RAW_IRON))
                            && !ForbiddenArcanusRecipeCatalog.matchesClibano(level, fixtureId, soul, ItemStack.EMPTY,
                            FireType.FIRE, ItemStack.EMPTY),
                    "Ingredient validation ignored reload or incorrectly required recipe fire/enhancer context");
            var tile = place(helper);
            helper.assertTrue(insert(topHandler(helper), soul, false).isEmpty()
                            && ItemStack.matches(tile.getInputSlot(0).getStack(), soul)
                            && tile.getInputSlot(1).isEmpty() && tile.getFireSlot().isEmpty(),
                    "A data-pack soul ingredient was hard-coded out of the ordinary input slots");
            tile.getConfig().getConfig(TransmissionType.ITEM).setDataType(DataType.EXTRA, RelativeSide.TOP);
            tile.getConfig().sideChanged(TransmissionType.ITEM, RelativeSide.TOP);
            IItemHandler extraHandler = topHandler(helper);
            helper.assertTrue(insert(extraHandler, soul, false).isEmpty()
                            && ItemStack.matches(tile.getInputSlot(0).getStack(), soul)
                            && ItemStack.matches(tile.getFireSlot().getStack(), soul),
                    "A configured EXTRA port could not target fire context when souls are also recipe materials");
            for (int slot = 0; slot < extraHandler.getSlots(); slot++) {
                helper.assertTrue(extraHandler.extractItem(slot, 1, false).isEmpty(),
                        "Routing broadened the context slots' external extraction permissions");
            }
        } finally {
            manager.replaceRecipes(original);
            ForbiddenArcanusRecipeCatalog.refresh(server);
        }
        helper.succeed();
    }

    private static ClibanoProcessorBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get());
        ClibanoProcessorBlockEntity tile = helper.getBlockEntity(POS);
        tile.getNativeEnergyContainer().setEnergy(0);
        return tile;
    }

    private static IItemHandler topHandler(GameTestHelper helper) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(POS), Direction.UP);
        helper.assertTrue(handler != null, "Actual top-facing NeoForge item capability is missing");
        return handler;
    }

    private static ItemStack insert(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    private static RecipeHolder<?> soulFixture(GameTestHelper helper, ResourceLocation id) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "forbidden_arcanus:clibano_combustion");
        json.addProperty("category", "misc");
        json.addProperty("cooking_time", 100);
        json.addProperty("experience", 0);
        json.addProperty("fire_type", "enchanted_fire");
        json.addProperty("enhancer", "forbidden_arcanus:artisan_relic");
        JsonObject input = new JsonObject();
        input.addProperty("item", "forbidden_arcanus:soul");
        json.add("ingredients", input);
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        json.add("result", ItemStack.CODEC.encodeStart(ops, new ItemStack(Items.IRON_INGOT)).getOrThrow());
        return new RecipeHolder<>(id, Recipe.CODEC.parse(ops, json).getOrThrow());
    }

    private static ItemStack item(String path) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path))
                .orElseThrow().getDefaultInstance();
    }
}
