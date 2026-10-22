package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.DisplaySnapshot;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoFireType;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.CreateItemResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.TransmuteInputResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.UpgradeTierResult;
import com.stal111.forbidden_arcanus.common.item.crafting.ApplyModifierRecipe;
import com.stal111.forbidden_arcanus.common.item.crafting.ClibanoRecipe;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerDefinition;
import com.stal111.forbidden_arcanus.common.item.modifier.ItemModifier;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import io.netty.buffer.Unpooled;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Pure viewer data and actual native network-codec boundaries, without a client or a paid job. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForbiddenRecipeDisplayCatalogGameTests {
    private ForbiddenRecipeDisplayCatalogGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void emptyDisplayIngredientCannotAssembleAFalseModifierExampleOrPublishServerState(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        ResourceLocation id = id("smithing/apply_eternal_modifier");
        ApplyModifierRecipe original = (ApplyModifierRecipe) level.getRecipeManager().byKey(id).orElseThrow().value();
        // An empty resolved tag has no representative template. Keep its absence
        // instead of indexing getItems()[0] or applying a modifier to a guessed input.
        ApplyModifierRecipe empty = new ApplyModifierRecipe(Ingredient.EMPTY, original.addition(), original.modifier());
        RecipeManager isolated = new RecipeManager(registries);
        isolated.replaceRecipes(List.of(new RecipeHolder<>(id, empty)));
        var serverSnapshot = ForbiddenArcanusRecipeCatalog.snapshot(level.getServer());
        long revision = ForbiddenArcanusRecipeCatalog.revision();
        ItemStack target = new ItemStack(Items.DIAMOND_SWORD);
        target.set(DataComponents.CUSTOM_NAME, Component.literal("Must remain unmodified"));
        ItemStack before = target.copy();
        var display = ForbiddenRecipeDisplayCatalog.read(isolated, registries);
        helper.assertTrue(display.modifiers().isEmpty()
                        && ForbiddenArcanus261Bridge.previewApplyModifier(isolated, registries, id, target).isEmpty()
                        && ItemStack.matches(before, target)
                        && ForbiddenArcanusRecipeCatalog.snapshot(level.getServer()) == serverSnapshot
                        && ForbiddenArcanusRecipeCatalog.revision() == revision,
                "An empty display ingredient assembled an invented result, changed an input, or published server cache state");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void detachedDisplayHasNativeCountsPairsAndImmutableStableSignatures(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var registries = level.registryAccess();
        var serverSnapshot = ForbiddenArcanusRecipeCatalog.snapshot(level.getServer());
        long revision = ForbiddenArcanusRecipeCatalog.revision();
        var display = ForbiddenRecipeDisplayCatalog.read(manager, registries);
        assertCounts(helper, display);
        helper.assertTrue(display.clibano().stream().map(row -> row.recipe().id().toString()).toList()
                        .equals(display.clibano().stream().map(row -> row.recipe().id().toString()).sorted().toList())
                        && display.modifiers().stream().map(row -> row.recipe().id().toString()).toList()
                        .equals(display.modifiers().stream().map(row -> row.recipe().id().toString()).sorted().toList())
                        && display.rituals().stream().map(row -> row.recipe().id().toString()).toList()
                        .equals(display.rituals().stream().map(row -> row.recipe().id().toString()).sorted().toList()),
                "Display recipes are not deterministically ordered by original ID");
        for (var row : display.clibano()) {
            ClibanoRecipe nativeRecipe = (ClibanoRecipe) manager.byKey(row.recipe().id()).orElseThrow().value();
            helper.assertTrue(row.recipe().cookingTimes().fire() == nativeRecipe.cookingTimes().get(ClibanoFireType.FIRE)
                            && row.recipe().cookingTimes().soulFire() == nativeRecipe.cookingTimes().get(ClibanoFireType.SOUL_FIRE)
                            && row.recipe().cookingTimes().enchantedFire() == nativeRecipe.cookingTimes().get(ClibanoFireType.ENCHANTED_FIRE)
                            && !row.recipe().canProduceResidue(FireType.FIRE)
                            && ItemStack.matches(row.recipe().output(), nativeRecipe.getResultItem(registries)),
                    "Display changed native fire timing, output or ordinary-fire residue semantics");
            row.fireMedia().forEach((fire, examples) -> examples.forEach(stack -> helper.assertTrue(
                    ForbiddenArcanus261Bridge.fireType(stack) == fire, "Display guessed a fire medium from its item ID")));
            for (ItemStack enhancer : row.enhancerExamples()) {
                helper.assertTrue(ForbiddenArcanus261Bridge.enhancerId(registries, enhancer).equals(row.recipe().requiredEnhancer()),
                        "Display guessed enhancer identity instead of reading its native component");
            }
            row.residue().ifPresent(residue -> {
                var nativeResidue = registries.registryOrThrow(FARegistries.RESIDUE_TYPE).get(residue.id());
                helper.assertTrue(residue.requiredAmount() == nativeResidue.combineInfo().requiredAmount()
                                && ItemStack.matches(residue.output(), nativeResidue.combineInfo().result()),
                        "Combined residue was presented as a single guaranteed recipe drop");
            });
        }
        assertRitualExamples(helper, display, registries);
        var second = ForbiddenRecipeDisplayCatalog.read(manager, registries);
        helper.assertTrue(signatures(display).equals(signatures(second))
                        && ForbiddenArcanusRecipeCatalog.snapshot(level.getServer()) == serverSnapshot
                        && ForbiddenArcanusRecipeCatalog.revision() == revision,
                "Repeated display reads changed semantic identities or the server catalog lifecycle");
        assertUnmodifiable(helper, () -> display.clibano().clear());
        assertUnmodifiable(helper, () -> display.modifiers().clear());
        assertUnmodifiable(helper, () -> display.clibano().getFirst().fireMedia().clear());
        assertUnmodifiable(helper, () -> display.rituals().getFirst().supportedTiers().clear());
        var example = display.rituals().stream().flatMap(row -> row.examples().stream())
                .findFirst().orElseThrow();
        ItemStack expectedOutput = example.output();
        example.output().set(DataComponents.CUSTOM_NAME, Component.literal("Not shared with display"));
        example.input().setCount(37);
        helper.assertTrue(ItemStack.matches(expectedOutput, example.output()) && example.input().getCount() == 1,
                "A viewer changed a retained example through its ItemStack accessor");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void previewsPreserveOwnedComponentsAndKeepTierChangesItemFree(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var manager = level.getRecipeManager();
        ResourceLocation eternal = id("smithing/apply_eternal_modifier");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD, 3);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Owned input components"));
        sword.setDamageValue(17);
        ItemStack original = sword.copy();
        ApplyModifierRecipe nativeRecipe = (ApplyModifierRecipe) manager.byKey(eternal).orElseThrow().value();
        ItemStack expected = nativeRecipe.assemble(new SmithingRecipeInput(nativeRecipe.template().getItems()[0].copy(),
                sword.copyWithCount(1), nativeRecipe.addition().getItems()[0].copy()), registries);
        ItemStack preview = ForbiddenArcanus261Bridge.previewApplyModifier(manager, registries, eternal, sword).orElseThrow();
        helper.assertTrue(ItemStack.matches(expected, preview) && ItemStack.matches(original, sword)
                        && !preview.has(DataComponents.DAMAGE) && !preview.has(DataComponents.MAX_DAMAGE)
                        && ForbiddenArcanus261Bridge.previewApplyModifier(manager, registries, eternal, preview).isEmpty(),
                "Preview lost native Eternal removals, mutated the base, or allowed reapplying a modifier");
        var display = ForbiddenRecipeDisplayCatalog.read(manager, registries);
        for (var row : display.rituals()) {
            Ritual ritual = registries.registryOrThrow(FARegistries.RITUAL).get(row.recipe().id());
            ItemStack main = row.recipe().mainIngredient().examples().getFirst().copyWithCount(4);
            main.set(DataComponents.CUSTOM_NAME, Component.literal("Main patch " + row.recipe().id()));
            ItemStack beforeMain = main.copy();
            int tier = row.supportedTiers().getFirst();
            if (row.upgradeTier() > 0) {
                helper.assertTrue(HephaestusForge261Bridge.previewRitual(
                                registries, row.recipe().id(), main, tier).isEmpty()
                                && HephaestusForge261Bridge.previewUpgradeTier(
                                registries, row.recipe().id(), tier).orElse(-1)
                                == row.upgradeTier()
                                && row.examples().stream().allMatch(pair -> pair.output().isEmpty()),
                        "Tier-change display invented a forge-core item output");
            } else {
                ItemStack actual = HephaestusForge261Bridge.previewRitual(
                        registries, row.recipe().id(), main, tier).orElseThrow();
                helper.assertTrue(ItemStack.matches(actual, ritual.result().getResultItem(main.copyWithCount(1))),
                        "Create/transmute preview replaced native component-patch output with a target default");
            }
            helper.assertTrue(ItemStack.matches(beforeMain, main),
                    "A display preview mutated the real main input");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void remoteNetworkRegistriesKeepModifierRulesAndOmitUnsyncedRitualContextSafely(GameTestHelper helper) {
        var server = helper.getLevel().registryAccess();
        Registry<EnhancerDefinition> enhancers = networkRegistry(server, server, FARegistries.ENHANCER_DEFINITION,
                EnhancerDefinition.NETWORK_CODEC);
        RegistryAccess withEnhancers = replace(server, enhancers);
        Registry<ItemModifier> modifiers = networkRegistry(server, withEnhancers, FARegistries.ITEM_MODIFIER,
                ItemModifier.DIRECT_CODEC); // The native registration deliberately uses DIRECT_CODEC for the network too.
        RegistryAccess withModifiers = replace(withEnhancers, modifiers);
        Registry<Ritual> rituals = networkRegistry(server, withModifiers, FARegistries.RITUAL, Ritual.NETWORK_CODEC);
        RegistryAccess remote = replace(withModifiers, rituals);
        helper.assertTrue(rituals.stream().allMatch(ritual -> ritual.magicCircleType() == null)
                        && enhancers.stream().allMatch(enhancer -> enhancer.effects().isEmpty())
                        && server.registryOrThrow(FARegistries.ENHANCER_DEFINITION).stream()
                        .anyMatch(enhancer -> !enhancer.effects().isEmpty()),
                "The remote fixture did not actually remove fields omitted by native NETWORK_CODEC");
        List<RecipeHolder<?>> networkRecipes = new ArrayList<>();
        for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ApplyModifierRecipe) && !(holder.value() instanceof ClibanoRecipe)) continue;
            var bytes = Unpooled.buffer();
            try {
                RecipeHolder.STREAM_CODEC.encode(new RegistryFriendlyByteBuf(bytes, server), holder);
                networkRecipes.add(RecipeHolder.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(bytes, remote)));
            } finally {
                bytes.release();
            }
        }
        RecipeManager remoteManager = new RecipeManager(remote);
        remoteManager.replaceRecipes(networkRecipes);
        var display = ForbiddenRecipeDisplayCatalog.read(remoteManager, remote);
        assertCounts(helper, display);
        assertRitualExamples(helper, display, remote);
        helper.assertTrue(display.rituals().stream().allMatch(row -> row.magicCircle().isEmpty()),
                "Remote display invented a magic circle absent from the native network payload");
        var local = ForbiddenRecipeDisplayCatalog.read(helper.getLevel().getRecipeManager(), server);
        helper.assertTrue(display.modifiers().stream().map(row -> row.signature()).toList()
                        .equals(local.modifiers().stream().map(row -> row.signature()).toList())
                        && display.clibano().stream().map(row -> row.signature()).toList()
                        .equals(local.clibano().stream().map(row -> row.signature()).toList()),
                "Remote recipe/modifier payloads no longer produce the same legal component-bearing examples");
        for (var row : display.rituals()) {
            var original = server.registryOrThrow(FARegistries.RITUAL).get(row.recipe().id());
            var raw = original.requirements().essences();
            helper.assertTrue(row.recipe().duration() == original.duration()
                            && row.recipe().essences().equals(new ForbiddenArcanusRecipeCatalog.EssenceRequirements(
                            raw.aureal(), raw.souls(), raw.blood(), raw.experience())),
                    "Remote display guessed missing enhancer effects instead of retaining raw essence requirements");
        }
        helper.succeed();
    }

    private static void assertCounts(GameTestHelper helper, DisplaySnapshot display) {
        helper.assertTrue(display.clibano().size() == 15 && display.modifiers().isEmpty() && display.rituals().size() == 22
                        && display.rituals().stream().filter(row -> row.upgradeTier() > 0).count() == 4
                        && display.rituals().stream().filter(row -> row.recipe().resultType().equals(id("create_item"))).count() == 10
                        && display.rituals().stream().filter(row -> row.recipe().resultType().equals(id("transmute_input"))).count() == 8,
                "Display omitted a supported machine recipe or restored the canceled modifier page");
    }

    private static void assertRitualExamples(GameTestHelper helper, DisplaySnapshot display, RegistryAccess registries) {
        for (var row : display.rituals()) {
            Ritual nativeRitual = registries.registryOrThrow(FARegistries.RITUAL).get(row.recipe().id());
            helper.assertTrue(nativeRitual.result() instanceof CreateItemResult || nativeRitual.result() instanceof TransmuteInputResult
                            || nativeRitual.result() instanceof UpgradeTierResult,
                    "Display admitted an unaudited executable world result");
            helper.assertTrue(!row.examples().isEmpty()
                            && !row.supportedTiers().isEmpty(),
                    "Native display omitted a material or internal tier");
            for (int tier : row.supportedTiers()) {
                helper.assertTrue(nativeRitual.requirements().tier().test(tier)
                                && (row.upgradeTier() == 0 || row.upgradeTier() == tier + 1),
                        "Display offered a wrong internal tier or skipped upgrade");
            }
            helper.assertTrue(row.examples().stream().allMatch(pair ->
                            row.upgradeTier() > 0 == pair.output().isEmpty()),
                    "Upgrade/item-result display confused output roles");
            for (int index = 0; index < row.enhancers().size(); index++) {
                for (ItemStack enhancer : row.enhancers().get(index)) {
                    helper.assertTrue(ForbiddenArcanus261Bridge.enhancerId(registries, enhancer)
                                    .filter(row.recipe().enhancers().get(index)::equals).isPresent(),
                            "Ritual display enhancer examples do not have the required native component identity");
                }
            }
        }
    }

    private static <T> Registry<T> networkRegistry(RegistryAccess source, RegistryAccess destination,
                    net.minecraft.resources.ResourceKey<Registry<T>> key, Codec<T> networkCodec) {
        Registry<T> original = source.registryOrThrow(key);
        MappedRegistry<T> decoded = new MappedRegistry<>(key, Lifecycle.stable());
        var encodeOps = RegistryOps.create(JsonOps.INSTANCE, source);
        var decodeOps = RegistryOps.create(JsonOps.INSTANCE, destination);
        original.holders().forEach(holder -> {
            JsonElement wire = networkCodec.encodeStart(encodeOps, holder.value()).getOrThrow();
            T result = networkCodec.parse(decodeOps, wire).getOrThrow();
            decoded.register(original.getId(holder.value()), holder.key(), result, RegistrationInfo.BUILT_IN);
        });
        return decoded.freeze();
    }

    private static RegistryAccess replace(RegistryAccess base, Registry<?> replacement) {
        List<Registry<?>> values = new ArrayList<>();
        base.registries().filter(entry -> !entry.key().equals(replacement.key())).forEach(entry -> values.add(entry.value()));
        values.add(replacement);
        return new RegistryAccess.ImmutableRegistryAccess(values);
    }

    private static List<String> signatures(DisplaySnapshot display) {
        List<String> result = new ArrayList<>();
        display.clibano().forEach(row -> result.add(row.signature()));
        display.modifiers().forEach(row -> result.add(row.signature()));
        display.rituals().forEach(row -> result.add(row.signature()));
        return result;
    }

    private static void assertUnmodifiable(GameTestHelper helper, Runnable mutation) {
        boolean refused = false;
        try { mutation.run(); } catch (UnsupportedOperationException expected) { refused = true; }
        helper.assertTrue(refused, "Display collection was externally mutable");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path);
    }
}
