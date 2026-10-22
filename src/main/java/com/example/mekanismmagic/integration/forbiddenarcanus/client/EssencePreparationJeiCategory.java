package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.integration.forbiddenarcanus.EssenceFluidCodec;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssencePreparationDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/** A retained sample creates banked essence with FE; no sand, tube or XP is consumed. */
public final class EssencePreparationJeiCategory extends ForbiddenJeiCategory<EssencePreparationDisplay> {
    public EssencePreparationJeiCategory(IGuiHelper helper) {
        this(helper, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.asItem());
    }

    EssencePreparationJeiCategory(IGuiHelper helper, ItemLike icon) {
        super(helper, ForbiddenArcanusJeiIntegration.ESSENCE_CONDENSER_TYPE,
                "essence_condenser", icon, 108);
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, EssencePreparationDisplay display, IFocusGroup focuses) {
        slot(builder, new ForbiddenJeiLayout.Slot("sample", 29, 24), RecipeIngredientRole.CATALYST)
                .addItemStack(display.sample())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(text("essence_condenser.retained")));
        EssenceFluidCodec.source(display.kind().channel).ifPresent(fluid ->
                builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 24)
                        .setSlotName("essence_fluid")
                        .addFluidStack(fluid, display.essencePerCycle()));
    }

    @Override public void draw(EssencePreparationDisplay display, IRecipeSlotsView slots, GuiGraphics graphics,
                               double mouseX, double mouseY) {
        super.draw(display, slots, graphics, mouseX, mouseY);
        label(graphics, text("essence_condenser.sample"), 8, 8, 70);
        label(graphics, text("essence_condenser.bank"), 116, 8, 60);
        arrow(graphics, 97, 30);
        label(graphics, text("essence_condenser.yield", display.essencePerCycle(),
                Component.translatable("gui.mekanism_magic.essence_condenser.bank." + display.kind().path)), 8, 52, 160);
        label(graphics, text("essence_condenser.base", display.baseTicks(), display.baseEnergyPerTick()), 8, 67, 160);
        label(graphics, text("essence_condenser.vessel"), 8, 84, 160);
    }

    @Override public ResourceLocation getRegistryName(EssencePreparationDisplay display) { return display.id(); }
    @Override public boolean isHandled(EssencePreparationDisplay display) {
        return !display.sample().isEmpty() && display.essencePerCycle() > 0;
    }
}
