package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.TransformExample;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Predicate;

/** Text and context chrome only; no native recipe calls during layout or drawing. */
abstract class ForbiddenJeiCategory<R> extends MagicJeiCategory<R> {
    ForbiddenJeiCategory(IGuiHelper helper, RecipeType<R> type, String titleKey, ItemLike icon, int height) {
        super(helper, type, text(titleKey), helper.createDrawableItemLike(icon), ForbiddenJeiLayout.WIDTH, height);
    }

    protected final IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder, ForbiddenJeiLayout.Slot position,
                                            RecipeIngredientRole role) {
        var slot = builder.addSlot(role, position.x(), position.y()).setSlotName(position.name());
        return role == RecipeIngredientRole.OUTPUT ? output(slot) : input(slot);
    }

    protected static Component text(String key, Object... arguments) {
        return Component.translatable("jei.mekanism_magic.forbidden." + key, arguments);
    }

    protected static void label(GuiGraphics graphics, Component text, int x, int y, int width) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, MagicGuiTheme.textMuted(), false);
        graphics.pose().popPose();
    }

    protected static void arrow(GuiGraphics graphics, int x, int y) {
        int color = MagicGuiTheme.accentSource();
        graphics.fill(x, y + 3, x + 15, y + 5, color);
        for (int step = 0; step < 4; step++) {
            graphics.fill(x + 11 + step, y + step, x + 12 + step, y + 8 - step, color);
        }
    }

    /** JEI can independently filter hidden items in linked slots. Keep the visible pair correct too. */
    protected static void synchronizeDisplayedPair(List<IRecipeSlotDrawable> slots, List<TransformExample> examples,
                                                    String inputName, String outputName) {
        synchronizeDisplayedPair(slots, examples, inputName, outputName, ForbiddenArcanusJeiIntegration::isItemVisible);
    }

    static void synchronizeDisplayedPair(List<IRecipeSlotDrawable> slots, List<TransformExample> examples,
                                         String inputName, String outputName, Predicate<ItemStack> visible) {
        var input = slots.stream().filter(slot -> slot.getSlotName().filter(inputName::equals).isPresent()).findFirst();
        var output = slots.stream().filter(slot -> slot.getSlotName().filter(outputName::equals).isPresent()).findFirst();
        if (input.isEmpty() || output.isEmpty()) return;
        // Only the paired result has overrides; read the actual source selected by JEI's focus/visibility rules.
        var source = input.get().getDisplayedItemStack();
        ItemStack result = source.flatMap(stack -> examples.stream()
                .filter(pair -> ItemStack.isSameItemSameComponents(pair.input(), stack)).findFirst()
                .map(TransformExample::output)).orElse(ItemStack.EMPTY);
        output.get().clearDisplayOverrides();
        output.get().createDisplayOverrides().addItemStacks(!result.isEmpty() && visible.test(result)
                ? List.of(result) : List.of());
    }
}
