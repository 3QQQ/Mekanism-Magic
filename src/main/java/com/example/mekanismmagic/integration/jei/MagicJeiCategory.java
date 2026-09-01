package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shared themed frame and slot chrome for every recipe category. */
public abstract class MagicJeiCategory<RECIPE>
        extends AbstractRecipeCategory<RECIPE> {
    private final IDrawable darkInputSlot;
    private final IDrawable darkOutputSlot;
    private final IDrawable lightInputSlot;
    private final IDrawable lightOutputSlot;

    protected MagicJeiCategory(IGuiHelper guiHelper, RecipeType<RECIPE> type,
                               Component title, IDrawable icon,
                               int width, int height) {
        super(type, title, icon, width, height);
        darkInputSlot = slot(guiHelper, "gui_theme", "normal");
        darkOutputSlot = slot(guiHelper, "gui_theme", "output");
        lightInputSlot = slot(guiHelper, "gui_theme_light", "normal");
        lightOutputSlot = slot(guiHelper, "gui_theme_light", "output");
    }

    protected final IRecipeSlotBuilder input(IRecipeSlotBuilder slot) {
        return slot.setBackground(MagicGuiTheme.isLight()
                ? lightInputSlot : darkInputSlot, -1, -1);
    }

    protected final IRecipeSlotBuilder output(IRecipeSlotBuilder slot) {
        return slot.setBackground(MagicGuiTheme.isLight()
                ? lightOutputSlot : darkOutputSlot, -1, -1);
    }

    @Override
    public void draw(RECIPE recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        MagicGuiTheme.renderRecipePanel(graphics, getWidth(), getHeight());
    }

    private static IDrawable slot(IGuiHelper guiHelper, String theme,
                                  String name) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID,
                theme + "/slot/" + name + ".png");
        return guiHelper.createDrawable(texture, 0, 0, 18, 18);
    }
}
