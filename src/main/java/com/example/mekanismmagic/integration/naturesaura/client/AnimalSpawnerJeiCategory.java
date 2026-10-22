package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;

public final class AnimalSpawnerJeiCategory extends MagicJeiCategory<AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView> {
    public AnimalSpawnerJeiCategory(IGuiHelper helper) {
        this(helper, NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK);
    }

    public AnimalSpawnerJeiCategory(IGuiHelper helper, net.minecraft.world.level.ItemLike icon) {
        super(helper, NaturesAuraJeiIntegration.ANIMAL_SPAWNER_TYPE,
                Component.translatable("jei.mekanism_magic.animal_spawner"),
                helper.createDrawableItemLike(icon), 178, 96);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe,
                          IFocusGroup focuses) {
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            int amount = recipe.inputAmounts().get(i);
            input(builder.addInputSlot(5 + i % 3 * 20, 16 + i / 3 * 20))
                    .setSlotName("material_" + i)
                    .addItemStacks(Arrays.stream(recipe.ingredients().get(i).getItems())
                            .map(stack -> stack.copyWithCount(amount)).toList())
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable("jei.mekanism_magic.animal_spawner.structure"));
                        tooltip.add(Component.translatable("jei.mekanism_magic.animal_spawner.selection"));
                    });
        }
        output(builder.addOutputSlot(136, 28)).setSlotName("spawn_egg").addItemStack(recipe.outputEgg());
    }

    @Override
    public void draw(AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe, IRecipeSlotsView slots,
                     GuiGraphics graphics, double x, double y) {
        super.draw(recipe, slots, graphics, x, y);
        var font = Minecraft.getInstance().font;
        String target = Component.translatable("jei.mekanism_magic.animal_spawner.result", recipe.entityType().getDescription()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(target, 102), 74, 12, 0x77AD71, false);
        graphics.drawString(font, Component.translatable("jei.mekanism_magic.animal_spawner.live"), 74, 49, 0x888888, false);
        graphics.drawString(font, Component.translatable("jei.mekanism_magic.nature_altar.summary", recipe.auraCost(), recipe.time()), 4, 65, 0x888888, false);
        graphics.drawString(font, Component.translatable("jei.mekanism_magic.animal_spawner.no_item"), 4, 80, 0x888888, false);
    }

    @Override public ResourceLocation getRegistryName(AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe) { return recipe.id(); }
}
