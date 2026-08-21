package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class MiniRitualJeiCategory extends AbstractRecipeCategory<
        OccultismRecipeBridge.PentacleJeiData> {
    private static final int SLOT_SPACING = 18;

    public MiniRitualJeiCategory(IGuiHelper guiHelper) {
        super(MekanismMagicJeiPlugin.MINI_RITUAL_TYPE,
                Component.translatable("jei.mekanism_magic.mini_ritual"),
                guiHelper.createDrawableItemLike(
                        NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.getSecondary()),
                176, 84);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.PentacleJeiData recipe,
                          IFocusGroup focuses) {
        for (int index = 0; index < Math.min(16, recipe.materials().size()); index++) {
            int row = index / 8;
            int column = index % 8;
            JeiSlotMarker.mark(
                            builder.addInputSlot(4 + column * SLOT_SPACING,
                                            4 + row * SLOT_SPACING)
                                    .setStandardSlotBackground(),
                            JeiSlotMarker.Kind.INPUT, "input_" + index)
                    .addItemStack(recipe.materials().get(index));
        }

        for (int index = 0; index < recipe.chalkColors().size()
                && index < 16; index++) {
            String color = recipe.chalkColors().get(index);
            ItemStack chalk = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(
                            "occultism", "chalk_" + color)));
            int row = index / 8;
            int column = index % 8;
            JeiSlotMarker.mark(
                            builder.addSlot(RecipeIngredientRole.CATALYST,
                                            4 + column * SLOT_SPACING,
                                            46 + row * SLOT_SPACING)
                                    .setStandardSlotBackground(),
                            JeiSlotMarker.Kind.CHALK, "chalk_" + index)
                    .addItemStack(chalk);
        }
        JeiSlotMarker.mark(
                        builder.addOutputSlot(148, 31)
                                .setOutputSlotBackground(),
                        JeiSlotMarker.Kind.OUTPUT, "output")
                .addItemStack(recipe.output());
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                .addItemStacks(MekanismMagicJeiPlugin.boundRitualSelectors(
                        recipe.pentacleId()));
    }

    @Override
    public ResourceLocation getRegistryName(
            OccultismRecipeBridge.PentacleJeiData recipe) {
        return recipe.pentacleId();
    }
}
