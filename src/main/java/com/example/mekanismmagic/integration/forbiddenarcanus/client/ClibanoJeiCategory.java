package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.ClibanoDisplay;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

import java.util.Locale;

import static com.example.mekanismmagic.integration.forbiddenarcanus.client.ForbiddenJeiLayout.*;

/** All native fire durations and probabilistic, accumulated residue remain explicit. */
public final class ClibanoJeiCategory extends ForbiddenJeiCategory<ClibanoDisplay> {
    public ClibanoJeiCategory(IGuiHelper helper) {
        this(helper, ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK);
    }

    ClibanoJeiCategory(IGuiHelper helper, ItemLike icon) {
        super(helper, ForbiddenArcanusJeiIntegration.CLIBANO_TYPE, "clibano",
                icon, CLIBANO_HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ClibanoDisplay display, IFocusGroup focuses) {
        var recipe = display.recipe();
        slot(builder, CLIBANO_FIRST, RecipeIngredientRole.INPUT).addItemStacks(recipe.ingredients().getFirst().examples());
        if (recipe.doubleInput()) {
            slot(builder, CLIBANO_SECOND, RecipeIngredientRole.INPUT).addItemStacks(recipe.ingredients().get(1).examples());
            builder.setShapeless(82, 24);
        }
        slot(builder, CLIBANO_OUTPUT, RecipeIngredientRole.OUTPUT).addItemStack(recipe.output());
        var media = display.fireMedia().entrySet().stream()
                .filter(entry -> entry.getKey().ordinal() >= recipe.requiredFire().ordinal())
                .flatMap(entry -> entry.getValue().stream()).toList();
        if (!media.isEmpty()) {
            slot(builder, CLIBANO_FIRE, RecipeIngredientRole.CATALYST).addItemStacks(media)
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(text("fire_context")));
        }
        if (!display.enhancerExamples().isEmpty()) {
            slot(builder, CLIBANO_ENHANCER, RecipeIngredientRole.CATALYST).addItemStacks(display.enhancerExamples())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(text("retained_enhancer")));
        }
        // Probabilistic accumulation is informative, not a guaranteed output
        // that an AE processing pattern can request from one operation.
        display.residue().ifPresent(residue -> slot(builder, CLIBANO_RESIDUE, RecipeIngredientRole.RENDER_ONLY)
                .addItemStack(residue.output()).addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(text("residue_rule", residue.requiredAmount(),
                            recipe.residue().map(chance -> String.format(Locale.ROOT, "%.2f%%", chance.chance() * 100)).orElse("0%")));
                    tooltip.add(text("residue_fire"));
                }));
    }

    @Override
    public void draw(ClibanoDisplay display, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        super.draw(display, slots, graphics, mouseX, mouseY);
        label(graphics, text("materials"), 12, 8, 66);
        label(graphics, text("result"), 126, 8, 44);
        arrow(graphics, 103, 27);
        label(graphics, text("fire"), 8, 50, 48);
        label(graphics, text("enhancer"), 56, 50, 52);
        label(graphics, text("residue"), 118, 50, 52);
        for (FireType fire : FireType.values()) {
            int ticks = display.recipe().cookingTimes().forFire(fire);
            label(graphics, text("fire_time", text("fire." + fire.name().toLowerCase(Locale.ROOT)),
                    fire.ordinal() >= display.recipe().requiredFire().ordinal() ? ticks : "—"),
                    8, 85 + fire.ordinal() * 12, 160);
        }
        label(graphics, text("xp", display.recipe().experience()), 8, 121, 160);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, ClibanoDisplay display, IRecipeSlotsView slots,
                           double mouseX, double mouseY) {
        if (mouseY >= 84 && mouseY < 120) {
            tooltip.add(text("minimum_fire", text("fire." + display.recipe().requiredFire().name().toLowerCase(Locale.ROOT))));
            tooltip.add(text("base_timing"));
        }
    }

    @Override
    public boolean isHandled(ClibanoDisplay display) {
        int inputs = display.recipe().doubleInput() ? 2 : 1;
        return display.recipe().ingredients().size() >= inputs && !display.recipe().output().isEmpty()
                && display.recipe().ingredients().stream().limit(inputs).allMatch(input -> !input.examples().isEmpty())
                && (display.recipe().requiredEnhancer().isEmpty() || !display.enhancerExamples().isEmpty());
    }

    @Override public ResourceLocation getRegistryName(ClibanoDisplay display) { return display.recipe().id(); }
}
