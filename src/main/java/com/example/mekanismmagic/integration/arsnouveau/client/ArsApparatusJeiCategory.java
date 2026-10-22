package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusJeiData;
import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusRecipeViews;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauMachineConfig;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.jei.MagicJeiCategory;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** The processor's exact-input view; Ars' physical apparatus views stay intact. */
public final class ArsApparatusJeiCategory
        extends MagicJeiCategory<ArsApparatusJeiData> {
    public static final String REAGENT_SLOT = "reagent";
    public static final String OUTPUT_SLOT = "output";

    public ArsApparatusJeiCategory(IGuiHelper helper) {
        super(helper, ArsNouveauJeiIntegration.APPARATUS_RECIPE_TYPE,
                Component.translatable("jei.mekanism_magic.apparatus"),
                helper.createDrawableItemLike(ArsNouveauRegistries
                        .ENCHANTING_APPARATUS_PROCESSOR_BLOCK.asItem()), 180, 110);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ArsApparatusJeiData view,
                          IFocusGroup focuses) {
        Level level = Minecraft.getInstance().level;
        List<Ingredient> ingredients = ArsApparatusRecipeViews
                .pedestalIngredients(view.recipe());
        List<ItemStack> focusedInputs = focuses
                .getItemStackFocuses(RecipeIngredientRole.INPUT)
                .map(focus -> focus.getTypedValue().getIngredient())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1)).toList();
        var selection = ArsApparatusRecipeViews.selectInputs(level, view.recipe(), focusedInputs);
        List<ItemStack> pedestals = selection.pedestals();
        if (focuses.isEmpty()) {
            // Lookup covers every supported item, including addon equipment;
            // this list is never sent through the assembly/canonical resolver.
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                    .addItemStacks(ArsApparatusJeiReagents.indexReagents(level,
                            view.recipe()));
        }

        // INPUT focus preserves full components in the selected input role.
        // An OUTPUT focus is never treated as an input or reverse-assembled.
        List<ItemStack> reagents = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();
        if (selection.valid() && ArsApparatusRecipeViews.isCurrent(level, view)) {
            addVariants(level, view, selection.reagents(), pedestals, reagents, results);
        }

        var reagentSlot = input(builder.addInputSlot(34, 47))
                .setSlotName(REAGENT_SLOT)
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(
                        Component.translatable(
                                "jei.mekanism_magic.apparatus.input_hint")));
        if (results.isEmpty()) {
            reagentSlot.addItemStacks(selection.reagents());
            // A reference is deliberately not OUTPUT: universal AE transfers
            // cannot mistake an unbound scroll/unknown armor state for a craft.
            ItemStack reference = level == null ? ItemStack.EMPTY
                    : view.recipe().getResultItem(level.registryAccess()).copy();
            output(builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 132, 47))
                    .setSlotName("output_reference").addItemStack(reference)
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(
                            Component.translatable(
                                    "jei.mekanism_magic.apparatus.requires_input")));
        } else {
            reagentSlot.addItemStacks(reagents);
            var outputSlot = output(builder.addOutputSlot(132, 47))
                    .setSlotName(OUTPUT_SLOT).addItemStacks(results)
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(
                            Component.translatable(
                                    "jei.mekanism_magic.apparatus.exact_output")));
            // Keep displayed concrete reagent/output variants paired as JEI
            // cycles through alternative items or applies a focus.
            builder.createFocusLink(reagentSlot, outputSlot);
        }

        for (int index = 0; index < ingredients.size(); index++) {
            int cell = index < 4 ? index : index + 1;
            input(builder.addInputSlot(12 + cell % 3 * 22,
                            25 + cell / 3 * 22))
                    .setSlotName("pedestal_" + index)
                    .addItemStack(pedestals.get(index))
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(
                            Component.translatable("jei.mekanism_magic.apparatus.pedestal_hint")));
            // Index every valid Ingredient choice while each drawn input stays
            // concrete. Transfer views contain drawn slots, not this index.
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                    .addIngredients(ingredients.get(index));
        }
    }

    @Override
    public void draw(ArsApparatusJeiData view, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        super.draw(view, slots, graphics, mouseX, mouseY);
        var font = Minecraft.getInstance().font;
        if (slots.findSlotByName(OUTPUT_SLOT).isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable(
                    "jei.mekanism_magic.apparatus.requires_input"),
                    86, 10, 88, 0xC98135);
        }
        graphics.drawString(font, Component.translatable(
                        "jei.mekanism_magic.apparatus.source_cost",
                        ArsNouveauMachineConfig.apparatusSourceCost(
                                view.recipe().sourceCost())),
                8, 94, 0x7F9FAB, false);
    }

    @Override
    public ResourceLocation getRegistryName(ArsApparatusJeiData recipe) {
        return recipe.id();
    }


    private static void addVariants(Level level, ArsApparatusJeiData view,
                                    List<ItemStack> candidates,
                                    List<ItemStack> pedestals,
                                    List<ItemStack> reagents,
                                    List<ItemStack> results) {
        for (ItemStack candidate : candidates) {
            if (reagents.stream().anyMatch(existing ->
                    ItemStack.isSameItemSameComponents(existing, candidate))) {
                continue;
            }
            List<ItemStack> inputs = new ArrayList<>(pedestals.size() + 1);
            inputs.add(candidate.copyWithCount(1));
            inputs.addAll(pedestals);
            ArsApparatusRecipeViews.resolveExact(level, view, inputs)
                    .ifPresent(result -> {
                        reagents.add(candidate.copyWithCount(1));
                        results.add(result.copy());
                    });
        }
    }
}
