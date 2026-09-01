package com.example.mekanismmagic.client;

import java.util.List;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * Maps this addon's existing JEI categories to Mekanism GUI recipe areas.
 */
public record MagicRecipeViewerType<RECIPE>(
        ResourceLocation id,
        Component title,
        ItemLike iconItem,
        Class<? extends RECIPE> recipeClass,
        boolean requiresHolder,
        int width,
        int height,
        List<ItemLike> workstations)
        implements IRecipeViewerRecipeType<RECIPE> {

    public MagicRecipeViewerType {
        workstations = List.copyOf(workstations);
    }

    public MagicRecipeViewerType(ResourceLocation id, Component title,
                                 ItemLike iconItem,
                                 Class<? extends RECIPE> recipeClass,
                                 boolean requiresHolder,
                                 int width, int height,
                                 ItemLike... workstations) {
        this(id, title, iconItem, recipeClass, requiresHolder,
                width, height, List.of(workstations));
    }

    @Override
    public Component getTextComponent() {
        return title;
    }

    @Override
    public ItemStack iconStack() {
        return new ItemStack(iconItem);
    }

    @Nullable
    @Override
    public ResourceLocation icon() {
        return null;
    }

    @Override
    public int xOffset() {
        return 0;
    }

    @Override
    public int yOffset() {
        return 0;
    }
}
