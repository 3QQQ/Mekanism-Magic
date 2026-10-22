package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.RitualDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.TransformExample;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.stream.Collectors;

import static com.example.mekanismmagic.integration.forbiddenarcanus.client.ForbiddenJeiLayout.*;

/** The machine tier is state, not a core or hammer item in each ritual. */
public final class HephaestusForgeJeiCategory extends ForbiddenJeiCategory<RitualDisplay> {
    public HephaestusForgeJeiCategory(IGuiHelper helper) {
        this(helper, ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK);
    }

    HephaestusForgeJeiCategory(IGuiHelper helper, ItemLike icon) {
        super(helper, ForbiddenArcanusJeiIntegration.HEPHAESTUS_FORGE_TYPE, "forge",
                icon, FORGE_HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RitualDisplay display, IFocusGroup focuses) {
        boolean upgrade = display.upgradeTier() > 0;
        var main = slot(builder, FORGE_MAIN, RecipeIngredientRole.INPUT)
                .addItemStacks(display.examples().stream().map(TransformExample::input).toList());
        for (int index = 0; index < display.recipe().inputs().size(); index++) {
            var input = display.recipe().inputs().get(index);
            slot(builder, FORGE_AUXILIARY.get(index), RecipeIngredientRole.INPUT).addItemStacks(input.ingredient().examples()
                    .stream().map(stack -> stack.copyWithCount(input.amount())).toList());
        }
        if (!upgrade) {
            var result = slot(builder, FORGE_OUTPUT, RecipeIngredientRole.OUTPUT)
                    .addItemStacks(display.examples().stream().map(TransformExample::output).toList())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(text("component_result")));
            builder.createFocusLink(main, result);
        }
        for (int index = 0; index < display.enhancers().size(); index++) {
            slot(builder, FORGE_ENHANCERS.get(index), RecipeIngredientRole.CATALYST).addItemStacks(display.enhancers().get(index))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(text("retained_enhancer")));
        }
    }

    @Override
    public void draw(RitualDisplay display, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        super.draw(display, slots, graphics, mouseX, mouseY);
        label(graphics, text("main"), 8, 8, 35);
        label(graphics, text("auxiliary"), 46, 8, 76);
        if (display.upgradeTier() > 0) {
            label(graphics, text("machine_upgrade"), 132, 23, 38);
            label(graphics, text("tier_arrow", tierText(display.supportedTiers()), display.upgradeTier()),
                    132, 37, 38);
        } else {
            label(graphics, text("result"), 138, 8, 32);
            arrow(graphics, 124, 36);
        }
        label(graphics, text("machine_tiers", tierText(display.supportedTiers())), 8, 65, 160);
        var essences = display.recipe().essences();
        int[] amounts = {essences.aureal(), essences.souls(), essences.blood(), essences.experience()};
        String[] names = {"aureal", "souls", "blood", "experience"};
        for (int channel = 0; channel < 4; channel++) {
            label(graphics, text("essence." + names[channel], amounts[channel]),
                    8 + channel % 2 * 82, 112 + channel / 2 * 12, 78);
        }
        label(graphics, text("ritual_time", display.recipe().duration()), 8, 141, 160);
        label(graphics, text("essence_raw"), 8, 153, 160);
        label(graphics, text("native_circle"), 8, 165, 160);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, RitualDisplay display, IRecipeSlotsView slots,
                           double mouseX, double mouseY) {
        if (mouseY >= 64 && mouseY < 80) {
            tooltip.add(text("machine_tier_note"));
        } else if (mouseY >= 108 && mouseY < 163) {
            tooltip.add(text("essence_rule"));
            tooltip.add(text("base_timing"));
        } else if (mouseY >= 164) {
            tooltip.add(text("native_circle"));
            display.magicCircle().ifPresent(id -> tooltip.add(text("circle_id", id.toString())));
        }
    }

    @Override
    public void onDisplayedIngredientsUpdate(RitualDisplay display, List<IRecipeSlotDrawable> slots, IFocusGroup focuses) {
        if (display.upgradeTier() == 0) {
            synchronizeDisplayedPair(slots, display.examples(), FORGE_MAIN.name(), FORGE_OUTPUT.name());
        }
    }

    @Override
    public boolean isHandled(RitualDisplay display) {
        return !display.examples().isEmpty()
                && !display.supportedTiers().isEmpty()
                && display.supportedTiers().stream().allMatch(tier -> tier >= 1 && tier <= 5)
                && (display.upgradeTier() > 0
                ? display.upgradeTier() >= 2 && display.upgradeTier() <= 5
                    && display.examples().stream().allMatch(pair -> !pair.input().isEmpty() && pair.output().isEmpty())
                : display.examples().stream().allMatch(pair -> !pair.input().isEmpty() && !pair.output().isEmpty()))
                && !display.recipe().mainIngredient().examples().isEmpty()
                && display.recipe().inputs().size() <= FORGE_AUXILIARY.size()
                && display.recipe().inputs().stream().allMatch(input -> input.amount() > 0 && !input.ingredient().examples().isEmpty())
                && display.enhancers().size() <= FORGE_ENHANCERS.size()
                && display.enhancers().stream().allMatch(examples -> !examples.isEmpty());
    }

    private static String tierText(List<Integer> tiers) {
        return tiers.stream().map(String::valueOf).collect(Collectors.joining(" / "));
    }

    @Override public ResourceLocation getRegistryName(RitualDisplay display) { return display.recipe().id(); }
}
