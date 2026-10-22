package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
import com.example.mekanismmagic.integration.naturesaura.OfferingRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Arrays;

/** Shows input/start roles and explains the original whole-batch trigger. */
public final class OfferingJeiCategory extends MagicJeiCategory<OfferingRecipeBridge.OfferingRecipeView> {
    public OfferingJeiCategory(IGuiHelper helper) {
        this(helper, NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK);
    }

    public OfferingJeiCategory(IGuiHelper helper, net.minecraft.world.level.ItemLike icon) {
        super(helper, NaturesAuraJeiIntegration.OFFERING_TYPE,
                Component.translatable("jei.mekanism_magic.offering"),
                helper.createDrawableItemLike(icon), 150, 76);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, OfferingRecipeBridge.OfferingRecipeView recipe,
                          IFocusGroup focuses) {
        input(builder.addInputSlot(14, 26)).setSlotName("offering_input")
                .addItemStacks(Arrays.stream(recipe.input().getItems())
                        .map(stack -> stack.copyWithCount(recipe.inputAmount())).toList());
        input(builder.addInputSlot(52, 26)).setSlotName("start_item")
                .addIngredients(recipe.startItem()).addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.mekanism_magic.offering.batch")));
        input(builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.CATALYST, 84, 26))
                .setSlotName("optional_wither_roses")
                .addItemStack(new ItemStack(net.minecraft.world.item.Items.WITHER_ROSE,
                        com.example.mekanismmagic.integration.naturesaura.OfferingProcessorBlockEntity.WITHER_ROSES_FOR_BONUS))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.mekanism_magic.offering.bonus")));
        output(builder.addOutputSlot(120, 26)).setSlotName("output").addItemStack(recipe.output())
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.mekanism_magic.offering.ring"));
                    tooltip.add(Component.translatable("jei.mekanism_magic.offering.batch"));
                    tooltip.add(Component.translatable("jei.mekanism_magic.offering.bonus"));
                });
    }

    @Override
    public ResourceLocation getRegistryName(OfferingRecipeBridge.OfferingRecipeView recipe) {
        return recipe.id();
    }
}
