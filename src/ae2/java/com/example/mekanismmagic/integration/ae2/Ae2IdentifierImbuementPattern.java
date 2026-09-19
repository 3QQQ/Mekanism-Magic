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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Exact imbuement processing pattern. Catalyst identifiers are optional for
 * zero-pedestal recipes and never enter planning or physical dispatch.
 */
public final class Ae2IdentifierImbuementPattern
        implements IPatternDetails {
    private final AEItemKey definition;
    private final AEProcessingPattern delegate;
    private final ResourceLocation recipeId;
    private final ItemStack identifier;
    private final boolean requiresCatalystIdentifier;
    private final String semanticSignature;
    private final WeakReference<Level> levelReference;
    private final long catalogVersion;

    private Ae2IdentifierImbuementPattern(
            AEItemKey definition, AEProcessingPattern delegate,
            ResourceLocation recipeId, ItemStack identifier, boolean requiresCatalystIdentifier,
            String semanticSignature, long catalogVersion, Level level) {
        this.definition = definition;
        this.delegate = delegate;
        this.recipeId = recipeId;
        this.identifier = identifier.copy();
        this.requiresCatalystIdentifier = requiresCatalystIdentifier;
        this.semanticSignature = semanticSignature;
        this.levelReference = new WeakReference<>(level);
        this.catalogVersion = catalogVersion;
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
        String declaredSignature = null;
        ItemStack identifier = ItemStack.EMPTY;
        List<GenericStack> filteredInputs = new ArrayList<>(
                encoded.sparseInputs().size());
        int identifierInputs = 0;
        int realInputs = 0;
        for (GenericStack input : encoded.sparseInputs()) {
            if (input == null) {
                filteredInputs.add(null);
                continue;
            }
            if (input.amount() <= 0L || input.amount() > Integer.MAX_VALUE
                    || !(input.what() instanceof AEItemKey)) {
                return null;
            }
            ItemStack possibleIdentifier = itemStack(input);
            if (ArsNouveauRecipeBridge.hasPatternRecipeMarker(possibleIdentifier)) {
                ResourceLocation markedRecipe = ArsNouveauRecipeBridge.patternRecipe(possibleIdentifier);
                if (markedRecipe == null || recipeId != null && !recipeId.equals(markedRecipe)) return null;
                recipeId = markedRecipe;
                if (ArsNouveauRecipeBridge.hasPatternRecipeSignature(possibleIdentifier)) {
                    String markedSignature = ArsNouveauRecipeBridge.patternRecipeSignature(possibleIdentifier);
                    if (markedSignature.isEmpty() || declaredSignature != null && !declaredSignature.equals(markedSignature)) return null;
                    declaredSignature = markedSignature;
                }
            }
            if (possibleIdentifier.is(ArsNouveauRegistries
                    .CATALYST_IDENTIFIER_ITEM.get())) {
                identifierInputs++;
                if (identifierInputs != 1 || input.amount() != 1L) {
                    return null;
                }
                identifier = possibleIdentifier.copyWithCount(1);
                filteredInputs.add(null);
            } else {
                realInputs++;
                if (realInputs != 1 || input.amount() != 1L) {
                    return null;
                }
                // Recipe IDs/signatures are routing metadata, never an AE
                // inventory key the crafting CPU must obtain as a material.
                GenericStack cleaned = GenericStack.fromItemStack(ArsNouveauRecipeBridge.recipeInputView(possibleIdentifier));
                if (cleaned == null) return null;
                filteredInputs.add(cleaned);
            }
        }
        if (realInputs != 1) {
            return null;
        }
        if (recipeId == null) {
            // Physical catalyst identifiers created by the assembler do not
            // carry the transient pattern_recipe field used by JEI transfer.
            // Resolve them from their signed recipe list plus the encoded
            // reagent/output instead of treating the identifier as a
            // consumable AE input.
            recipeId = identifier.isEmpty()
                    ? resolveCatalystFreeRecipe(level, filteredInputs, encoded.sparseOutputs())
                    : resolvePhysicalIdentifierRecipe(level, identifier, filteredInputs, encoded.sparseOutputs());
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
        String semanticSignature = ArsNouveauRecipeScanner
                .semanticSignature(level.getRecipeManager(), recipeId);
        if (semanticSignature.isEmpty()) {
            return null;
        }
        if (declaredSignature != null && !declaredSignature.equals(semanticSignature)) return null;
        return new Ae2IdentifierImbuementPattern(definition,
                new AEProcessingPattern(filteredKey), recipeId, identifier,
                ArsNouveauRecipeBridge.requiresCatalystIdentifier(level, recipeId),
                semanticSignature, ArsNouveauRecipeScanner.version(
                        level.getRecipeManager()), level);
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
            if (input != null
                    && input.what() instanceof AEItemKey item
                    && item.toStack(1).is(ArsNouveauRegistries
                    .CATALYST_IDENTIFIER_ITEM.get())) {
                return true;
            }
        }
        return false;
    }

    /** Explicit but invalid recipe metadata must not fall back to a stock material-key pattern. */
    public static boolean containsPatternRecipeMarker(AEItemKey definition) {
        if (definition == null) return false;
        EncodedProcessingPattern encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return false;
        for (GenericStack input : encoded.sparseInputs()) {
            if (input != null && input.what() instanceof AEItemKey item
                    && ArsNouveauRecipeBridge.hasPatternRecipeMarker(item.toStack(1))) return true;
        }
        return false;
    }

    /** Only an exact, unambiguous zero-pedestal input/output shape claims an ordinary AE pattern. */
    private static ResourceLocation resolveCatalystFreeRecipe(Level level,
                                                               List<GenericStack> inputs, List<GenericStack> outputs) {
        ResourceLocation found = null;
        for (var holder : ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            if (!holder.value().getPedestalItems().isEmpty() || !matchesInputAndOutput(level, holder, inputs, outputs)) continue;
            if (found != null && !found.equals(holder.id())) return null;
            found = holder.id();
        }
        return found;
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

    public boolean requiresCatalystIdentifier() {
        return requiresCatalystIdentifier;
    }

    /**
     * MekE routes KeyCounter arrays directly and does not call the external
     * inventory callback below. Sign an owned copy at that boundary, keeping
     * the crafting CPU's original material keys and counts completely intact.
     */
    public KeyCounter[] copySignedCatalystFreeInputs(KeyCounter[] inputs, Level level) {
        if (requiresCatalystIdentifier || inputs == null || inputs.length == 0 || !matchesCurrentRecipe(level)) return null;
        IInput[] planned = delegate.getInputs();
        if (planned.length != 1) return null;
        KeyCounter[] signed = new KeyCounter[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            KeyCounter source = inputs[index];
            if (source == null || source.isEmpty() || source.size() != 1) return null;
            KeyCounter target = new KeyCounter();
            for (var entry : source) {
                long amount = entry.getLongValue();
                if (amount <= 0 || !(entry.getKey() instanceof AEItemKey key)
                        || !planned[0].isValid(key, level)) return null;
                ItemStack marked = ArsNouveauRecipeBridge.markPatternInput(key.toStack(1), recipeId, semanticSignature);
                AEItemKey markedKey = AEItemKey.of(marked);
                if (markedKey == null) return null;
                target.add(markedKey, amount);
            }
            signed[index] = target;
        }
        return signed;
    }

    /** Rejects queued/decoded jobs whose recipe semantics changed on reload. */
    public boolean matchesCurrentRecipe(Level level) {
        return level != null
                && catalogVersion == ArsNouveauRecipeScanner.version(
                level.getRecipeManager())
                && semanticSignature.equals(ArsNouveauRecipeScanner
                .semanticSignature(level.getRecipeManager(), recipeId))
                && requiresCatalystIdentifier == ArsNouveauRecipeBridge.requiresCatalystIdentifier(level, recipeId)
                && (identifier.isEmpty() ? !requiresCatalystIdentifier
                : ArsNouveauRecipeBridge.identifierMatchesRecipe(level, identifier, recipeId));
    }

    private static boolean matchesCurrentRecipe(
            Level level, ResourceLocation recipeId, ItemStack identifier,
            List<GenericStack> inputs, List<GenericStack> outputs) {
        var holder = ArsNouveauRecipeScanner.scan(
                        level.getRecipeManager()).stream()
                .filter(candidate -> candidate.id().equals(recipeId))
                .findFirst().orElse(null);
        if (holder == null || (identifier.isEmpty() ? !holder.value().getPedestalItems().isEmpty()
                : !ArsNouveauRecipeBridge.identifierMatchesRecipe(level, identifier, recipeId))) {
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
        ItemStack actualInput = singleItem(inputs);
        boolean inputMatches = !actualInput.isEmpty()
                && actualInput.getCount() == 1
                && holder.value().getInput().test(actualInput);
        AEItemKey expectedOutput = AEItemKey.of(
                holder.value().getResultItem(level.registryAccess()));
        GenericStack actualOutput = singleStack(outputs);
        ItemStack expectedStack = holder.value().getResultItem(
                level.registryAccess());
        boolean outputMatches = expectedOutput != null
                && actualOutput != null
                && expectedOutput.equals(actualOutput.what())
                && actualOutput.amount() == expectedStack.getCount();
        return inputMatches && outputMatches;
    }

    private static ItemStack singleItem(List<GenericStack> stacks) {
        GenericStack stack = singleStack(stacks);
        return stack == null ? ItemStack.EMPTY : itemStack(stack);
    }

    private static GenericStack singleStack(List<GenericStack> stacks) {
        GenericStack found = null;
        for (GenericStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            if (stack.amount() <= 0L || found != null) {
                return null;
            }
            found = stack;
        }
        return found;
    }

    private static ItemStack itemStack(GenericStack stack) {
        if (stack == null || stack.amount() <= 0L
                || stack.amount() > Integer.MAX_VALUE
                || !(stack.what() instanceof AEItemKey item)) {
            return ItemStack.EMPTY;
        }
        return item.toStack((int) stack.amount());
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
        Level level = levelReference.get();
        return level != null && matchesCurrentRecipe(level)
                && delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(
            KeyCounter[] inputHolder, PatternInputSink inputSink) {
        if (!matchesCurrentRecipe(levelReference.get())) {
            return;
        }
        delegate.pushInputsToExternalInventory(inputHolder,
                (key, amount) -> {
                    if (key instanceof AEItemKey itemKey) {
                        ItemStack marked = ArsNouveauRecipeBridge
                                .markPatternInput(
                                        itemKey.toStack(1), recipeId, semanticSignature);
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
        if (requiresCatalystIdentifier) tooltip.addProperty(
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
