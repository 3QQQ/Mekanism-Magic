package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ApplyModifierRecipeView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ClibanoRecipeView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.CookingTimes;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.EnhancerView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.EssenceRequirements;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.IngredientView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.MagicCircleView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ModifierDefinitionView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ResidueChanceView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ResidueView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualIngredient;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.Snapshot;
import com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoFireType;
import com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoMainBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.item.crafting.ApplyModifierRecipe;
import com.stal111.forbidden_arcanus.common.item.crafting.ClibanoRecipe;
import com.stal111.forbidden_arcanus.common.item.crafting.ClibanoRecipeInput;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerDefinition;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerHelper;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerTarget;
import com.stal111.forbidden_arcanus.common.item.enhancer.effect.MultiplySoulDurationEffect;
import com.stal111.forbidden_arcanus.core.init.ModRecipeTypes;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The sole runtime ABI boundary for Forbidden & Arcanus 2.6.1 / Valhelsia Core 1.1.5.
 * Load only after the integration's exact-version gate. Reading and assembling here
 * never consumes inputs, creates essence storage, executes rituals, or rolls residues.
 */
public final class ForbiddenArcanus261Bridge {
    private ForbiddenArcanus261Bridge() {
    }

    /** Client-capable read boundary. Do not route it through the server-only weak-key cache. */
    static ForbiddenRecipeDisplayCatalog.DisplaySnapshot readDisplay(RecipeManager manager, RegistryAccess registries) {
        List<ItemStack> candidates = BuiltInRegistries.ITEM.stream()
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .map(item -> item.getDefaultInstance().copyWithCount(1)).filter(stack -> !stack.isEmpty()).toList();
        Map<FireType, List<ItemStack>> fireMedia = new EnumMap<>(FireType.class);
        fireMedia.put(FireType.FIRE, List.of()); // Ordinary machine fire uses FE, not a fuel item per operation.
        for (FireType fire : List.of(FireType.SOUL_FIRE, FireType.ENCHANTED_FIRE)) {
            fireMedia.put(fire, candidates.stream().filter(stack -> fireType(stack.copy()) == fire).toList());
        }
        Map<ResourceLocation, List<ItemStack>> enhancerExamples = new LinkedHashMap<>();
        for (ItemStack candidate : candidates) {
            enhancerId(registries, candidate.copy()).ifPresent(id ->
                    enhancerExamples.computeIfAbsent(id, ignored -> new ArrayList<>()).add(candidate));
        }
        Map<ResourceLocation, ResidueView> residues = new LinkedHashMap<>();
        registries.registryOrThrow(FARegistries.RESIDUE_TYPE).holders().forEach(holder -> {
            var combine = holder.value().combineInfo();
            residues.put(holder.key().location(), new ResidueView(holder.key().location(), combine.requiredAmount(), combine.result()));
        });
        List<ForbiddenRecipeDisplayCatalog.ClibanoDisplay> clibano = new ArrayList<>();
        manager.getAllRecipesFor(ModRecipeTypes.CLIBANO_COMBUSTION.get()).stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString())).forEach(holder -> {
                    var view = clibanoView(holder, registries);
                    List<ItemStack> enhancer = view.requiredEnhancer().map(id -> enhancerExamples.getOrDefault(id, List.of())).orElse(List.of());
                    Optional<ResidueView> residue = view.residue().map(chance -> residues.get(chance.type()));
                    clibano.add(new ForbiddenRecipeDisplayCatalog.ClibanoDisplay(view, enhancer, residue, fireMedia,
                            ForbiddenRecipeDisplayCatalog.signature(registries, view, enhancer, residue, fireMedia)));
                });
        return new ForbiddenRecipeDisplayCatalog.DisplaySnapshot(clibano, List.of(),
                HephaestusForge261Bridge.readDisplayRituals(registries, candidates, enhancerExamples));
    }

    /** A pure owned preview for an arbitrary component-bearing target; never a machine commit. */
    public static Optional<ItemStack> previewApplyModifier(RecipeManager manager, RegistryAccess registries,
                                                           ResourceLocation id, ItemStack base) {
        var holder = manager.byKey(id);
        return holder.isPresent() && holder.get().value() instanceof ApplyModifierRecipe recipe
                ? previewApplyModifier(recipe, registries, base) : Optional.empty();
    }

    private static Optional<ItemStack> previewApplyModifier(ApplyModifierRecipe recipe, RegistryAccess registries, ItemStack base) {
        if (base.isEmpty()) return Optional.empty();
        ItemStack single = base.copyWithCount(1);
        if (!recipe.isBaseIngredient(single)) return Optional.empty();
        Optional<ItemStack> template = Arrays.stream(recipe.template().getItems())
                .filter(stack -> !stack.isEmpty() && recipe.isTemplateIngredient(stack.copy())).findFirst();
        Optional<ItemStack> addition = Arrays.stream(recipe.addition().getItems())
                .filter(stack -> !stack.isEmpty() && recipe.isAdditionIngredient(stack.copy())).findFirst();
        if (template.isEmpty() || addition.isEmpty()) return Optional.empty();
        // Native matches is exactly these three role predicates, and never uses its Level argument.
        ItemStack output = recipe.assemble(new SmithingRecipeInput(template.get().copyWithCount(1),
                single, addition.get().copyWithCount(1)), registries);
        return output.isEmpty() || output.getCount() != 1 ? Optional.empty() : Optional.of(output.copy());
    }

    /** Client-capable, non-committing validation for a JEI processing-pattern preview. */
    public static Optional<ItemStack> previewClibano(RecipeManager manager, RegistryAccess registries, Level level,
                                                      ResourceLocation id, ItemStack first, ItemStack second,
                                                      FireType fire, ItemStack enhancer) {
        var holder = manager.byKey(id);
        if (holder.isEmpty() || !(holder.get().value() instanceof ClibanoRecipe recipe)
                || first.isEmpty() || nativeFire(fire).ordinal() < recipe.requiredFireType().ordinal()) {
            return Optional.empty();
        }
        ClibanoRecipeInput input = new ClibanoRecipeInput(first.copy(), second.copy());
        if (!recipe.matches(input, level, enhancers(registries, enhancer))) return Optional.empty();
        ItemStack result = recipe.assemble(input, registries);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.copy());
    }

    private static ClibanoRecipeView clibanoView(RecipeHolder<ClibanoRecipe> holder, RegistryAccess registries) {
        ClibanoRecipe recipe = holder.value();
        var times = recipe.cookingTimes();
        return new ClibanoRecipeView(holder.id(), recipe.isDoubleRecipe(),
                recipe.getIngredients().stream().map(ForbiddenArcanus261Bridge::ingredient).toList(),
                new CookingTimes(times.get(ClibanoFireType.FIRE), times.get(ClibanoFireType.SOUL_FIRE),
                        times.get(ClibanoFireType.ENCHANTED_FIRE)), fireType(recipe.requiredFireType()),
                recipe.requiredEnhancer().map(ForbiddenArcanus261Bridge::referenceId),
                recipe.residueChance().map(chance -> new ResidueChanceView(referenceId(chance.type()), chance.chance())),
                recipe.experience(), recipe.getResultItem(registries));
    }

    static LoadedCatalog read(RecipeManager manager, RegistryAccess registries, long revision) {
        Map<ResourceLocation, ClibanoRecipeView> clibanoViews = new LinkedHashMap<>();
        Map<ResourceLocation, RecipeHolder<ClibanoRecipe>> clibanoRecipes = new LinkedHashMap<>();
        for (var holder : manager.getAllRecipesFor(ModRecipeTypes.CLIBANO_COMBUSTION.get())) {
            clibanoViews.put(holder.id(), clibanoView(holder, registries));
            clibanoRecipes.put(holder.id(), holder);
        }

        Map<ResourceLocation, ApplyModifierRecipeView> modifierViews = new LinkedHashMap<>();
        Map<ResourceLocation, RecipeHolder<ApplyModifierRecipe>> modifierRecipes = new LinkedHashMap<>();
        for (var holder : manager.getAllRecipesFor(RecipeType.SMITHING)) {
            if (holder.value() instanceof ApplyModifierRecipe recipe) {
                modifierViews.put(holder.id(), new ApplyModifierRecipeView(holder.id(),
                        ingredient(recipe.template()), ingredient(recipe.addition()),
                        referenceId(recipe.modifier())));
                modifierRecipes.put(holder.id(), new RecipeHolder<>(holder.id(), recipe));
            }
        }

        Map<ResourceLocation, RitualView> rituals = new LinkedHashMap<>();
        Map<ResourceLocation, Holder<Ritual>> nativeRituals = new LinkedHashMap<>();
        var ritualRegistry = registries.registryOrThrow(FARegistries.RITUAL);
        ritualRegistry.holders().forEach(holder -> {
            var ritual = holder.value();
            var requirements = ritual.requirements();
            var essences = requirements.essences();
            ResourceLocation id = holder.key().location();
            nativeRituals.put(id, holder);
            rituals.put(id, new RitualView(id, ingredient(ritual.mainIngredient()),
                    ritual.inputs().stream().map(input -> new RitualIngredient(
                            ingredient(input.ingredient()), input.amount())).toList(),
                    new EssenceRequirements(essences.aureal(), essences.souls(), essences.blood(),
                            essences.experience()), requirements.tier().tier(),
                    requirements.tier().matchExact(), requirements.enhancers().stream()
                    .map(ForbiddenArcanus261Bridge::referenceId)
                    .sorted(java.util.Comparator.comparing(ResourceLocation::toString)).toList(),
                    referenceId(ritual.magicCircleType()), ritual.duration(),
                    FARegistries.RITUAL_RESULT_TYPE_REGISTRY.getKey(ritual.result().getType())));
        });

        Map<ResourceLocation, EnhancerView> enhancers = new LinkedHashMap<>();
        registries.registryOrThrow(FARegistries.ENHANCER_DEFINITION).holders().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            enhancers.put(id, new EnhancerView(id, referenceId(holder.value().displayItem()),
                    soulDuration(holder.value())));
        });
        Map<ResourceLocation, ModifierDefinitionView> modifierDefinitions = new LinkedHashMap<>();
        registries.registryOrThrow(FARegistries.ITEM_MODIFIER).keySet().forEach(id ->
                modifierDefinitions.put(id, new ModifierDefinitionView(id)));
        Map<ResourceLocation, ResidueView> residues = new LinkedHashMap<>();
        registries.registryOrThrow(FARegistries.RESIDUE_TYPE).holders().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            var combine = holder.value().combineInfo();
            residues.put(id, new ResidueView(id, combine.requiredAmount(), combine.result()));
        });
        Map<ResourceLocation, MagicCircleView> circles = new LinkedHashMap<>();
        registries.registryOrThrow(FARegistries.MAGIC_CIRCLE).holders().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            circles.put(id, new MagicCircleView(id, holder.value().innerTexture(),
                    holder.value().outerTexture()));
        });
        return new LoadedCatalog(new Snapshot(revision, clibanoViews, modifierViews, rituals,
                enhancers, modifierDefinitions, residues, circles),
                new NativeRecipes(clibanoRecipes, modifierRecipes),
                new HephaestusForge261Bridge.NativeRituals(nativeRituals, rituals));
    }

    /** Resolve the native data-pack tags; the medium is not consumed. */
    public static FireType fireType(ItemStack medium) {
        return fireType(ClibanoFireType.fromItem(medium));
    }

    public static boolean isFireSufficient(FireType available, FireType required) {
        return nativeFire(available).ordinal() >= nativeFire(required).ordinal();
    }

    /** The native ENHANCER component determines identity, not the item's display name or ID. */
    public static Optional<ResourceLocation> enhancerId(RegistryAccess registries, ItemStack stack) {
        return EnhancerHelper.getEnhancerHolder(registries, stack)
                .map(ForbiddenArcanus261Bridge::referenceId);
    }

    public static boolean isEnhancerItem(RegistryAccess registries, ItemStack stack) {
        return !stack.isEmpty() && EnhancerHelper.getEnhancerHolder(registries, stack).isPresent();
    }

    public static int soulDuration(RegistryAccess registries, ItemStack enhancer) {
        return EnhancerHelper.getEnhancer(registries, enhancer)
                .map(ForbiddenArcanus261Bridge::soulDuration)
                .orElse(ClibanoMainBlockEntity.SOUL_DURATION);
    }

    private static int soulDuration(EnhancerDefinition enhancer) {
        int duration = ClibanoMainBlockEntity.SOUL_DURATION;
        for (var effect : enhancer.getEffects(EnhancerTarget.CLIBANO).toList()) {
            if (effect instanceof MultiplySoulDurationEffect multiplier) {
                duration = multiplier.getModifiedValue(duration);
            }
        }
        return duration;
    }

    static IngredientView ingredient(Ingredient ingredient) {
        return new IngredientView(Arrays.asList(ingredient.getItems()));
    }

    static ResourceLocation referenceId(Holder<?> holder) {
        return holder.unwrapKey().orElseThrow(() -> new IllegalArgumentException(
                "Forbidden & Arcanus catalog requires a registered definition: " + holder)).location();
    }

    private static FireType fireType(ClibanoFireType fire) {
        return switch (fire) {
            case FIRE -> FireType.FIRE;
            case SOUL_FIRE -> FireType.SOUL_FIRE;
            case ENCHANTED_FIRE -> FireType.ENCHANTED_FIRE;
        };
    }

    private static ClibanoFireType nativeFire(FireType fire) {
        return switch (fire) {
            case FIRE -> ClibanoFireType.FIRE;
            case SOUL_FIRE -> ClibanoFireType.SOUL_FIRE;
            case ENCHANTED_FIRE -> ClibanoFireType.ENCHANTED_FIRE;
        };
    }

    private static HolderSet<EnhancerDefinition> enhancers(RegistryAccess registries, ItemStack stack) {
        return EnhancerHelper.getEnhancerHolder(registries, stack)
                .<HolderSet<EnhancerDefinition>>map(holder -> HolderSet.direct(holder))
                .orElseGet(HolderSet::empty);
    }

    record LoadedCatalog(Snapshot snapshot, NativeRecipes recipes,
                         HephaestusForge261Bridge.NativeRituals forgeRituals) {
    }

    /** No manager, level, server, or RegistryAccess may be retained here. */
    static final class NativeRecipes {
        private final Map<ResourceLocation, RecipeHolder<ClibanoRecipe>> clibano;
        private final Map<ResourceLocation, RecipeHolder<ApplyModifierRecipe>> modifiers;
        private final List<Ingredient> clibanoIngredients;

        private NativeRecipes(Map<ResourceLocation, RecipeHolder<ClibanoRecipe>> clibano,
                              Map<ResourceLocation, RecipeHolder<ApplyModifierRecipe>> modifiers) {
            this.clibano = Map.copyOf(clibano);
            this.modifiers = Map.copyOf(modifiers);
            this.clibanoIngredients = this.clibano.values().stream()
                    .flatMap(holder -> holder.value().getIngredients().stream()).toList();
        }

        boolean isClibanoIngredient(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            ItemStack candidate = stack.copy();
            for (Ingredient ingredient : clibanoIngredients) {
                if (ingredient.test(candidate)) {
                    return true;
                }
            }
            return false;
        }

        boolean matchesClibano(ResourceLocation id, ServerLevel level, ItemStack first,
                               ItemStack second, FireType fire, ItemStack enhancer) {
            var holder = clibano.get(id);
            if (holder == null || nativeFire(fire).ordinal() < holder.value().requiredFireType().ordinal()) {
                return false;
            }
            // 2.6.1 RecipeInput#getItem/size are stubs. Its native matches uses getInputs instead.
            return holder.value().matches(new ClibanoRecipeInput(first.copy(), second.copy()), level,
                    enhancers(level.registryAccess(), enhancer));
        }

        Optional<ItemStack> assembleClibano(ResourceLocation id, ServerLevel level, ItemStack first,
                                             ItemStack second, FireType fire, ItemStack enhancer) {
            if (!matchesClibano(id, level, first, second, fire, enhancer)) {
                return Optional.empty();
            }
            return Optional.of(clibano.get(id).value().assemble(
                    new ClibanoRecipeInput(first.copy(), second.copy()), level.registryAccess()).copy());
        }

        boolean matchesApplyModifier(ResourceLocation id, ServerLevel level, ItemStack template,
                                     ItemStack base, ItemStack addition) {
            var holder = modifiers.get(id);
            return holder != null && holder.value().matches(
                    new SmithingRecipeInput(template.copy(), base.copy(), addition.copy()), level);
        }

        boolean isApplyModifierIngredient(ItemStack stack) {
            if (stack.isEmpty()) return false;
            ItemStack single = stack.copyWithCount(1);
            for (var holder : modifiers.values()) {
                var recipe = holder.value();
                if (recipe.isTemplateIngredient(single) || recipe.isAdditionIngredient(single)
                        || recipe.isBaseIngredient(single)) return true;
            }
            return false;
        }

        Optional<ItemStack> assembleApplyModifier(ResourceLocation id, ServerLevel level,
                                                   ItemStack template, ItemStack base,
                                                   ItemStack addition) {
            if (!matchesApplyModifier(id, level, template, base, addition)) {
                return Optional.empty();
            }
            return Optional.of(modifiers.get(id).value().assemble(
                    new SmithingRecipeInput(template.copy(), base.copy(), addition.copy()),
                    level.registryAccess()).copy());
        }
    }
}
