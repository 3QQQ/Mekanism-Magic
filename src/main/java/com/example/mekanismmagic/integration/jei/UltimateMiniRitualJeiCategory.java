package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.blockentity.OccultismRecipeBridge;
import com.example.mekanismmagic.recipe.UltimateMiniRitualRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class UltimateMiniRitualJeiCategory extends AbstractRecipeCategory<
        UltimateMiniRitualRecipe> {
    public UltimateMiniRitualJeiCategory(IGuiHelper guiHelper) {
        super(MekanismMagicJeiPlugin.ULTIMATE_MINI_RITUAL_TYPE,
                Component.translatable("jei.mekanism_magic.ultimate_mini_ritual"),
                guiHelper.createDrawableItemLike(
                        MekanismMagic.ULTIMATE_MINI_RITUAL.get()),
                176, 84);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          UltimateMiniRitualRecipe recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        builder.addInputSlot(4, 4).setStandardSlotBackground()
                .addItemStack(pentacle("occultism:craft_marid"));
        builder.addInputSlot(40, 4).setStandardSlotBackground()
                .addItemStack(new ItemStack(id("mekanism:alloy_atomic")));
        builder.addInputSlot(76, 4).setStandardSlotBackground()
                .addItemStack(pentacle("occultism:possess_marid"));

        builder.addInputSlot(4, 22).setStandardSlotBackground()
                .addItemStack(new ItemStack(id("occultism:spirit_attuned_crystal")));
        builder.addInputSlot(40, 22).setStandardSlotBackground()
                .addItemStack(new ItemStack(id("minecraft:nether_star")));
        builder.addInputSlot(76, 22).setStandardSlotBackground()
                .addItemStack(new ItemStack(id("occultism:spirit_attuned_crystal")));

        builder.addInputSlot(4, 40).setStandardSlotBackground()
                .addItemStack(pentacle("occultism:summon_marid"));
        builder.addInputSlot(40, 40).setStandardSlotBackground()
                .addItemStack(new ItemStack(id("mekanism:ultimate_control_circuit")));
        builder.addInputSlot(76, 40).setStandardSlotBackground()
                .addItemStack(pentacle("occultism:summon_unbound_marid"));

        builder.addOutputSlot(140, 22).setOutputSlotBackground()
                .addItemStack(new ItemStack(MekanismMagic.ULTIMATE_MINI_RITUAL.get()));
    }

    private static ItemStack pentacle(String id) {
        ItemStack stack = new ItemStack(MekanismMagic.MINI_RITUAL.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putString("pentacle", id));
        return stack;
    }

    private static net.minecraft.world.item.Item id(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.parse(id));
    }
}
