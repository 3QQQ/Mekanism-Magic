package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.List;

final class Ae2ImbuementPattern implements IPatternDetails {
    private final ImbuementProcessorBlockEntity tile;
    private final RecipeHolder<ImbuementRecipe> holder;
    private final ResourceLocation catalystId;
    private final ItemStack definition;
    private final boolean requiresCatalystIdentifier;
    private final GenericStack input;
    private final GenericStack output;
    private final IInput[] inputs;

    Ae2ImbuementPattern(ImbuementProcessorBlockEntity tile,
                        RecipeHolder<ImbuementRecipe> holder) {
        this.tile = tile;
        this.holder = holder;
        this.catalystId = ArsNouveauRecipeBridge.catalystIdForRecipe(
                tile.getLevel(), holder.id());
        this.definition = ArsNouveauRecipeBridge
                .createPatternIdentifierForRecipe(
                        tile.getLevel(), holder.id());
        this.requiresCatalystIdentifier =
                !holder.value().getPedestalItems().isEmpty();
        ItemStack[] inputChoices = holder.value().getInput().getItems();
        ItemStack reagent = inputChoices.length == 0
                ? ItemStack.EMPTY : inputChoices[0].copyWithCount(1);
        this.input = GenericStack.fromItemStack(reagent);
        this.output = GenericStack.fromItemStack(
                holder.value().getResultItem(
                        tile.getLevel().registryAccess()).copy());
        this.inputs = new IInput[]{new ReagentInput(
                holder.value().getInput(), input)};
    }

    ImbuementProcessorBlockEntity tile() {
        return tile;
    }

    ResourceLocation catalystId() {
        return catalystId;
    }

    boolean requiresCatalystIdentifier() {
        return requiresCatalystIdentifier;
    }

    @Override
    public AEItemKey getDefinition() {
        return AEItemKey.of(definition);
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return List.of(output);
    }

    @Override
    public PatternDetailsTooltip getTooltip(
            Level level, TooltipFlag flag) {
        PatternDetailsTooltip tooltip =
                IPatternDetails.super.getTooltip(level, flag);
        tooltip.addProperty(Component.translatable(
                        "ae2.mekanism_magic.virtual_catalyst"),
                definition.getHoverName());
        return tooltip;
    }

    private record ReagentInput(Ingredient ingredient,
                                GenericStack stack) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{stack};
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            return key instanceof AEItemKey item
                    && ingredient.test(item.toStack());
        }

        @Override
        public AEKey getRemainingKey(AEKey key) {
            return null;
        }
    }
}
