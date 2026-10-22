package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RitualJeiCategory extends MagicJeiCategory<
        OccultismRecipeBridge.RitualJeiData> {
    public RitualJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper, MekanismMagicJeiPlugin.RITUAL_TYPE,
                Component.translatable("jei.mekanism_magic.ritual"),
                guiHelper.createDrawableItemLike(NativeMekanismRegistries.RITUAL_BLOCK.asItem()),
                176, 110);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          OccultismRecipeBridge.RitualJeiData recipe,
                          IFocusGroup focuses) {
        var level = Minecraft.getInstance().level;
        List<ItemStack> inputFocuses = focuses.getItemStackFocuses(RecipeIngredientRole.INPUT)
                .map(focus -> focus.getTypedValue().getIngredient())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1)).toList();
        List<ItemStack> exactInputs = new ArrayList<>();
        boolean preservesPrimary = OccultismRecipeBridge
                .ritualJeiPreservesPrimaryInput(level, recipe);
        boolean requiresActivationData = OccultismRecipeBridge
                .ritualJeiRequiresActivationData(level, recipe);
        JeiSlotMarker.mark(
                        input(builder.addSlot(
                                RecipeIngredientRole.CATALYST, 4, 46)),
                        JeiSlotMarker.Kind.RITUAL_SELECTOR,
                        "ritual_selector")
                .addItemStack(recipe.selector());
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemStack(
                        OccultismRecipeBridge.createPentacleMiniRitual(
                                recipe.pentacleId()));
        for (int index = 0; index < Math.min(16, recipe.ingredients().size()); index++) {
            int row = index / 8;
            int column = index % 8;
            var ingredient = recipe.ingredients().get(index);
            if (!ingredient.isEmpty() && ingredient.getItems().length > 0) {
                Optional<ItemStack> focused = inputFocuses.stream()
                        .filter(ingredient::test).findFirst();
                ItemStack selected = focused.orElse(ingredient.getItems()[0]).copyWithCount(1);
                exactInputs.add(selected);
                var slot = JeiSlotMarker.mark(
                                input(builder.addInputSlot(
                                        4 + column * 18, 4 + row * 18)),
                                JeiSlotMarker.Kind.INPUT, "input_" + index);
                if (focused.isPresent() || preservesPrimary && index == 0) {
                    // Upgrade output carries the first material's complete
                    // components. Keep that material fixed with its result.
                    slot.addItemStack(selected);
                    builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                            .addIngredients(ingredient);
                } else {
                    slot.addIngredients(ingredient);
                }
            }
        }
        Optional<ItemStack> activationFocus = inputFocuses.stream()
                .filter(stack -> OccultismRecipeBridge.matchesRitualJeiActivation(
                        level, recipe, stack)).findFirst();
        if (recipe.activation() != null
                && !recipe.activation().isEmpty()
                && recipe.activation().getItems().length > 0) {
            ItemStack activation = activationFocus.orElse(
                    recipe.activation().getItems()[0]).copyWithCount(1);
            exactInputs.add(activation);
            var activationSlot = JeiSlotMarker.mark(
                            input(builder.addInputSlot(22, 46)),
                            JeiSlotMarker.Kind.ACTIVATION, "activation");
            if (requiresActivationData && activationFocus.isEmpty()) {
                activationSlot.addIngredients(recipe.activation());
            } else {
                activationSlot.addItemStack(activation);
            }
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                    .addIngredients(recipe.activation());
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                    .addIngredients(OccultismRecipeBridge
                            .ritualJeiActivationIngredient(level, recipe));
            activationSlot.addRichTooltipCallback((view, tooltip) -> {
                        if (view.getDisplayedItemStack()
                                .filter(RitualJeiCategory::isUnboundBindingBook)
                                .isPresent()) {
                            tooltip.add(Component.translatable(
                                    "jei.mekanism_magic.ritual.dictionary_required"));
                            tooltip.add(Component.translatable(
                                    "jei.mekanism_magic.ritual.bound_book_alternative"));
                        }
                    });
            if (isUnboundBindingBook(activation)) {
                input(builder.addSlot(RecipeIngredientRole.CATALYST, 22, 76))
                        .setSlotName("dictionary_of_spirits")
                        .addItemStack(new ItemStack(BuiltInRegistries.ITEM.get(
                                ResourceLocation.fromNamespaceAndPath(
                                        "occultism", "dictionary_of_spirits"))))
                        .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                                Component.translatable("jei.mekanism_magic.ritual.dictionary_required")));
            }
        }
        for (int index = 0; index < Math.min(4, recipe.sacrifices().size()); index++) {
            JeiSlotMarker.mark(
                            input(builder.addInputSlot(
                                    58 + index * 18, 46)),
                            JeiSlotMarker.Kind.SACRIFICE,
                            "sacrifice_" + index)
                    .addItemStack(recipe.sacrifices().get(index));
            exactInputs.add(recipe.sacrifices().get(index).copyWithCount(1));
        }
        Optional<ItemStack> exactOutput = requiresActivationData && activationFocus.isEmpty()
                ? Optional.empty() : OccultismRecipeBridge.resolveRitualJeiOutput(
                level, recipe, exactInputs);
        if (exactOutput.isEmpty()) {
            // JEI and AE must never register repair_icon/resurrect_icon as a
            // craftable output, including invisible recipe-lookup ingredients.
            output(builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 148, 22))
                    .setSlotName("dynamic_output_hint")
                    .addItemStack(OccultismRecipeBridge.ritualJeiOutputHint(level, recipe))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable("jei.mekanism_magic.ritual.dynamic_output_hint")));
            return;
        }
        ItemStack displayedOutput = exactOutput.get();
        var outputSlot = JeiSlotMarker.mark(
                        output(builder.addOutputSlot(148, 22)),
                        JeiSlotMarker.Kind.OUTPUT, "output")
                .addItemStack(displayedOutput);
        if (requiresActivationData || preservesPrimary) {
            outputSlot.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                    Component.translatable("jei.mekanism_magic.ritual.exact_input_output")));
        }
        if (!requiresActivationData && !preservesPrimary
                && OccultismRecipeBridge.isMinerItem(displayedOutput)) {
            outputSlot.addRichTooltipCallback((view, tooltip) ->
                    tooltip.add(Component.translatable(
                            "jei.mekanism_magic.ritual.stable_miner_name")));
        } else if (!requiresActivationData && !preservesPrimary
                && com.example.mekanismmagic.integration.occultism
                .OccultismMachineItemIdentity.isStableNamedOutput(displayedOutput)) {
            outputSlot.addRichTooltipCallback((view, tooltip) ->
                    tooltip.add(Component.translatable(
                            "jei.mekanism_magic.ritual.stable_named_item")));
        }
    }

    private static boolean isUnboundBindingBook(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "occultism".equals(itemId.getNamespace())
                && switch (itemId.getPath()) {
                    case "book_of_binding_foliot", "book_of_binding_djinni",
                            "book_of_binding_afrit", "book_of_binding_marid" -> true;
                    default -> false;
                };
    }

    @Override
    public ResourceLocation getRegistryName(
            OccultismRecipeBridge.RitualJeiData recipe) {
        return recipe.recipeId();
    }
}
