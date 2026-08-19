package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class MiniRitualJeiCategory extends AbstractRecipeCategory<
        OccultismRecipeBridge.PentacleJeiData> {
    private static final int SLOT_SPACING = 18;

    public MiniRitualJeiCategory(IGuiHelper guiHelper) {
        super(MekanismMagicJeiPlugin.MINI_RITUAL_TYPE,
                Component.translatable("jei.mekanism_magic.mini_ritual"),
                guiHelper.createDrawableItemLike(
                        NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.asItem()),
                176, 84);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.PentacleJeiData recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        for (int index = 0; index < Math.min(16, recipe.materials().size()); index++) {
            int row = index / 8;
            int column = index % 8;
            builder.addInputSlot(4 + column * SLOT_SPACING, 4 + row * SLOT_SPACING)
                    .setStandardSlotBackground()
                    .addItemStack(recipe.materials().get(index));
        }

        for (int index = 0; index < recipe.chalkColors().size()
                && index < 16; index++) {
            String color = recipe.chalkColors().get(index);
            ItemStack chalk = new ItemStack(
                    BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                            "occultism", "chalk_" + color)));
            int row = index / 8;
            int column = index % 8;
            builder.addInputSlot(4 + column * SLOT_SPACING,
                            46 + row * SLOT_SPACING)
                    .setStandardSlotBackground()
                    .addItemStack(chalk);
        }
        builder.addOutputSlot(148, 31)
                .setOutputSlotBackground()
                .addItemStack(recipe.output());
    }
}
