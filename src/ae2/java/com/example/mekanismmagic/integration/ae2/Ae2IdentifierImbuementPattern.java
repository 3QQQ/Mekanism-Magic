package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * AE processing pattern that keeps a catalyst identifier in its encoded
 * display data while excluding it from planning and physical dispatch.
 */
public final class Ae2IdentifierImbuementPattern
        implements IPatternDetails {
    private final AEItemKey definition;
    private final AEProcessingPattern delegate;
    private final ResourceLocation recipeId;
    private final ItemStack identifier;

    private Ae2IdentifierImbuementPattern(
            AEItemKey definition, AEProcessingPattern delegate,
            ResourceLocation recipeId, ItemStack identifier) {
        this.definition = definition;
        this.delegate = delegate;
        this.recipeId = recipeId;
        this.identifier = identifier;
    }

    public static Ae2IdentifierImbuementPattern tryDecode(
            AEItemKey definition, Level level) {
        if (definition == null || level == null) {
            return null;
        }
        EncodedProcessingPattern encoded = definition.get(
                AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) {
            return null;
        }
        ResourceLocation recipeId = null;
        ItemStack identifier = ItemStack.EMPTY;
        List<GenericStack> filteredInputs = new ArrayList<>(
                encoded.sparseInputs().size());
        boolean hasRealInput = false;
        for (GenericStack input : encoded.sparseInputs()) {
            ItemStack possibleIdentifier = itemStack(input);
            if (possibleIdentifier.is(ArsNouveauRegistries
                    .CATALYST_IDENTIFIER_ITEM.get())) {
                if (!identifier.isEmpty()
                        && !ItemStack.isSameItemSameComponents(
                        identifier, possibleIdentifier)) {
                    return null;
                }
                identifier = possibleIdentifier.copyWithCount(1);
                ResourceLocation markedRecipe = ArsNouveauRecipeBridge
                        .patternRecipe(possibleIdentifier);
                if (markedRecipe != null) {
                    if (recipeId != null
                            && !recipeId.equals(markedRecipe)) {
                        return null;
                    }
                    recipeId = markedRecipe;
                }
                filteredInputs.add(null);
            } else {
                filteredInputs.add(input);
                hasRealInput |= input != null && input.amount() > 0;
            }
        }
        if (identifier.isEmpty() || !hasRealInput) {
            return null;
        }
        if (recipeId == null) {
            // Physical catalyst identifiers created by the assembler do not
            // carry the transient pattern_recipe field used by JEI transfer.
            // Resolve them from their signed recipe list plus the encoded
            // reagent/output instead of treating the identifier as a
            // consumable AE input.
            recipeId = resolvePhysicalIdentifierRecipe(level, identifier,
                    filteredInputs, encoded.sparseOutputs());
        }
        if (recipeId == null
                || !matchesCurrentRecipe(level, recipeId, identifier,
                filteredInputs, encoded.sparseOutputs())) {
            return null;
        }
        ItemStack filteredDefinition = definition.toStack();
        filteredDefinition.set(AEComponents.ENCODED_PROCESSING_PATTERN,
                new EncodedProcessingPattern(filteredInputs,
                        encoded.sparseOutputs()));
        AEItemKey filteredKey = AEItemKey.of(filteredDefinition);
        if (filteredKey == null) {
            return null;
        }
        return new Ae2IdentifierImbuementPattern(definition,
                new AEProcessingPattern(filteredKey), recipeId, identifier);
    }

    /**
     * Identifies patterns that must be handled by this decoder even when
     * their recipe marker is invalid or ambiguous. Letting AE2 fall back to a
     * normal processing pattern would turn the persistent catalyst identifier
     * into a consumable input.
     */
    public static boolean containsCatalystIdentifier(
            AEItemKey definition) {
        if (definition == null) {
            return false;
        }
        EncodedProcessingPattern encoded = definition.get(
                AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) {
            return false;
        }
        for (GenericStack input : encoded.sparseInputs()) {
            if (itemStack(input).is(ArsNouveauRegistries
                    .CATALYST_IDENTIFIER_ITEM.get())) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation resolvePhysicalIdentifierRecipe(
            Level level, ItemStack identifier,
            List<GenericStack> inputs, List<GenericStack> outputs) {
        ResourceLocation match = null;
        for (var holder : ArsNouveauRecipeScanner.scan(
                level.getRecipeManager())) {
            ResourceLocation candidate = holder.id();
            if (!ArsNouveauRecipeBridge.identifierMatchesRecipe(
                    level, identifier, candidate)
                    || !matchesInputAndOutput(level, holder,
                    inputs, outputs)) {
                continue;
            }
            // A processing pattern without an explicit pattern_recipe must
            // never silently pick between two otherwise identical recipes.
            if (match != null && !match.equals(candidate)) {
                return null;
            }
            match = candidate;
        }
        return match;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    private static boolean matchesCurrentRecipe(
            Level level, ResourceLocation recipeId, ItemStack identifier,
            List<GenericStack> inputs, List<GenericStack> outputs) {
        var holder = ArsNouveauRecipeScanner.scan(
                        level.getRecipeManager()).stream()
                .filter(candidate -> candidate.id().equals(recipeId))
                .findFirst().orElse(null);
        if (holder == null
                || !ArsNouveauRecipeBridge.identifierMatchesRecipe(
                level, identifier, recipeId)) {
            return false;
        }
        return matchesInputAndOutput(level, holder, inputs, outputs);
    }

    private static boolean matchesInputAndOutput(
            Level level,
            net.minecraft.world.item.crafting.RecipeHolder<
                    com.hollingsworth.arsnouveau.common.crafting.recipes
                            .ImbuementRecipe> holder,
            List<GenericStack> inputs, List<GenericStack> outputs) {
        boolean inputMatches = inputs.stream()
                .map(Ae2IdentifierImbuementPattern::itemStack)
                .filter(stack -> !stack.isEmpty())
                .anyMatch(holder.value().getInput()::test);
        AEItemKey expectedOutput = AEItemKey.of(
                holder.value().getResultItem(level.registryAccess()));
        boolean outputMatches = expectedOutput != null && outputs.stream()
                .filter(stack -> stack != null && stack.amount() > 0)
                .anyMatch(stack -> expectedOutput.equals(stack.what()));
        return inputMatches && outputMatches;
    }

    private static ItemStack itemStack(GenericStack stack) {
        if (stack == null || !(stack.what() instanceof AEItemKey item)) {
            return ItemStack.EMPTY;
        }
        return item.toStack((int) Math.min(Integer.MAX_VALUE,
                Math.max(1, stack.amount())));
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return delegate.getInputs();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(
            KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder,
                (key, amount) -> {
                    if (key instanceof AEItemKey itemKey) {
                        ItemStack marked = ArsNouveauRecipeBridge
                                .markPatternInput(
                                        itemKey.toStack(1), recipeId);
                        AEItemKey markedKey = AEItemKey.of(marked);
                        if (markedKey != null) {
                            inputSink.pushInput(markedKey, amount);
                            return;
                        }
                    }
                    inputSink.pushInput(key, amount);
                });
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flag) {
        PatternDetailsTooltip tooltip = delegate.getTooltip(level, flag);
        tooltip.addProperty(
                net.minecraft.network.chat.Component.translatable(
                        "ae2.mekanism_magic.virtual_catalyst"),
                identifier.getHoverName());
        return tooltip;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Ae2IdentifierImbuementPattern pattern
                && definition.equals(pattern.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }
}
