package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryWindowSlot;
import com.example.mekanismmagic.integration.mekextras.ExtraNatureAltarFactoryBlockEntity;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.SerializationConstants;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for the expandable Nature altar catalyst library. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NatureAltarDynamicCatalystGameTests {
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);
    private static final BlockPos THIRD = new BlockPos(5, 1, 1);
    private static final ResourceLocation THIRD_RECIPE = id(
            "dynamic_catalyst_test/third");
    private static final List<Item> EXTRA_ITEMS = List.of(
            Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI,
            Items.REDSTONE, Items.COAL, Items.CHARCOAL,
            Items.GOLD_INGOT, Items.IRON_INGOT, Items.COPPER_INGOT,
            Items.NETHERITE_INGOT, Items.QUARTZ, Items.AMETHYST_SHARD,
            Items.FLINT, Items.CLAY_BALL, Items.BRICK,
            Items.PAPER, Items.STRING, Items.FEATHER,
            Items.LEATHER, Items.RABBIT_HIDE, Items.BONE,
            Items.BLAZE_ROD, Items.ENDER_PEARL, Items.GLASS_BOTTLE,
            Items.GLOWSTONE_DUST, Items.PRISMARINE_SHARD);

    private NatureAltarDynamicCatalystGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            batch = "natureCatalystCatalog", timeoutTicks = 120)
    public static void thirdDatapackCatalystWorksAcrossSingleBasicAndInfinite(
            GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        try {
            List<RecipeHolder<?>> changed = new ArrayList<>(original);
            changed.add(testRecipe(THIRD_RECIPE, Items.DIAMOND));
            manager.replaceRecipes(changed);
            NatureAuraRecipeScanner.refresh(manager);
            helper.assertTrue(NatureAuraRecipeScanner.catalystKinds(manager)
                            .size() >= 3,
                    "Third data-pack catalyst did not expand the recipe catalog");

            for (TileEntityMekanism tile : List.of(
                    placeSingle(helper, FIRST),
                    placeFactory(helper, SECOND, "basic"),
                    placeFactory(helper, THIRD, "infinite"))) {
                helper.assertTrue(visibleCount(tile) == 1
                                && windowSlots(tile).size() == 16,
                        "A third catalyst did not expose exactly one overflow position: "
                                + tile.getClass().getSimpleName());
                setLibraryOpen(tile, true);
                windowSlots(tile).getFirst().setStack(
                        new ItemStack(Items.DIAMOND));
                List<ItemStack> retained = catalystStacks(tile);
                helper.assertTrue(retained.size() >= 3
                                && retained.get(2).is(Items.DIAMOND),
                        "Third catalyst did not remain in overflow storage");
                ItemStack marked = NatureAuraRecipeBridge.markPatternInput(
                        new ItemStack(Items.DIAMOND_BLOCK),
                        NatureAuraRecipeBridge.createPatternContext(
                                helper.getLevel(), THIRD_RECIPE));
                helper.assertTrue(((NatureAltarAutomationHost) tile)
                                .acceptNatureAltarPattern(THIRD_RECIPE,
                                        marked),
                        "Machine did not use its third catalyst for an exact recipe: "
                                + tile.getClass().getSimpleName());
            }
        } finally {
            manager.replaceRecipes(original);
            NatureAuraRecipeScanner.refresh(manager);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            batch = "natureCatalystCatalog", timeoutTicks = 120)
    public static void manyDatapackCatalystsPageAndShrinkWithoutDroppingItems(
            GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        try {
            List<RecipeHolder<?>> changed = new ArrayList<>(original);
            for (int index = 0; index < EXTRA_ITEMS.size(); index++) {
                changed.add(testRecipe(id("dynamic_catalyst_test/page_"
                        + index), EXTRA_ITEMS.get(index)));
            }
            manager.replaceRecipes(changed);
            NatureAuraRecipeScanner.refresh(manager);
            TileEntityMekanism factory = placeFactory(helper, FIRST,
                    "basic");
            setLibraryOpen(factory, true);
            int expandedCount = visibleCount(factory);
            helper.assertTrue(expandedCount > 16
                            && pageCount(factory) > 1,
                    "Catalyst library did not page when the data pack added more than sixteen kinds");
            windowSlots(factory).getFirst().setStack(
                    new ItemStack(EXTRA_ITEMS.getFirst()));
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(factory.getBlockPos()));
            var menu = new NatureAltarFactoryContainer(96,
                    player.getInventory(),
                    (NatureAltarFactoryBlockEntity) factory);
            player.containerMenu = menu;
            helper.assertTrue(menu.clickMenuButton(player,
                            NatureAltarFactoryContainer.NEXT_CATALYST_PAGE)
                            && page(factory) == 1
                            && menu.clickMenuButton(player,
                            NatureAltarFactoryContainer.PREVIOUS_CATALYST_PAGE)
                            && page(factory) == 0
                            && menu.clickMenuButton(player,
                            NatureAltarFactoryContainer.NEXT_CATALYST_PAGE)
                            && page(factory) == 1,
                    "Server page buttons did not cycle forward and back");
            ItemStack farCatalyst = new ItemStack(
                    EXTRA_ITEMS.get(16));
            windowSlots(factory).getFirst().setStack(
                    farCatalyst.copy());
            helper.assertTrue(ItemStack.isSameItemSameComponents(
                            pageStack(factory, 0), farCatalyst),
                    "Second-page catalyst did not enter the dynamic library");

            manager.replaceRecipes(original);
            NatureAuraRecipeScanner.refresh(manager);
            int shrunkCount = visibleCount(factory);
            helper.assertTrue(shrunkCount < expandedCount
                            && shrunkCount >= 17
                            && catalystStacks(factory).get(18)
                            .is(farCatalyst.getItem()),
                    "Recipe reload discarded the occupied second page or kept all removed empty kinds");
            ItemStack extracted = windowSlots(factory).getFirst()
                    .extractItem(1, Action.EXECUTE,
                            AutomationType.MANUAL);
            helper.assertTrue(ItemStack.isSameItemSameComponents(
                            extracted, farCatalyst),
                    "Removed data-pack catalyst could not be recovered from the old page");
            helper.assertTrue(pageCount(factory) == 1
                            && visibleCount(factory) < shrunkCount,
                    "Empty page remained allocated after its old catalyst was recovered");
        } finally {
            manager.replaceRecipes(original);
            NatureAuraRecipeScanner.refresh(manager);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            batch = "natureCatalystCatalog", timeoutTicks = 120)
    public static void oldTwoCatalystFactoryWorldsAndItemsKeepInputsAndEnergy(
            GameTestHelper helper) {
        var recipe = NatureAuraRecipeScanner.find(
                helper.getLevel().getRecipeManager(),
                ResourceLocation.fromNamespaceAndPath("naturesaura",
                        "altar/glowstone")).orElseThrow();
        ItemStack input = NatureAuraRecipeBridge.representativeChoice(
                recipe.input()).orElseThrow();
        ItemStack output = recipe.output();
        ItemStack first = item("naturesaura:conversion_catalyst");
        ItemStack second = item("naturesaura:crushing_catalyst");
        ItemStack energy = item("mekanism:energy_tablet");
        var registries = helper.getLevel().registryAccess();
        for (String tier : List.of("single", "basic", "infinite")) {
            TileEntityMekanism source = placeKind(helper, FIRST, tier);
            helper.assertTrue(visibleCount(source) == 0
                            && pageCount(source) == 1,
                    tier + " exposed an extra empty catalyst slot with only the two native kinds");
            IMekanismMagicAutomation sourceAutomation = automation(source);
            sourceAutomation.mekanismMagicPatternInputs().getFirst()
                    .setStack(input.copy());
            sourceAutomation.mekanismMagicPatternOutputs().getFirst()
                    .setStack(output.copy());
            sourceAutomation.mekanismMagicPersistentInputs().get(0)
                    .setStack(first.copy());
            sourceAutomation.mekanismMagicPersistentInputs().get(1)
                    .setStack(second.copy());
            energySlot(source).setStack(energy.copy());
            List<IInventorySlot> oldSlots = source.getInventorySlots(null)
                    .stream().filter(slot -> !(slot instanceof
                            CatalystLibraryWindowSlot)).toList();

            CompoundTag oldWorld = new CompoundTag();
            ContainerType.ITEM.saveTo(registries, oldWorld, oldSlots);
            oldWorld.putInt("nature_altar_catalyst_slots", 2);
            helper.assertTrue(oldWorld.contains(
                            SerializationConstants.ITEMS, Tag.TAG_LIST),
                    tier + " test fixture did not write legacy item slots");
            CompoundTag originalTag = oldWorld.copy();
            TileEntityMekanism worldRestored = placeKind(
                    helper, SECOND, tier);
            worldRestored.loadAdditional(oldWorld, registries);
            assertLegacyState(helper, worldRestored, input, output,
                    first, second, energy, tier + " world");
            helper.assertTrue(originalTag.equals(oldWorld),
                    tier + " legacy world tag was modified during migration");

            ItemStack oldItem = new ItemStack(source.getBlockState()
                    .getBlock());
            oldItem.set(MekanismDataComponents.ATTACHED_ITEMS,
                    new AttachedItems(oldSlots.stream().map(slot ->
                            slot.getStack().copy()).toList()));
            ItemStack untouchedItem = oldItem.copy();
            TileEntityMekanism itemRestored = placeKind(
                    helper, THIRD, tier);
            itemRestored.applyComponentsFromItemStack(oldItem);
            assertLegacyState(helper, itemRestored, input, output,
                    first, second, energy, tier + " item");
            helper.assertTrue(ItemStack.matches(oldItem, untouchedItem),
                    tier + " legacy machine item was mutated during migration");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            batch = "natureCatalystCatalog", timeoutTicks = 120)
    public static void overflowCatalystSurvivesWorldItemAndFactoryUpgrade(
            GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        var registries = helper.getLevel().registryAccess();
        try {
            List<RecipeHolder<?>> changed = new ArrayList<>(original);
            changed.add(testRecipe(THIRD_RECIPE, Items.DIAMOND));
            manager.replaceRecipes(changed);
            NatureAuraRecipeScanner.refresh(manager);

            TileEntityMekanism source = placeFactory(helper, FIRST,
                    "basic");
            var originalRecipe = NatureAuraRecipeScanner.find(manager,
                    ResourceLocation.fromNamespaceAndPath("naturesaura",
                            "altar/glowstone")).orElseThrow();
            ItemStack input = NatureAuraRecipeBridge.representativeChoice(
                    originalRecipe.input()).orElseThrow();
            ItemStack output = originalRecipe.output();
            ItemStack first = item("naturesaura:conversion_catalyst");
            ItemStack second = item("naturesaura:crushing_catalyst");
            ItemStack energy = item("mekanism:energy_tablet");
            IMekanismMagicAutomation sourceAutomation = automation(source);
            sourceAutomation.mekanismMagicPatternInputs().getFirst()
                    .setStack(input.copy());
            sourceAutomation.mekanismMagicPatternOutputs().getFirst()
                    .setStack(output.copy());
            sourceAutomation.mekanismMagicPersistentInputs().get(0)
                    .setStack(first.copy());
            sourceAutomation.mekanismMagicPersistentInputs().get(1)
                    .setStack(second.copy());
            energySlot(source).setStack(energy.copy());
            setLibraryOpen(source, true);
            windowSlots(source).getFirst().setStack(
                    new ItemStack(Items.DIAMOND));

            CompoundTag world = new CompoundTag();
            source.saveAdditional(world, registries);
            TileEntityMekanism worldRestored = placeFactory(
                    helper, SECOND, "basic");
            worldRestored.loadAdditional(world, registries);
            assertLegacyState(helper, worldRestored, input, output,
                    first, second, energy, "new world");
            helper.assertTrue(catalystStacks(worldRestored).get(2)
                            .is(Items.DIAMOND),
                    "World save lost the overflow catalyst");

            ItemStack machineItem = new ItemStack(source.getBlockState()
                    .getBlock());
            source.saveToItem(machineItem, registries);
            TileEntityMekanism itemRestored = placeFactory(
                    helper, THIRD, "basic");
            helper.assertTrue(BlockItem.updateCustomBlockEntityTag(
                            helper.getLevel(),
                            helper.makeMockPlayer(
                                    net.minecraft.world.level.GameType.SURVIVAL),
                            helper.absolutePos(THIRD), machineItem),
                    "Machine item did not carry block entity save data");
            itemRestored.applyComponentsFromItemStack(machineItem);
            assertLegacyState(helper, itemRestored, input, output,
                    first, second, energy, "new item");
            helper.assertTrue(catalystStacks(itemRestored).get(2)
                            .is(Items.DIAMOND),
                    "Machine item lost the overflow catalyst");

            var upgrade = source.getUpgradeData(registries);
            helper.assertTrue(upgrade instanceof
                            NatureAltarFactoryUpgradeData data
                            && data.catalysts().size() >= 3
                            && data.catalyst(2).is(Items.DIAMOND),
                    "Factory upgrade payload omitted the overflow catalyst");
            TileEntityMekanism infinite = placeFactory(helper, THIRD,
                    "infinite");
            infinite.parseUpgradeData(registries, upgrade);
            assertLegacyState(helper, infinite, input, output,
                    first, second, energy, "basic-to-infinite upgrade");
            helper.assertTrue(catalystStacks(infinite).get(2)
                            .is(Items.DIAMOND),
                    "Basic-to-infinite upgrade lost the overflow catalyst");
        } finally {
            manager.replaceRecipes(original);
            NatureAuraRecipeScanner.refresh(manager);
        }
        helper.succeed();
    }

    private static RecipeHolder<AltarRecipe> testRecipe(
            ResourceLocation id, Item catalyst) {
        return new RecipeHolder<>(id, new AltarRecipe(
                Ingredient.of(Items.DIAMOND_BLOCK),
                new ItemStack(Items.EMERALD_BLOCK),
                Ingredient.of(catalyst), 1, 20));
    }

    private static void assertLegacyState(GameTestHelper helper,
                                          TileEntityMekanism tile,
                                          ItemStack input,
                                          ItemStack output,
                                          ItemStack first,
                                          ItemStack second,
                                          ItemStack energy,
                                          String context) {
        IMekanismMagicAutomation machine = automation(tile);
        helper.assertTrue(ItemStack.matches(machine
                        .mekanismMagicPatternInputs().getFirst().getStack(),
                        input)
                        && ItemStack.matches(machine
                        .mekanismMagicPatternOutputs().getFirst().getStack(),
                        output)
                        && ItemStack.matches(machine
                        .mekanismMagicPersistentInputs().get(0).getStack(),
                        first)
                        && ItemStack.matches(machine
                        .mekanismMagicPersistentInputs().get(1).getStack(),
                        second)
                        && ItemStack.matches(energySlot(tile).getStack(),
                        energy),
                context + " changed an old input/output/catalyst/energy slot");
    }

    private static IMekanismMagicAutomation automation(
            TileEntityMekanism tile) {
        return (IMekanismMagicAutomation) tile;
    }

    private static EnergyInventorySlot energySlot(
            TileEntityMekanism tile) {
        return tile.getInventorySlots(null).stream()
                .filter(EnergyInventorySlot.class::isInstance)
                .map(EnergyInventorySlot.class::cast)
                .findFirst().orElseThrow();
    }

    private static ItemStack item(String id) {
        ResourceLocation parsed = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(parsed)) {
            throw new IllegalStateException("Missing fixture item " + id);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(parsed));
    }

    private static NatureAltarProcessorBlockEntity placeSingle(
            GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, NaturesAuraRegistries
                .NATURE_ALTAR_PROCESSOR_BLOCK.get());
        return (NatureAltarProcessorBlockEntity) helper.getBlockEntity(pos);
    }

    private static TileEntityMekanism placeKind(GameTestHelper helper,
                                                 BlockPos pos,
                                                 String kind) {
        return "single".equals(kind) ? placeSingle(helper, pos)
                : placeFactory(helper, pos, kind);
    }

    private static TileEntityMekanism placeFactory(GameTestHelper helper,
                                                   BlockPos pos,
                                                   String tier) {
        ResourceLocation blockId = id(tier + "_nature_altar_factory");
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(blockId),
                "Missing factory block " + blockId);
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(blockId));
        return (TileEntityMekanism) helper.getBlockEntity(pos);
    }

    private static List<ItemStack> catalystStacks(TileEntityMekanism tile) {
        if (tile instanceof NatureAltarProcessorBlockEntity single) {
            return single.getCatalystStacks();
        }
        if (tile instanceof NatureAltarFactoryBlockEntity factory) {
            return factory.getCatalystStacks();
        }
        return ((ExtraNatureAltarFactoryBlockEntity) tile)
                .getCatalystStacks();
    }

    private static List<CatalystLibraryWindowSlot> windowSlots(
            TileEntityMekanism tile) {
        if (tile instanceof NatureAltarProcessorBlockEntity single) {
            return single.catalystLibraryWindowSlots();
        }
        if (tile instanceof NatureAltarFactoryBlockEntity factory) {
            return factory.catalystLibraryWindowSlots();
        }
        return ((ExtraNatureAltarFactoryBlockEntity) tile)
                .catalystLibraryWindowSlots();
    }

    private static void setLibraryOpen(TileEntityMekanism tile,
                                       boolean open) {
        if (tile instanceof NatureAltarProcessorBlockEntity single) {
            single.setCatalystLibraryOpen(open);
        } else if (tile instanceof NatureAltarFactoryBlockEntity factory) {
            factory.setCatalystLibraryOpen(open);
        } else {
            ((ExtraNatureAltarFactoryBlockEntity) tile)
                    .setCatalystLibraryOpen(open);
        }
    }

    private static int visibleCount(TileEntityMekanism tile) {
        if (tile instanceof NatureAltarProcessorBlockEntity single) {
            return single.catalystVisibleSlotCount();
        }
        if (tile instanceof NatureAltarFactoryBlockEntity factory) {
            return factory.catalystVisibleSlotCount();
        }
        return ((ExtraNatureAltarFactoryBlockEntity) tile)
                .catalystVisibleSlotCount();
    }

    private static int pageCount(TileEntityMekanism tile) {
        if (tile instanceof NatureAltarProcessorBlockEntity single) {
            return single.catalystPageCount();
        }
        if (tile instanceof NatureAltarFactoryBlockEntity factory) {
            return factory.catalystPageCount();
        }
        return ((ExtraNatureAltarFactoryBlockEntity) tile)
                .catalystPageCount();
    }

    private static int page(TileEntityMekanism tile) {
        if (tile instanceof NatureAltarProcessorBlockEntity single) {
            return single.catalystPage();
        }
        if (tile instanceof NatureAltarFactoryBlockEntity factory) {
            return factory.catalystPage();
        }
        return ((ExtraNatureAltarFactoryBlockEntity) tile)
                .catalystPage();
    }

    private static ItemStack pageStack(TileEntityMekanism tile,
                                       int pageSlot) {
        if (tile instanceof NatureAltarProcessorBlockEntity single) {
            return single.catalystPageStack(pageSlot);
        }
        if (tile instanceof NatureAltarFactoryBlockEntity factory) {
            return factory.catalystPageStack(pageSlot);
        }
        return ((ExtraNatureAltarFactoryBlockEntity) tile)
                .catalystPageStack(pageSlot);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                path);
    }
}
