package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A catalog/legacy virtual choice must never replace a retained identifier. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArsImbuementFactoryCatalystPresenceGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final ResourceLocation RECIPE =
            ResourceLocation.fromNamespaceAndPath(
                    "ars_nouveau", "imbuement_fire_essence");

    private ArsImbuementFactoryCatalystPresenceGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeFactoryNeedsRetainedSignedIdentifier(
            GameTestHelper helper) {
        verify(helper, false);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void extrasFactoryNeedsRetainedSignedIdentifier(
            GameTestHelper helper) {
        verify(helper, true);
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, boolean extras) {
        var holder = ArsNouveauRecipeScanner.find(
                helper.getLevel().getRecipeManager(), RECIPE);
        helper.assertTrue(holder != null
                        && !holder.value().getPedestalItems().isEmpty(),
                "Missing catalyst-required imbuement fixture");
        ItemStack identifier = ArsNouveauRecipeBridge
                .createIdentifierForRecipe(helper.getLevel(), RECIPE);
        helper.assertTrue(ArsNouveauRecipeBridge.identifierMatchesRecipe(
                        helper.getLevel(), identifier, RECIPE),
                "Fixture identifier is not signed for the current recipe");
        String catalystId = CatalystIdentifierItem.catalystId(
                identifier).toString();
        ItemStack reagent = ArsNouveauRecipeBridge.representativeChoice(
                holder.value().getInput());
        helper.assertTrue(!reagent.isEmpty(), "Recipe has no reagent example");

        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, extras
                ? MekanismExtrasImbuementFactories.INFINITE_BLOCK.get()
                : ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get());
        TileEntityMekanism tile = helper.getBlockEntity(POS);
        if (tile instanceof ImbuementFactoryBlockEntity factory
                && factory.isSorting()) {
            factory.toggleSorting();
        }
        if (tile instanceof ExtraImbuementFactoryBlockEntity factory
                && factory.isSorting()) {
            factory.toggleSorting();
        }
        CatalystIdentifierSelectionHost selection =
                (CatalystIdentifierSelectionHost) tile;
        IMekanismMagicAutomation automation =
                (IMekanismMagicAutomation) tile;
        IInventorySlot library = automation.mekanismMagicPersistentInputs()
                .getFirst();
        IInventorySlot input = automation.mekanismMagicPatternInputs()
                .getFirst();

        // An old save may still contain a virtual choice, but it must resolve
        // to retained stock rather than regenerating a recipe from the catalog.
        CompoundTag legacy = tile.saveWithoutMetadata(
                helper.getLevel().registryAccess());
        legacy.putBoolean("virtual_catalyst_selected", true);
        legacy.putString("virtual_catalyst_id", catalystId);
        tile.loadAdditional(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(selection.selectedCatalystIdentifier().isEmpty()
                        && !selection.hasCatalystIdentifierForRecipe(RECIPE)
                        && !selection.canSelectCatalystIdentifierId(catalystId)
                        && !selection.selectCatalystIdentifierId(catalystId),
                "Catalog metadata selected a catalyst without retained stock");
        input.setStack(ArsNouveauRecipeBridge.markPatternInput(
                reagent, RECIPE));
        helper.assertTrue(recipe(tile) == null,
                "Marked factory recipe bypassed the empty catalyst library");

        input.setStack(ItemStack.EMPTY);
        library.setStack(identifier.copy());
        helper.assertTrue(selection.hasCatalystIdentifierForRecipe(RECIPE)
                        && selection.canSelectCatalystIdentifierId(catalystId)
                        && selection.selectCatalystIdentifierId(catalystId)
                        && ItemStack.isSameItemSameComponents(
                        selection.selectedCatalystIdentifier(), identifier),
                "A retained signed identifier did not enable exact selection");
        input.setStack(ArsNouveauRecipeBridge.markPatternInput(
                reagent, RECIPE));
        var accepted = recipe(tile);
        helper.assertTrue(accepted != null
                        && RECIPE.equals(accepted.imbuementId()),
                "Physical identifier did not resume the exact recipe");

        library.setStack(ItemStack.EMPTY);
        input.setStack(ArsNouveauRecipeBridge.markPatternInput(
                reagent, RECIPE));
        helper.assertTrue(selection.selectedCatalystIdentifier().isEmpty()
                        && !selection.hasCatalystIdentifierForRecipe(RECIPE)
                        && recipe(tile) == null,
                "Removing the retained identifier left the recipe runnable");

        ItemStack stale = identifier.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, stale, tag ->
                tag.getCompound("recipe_signatures").putString(
                        RECIPE.toString(), "stale"));
        input.setStack(ItemStack.EMPTY);
        library.setStack(stale);
        helper.assertTrue(!selection.hasCatalystIdentifierForRecipe(RECIPE)
                        && !selection.canSelectCatalystIdentifierId(catalystId),
                "A stale signature passed physical catalyst preflight");
    }

    private static ImbuementFactoryRecipe recipe(TileEntityMekanism tile) {
        return tile instanceof ImbuementFactoryBlockEntity nativeFactory
                ? nativeFactory.getRecipe(0)
                : ((ExtraImbuementFactoryBlockEntity) tile).getRecipe(0);
    }
}
