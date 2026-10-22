package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
import com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class TreeRitualJeiCategory extends MagicJeiCategory<TreeRitualRecipeBridge.TreeRitualRecipeView> {
    private final net.minecraft.world.level.ItemLike goldPowder;
    public TreeRitualJeiCategory(IGuiHelper helper) {
        this(helper, NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_BLOCK);
    }

    public TreeRitualJeiCategory(IGuiHelper helper, net.minecraft.world.level.ItemLike icon) {
        this(helper, icon, BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("naturesaura", "gold_powder")));
    }

    public TreeRitualJeiCategory(IGuiHelper helper, net.minecraft.world.level.ItemLike icon, net.minecraft.world.level.ItemLike goldPowder) {
        super(helper, NaturesAuraJeiIntegration.TREE_RITUAL_TYPE,
                Component.translatable("jei.mekanism_magic.tree_ritual"),
                helper.createDrawableItemLike(icon), 178, 96);
        this.goldPowder = goldPowder;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TreeRitualRecipeBridge.TreeRitualRecipeView recipe,
                          IFocusGroup focuses) {
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            input(builder.addInputSlot(4 + i % 4 * 18, 18 + i / 4 * 18)).setSlotName("material_" + i).addIngredients(recipe.ingredients().get(i));
        }
        input(builder.addInputSlot(88, 18)).setSlotName("sapling").addIngredients(recipe.sapling())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.mekanism_magic.tree_ritual.growth")));
        input(builder.addInputSlot(108, 18)).setSlotName("gold_powder")
                .addItemStack(new ItemStack(goldPowder, TreeRitualRecipeBridge.GOLD_POWDER_PER_OPERATION))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.mekanism_magic.tree_ritual.powder")));
        output(builder.addOutputSlot(151, 28)).setSlotName("output").addItemStack(recipe.output())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.mekanism_magic.tree_ritual.structure")));
    }

    @Override
    public void draw(TreeRitualRecipeBridge.TreeRitualRecipeView recipe, IRecipeSlotsView slots,
                     GuiGraphics graphics, double x, double y) {
        super.draw(recipe, slots, graphics, x, y);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("jei.mekanism_magic.tree_ritual.time", recipe.time()), 4, 65, 0x888888, false);
        graphics.drawString(font, Component.translatable("jei.mekanism_magic.tree_ritual.no_aura"), 4, 80, 0x888888, false);
    }

    @Override public ResourceLocation getRegistryName(TreeRitualRecipeBridge.TreeRitualRecipeView recipe) { return recipe.id(); }
}
