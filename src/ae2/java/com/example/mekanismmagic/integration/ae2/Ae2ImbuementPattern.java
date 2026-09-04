package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

final class Ae2ImbuementPattern implements IPatternDetails {
    private final WeakReference<ImbuementProcessorBlockEntity> tileReference;
    private final ResourceLocation recipeId;
    private final String semanticSignature;
    private final long catalogVersion;
    private final ResourceLocation catalystId;
    private final ItemStack definition;
    private final boolean requiresCatalystIdentifier;
    private final GenericStack output;
    private final IInput[] inputs;

    Ae2ImbuementPattern(ImbuementProcessorBlockEntity tile,
                        RecipeHolder<ImbuementRecipe> holder) {
        this.tileReference = new WeakReference<>(tile);
        this.recipeId = holder.id();
        this.semanticSignature = ArsNouveauRecipeScanner.semanticSignature(
                tile.getLevel().getRecipeManager(), holder.id());
        this.catalogVersion = ArsNouveauRecipeScanner.version(
                tile.getLevel().getRecipeManager());
        this.catalystId = ArsNouveauRecipeBridge.catalystIdForRecipe(
                tile.getLevel(), holder.id());
        this.definition = ArsNouveauRecipeBridge
                .createPatternIdentifierForRecipe(
                        tile.getLevel(), holder.id());
        this.requiresCatalystIdentifier =
                !holder.value().getPedestalItems().isEmpty();
        GenericStack[] inputChoices = Arrays.stream(
                        holder.value().getInput().getItems())
                .map(stack -> stack.copyWithCount(1))
                .map(GenericStack::fromItemStack)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toArray(GenericStack[]::new);
        this.output = GenericStack.fromItemStack(
                holder.value().getResultItem(
                        tile.getLevel().registryAccess()).copy());
        this.inputs = inputChoices.length == 0 ? new IInput[0]
                : new IInput[]{new ReagentInput(
                holder.value().getInput(), inputChoices)};
    }

    ImbuementProcessorBlockEntity tile() {
        return tileReference.get();
    }

    boolean acceptsInput(AEItemKey key, Level level) {
        if (key == null || !matchesCurrentRecipe(level)) {
            return false;
        }
        RecipeHolder<ImbuementRecipe> current = ArsNouveauRecipeScanner.find(
                level.getRecipeManager(), recipeId);
        return current != null && current.value().getInput().test(
                key.toStack());
    }

    long expectedInputAmount() {
        return 1L;
    }

    ResourceLocation catalystId() {
        return catalystId;
    }

    boolean requiresCatalystIdentifier() {
        return requiresCatalystIdentifier;
    }

    boolean isUsable() {
        return getDefinition() != null && inputs.length == 1
                && output != null && output.amount() > 0;
    }

    boolean matchesCurrentRecipe(Level level) {
        return level != null && !semanticSignature.isEmpty()
                && catalogVersion == ArsNouveauRecipeScanner.version(
                level.getRecipeManager())
                && semanticSignature.equals(
                ArsNouveauRecipeScanner.semanticSignature(
                        level.getRecipeManager(), recipeId));
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
                                GenericStack[] choices) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return choices.clone();
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
