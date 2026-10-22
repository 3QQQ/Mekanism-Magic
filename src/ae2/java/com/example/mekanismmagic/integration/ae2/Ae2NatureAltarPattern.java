package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraRecipeScanner;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Processing pattern with a non-consumable Nature altar recipe context.
 *
 * <p>The context item remains in the encoded definition for display and
 * identity, but is removed from AE's planning inputs. Immediately before a
 * normal Pattern Provider inserts the real reagent, the recipe id is copied
 * onto that reagent. The machine consumes the reagent normally and uses the
 * transient marker only to select the exact current recipe.</p>
 */
public final class Ae2NatureAltarPattern implements IPatternDetails {
    private final AEItemKey definition;
    private final AEProcessingPattern delegate;
    private final ResourceLocation recipeId;
    private final ItemStack context;
    private final GenericStack expectedOutput;
    private final WeakReference<Level> levelReference;

    private Ae2NatureAltarPattern(
            AEItemKey definition, AEProcessingPattern delegate,
            ResourceLocation recipeId, ItemStack context,
            GenericStack expectedOutput, Level level) {
        this.definition = definition;
        this.delegate = delegate;
        this.recipeId = recipeId;
        this.context = context;
        this.expectedOutput = expectedOutput;
        this.levelReference = new WeakReference<>(level);
    }

    public static Ae2NatureAltarPattern tryDecode(
            AEItemKey definition, Level level) {
        if (definition == null || level == null) {
            return null;
        }
        EncodedProcessingPattern encoded = definition.get(
                AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) {
            return null;
        }

        ItemStack context = ItemStack.EMPTY;
        ResourceLocation recipeId = null;
        List<GenericStack> filteredInputs = new ArrayList<>(
                encoded.sparseInputs().size());
        int realInputCount = 0;
        GenericStack realInput = null;
        for (GenericStack input : encoded.sparseInputs()) {
            ItemStack stack = itemStack(input);
            if (NatureAuraRecipeBridge.isPatternContext(stack)) {
                if (!context.isEmpty() || input == null
                        || input.amount() != 1) {
                    return null;
                }
                context = stack.copyWithCount(1);
                recipeId = NatureAuraRecipeBridge.patternRecipe(context);
                filteredInputs.add(null);
            } else {
                filteredInputs.add(input);
                if (input != null && input.amount() > 0) {
                    realInputCount++;
                    realInput = input;
                }
            }
        }
        if (context.isEmpty() || recipeId == null
                || realInputCount != 1 || realInput == null
                || realInput.amount() != 1) {
            return null;
        }

        GenericStack expectedOutput = singleOutput(
                encoded.sparseOutputs());
        if (expectedOutput == null
                || !matchesCurrentRecipe(level, recipeId, context,
                realInput, expectedOutput)) {
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
        try {
            return new Ae2NatureAltarPattern(definition,
                    new AEProcessingPattern(filteredKey), recipeId,
                    context, expectedOutput, level);
        } catch (IllegalArgumentException invalidPattern) {
            return null;
        }
    }

    /**
     * Prevents an invalid context pattern from falling back to AE's ordinary
     * processing decoder, which would request and consume the virtual item.
     */
    public static boolean containsNatureAltarContext(
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
                    && item.is(NaturesAuraRegistries
                    .NATURE_ALTAR_CONTEXT_ITEM.get())) {
                return true;
            }
        }
        return false;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    /** Collects one complete (possibly multiplied) reagent batch. */
    public ItemStack collectInput(KeyCounter[] inputItems, Level level) {
        if (level == null || inputItems == null || inputItems.length == 0
                || !isCurrent(level)) {
            return ItemStack.EMPTY;
        }
        AEItemKey selected = null;
        long total = 0;
        try {
            for (KeyCounter counter : inputItems) {
                if (counter == null || counter.size() != 1) {
                    return ItemStack.EMPTY;
                }
                var iterator = counter.iterator();
                if (!iterator.hasNext()) {
                    return ItemStack.EMPTY;
                }
                var entry = iterator.next();
                if (iterator.hasNext()
                        || !(entry.getKey() instanceof AEItemKey itemKey)
                        || entry.getLongValue() <= 0
                        || !acceptsCurrentInput(level, itemKey)) {
                    return ItemStack.EMPTY;
                }
                if (selected != null && !selected.equals(itemKey)) {
                    return ItemStack.EMPTY;
                }
                selected = itemKey;
                total = Math.addExact(total, entry.getLongValue());
                if (total > Integer.MAX_VALUE) {
                    return ItemStack.EMPTY;
                }
            }
        } catch (ArithmeticException overflow) {
            return ItemStack.EMPTY;
        }
        return selected == null || total <= 0
                ? ItemStack.EMPTY : selected.toStack((int) total);
    }

    private boolean acceptsCurrentInput(Level level, AEItemKey input) {
        return NatureAuraRecipeScanner.find(
                        level.getRecipeManager(), recipeId)
                .map(recipe -> recipe.input().test(input.toStack(1)))
                .orElse(false);
    }

    private boolean isCurrent(Level level) {
        return NatureAuraRecipeBridge.patternContextMatchesRecipe(
                level, context, recipeId)
                && NatureAuraRecipeScanner.find(
                        level.getRecipeManager(), recipeId)
                .map(recipe -> {
                    AEItemKey currentOutput = AEItemKey.of(recipe.output());
                    return currentOutput != null
                            && currentOutput.equals(expectedOutput.what())
                            && recipe.output().getCount()
                            == expectedOutput.amount();
                }).orElse(false);
    }

    private static boolean matchesCurrentRecipe(
            Level level, ResourceLocation recipeId, ItemStack context,
            GenericStack realInput, GenericStack expectedOutput) {
        if (!(realInput.what() instanceof AEItemKey inputKey)
                || !(expectedOutput.what() instanceof AEItemKey outputKey)
                || !NatureAuraRecipeBridge.patternContextMatchesRecipe(
                level, context, recipeId)) {
            return false;
        }
        return NatureAuraRecipeScanner.find(
                        level.getRecipeManager(), recipeId)
                .map(recipe -> {
                    AEItemKey currentOutput = AEItemKey.of(recipe.output());
                    return recipe.input().test(inputKey.toStack(1))
                            && currentOutput != null
                            && currentOutput.equals(outputKey)
                            && recipe.output().getCount()
                            == expectedOutput.amount();
                }).orElse(false);
    }

    private static GenericStack singleOutput(List<GenericStack> outputs) {
        GenericStack result = null;
        for (GenericStack output : outputs) {
            if (output == null || output.amount() <= 0) {
                continue;
            }
            if (result != null || !(output.what() instanceof AEItemKey)) {
                return null;
            }
            result = output;
        }
        return result;
    }

    private static ItemStack itemStack(GenericStack stack) {
        if (stack == null || !(stack.what() instanceof AEItemKey item)
                || stack.amount() <= 0) {
            return ItemStack.EMPTY;
        }
        return item.toStack((int) Math.min(Integer.MAX_VALUE,
                stack.amount()));
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
        return level != null && isCurrent(level)
                && delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(
            KeyCounter[] inputHolder, PatternInputSink inputSink) {
        Level level = levelReference.get();
        if (level == null || !isCurrent(level)
                || collectInput(inputHolder, level).isEmpty()) {
            return;
        }
        delegate.pushInputsToExternalInventory(inputHolder,
                (key, amount) -> {
                    if (key instanceof AEItemKey itemKey
                            && acceptsCurrentInput(level, itemKey)) {
                        ItemStack marked = NatureAuraRecipeBridge
                                .markPatternInput(
                                        itemKey.toStack(1), context);
                        AEItemKey markedKey = AEItemKey.of(marked);
                        if (markedKey != null) {
                            inputSink.pushInput(markedKey, amount);
                            return;
                        }
                    }
                    // Decoding and the preflight above make this unreachable;
                    // keeping it as a no-op is safer than leaking an unmarked
                    // reagent or throwing from a server tick.
                });
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flag) {
        PatternDetailsTooltip tooltip = delegate.getTooltip(level, flag);
        tooltip.addProperty(Component.translatable(
                        "ae2.mekanism_magic.virtual_catalyst"),
                context.getHoverName());
        return tooltip;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Ae2NatureAltarPattern pattern
                && definition.equals(pattern.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }
}
