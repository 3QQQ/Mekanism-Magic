package com.example.mekanismmagic.integration.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanus261Bridge;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.ClibanoDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.RitualDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForge261Bridge;
import com.example.mekanismmagic.integration.forbiddenarcanus.client.ForbiddenArcanusJeiIntegration;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraRecipeScanner;
import com.example.mekanismmagic.integration.naturesaura.OfferingRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.client.NaturesAuraJeiIntegration;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Only this addon's machine-facing JEI categories are AE processing-pattern recipes. */
public abstract class Ae2AddonMachineJeiTransfer<R> implements IRecipeTransferHandler<PatternEncodingTermMenu, R> {
    private static final String MISSING = "jei.mekanism_magic.dynamic_recipe.exact_input_required";
    private static final String STALE = "jei.mekanism_magic.dynamic_recipe.stale_output";
    private static final String SMALL = "jei.mekanism_magic.dynamic_recipe.terminal_too_small";
    private static final String FORGE_UPGRADE = "jei.mekanism_magic.dynamic_recipe.forge_upgrade_not_ae_output";
    private final IRecipeTransferHandlerHelper helper;

    protected Ae2AddonMachineJeiTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    public static void registerNature(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new Offering(helper), NaturesAuraJeiIntegration.OFFERING_TYPE);
        registration.addRecipeTransferHandler(new Tree(helper), NaturesAuraJeiIntegration.TREE_RITUAL_TYPE);
        registration.addRecipeTransferHandler(new Animal(helper), NaturesAuraJeiIntegration.ANIMAL_SPAWNER_TYPE);
    }

    public static void registerForbidden(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new Clibano(helper), ForbiddenArcanusJeiIntegration.CLIBANO_TYPE);
        registration.addRecipeTransferHandler(new Forge(helper), ForbiddenArcanusJeiIntegration.HEPHAESTUS_FORGE_TYPE);
    }

    @Override public final Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override public final Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        // Also covers AE2's wireless and extended pattern terminals.
        return Optional.empty();
    }

    protected abstract Check check(R recipe, IRecipeSlotsView slots, Player player);

    @Override public final IRecipeTransferError transferRecipe(PatternEncodingTermMenu menu, R recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        Check checked;
        try {
            checked = check(recipe, slots, player);
        } catch (RuntimeException invalidRecipe) {
            return error(STALE);
        }
        if (checked.error() != null) return error(checked.error());
        Plan plan = checked.plan();
        if (plan.inputs().isEmpty() || plan.outputs().isEmpty()
                || plan.inputs().stream().anyMatch(ItemStack::isEmpty)
                || plan.outputs().stream().anyMatch(ItemStack::isEmpty)) return error(MISSING);
        for (int i = 0; i < plan.outputs().size(); i++) {
            ItemStack visible = slot(slots, plan.outputNames().get(i), RecipeIngredientRole.OUTPUT);
            if (visible.isEmpty()) return error(MISSING);
            if (!ItemStack.matches(visible, plan.outputs().get(i))) return error(STALE);
        }
        Map<AEItemKey, Long> grouped = new LinkedHashMap<>();
        for (ItemStack input : plan.inputs()) {
            AEItemKey key = AEItemKey.of(input);
            if (key == null || input.getCount() <= 0) return helper.createInternalError();
            grouped.merge(key, (long) input.getCount(), Math::addExact);
        }
        if (grouped.size() > menu.getProcessingInputSlots().length
                || plan.outputs().size() > menu.getProcessingOutputSlots().length) return error(SMALL);
        List<GenericStack> outputs = plan.outputs().stream().map(GenericStack::fromItemStack).toList();
        if (outputs.stream().anyMatch(output -> output == null)) return helper.createInternalError();
        if (doTransfer) {
            // Singleton alternatives prevent AE2 from substituting a same-item,
            // different-component input behind the exact visible output.
            EncodingHelper.encodeProcessingRecipe(menu, grouped.entrySet().stream()
                    .map(entry -> List.of(new GenericStack(entry.getKey(), entry.getValue())))
                    .toList(), outputs);
        }
        return null;
    }

    private IRecipeTransferError error(String key) {
        return helper.createUserErrorWithTooltip(Component.translatable(key));
    }

    private static ItemStack slot(IRecipeSlotsView slots, String name, RecipeIngredientRole role) {
        return slots.findSlotByName(name).filter(view -> view.getRole() == role)
                .flatMap(view -> view.getDisplayedItemStack()).map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    protected static ItemStack input(IRecipeSlotsView slots, String name) {
        return slot(slots, name, RecipeIngredientRole.INPUT);
    }

    protected static Check missing() { return new Check(MISSING, null); }
    protected static Check stale() { return new Check(STALE, null); }
    protected static Check forgeUpgrade() { return new Check(FORGE_UPGRADE, null); }
    protected static Check valid(List<ItemStack> inputs, List<ItemStack> outputs, String... outputNames) {
        return new Check(null, new Plan(List.copyOf(inputs), List.copyOf(outputs), List.of(outputNames)));
    }

    protected record Check(String error, Plan plan) { }
    protected record Plan(List<ItemStack> inputs, List<ItemStack> outputs, List<String> outputNames) { }

    private static final class Offering extends Ae2AddonMachineJeiTransfer<OfferingRecipeBridge.OfferingRecipeView> {
        private Offering(IRecipeTransferHandlerHelper helper) { super(helper); }
        @Override public RecipeType<OfferingRecipeBridge.OfferingRecipeView> getRecipeType() {
            return NaturesAuraJeiIntegration.OFFERING_TYPE;
        }
        @Override protected Check check(OfferingRecipeBridge.OfferingRecipeView recipe, IRecipeSlotsView slots, Player player) {
            ItemStack offered = input(slots, "offering_input");
            ItemStack starter = input(slots, "start_item");
            if (offered.isEmpty() || starter.isEmpty()) return missing();
            var current = NatureAuraRecipeScanner.findOffering(player.level().getRecipeManager(), recipe.id());
            if (current.isEmpty() || !current.get().signature().equals(recipe.signature())
                    || offered.getCount() != recipe.inputAmount() || starter.getCount() != 1
                    || !OfferingRecipeBridge.findRecipe(player.level(), offered, starter)
                    .map(found -> found.id().equals(recipe.id()) && found.signature().equals(recipe.signature()))
                    .orElse(false)) return stale();
            return valid(List.of(offered, starter), List.of(recipe.output()), "output");
        }
    }

    private static final class Tree extends Ae2AddonMachineJeiTransfer<TreeRitualRecipeBridge.TreeRitualRecipeView> {
        private Tree(IRecipeTransferHandlerHelper helper) { super(helper); }
        @Override public RecipeType<TreeRitualRecipeBridge.TreeRitualRecipeView> getRecipeType() {
            return NaturesAuraJeiIntegration.TREE_RITUAL_TYPE;
        }
        @Override protected Check check(TreeRitualRecipeBridge.TreeRitualRecipeView recipe, IRecipeSlotsView slots, Player player) {
            List<ItemStack> materials = new ArrayList<>();
            for (int i = 0; i < recipe.ingredients().size(); i++) {
                ItemStack material = input(slots, "material_" + i);
                if (material.isEmpty()) return missing();
                materials.add(material);
            }
            ItemStack sapling = input(slots, "sapling");
            ItemStack powder = input(slots, "gold_powder");
            if (sapling.isEmpty() || powder.isEmpty()) return missing();
            boolean current = TreeRitualRecipeBridge.scan(player.level().getRecipeManager()).stream()
                    .anyMatch(found -> found.id().equals(recipe.id()) && found.signature().equals(recipe.signature()));
            if (!current || materials.stream().anyMatch(stack -> stack.getCount() != 1)
                    || sapling.getCount() != 1 || powder.getCount() != TreeRitualRecipeBridge.GOLD_POWDER_PER_OPERATION
                    || !powder.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("naturesaura", "gold_powder")))
                    || recipe.matchMaterials(materials, sapling).isEmpty()) return stale();
            List<ItemStack> consumed = new ArrayList<>(materials);
            consumed.add(sapling);
            consumed.add(powder);
            return valid(consumed, List.of(recipe.output()), "output");
        }
    }

    private static final class Animal extends Ae2AddonMachineJeiTransfer<AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView> {
        private Animal(IRecipeTransferHandlerHelper helper) { super(helper); }
        @Override public RecipeType<AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView> getRecipeType() {
            return NaturesAuraJeiIntegration.ANIMAL_SPAWNER_TYPE;
        }
        @Override protected Check check(AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe, IRecipeSlotsView slots, Player player) {
            List<ItemStack> materials = new ArrayList<>();
            for (int i = 0; i < recipe.ingredients().size(); i++) {
                ItemStack material = input(slots, "material_" + i);
                if (material.isEmpty()) return missing();
                materials.add(material);
            }
            boolean current = AnimalSpawnerRecipeBridge.scan(player.level().getRecipeManager()).stream()
                    .anyMatch(found -> found.id().equals(recipe.id()) && found.signature().equals(recipe.signature()));
            if (!current || recipe.match(materials).isEmpty()) return stale();
            return valid(materials, List.of(recipe.outputEgg()), "spawn_egg");
        }
    }

    private static ForbiddenRecipeDisplayCatalog.DisplaySnapshot display(Player player) {
        return ForbiddenRecipeDisplayCatalog.read(player.level().getRecipeManager(), player.level().registryAccess());
    }

    private static final class Clibano extends Ae2AddonMachineJeiTransfer<ClibanoDisplay> {
        private Clibano(IRecipeTransferHandlerHelper helper) { super(helper); }
        @Override public RecipeType<ClibanoDisplay> getRecipeType() {
            return ForbiddenArcanusJeiIntegration.CLIBANO_TYPE;
        }
        @Override protected Check check(ClibanoDisplay recipe, IRecipeSlotsView slots, Player player) {
            ItemStack first = input(slots, "material_0");
            ItemStack second = recipe.recipe().doubleInput() ? input(slots, "material_1") : ItemStack.EMPTY;
            if (first.isEmpty() || recipe.recipe().doubleInput() && second.isEmpty()) return missing();
            var current = display(player).clibano().stream().filter(found -> found.recipe().id().equals(recipe.recipe().id()))
                    .findFirst();
            if (current.isEmpty() || !current.get().signature().equals(recipe.signature())
                    || first.getCount() != 1 || recipe.recipe().doubleInput() && second.getCount() != 1) return stale();
            ItemStack enhancer = current.get().enhancerExamples().stream().findFirst().orElse(ItemStack.EMPTY);
            var output = ForbiddenArcanus261Bridge.previewClibano(player.level().getRecipeManager(),
                    player.level().registryAccess(), player.level(), recipe.recipe().id(), first, second,
                    recipe.recipe().requiredFire(), enhancer);
            if (output.isEmpty()) return stale();
            return valid(recipe.recipe().doubleInput() ? List.of(first, second) : List.of(first),
                    List.of(output.get()), "result");
        }
    }

    private static final class Forge extends Ae2AddonMachineJeiTransfer<RitualDisplay> {
        private Forge(IRecipeTransferHandlerHelper helper) { super(helper); }
        @Override public RecipeType<RitualDisplay> getRecipeType() {
            return ForbiddenArcanusJeiIntegration.HEPHAESTUS_FORGE_TYPE;
        }
        @Override protected Check check(RitualDisplay recipe, IRecipeSlotsView slots, Player player) {
            if (recipe.upgradeTier() > 0) return forgeUpgrade();
            ItemStack main = input(slots, "main_material");
            if (main.isEmpty()) return missing();
            var current = display(player).rituals().stream().filter(found -> found.recipe().id().equals(recipe.recipe().id()))
                    .findFirst();
            if (current.isEmpty() || !current.get().signature().equals(recipe.signature()) || main.getCount() != 1) return stale();
            if (current.get().supportedTiers().isEmpty()) return stale();
            List<ItemStack> auxiliary = new ArrayList<>();
            for (int i = 0; i < recipe.recipe().inputs().size(); i++) {
                ItemStack material = input(slots, "auxiliary_" + i);
                if (material.isEmpty()) return missing();
                auxiliary.add(material);
            }
            if (!HephaestusForge261Bridge.matchesDisplayedAuxiliary(player.level().registryAccess(),
                    recipe.recipe().id(), auxiliary)) return stale();
            var output = HephaestusForge261Bridge.previewRitual(player.level().registryAccess(),
                    recipe.recipe().id(), main, current.get().supportedTiers().getFirst());
            if (output.isEmpty()) return stale();
            List<ItemStack> consumed = new ArrayList<>(auxiliary.size() + 1);
            consumed.add(main);
            consumed.addAll(auxiliary);
            return valid(consumed, List.of(output.get()), "result");
        }
    }

}
