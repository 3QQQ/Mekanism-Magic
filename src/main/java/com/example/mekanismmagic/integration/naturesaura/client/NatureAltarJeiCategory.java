package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
import com.example.mekanismmagic.integration.naturesaura
        .NatureAuraRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura
        .NaturesAuraRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Machine-facing view of Nature's Aura altar recipes. */
public final class NatureAltarJeiCategory extends MagicJeiCategory<
        NatureAuraRecipeBridge.AltarRecipeView> {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 76;

    public NatureAltarJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                NaturesAuraJeiIntegration.NATURE_ALTAR_RECIPE_TYPE,
                Component.translatable(
                        "jei.mekanism_magic.nature_altar"),
                guiHelper.createDrawableItemLike(
                        NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK
                                .asItem()),
                WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            NatureAuraRecipeBridge.AltarRecipeView recipe,
            IFocusGroup focuses) {
        ItemStack context = NatureAuraRecipeBridge.createPatternContext(
                Minecraft.getInstance().level, recipe.id());
        input(builder.addInputSlot(4, 25))
                .setSlotName("nature_altar_context")
                .addItemStack(context)
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable(
                                "jei.mekanism_magic.nature_altar"
                                        + ".context_hint")));
        if (recipe.requiresCatalyst()) {
            input(builder.addSlot(RecipeIngredientRole.CATALYST,
                            34, 25))
                    .setSlotName("persistent_catalyst")
                    .addIngredients(recipe.catalyst())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable(
                                    "jei.mekanism_magic.nature_altar"
                                            + ".catalyst_hint")));
        }
        input(builder.addInputSlot(70, 25))
                .setSlotName("input")
                .addIngredients(recipe.input());
        output(builder.addOutputSlot(126, 25))
                .setSlotName("output")
                .addItemStack(recipe.output())
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable(
                            "jei.mekanism_magic.nature_altar.aura_cost",
                            recipe.auraCost()));
                    tooltip.add(Component.translatable(
                            "jei.mekanism_magic.nature_altar.duration",
                            recipe.duration()));
                });
    }

    @Override
    public void draw(
            NatureAuraRecipeBridge.AltarRecipeView recipe,
            IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
            double mouseX, double mouseY) {
        super.draw(recipe, recipeSlotsView, graphics, mouseX, mouseY);
        var font = Minecraft.getInstance().font;
        Component arrow = Component.literal(">");
        graphics.drawString(font, arrow, 104, 30,
                MagicGuiTheme.textPrimary(), false);
        if (!recipe.requiresCatalyst()) {
            Component none = Component.translatable(
                    "jei.mekanism_magic.nature_altar.no_catalyst");
            graphics.drawString(font, none,
                    Math.max(20, 43 - font.width(none) / 2), 48,
                    MagicGuiTheme.textMuted(), false);
        }
        Component cost = Component.translatable(
                "jei.mekanism_magic.nature_altar.summary",
                recipe.auraCost(), recipe.duration());
        graphics.drawString(font, cost,
                Math.max(2, (WIDTH - font.width(cost)) / 2), 61,
                MagicGuiTheme.textMuted(), false);
    }

    @Override
    public ResourceLocation getRegistryName(
            NatureAuraRecipeBridge.AltarRecipeView recipe) {
        return recipe.id();
    }
}
