package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraFuelCatalog;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Native material/variant previews, not new item-crafting recipes. */
public final class AuraFuelJeiCategory extends MagicJeiCategory<NatureAuraFuelCatalog.Fuel> {
    public AuraFuelJeiCategory(IGuiHelper helper) {
        super(helper, NaturesAuraJeiIntegration.AURA_FUEL_TYPE,
                Component.translatable("jei.mekanism_magic.aura_fuel"),
                helper.createDrawableItemLike(NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK), 166, 82);
    }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, NatureAuraFuelCatalog.Fuel recipe, IFocusGroup focuses) {
        input(builder.addInputSlot(22, 20)).addItemStack(recipe.input()).addRichTooltipCallback((view, tooltip) -> {
            tooltip.add(Component.translatable("jei.mekanism_magic.aura_fuel.consume"));
            if (recipe.kind() == NatureAuraFuelCatalog.Kind.FLOWER) {
                tooltip.add(Component.translatable("jei.mekanism_magic.aura_fuel.flower"));
            } else if (recipe.overworldOnly()) {
                tooltip.add(Component.translatable("jei.mekanism_magic.aura_fuel.overworld"));
            }
            if (recipe.kind() == NatureAuraFuelCatalog.Kind.FIREWORK || recipe.kind() == NatureAuraFuelCatalog.Kind.POTION) {
                tooltip.add(Component.translatable("jei.mekanism_magic.aura_fuel.components"));
            }
        });
        if (!recipe.secondary().isEmpty()) input(builder.addInputSlot(48, 20)).addItemStack(recipe.secondary());
        if (!recipe.remainder().isEmpty()) output(builder.addOutputSlot(130, 20)).addItemStack(recipe.remainder());
    }
    @Override public void draw(NatureAuraFuelCatalog.Fuel recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                               double mouseX, double mouseY) {
        super.draw(recipe, slots, graphics, mouseX, mouseY);
        var font = Minecraft.getInstance().font;
        MagicGuiTheme.drawCenteredText(graphics, font,
                Component.translatable("jei.mekanism_magic.aura_fuel.amount", recipe.aura()),
                getWidth() / 2, 48, MagicGuiTheme.textPrimary());
        if (recipe.overworldOnly()) {
            MagicGuiTheme.drawCenteredText(graphics, font,
                    Component.translatable("jei.mekanism_magic.aura_fuel.overworld"),
                    getWidth() / 2, 64, MagicGuiTheme.textMuted());
        }
    }
}
