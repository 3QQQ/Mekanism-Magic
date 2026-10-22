package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Selection and invalidation over the actual server's published 2.6.1 catalog. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClibanoSelectionGameTests {
    private ClibanoSelectionGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void alloyPriorityAndSingleSlotConsumptionFollowNativeMatching(GameTestHelper helper) {
        var selector = new ClibanoRecipeSelector();
        var level = helper.getLevel();
        ItemStack iron = new ItemStack(Items.RAW_IRON, 12);
        ItemStack obsidian = new ItemStack(Items.OBSIDIAN, 8);
        ItemStack artisan = item("artisan_relic");
        var alloy = selector.select(level, iron, obsidian, FireType.FIRE, artisan, ItemStack.EMPTY).orElseThrow();
        helper.assertTrue(alloy.view().doubleInput() && alloy.firstInputSlot() == 0 && alloy.secondInputSlot() == 1
                        && alloy.firstAmount() == 1 && alloy.secondAmount() == 1,
                "An available alloy must take priority over raw iron's single recipe");
        var reversed = selector.select(level, obsidian, iron, FireType.FIRE, artisan, ItemStack.EMPTY).orElseThrow();
        helper.assertTrue(reversed.id().equals(alloy.id()) && reversed.firstInputSlot() == 0
                        && reversed.secondInputSlot() == 1, "Alloy ingredient order changed its consumption slots");
        var single = selector.select(level, obsidian, iron, FireType.FIRE, ItemStack.EMPTY, ItemStack.EMPTY).orElseThrow();
        helper.assertTrue(!single.view().doubleInput() && single.firstInputSlot() == 1
                        && single.secondInputSlot() == -1 && single.firstAmount() == 1 && single.secondAmount() == 0,
                "Failed alloy matching must consume only the matching single slot");
        var first = selector.select(level, iron, new ItemStack(Items.RAW_COPPER), FireType.FIRE,
                ItemStack.EMPTY, ItemStack.EMPTY).orElseThrow();
        helper.assertTrue(first.firstInputSlot() == 0 && first.view().output().is(Items.IRON_INGOT),
                "Two single recipes must prefer slot zero");
        helper.assertTrue(iron.getCount() == 12 && obsidian.getCount() == 8 && artisan.getCount() == 1,
                "Selecting a recipe consumed inventory");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void stableTicksAndNegativeResultsDoNotRescanCandidates(GameTestHelper helper) {
        var selector = new ClibanoRecipeSelector();
        var level = helper.getLevel();
        ItemStack iron = new ItemStack(Items.RAW_IRON, 12);
        ItemStack medium = item("soul");
        var initial = selector.select(level, iron, ItemStack.EMPTY, FireType.SOUL_FIRE,
                ItemStack.EMPTY, medium).orElseThrow();
        long scans = selector.candidateScanCount();
        for (int tick = 0; tick < 100; tick++) {
            helper.assertTrue(selector.select(level, iron, ItemStack.EMPTY, FireType.SOUL_FIRE,
                    ItemStack.EMPTY, medium).orElseThrow() == initial, "A stable tick rebuilt its selection");
        }
        helper.assertTrue(selector.candidateScanCount() == scans, "A stable tick scanned all recipes");
        iron.grow(1);
        var replenished = selector.select(level, iron, ItemStack.EMPTY, FireType.SOUL_FIRE,
                ItemStack.EMPTY, medium).orElseThrow();
        helper.assertTrue(selector.candidateScanCount() == scans + 1
                        && replenished.signature().equals(initial.signature()),
                "Input count must invalidate lookup without resetting the operation signature");
        iron.set(DataComponents.CUSTOM_NAME, Component.literal("Changed input components"));
        var renamed = selector.select(level, iron, ItemStack.EMPTY, FireType.SOUL_FIRE,
                ItemStack.EMPTY, medium).orElseThrow();
        helper.assertTrue(!renamed.signature().equals(initial.signature()),
                "Input components did not invalidate the operation signature");
        medium.grow(1);
        scans = selector.candidateScanCount();
        var mediumChanged = selector.select(level, iron, ItemStack.EMPTY, FireType.SOUL_FIRE,
                ItemStack.EMPTY, medium).orElseThrow();
        helper.assertTrue(selector.candidateScanCount() == scans + 1
                        && mediumChanged.signature().equals(renamed.signature()),
                "Medium replenishment changed the already-active operation");
        var fireChanged = selector.select(level, iron, ItemStack.EMPTY, FireType.ENCHANTED_FIRE,
                ItemStack.EMPTY, medium).orElseThrow();
        helper.assertTrue(!fireChanged.signature().equals(renamed.signature()),
                "Effective fire changes did not invalidate the operation signature");
        ItemStack dirt = new ItemStack(Items.DIRT);
        selector.select(level, dirt, ItemStack.EMPTY, FireType.FIRE, ItemStack.EMPTY, ItemStack.EMPTY);
        scans = selector.candidateScanCount();
        for (int tick = 0; tick < 100; tick++) {
            helper.assertTrue(selector.select(level, dirt, ItemStack.EMPTY, FireType.FIRE,
                    ItemStack.EMPTY, ItemStack.EMPTY).isEmpty(), "Unmatched input acquired a recipe");
        }
        helper.assertTrue(selector.candidateScanCount() == scans, "Negative cache result rescanned on every tick");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void reloadAndEnhancerComponentsInvalidateSelections(GameTestHelper helper) {
        var selector = new ClibanoRecipeSelector();
        var level = helper.getLevel();
        ItemStack iron = new ItemStack(Items.RAW_IRON);
        ItemStack enhancer = item("crimson_stone");
        var initial = selector.select(level, iron, ItemStack.EMPTY, FireType.FIRE,
                enhancer, ItemStack.EMPTY).orElseThrow();
        enhancer.set(DataComponents.CUSTOM_NAME, Component.literal("Changed enhancer"));
        var changed = selector.select(level, iron, ItemStack.EMPTY, FireType.FIRE,
                enhancer, ItemStack.EMPTY).orElseThrow();
        helper.assertTrue(!initial.signature().equals(changed.signature()),
                "Enhancer component changes were ignored");
        long scans = selector.candidateScanCount();
        ForbiddenArcanusRecipeCatalog.refresh(level.getServer());
        helper.assertTrue(!selector.stillMatches(level, changed, iron, ItemStack.EMPTY, FireType.FIRE, enhancer),
                "Commit validation accepted an old catalog view after reload");
        var refreshed = selector.select(level, iron, ItemStack.EMPTY, FireType.FIRE,
                enhancer, ItemStack.EMPTY).orElseThrow();
        helper.assertTrue(selector.candidateScanCount() == scans + 1
                        && refreshed.signature().equals(changed.signature()),
                "Reload failed to rescan, or identical data changed the persisted signature");
        helper.assertTrue(new ClibanoRecipeSelector().select(level, iron, ItemStack.EMPTY, FireType.FIRE,
                        enhancer, ItemStack.EMPTY).orElseThrow().signature().equals(refreshed.signature()),
                "A fresh selector did not reproduce the saved operation signature");
        helper.succeed();
    }

    private static ItemStack item(String path) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                "forbidden_arcanus", path)));
        if (stack.isEmpty()) {
            throw new IllegalStateException("Missing native Forbidden & Arcanus item " + path);
        }
        return stack;
    }
}
