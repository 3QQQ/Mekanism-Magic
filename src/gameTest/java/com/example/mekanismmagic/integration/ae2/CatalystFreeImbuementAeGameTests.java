package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.pattern.AEProcessingPattern;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierSelectionHost;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Real AE decode/planning/dispatch paths, including legacy virtual catalyst definitions. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CatalystFreeImbuementAeGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final ResourceLocation FREE_ID = id("ae_catalyst_free_fixture");
    private static final ResourceLocation CATALYST_ID = id("ae_catalyst_collision_fixture");

    private CatalystFreeImbuementAeGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void currentJeiEncodingOmitsFreeIdentifiersAndRealDecoderPlansOnlyTheReagent(GameTestHelper helper) {
        int freeRecipes = 0, catalystRecipes = 0;
        for (var display : ArsNouveauRecipeBridge.identifierImbuementJeiRecipes(helper.getLevel())) {
            List<List<GenericStack>> inputs = Ae2ImbuementPatternEncoding.encodingInputs(display);
            helper.assertTrue(!inputs.isEmpty(), "An actual imbuement JEI entry had no encodable input");
            ItemStack definition = PatternDetailsHelper.encodeProcessingPattern(inputs.stream().map(List::getFirst).toList(),
                    List.of(GenericStack.fromItemStack(display.output())));
            IPatternDetails decoded = PatternDetailsHelper.decodePattern(definition, helper.getLevel());
            helper.assertTrue(decoded instanceof Ae2IdentifierImbuementPattern, "JEI imbuement did not resolve through its exact decoder");
            var pattern = (Ae2IdentifierImbuementPattern) decoded;
            helper.assertTrue(pattern.requiresCatalystIdentifier() == !display.identifier().isEmpty()
                            && inputs.size() == (pattern.requiresCatalystIdentifier() ? 2 : 1),
                    "JEI encoding and native pedestal requirement disagree");
            helper.assertTrue(pattern.getInputs().length == 1, "A catalyst identifier leaked into AE material planning");
            for (GenericStack possible : pattern.getInputs()[0].getPossibleInputs()) {
                helper.assertTrue(possible.what() instanceof AEItemKey key
                                && !key.toStack().is(ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get())
                                && !ArsNouveauRecipeBridge.hasPatternRecipeMarker(key.toStack()),
                        "AE planned a virtual identifier or a transiently marked inventory key");
            }
            if (pattern.requiresCatalystIdentifier()) catalystRecipes++;
            else freeRecipes++;
        }
        helper.assertTrue(freeRecipes > 0 && catalystRecipes > 0, "Regression did not cover both free and pedestal-backed native recipes");
        helper.setBlock(POS, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity processor = helper.getBlockEntity(POS);
        var advertised = Ae2ImbuementProvider.forTile(processor).getAvailablePatterns();
        helper.assertTrue(advertised.stream().filter(pattern -> pattern instanceof Ae2ImbuementPattern nativePattern
                        && !nativePattern.requiresCatalystIdentifier() && nativePattern.getDefinition() != null).count() == freeRecipes,
                "Removing the JEI catalyst display also removed the native AE provider's free recipe definitions");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void oldVirtualAndIdOnlyDefinitionsNormalizePlanningAndCarrySignedDispatchContext(GameTestHelper helper) {
        var recipe = freeRecipe(helper.getLevel());
        ItemStack reagent = ArsNouveauRecipeBridge.representativeChoice(recipe.value().getInput());
        CustomData.update(DataComponents.CUSTOM_DATA, reagent, tag -> tag.putString("unrelated_payload", "kept"));
        ItemStack output = recipe.value().getResultItem(helper.getLevel().registryAccess());
        ItemStack identifier = ArsNouveauRecipeBridge.createPatternIdentifierForRecipe(helper.getLevel(), recipe.id());
        String signature = ArsNouveauRecipeScanner.semanticSignature(helper.getLevel().getRecipeManager(), recipe.id());
        for (ItemStack encoded : List.of(
                encode(List.of(reagent, identifier), output),
                encode(List.of(ArsNouveauRecipeBridge.markPatternInput(reagent, recipe.id())), output),
                encode(List.of(ArsNouveauRecipeBridge.markPatternInput(reagent, recipe.id(), signature)), output))) {
            var pattern = exact(helper, encoded);
            helper.assertTrue(!pattern.requiresCatalystIdentifier() && pattern.recipeId().equals(recipe.id())
                            && pattern.getInputs().length == 1, "A legacy free recipe still requires virtual catalyst selection");
            GenericStack planned = pattern.getInputs()[0].getPossibleInputs()[0];
            helper.assertTrue(planned.what().equals(AEItemKey.of(reagent)) && planned.amount() == 1,
                    "Normalizing an old marker changed real input components or left a marker in inventory planning");
            List<GenericStack> dispatched = new ArrayList<>();
            pattern.pushInputsToExternalInventory(counters(reagent, 1), (key, amount) -> dispatched.add(new GenericStack(key, amount)));
            helper.assertTrue(dispatched.size() == 1 && dispatched.getFirst().amount() == 1
                            && dispatched.getFirst().what() instanceof AEItemKey, "Legacy pattern did not dispatch exactly one physical reagent");
            ItemStack marked = ((AEItemKey) dispatched.getFirst().what()).toStack();
            helper.assertTrue(recipe.id().equals(ArsNouveauRecipeBridge.patternRecipe(marked))
                            && signature.equals(ArsNouveauRecipeBridge.patternRecipeSignature(marked))
                            && ItemStack.isSameItemSameComponents(reagent, ArsNouveauRecipeBridge.recipeInputView(marked)),
                    "Dispatch omitted its exact recipe receipt or overwrote unrelated input components");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void invalidExplicitContextsFailClosedWithoutHijackingUnrelatedProcessingPatterns(GameTestHelper helper) {
        var recipe = freeRecipe(helper.getLevel());
        ItemStack reagent = ArsNouveauRecipeBridge.representativeChoice(recipe.value().getInput());
        ItemStack output = recipe.value().getResultItem(helper.getLevel().registryAccess());
        List<ItemStack> invalid = new ArrayList<>();
        invalid.add(encode(List.of(ArsNouveauRecipeBridge.markPatternInput(reagent, id("missing_imbuement"))), output));
        invalid.add(encode(List.of(ArsNouveauRecipeBridge.markPatternInput(reagent, recipe.id(), "stale signature")), output));
        invalid.add(encode(List.of(ArsNouveauRecipeBridge.markPatternInput(reagent, recipe.id(), "")), output));
        ItemStack badType = ArsNouveauRecipeBridge.markPatternInput(reagent, recipe.id());
        CustomData.update(DataComponents.CUSTOM_DATA, badType, tag -> tag.putInt("pattern_recipe_signature", 4));
        invalid.add(encode(List.of(badType), output));
        invalid.add(encode(List.of(ArsNouveauRecipeBridge.markPatternInput(reagent, recipe.id())), new ItemStack(Items.DIRT)));
        ItemStack legacy = ArsNouveauRecipeBridge.createPatternIdentifierForRecipe(helper.getLevel(), recipe.id());
        invalid.add(encode(List.of(reagent.copyWithCount(2), legacy), output));
        invalid.add(encode(List.of(reagent, legacy.copyWithCount(2)), output));
        for (ItemStack definition : invalid) helper.assertTrue(PatternDetailsHelper.decodePattern(definition, helper.getLevel()) == null,
                "An invalid explicit imbuement context fell back to a normal material-key processing pattern");
        ItemStack unrelated = encode(List.of(new ItemStack(Items.COBBLESTONE)), new ItemStack(Items.STONE));
        helper.assertTrue(PatternDetailsHelper.decodePattern(unrelated, helper.getLevel()) instanceof AEProcessingPattern,
                "The imbuement decoder hijacked an unrelated vanilla AE processing pattern");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void queuedDecodedPatternsRejectSourceCostAndOutputChanges(GameTestHelper helper) {
        var nativeRecipe = freeRecipe(helper.getLevel());
        ItemStack reagent = ArsNouveauRecipeBridge.representativeChoice(nativeRecipe.value().getInput());
        ItemStack output = nativeRecipe.value().getResultItem(helper.getLevel().registryAccess());
        var pattern = exact(helper, encode(List.of(reagent), output));
        withRecipes(helper, List.of(new RecipeHolder<>(nativeRecipe.id(), new ImbuementRecipe(nativeRecipe.value().getInput(),
                output.copy(), nativeRecipe.value().getSource() + 13, List.of()))), () -> {
            helper.assertTrue(!pattern.matchesCurrentRecipe(helper.getLevel()) && !pattern.supportsPushInputsToExternalInventory(),
                    "A queued decoded recipe retained the old cheaper Source cost");
            List<GenericStack> sent = new ArrayList<>();
            KeyCounter[] offered = counters(reagent, 1);
            pattern.pushInputsToExternalInventory(offered, (key, amount) -> sent.add(new GenericStack(key, amount)));
            helper.assertTrue(sent.isEmpty() && offered[0].get(AEItemKey.of(reagent)) == 1,
                    "A stale decoded job dispatched or consumed a caller's offered reagent");
        });
        var refreshed = exact(helper, encode(List.of(reagent), output));
        withRecipes(helper, List.of(new RecipeHolder<>(nativeRecipe.id(), new ImbuementRecipe(nativeRecipe.value().getInput(),
                new ItemStack(Items.DIRT), nativeRecipe.value().getSource(), List.of()))), () -> {
            helper.assertTrue(!refreshed.matchesCurrentRecipe(helper.getLevel()), "A queued pattern survived a changed output definition");
        });
        String oldSignature = ArsNouveauRecipeScanner.semanticSignature(helper.getLevel().getRecipeManager(), nativeRecipe.id());
        var oldCountPattern = exact(helper, encode(List.of(reagent), output));
        ItemStack largerOutput = output.copyWithCount(output.getCount() + 1);
        withRecipes(helper, List.of(new RecipeHolder<>(nativeRecipe.id(), new ImbuementRecipe(nativeRecipe.value().getInput(),
                largerOutput, nativeRecipe.value().getSource(), List.of()))), () -> {
            helper.assertTrue(!oldSignature.equals(ArsNouveauRecipeScanner.semanticSignature(helper.getLevel().getRecipeManager(), nativeRecipe.id()))
                            && !oldCountPattern.matchesCurrentRecipe(helper.getLevel()),
                    "Changing only output count retained the old semantic receipt");
            ItemStack oldReceipt = ArsNouveauRecipeBridge.markPatternInput(reagent, nativeRecipe.id(), oldSignature);
            helper.assertTrue(PatternDetailsHelper.decodePattern(encode(List.of(oldReceipt), largerOutput), helper.getLevel()) == null,
                    "A persisted old receipt was accepted even after its output quantity changed");
        });
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeProviderDispatchesItsAdvertisedFreeRecipeWhenOneInputHasDifferentOutputs(GameTestHelper helper) {
        helper.setBlock(POS, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity processor = helper.getBlockEntity(POS);
        ResourceLocation alternativeId = id("aa_free_alternative_fixture");
        var target = new RecipeHolder<>(FREE_ID, new ImbuementRecipe(Ingredient.of(Items.AMETHYST_SHARD),
                new ItemStack(Items.DIAMOND), 100, List.of()));
        var alternative = new RecipeHolder<>(alternativeId, new ImbuementRecipe(Ingredient.of(Items.AMETHYST_SHARD),
                new ItemStack(Items.EMERALD), 100, List.of()));
        withRecipes(helper, List.of(target, alternative), () -> {
            var provider = Ae2ImbuementProvider.forTile(processor);
            IPatternDetails advertised = provider.getAvailablePatterns().stream()
                    .filter(pattern -> pattern instanceof Ae2ImbuementPattern nativePattern && !nativePattern.requiresCatalystIdentifier()
                            && FREE_ID.equals(ArsNouveauRecipeBridge.patternRecipe(pattern.getDefinition().toStack()))
                            && pattern.getOutputs().size() == 1 && pattern.getOutputs().getFirst().what().equals(AEItemKey.of(Items.DIAMOND)))
                    .findFirst().orElseThrow();
            helper.assertTrue(provider.pushPattern(advertised, counters(new ItemStack(Items.AMETHYST_SHARD), 1)),
                    "Native provider rejected its own advertised catalyst-free recipe");
            ItemStack inserted = processor.getNativeInputSlot().getStack();
            helper.assertTrue(FREE_ID.equals(ArsNouveauRecipeBridge.patternRecipe(inserted))
                            && ArsNouveauRecipeScanner.semanticSignature(helper.getLevel().getRecipeManager(), FREE_ID)
                            .equals(ArsNouveauRecipeBridge.patternRecipeSignature(inserted)),
                    "Native provider dispatched raw reagent without its selected recipe/signature");
            processor.setSource(100);
            processor.getNativeEnergyContainer().setEnergy(processor.getNativeEnergyContainer().getMaxEnergy());
            for (int tick = 0; tick < 105; tick++) TileEntityMekanism.tickServer(helper.getLevel(), processor.getBlockPos(), processor.getBlockState(), processor);
            helper.assertTrue(processor.getNativeOutputSlot().getStack().is(Items.DIAMOND)
                            && processor.getNativeOutputSlot().getCount() == 1 && processor.getNativeInputSlot().isEmpty(),
                    "Native provider's reagent was stolen by another free recipe with the same input");
        });
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void plainFreePatternDispatchesOnNativeFactoryWithoutChangingItsOtherCatalyst(GameTestHelper helper) {
        dispatchScenario(helper, false, false);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void oldVirtualFreePatternDispatchesOnNativeFactoryWithoutChangingItsOtherCatalyst(GameTestHelper helper) {
        dispatchScenario(helper, false, true);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void plainFreePatternDispatchesOnExtrasFactoryWithoutChangingItsOtherCatalyst(GameTestHelper helper) {
        dispatchScenario(helper, true, false);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void oldVirtualFreePatternDispatchesOnExtrasFactoryWithoutChangingItsOtherCatalyst(GameTestHelper helper) {
        dispatchScenario(helper, true, true);
    }

    private static void dispatchScenario(GameTestHelper helper, boolean extra, boolean legacy) {
        helper.setBlock(POS, extra ? MekanismExtrasImbuementFactories.ABSOLUTE_BLOCK.get()
                : ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get());
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        TileEntityMekanism tile = helper.getBlockEntity(POS);
        var automation = (IMekanismMagicAutomation) tile;
        var host = (CatalystIdentifierSelectionHost) tile;
        AbstractMeAeSupport<?> support = install(helper, tile);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(support.getMainNode().isActive(), "MekE network is not ready"))
                .thenExecute(() -> withRecipes(helper, collisionRecipes(), () -> {
                    helper.assertTrue(host.selectCatalystIdentifierForRecipe(CATALYST_ID), "Could not select the colliding pedestal recipe");
                    ItemStack selection = host.selectedCatalystIdentifier().copy();
                    ItemStack reagent = new ItemStack(Items.AMETHYST_SHARD);
                    ItemStack encoded = encode(legacy ? List.of(reagent,
                            ArsNouveauRecipeBridge.createPatternIdentifierForRecipe(helper.getLevel(), FREE_ID)) : List.of(reagent),
                            new ItemStack(Items.DIAMOND));
                    var pattern = exact(helper, encoded);
                    helper.assertTrue(!pattern.requiresCatalystIdentifier() && pattern.recipeId().equals(FREE_ID), "Fixture did not choose the free exact recipe");
                    support.getPatternSlots().getFirst().setStack(encoded);
                    support.updatePatterns();
                    KeyCounter[] wrongOffer = counters(new ItemStack(Items.DIRT), 1);
                    helper.assertTrue(!support.pushPatternWithAdapter(pattern, wrongOffer)
                                    && wrongOffer[0].size() == 1 && wrongOffer[0].get(AEItemKey.of(Items.DIRT)) == 1
                                    && automation.mekanismMagicPatternInputs().stream().allMatch(slot -> slot.isEmpty())
                                    && ItemStack.matches(selection, host.selectedCatalystIdentifier()),
                            "Signing a free dispatch accepted the wrong material or mutated a rejected caller");
                    // Rejection must leave the existing selection untouched.
                    automation.mekanismMagicPatternInputs().forEach(slot -> slot.setStack(new ItemStack(Items.COBBLESTONE, 64)));
                    KeyCounter[] rejectedOffer = counters(reagent, 1);
                    helper.assertTrue(!support.pushPatternWithAdapter(pattern, rejectedOffer)
                                    && ItemStack.matches(selection, host.selectedCatalystIdentifier()),
                            "Failed free-recipe dispatch changed the host's shared selection");
                    helper.assertTrue(rejectedOffer.length == 1 && rejectedOffer[0].size() == 1
                                    && rejectedOffer[0].get(AEItemKey.of(reagent)) == 1,
                            "Failed dispatch mutated the CPU's original raw material key/count");
                    automation.mekanismMagicPatternInputs().forEach(slot -> slot.setStack(ItemStack.EMPTY));
                    KeyCounter[] acceptedOffer = counters(reagent, 1);
                    helper.assertTrue(support.pushPatternWithAdapter(pattern, acceptedOffer), "A valid free recipe was rejected by virtual-catalyst preflight");
                    helper.assertTrue(acceptedOffer.length == 1 && acceptedOffer[0].size() == 1
                                    && acceptedOffer[0].get(AEItemKey.of(reagent)) == 1,
                            "Successful dispatch rewrote or drained the CPU's original raw material key/count");
                    helper.assertTrue(ItemStack.matches(selection, host.selectedCatalystIdentifier()), "Free dispatch changed or cleared a retained catalyst");
                    ItemStack inserted = automation.mekanismMagicPatternInputs().stream().map(slot -> slot.getStack())
                            .filter(stack -> !stack.isEmpty()).findFirst().orElseThrow();
                    helper.assertTrue(FREE_ID.equals(ArsNouveauRecipeBridge.patternRecipe(inserted))
                                    && ArsNouveauRecipeBridge.hasPatternRecipeSignature(inserted), "MekE discarded the free recipe's signed dispatch context");
                    if (tile instanceof ImbuementFactoryBlockEntity factory) factory.setSource(100);
                    else ((ExtraImbuementFactoryBlockEntity) tile).setSource(100);
                    automation.mekanismMagicEnergyContainer().setEnergy(automation.mekanismMagicEnergyContainer().getMaxEnergy());
                    for (int tick = 0; tick < 105; tick++) TileEntityMekanism.tickServer(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
                    long diamonds = automation.mekanismMagicPatternOutputs().stream().map(slot -> slot.getStack())
                            .filter(stack -> stack.is(Items.DIAMOND)).mapToLong(ItemStack::getCount).sum();
                    helper.assertTrue(diamonds == 1 && automation.mekanismMagicPatternOutputs().stream().noneMatch(slot -> slot.getStack().is(Items.EMERALD))
                                    && automation.mekanismMagicPatternInputs().stream().allMatch(slot -> slot.isEmpty())
                                    && ItemStack.matches(selection, host.selectedCatalystIdentifier()),
                            "The host's preselected catalyst stole the free reagent or changed its expected output");
                })).thenSucceed();
    }

    private static List<RecipeHolder<ImbuementRecipe>> collisionRecipes() {
        return List.of(new RecipeHolder<>(FREE_ID, new ImbuementRecipe(Ingredient.of(Items.AMETHYST_SHARD), new ItemStack(Items.DIAMOND), 100, List.of())),
                new RecipeHolder<>(CATALYST_ID, new ImbuementRecipe(Ingredient.of(Items.AMETHYST_SHARD), new ItemStack(Items.EMERALD), 100,
                        List.of(Ingredient.of(Items.GOLD_INGOT)))));
    }

    private static AbstractMeAeSupport<?> install(GameTestHelper helper, TileEntityMekanism tile) {
        helper.assertTrue(tile instanceof MeUpgradeRecipeMachineAdapter, "MekE host mixin is absent");
        var adapter = (MeUpgradeRecipeMachineAdapter) tile;
        helper.assertTrue(adapter.isMeUpgradeTarget() && adapter.getMeUpgradeContainer().install(MeUpgradeType.PATTERN_PROVIDER).successful(),
                "MekE pattern provider could not be installed on the imbuement factory");
        adapter.onMeUpgradeStateChanged();
        adapter.createMeNodeIfActive();
        return adapter.getOrCreateMeUpgradeRuntime().support();
    }

    private static RecipeHolder<ImbuementRecipe> freeRecipe(Level level) {
        return ArsNouveauRecipeScanner.scan(level.getRecipeManager()).stream()
                .filter(holder -> holder.value().getPedestalItems().isEmpty()
                        && holder.value().getInput().test(new ItemStack(Items.AMETHYST_SHARD)))
                .findFirst().orElseThrow();
    }

    private static ItemStack encode(List<ItemStack> inputs, ItemStack output) {
        return PatternDetailsHelper.encodeProcessingPattern(inputs.stream().map(GenericStack::fromItemStack).toList(),
                List.of(GenericStack.fromItemStack(output)));
    }

    private static Ae2IdentifierImbuementPattern exact(GameTestHelper helper, ItemStack definition) {
        IPatternDetails decoded = PatternDetailsHelper.decodePattern(definition, helper.getLevel());
        helper.assertTrue(decoded instanceof Ae2IdentifierImbuementPattern, "Expected an exact imbuement processing pattern");
        return (Ae2IdentifierImbuementPattern) decoded;
    }

    private static KeyCounter[] counters(ItemStack item, long amount) {
        KeyCounter counter = new KeyCounter(); counter.add(AEItemKey.of(item), amount); return new KeyCounter[]{counter};
    }

    private static void withRecipes(GameTestHelper helper, List<RecipeHolder<ImbuementRecipe>> replacements, Runnable action) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        try {
            List<RecipeHolder<?>> changed = new ArrayList<>(original);
            changed.removeIf(holder -> replacements.stream().anyMatch(replacement -> replacement.id().equals(holder.id())));
            changed.addAll(replacements);
            manager.replaceRecipes(changed);
            ArsNouveauRecipeScanner.refresh(manager);
            action.run();
        } finally {
            manager.replaceRecipes(original);
            ArsNouveauRecipeScanner.refresh(manager);
        }
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, path); }
}
