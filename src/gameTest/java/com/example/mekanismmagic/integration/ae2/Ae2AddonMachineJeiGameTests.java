package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssencePreparationDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.ClibanoDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.RitualDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForge261Bridge;
import com.example.mekanismmagic.integration.forbiddenarcanus.client.ForbiddenArcanusJeiIntegration;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraRecipeScanner;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import com.example.mekanismmagic.integration.naturesaura.OfferingProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.OfferingRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.client.NaturesAuraJeiIntegration;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real AE menu + currently displayed JEI slots. Packet-sending doTransfer=true is client-only. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2AddonMachineJeiGameTests {
    private static final BlockPos TERMINAL = new BlockPos(1, 2, 1);
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    private Ae2AddonMachineJeiGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void everyAddonItemCategoryHasExplicitAeHandler(GameTestHelper helper) {
        var handlers = handlers();
        // Only pages with consumed item inputs and an item output encode AE
        // processing patterns. The four Condenser samples are retained and
        // its FE-to-essence guide must never become a consumptive item recipe.
        helper.assertTrue(handlers.size() == 6
                        && handlers.containsKey(NaturesAuraJeiIntegration.NATURE_ALTAR_RECIPE_TYPE)
                        && handlers.containsKey(NaturesAuraJeiIntegration.OFFERING_TYPE)
                        && handlers.containsKey(NaturesAuraJeiIntegration.TREE_RITUAL_TYPE)
                        && handlers.containsKey(NaturesAuraJeiIntegration.ANIMAL_SPAWNER_TYPE)
                        && handlers.containsKey(ForbiddenArcanusJeiIntegration.CLIBANO_TYPE)
                        && handlers.containsKey(ForbiddenArcanusJeiIntegration.HEPHAESTUS_FORGE_TYPE)
                        && EssencePreparationDisplay.recipes().size() == 4
                        && !handlers.containsKey(ForbiddenArcanusJeiIntegration.ESSENCE_CONDENSER_TYPE)
                        && !handlers.containsKey(NaturesAuraJeiIntegration.AURA_FUEL_TYPE),
                "Missing item-output JEI transfer, or retained-sample/FE guide encoded as an AE item pattern");
        for (var handler : handlers.values()) {
            helper.assertTrue(handler.getContainerClass() == PatternEncodingTermMenu.class
                            && handler.getMenuType().isEmpty(),
                    "Handler does not cover the wired/wireless/extended AE pattern terminals");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void offeringAndExistingSignedAltarPreview(GameTestHelper helper) {
        var terminal = Ae2PatternEncodingTestSupport.terminal(helper, TERMINAL);
        var handlers = handlers();
        var offering = handler(handlers, NaturesAuraJeiIntegration.OFFERING_TYPE);
        for (OfferingRecipeBridge.OfferingRecipeView recipe : NatureAuraRecipeScanner.scanOfferings(
                helper.getLevel().getRecipeManager())) {
            ItemStack material = first(recipe.input(), recipe.inputAmount());
            ItemStack starter = first(recipe.startItem(), 1);
            if (material.isEmpty() || starter.isEmpty()) continue;
            Map<String, ItemStack> stacks = new LinkedHashMap<>();
            stacks.put("offering_input", material);
            stacks.put("start_item", starter);
            stacks.put("optional_wither_roses", new ItemStack(Items.WITHER_ROSE, 4));
            stacks.put("output", recipe.output());
            var roles = Map.of("optional_wither_roses", RecipeIngredientRole.CATALYST,
                    "output", RecipeIngredientRole.OUTPUT);
            var view = Ae2PatternEncodingTestSupport.slots(stacks, roles);
            if (offering.transferRecipe(terminal.menu(), recipe, view, terminal.player(), false, false) != null) continue;
            stacks.remove("start_item");
            assertUserError(helper, offering.transferRecipe(terminal.menu(), recipe,
                    Ae2PatternEncodingTestSupport.slots(stacks, roles), terminal.player(), false, false),
                    "jei.mekanism_magic.dynamic_recipe.exact_input_required");
            stacks.put("start_item", starter);
            stacks.put("output", new ItemStack(Items.BARRIER));
            assertUserError(helper, offering.transferRecipe(terminal.menu(), recipe,
                    Ae2PatternEncodingTestSupport.slots(stacks, roles), terminal.player(), false, false),
                    "jei.mekanism_magic.dynamic_recipe.stale_output");
            helper.setBlock(MACHINE, NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK.get());
            OfferingProcessorBlockEntity machine = helper.getBlockEntity(MACHINE);
            ItemStack roses = new ItemStack(Items.WITHER_ROSE, 4);
            machine.getWitherRoseSlot().setStack(roses);
            helper.assertTrue(dispatch(helper, machine, List.of(material, starter), List.of(recipe.output())),
                    "Decoded Offering AE pattern did not dispatch its two consumed item roles");
            helper.assertTrue(ItemStack.matches(roses, machine.getWitherRoseSlot().getStack()),
                    "AE pattern consumed Offering's retained rose context");
            var altarRecipes = NatureAuraRecipeScanner.scan(helper.getLevel().getRecipeManager());
            helper.assertTrue(!altarRecipes.isEmpty(), "No real Nature Altar recipe for existing handler");
            var altar = handler(handlers, NaturesAuraJeiIntegration.NATURE_ALTAR_RECIPE_TYPE);
            helper.assertTrue(altar.transferRecipe(terminal.menu(), altarRecipes.getFirst(),
                    Ae2PatternEncodingTestSupport.slots(Map.of(), Map.of()), terminal.player(), false, false) == null,
                    "Existing signed Nature Altar handler did not accept a real recipe");
            helper.succeed();
            return;
        }
        helper.fail("No current Offering recipe passed the exact AE terminal preview");
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void treeAndAnimalUseRealInputsAndEggOutput(GameTestHelper helper) {
        var terminal = Ae2PatternEncodingTestSupport.terminal(helper, TERMINAL);
        var handlers = handlers();
        boolean treeChecked = false;
        for (TreeRitualRecipeBridge.TreeRitualRecipeView recipe : TreeRitualRecipeBridge.scan(
                helper.getLevel().getRecipeManager())) {
            Map<String, ItemStack> stacks = new LinkedHashMap<>();
            for (int i = 0; i < recipe.ingredients().size(); i++)
                stacks.put("material_" + i, first(recipe.ingredients().get(i), 1));
            stacks.put("sapling", first(recipe.sapling(), 1));
            stacks.put("gold_powder", new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("naturesaura", "gold_powder")), 16));
            stacks.put("output", recipe.output());
            if (stacks.values().stream().anyMatch(ItemStack::isEmpty)) continue;
            var transfer = handler(handlers, NaturesAuraJeiIntegration.TREE_RITUAL_TYPE);
            var result = transfer.transferRecipe(terminal.menu(), recipe,
                    Ae2PatternEncodingTestSupport.slots(stacks, Map.of("output", RecipeIngredientRole.OUTPUT)),
                    terminal.player(), false, false);
            long unique = stacks.entrySet().stream().filter(entry -> !entry.getKey().equals("output"))
                    .map(entry -> AEItemKey.of(entry.getValue())).distinct().count();
            if (unique > terminal.menu().getProcessingInputSlots().length) {
                assertUserError(helper, result, "jei.mekanism_magic.dynamic_recipe.terminal_too_small");
            } else {
                helper.assertTrue(result == null, "Current Tree ritual was not accepted by the AE terminal");
            }
            stacks.remove("gold_powder");
            assertUserError(helper, transfer.transferRecipe(terminal.menu(), recipe,
                    Ae2PatternEncodingTestSupport.slots(stacks, Map.of("output", RecipeIngredientRole.OUTPUT)),
                    terminal.player(), false, false), "jei.mekanism_magic.dynamic_recipe.exact_input_required");
            treeChecked = true;
            break;
        }
        helper.assertTrue(treeChecked, "No current Tree ritual could be checked");
        boolean animalChecked = false;
        for (AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView recipe : AnimalSpawnerRecipeBridge.scan(
                helper.getLevel().getRecipeManager())) {
            Map<String, ItemStack> stacks = new LinkedHashMap<>();
            for (int i = 0; i < recipe.ingredients().size(); i++)
                stacks.put("material_" + i, first(recipe.ingredients().get(i), recipe.inputAmounts().get(i)));
            stacks.put("spawn_egg", recipe.outputEgg());
            if (stacks.values().stream().anyMatch(ItemStack::isEmpty)) continue;
            var transfer = handler(handlers, NaturesAuraJeiIntegration.ANIMAL_SPAWNER_TYPE);
            var result = transfer.transferRecipe(terminal.menu(), recipe,
                    Ae2PatternEncodingTestSupport.slots(stacks,
                            Map.of("spawn_egg", RecipeIngredientRole.OUTPUT)),
                    terminal.player(), false, false);
            if (result != null) continue;
            helper.setBlock(MACHINE, NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK.get());
            var machine = helper.getBlockEntity(MACHINE);
            List<ItemStack> consumed = stacks.entrySet().stream().filter(entry -> !entry.getKey().equals("spawn_egg"))
                    .map(Map.Entry::getValue).toList();
            helper.assertTrue(dispatch(helper, machine, consumed, List.of(recipe.outputEgg())),
                    "Decoded Animal AE pattern did not accept the real spawn-egg output");
            stacks.put("spawn_egg", new ItemStack(Items.BARRIER));
            assertUserError(helper, transfer.transferRecipe(terminal.menu(), recipe,
                    Ae2PatternEncodingTestSupport.slots(stacks,
                            Map.of("spawn_egg", RecipeIngredientRole.OUTPUT)),
                    terminal.player(), false, false), "jei.mekanism_magic.dynamic_recipe.stale_output");
            animalChecked = true;
            break;
        }
        helper.assertTrue(animalChecked, "No real Animal recipe encoded its spawn egg output");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void clibanoForgeAndRetainedSamplesKeepApparatusOutOfPattern(GameTestHelper helper) {
        var terminal = Ae2PatternEncodingTestSupport.terminal(helper, TERMINAL);
        var handlers = handlers();
        var displays = ForbiddenRecipeDisplayCatalog.read(helper.getLevel().getRecipeManager(),
                helper.getLevel().registryAccess());
        boolean clibanoChecked = false;
        for (ClibanoDisplay display : displays.clibano()) {
            var recipe = display.recipe();
            if (recipe.ingredients().isEmpty() || recipe.ingredients().getFirst().examples().isEmpty()
                    || recipe.doubleInput() && (recipe.ingredients().size() < 2
                    || recipe.ingredients().get(1).examples().isEmpty())
                    || recipe.requiredEnhancer().isPresent() && display.enhancerExamples().isEmpty()) continue;
            Map<String, ItemStack> stacks = new LinkedHashMap<>();
            stacks.put("material_0", recipe.ingredients().getFirst().examples().getFirst().copyWithCount(1));
            if (recipe.doubleInput()) stacks.put("material_1", recipe.ingredients().get(1).examples().getFirst().copyWithCount(1));
            stacks.put("result", recipe.output());
            var transfer = handler(handlers, ForbiddenArcanusJeiIntegration.CLIBANO_TYPE);
            if (transfer.transferRecipe(terminal.menu(), display,
                    Ae2PatternEncodingTestSupport.slots(stacks, Map.of("result", RecipeIngredientRole.OUTPUT)),
                    terminal.player(), false, false) == null) {
                clibanoChecked = true;
                break;
            }
        }
        helper.assertTrue(clibanoChecked, "No real Clibano input/output passed native preview");
        boolean forgeChecked = false;
        for (RitualDisplay display : displays.rituals()) {
            if (display.upgradeTier() != 0 || display.examples().isEmpty()
                    || display.supportedTiers().isEmpty()) continue;
            ItemStack main = display.examples().getFirst().input();
            var output = HephaestusForge261Bridge.previewRitual(helper.getLevel().registryAccess(),
                    display.recipe().id(), main, display.supportedTiers().getFirst());
            if (output.isEmpty()) continue;
            Map<String, ItemStack> stacks = new LinkedHashMap<>();
            stacks.put("main_material", main);
            for (int i = 0; i < display.recipe().inputs().size(); i++) {
                var required = display.recipe().inputs().get(i);
                if (required.ingredient().examples().isEmpty()) break;
                stacks.put("auxiliary_" + i, required.ingredient().examples().getFirst().copyWithCount(required.amount()));
            }
            if (stacks.size() != 1 + display.recipe().inputs().size()) continue;
            stacks.put("result", output.get());
            var roles = Map.of("result", RecipeIngredientRole.OUTPUT);
            var transfer = handler(handlers, ForbiddenArcanusJeiIntegration.HEPHAESTUS_FORGE_TYPE);
            var result = transfer.transferRecipe(terminal.menu(), display,
                    Ae2PatternEncodingTestSupport.slots(stacks, roles), terminal.player(), false, false);
            if (result != null) continue;
            ItemStack forgedWrong = output.get().copy();
            forgedWrong.setCount(output.get().getCount() + 1);
            stacks.put("result", forgedWrong);
            assertUserError(helper, transfer.transferRecipe(terminal.menu(), display,
                    Ae2PatternEncodingTestSupport.slots(stacks, roles), terminal.player(), false, false),
                    "jei.mekanism_magic.dynamic_recipe.stale_output");
            RitualDisplay fakeUpgrade = new RitualDisplay(display.recipe(),
                    display.examples(), display.supportedTiers(),
                    display.enhancers(), 1, display.signature());
            assertUserError(helper, transfer.transferRecipe(terminal.menu(), fakeUpgrade,
                    Ae2PatternEncodingTestSupport.slots(stacks, roles), terminal.player(), false, false),
                    "jei.mekanism_magic.dynamic_recipe.forge_upgrade_not_ae_output");
            forgeChecked = true;
            break;
        }
        helper.assertTrue(forgeChecked, "No ordinary real Forge ritual passed component-safe preview");
        boolean transmuteChecked = false;
        for (RitualDisplay display : displays.rituals()) {
            if (display.upgradeTier() != 0 || !display.recipe().resultType().getPath().contains("transmute")
                    || display.examples().isEmpty() || display.supportedTiers().isEmpty()
                    || display.recipe().inputs().stream().anyMatch(required -> required.ingredient().examples().isEmpty())) continue;
            ItemStack named = display.examples().getFirst().input();
            named.set(DataComponents.CUSTOM_NAME, Component.literal("AE exact transmute"));
            var paired = HephaestusForge261Bridge.previewRitual(helper.getLevel().registryAccess(),
                    display.recipe().id(), named, display.supportedTiers().getFirst());
            if (paired.isEmpty() || ItemStack.matches(paired.get(), display.examples().getFirst().output())) continue;
            Map<String, ItemStack> stacks = new LinkedHashMap<>();
            stacks.put("main_material", named);
            for (int i = 0; i < display.recipe().inputs().size(); i++) {
                var required = display.recipe().inputs().get(i);
                stacks.put("auxiliary_" + i, required.ingredient().examples().getFirst().copyWithCount(required.amount()));
            }
            var roles = Map.of("result", RecipeIngredientRole.OUTPUT);
            var transfer = handler(handlers, ForbiddenArcanusJeiIntegration.HEPHAESTUS_FORGE_TYPE);
            stacks.put("result", display.examples().getFirst().output());
            assertUserError(helper, transfer.transferRecipe(terminal.menu(), display,
                    Ae2PatternEncodingTestSupport.slots(stacks, roles), terminal.player(), false, false),
                    "jei.mekanism_magic.dynamic_recipe.stale_output");
            stacks.put("result", paired.get());
            helper.assertTrue(transfer.transferRecipe(terminal.menu(), display,
                            Ae2PatternEncodingTestSupport.slots(stacks, roles), terminal.player(), false, false) == null,
                    "Component-preserving Forge transmute rejected its exact current input/output pair");
            transmuteChecked = true;
            break;
        }
        helper.assertTrue(transmuteChecked, "No component-bearing Forge Transmute pair was verified");
        helper.assertTrue(EssencePreparationDisplay.recipes().size() == 4
                        && !handlers.containsKey(
                        ForbiddenArcanusJeiIntegration.ESSENCE_CONDENSER_TYPE),
                "Retained-sample, FE-only condenser recipes were encoded as consumptive AE item patterns");
        helper.succeed();
    }

    private static ItemStack first(Ingredient ingredient, int count) {
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty()) return candidate.copyWithCount(count);
        }
        return ItemStack.EMPTY;
    }

    private static boolean dispatch(GameTestHelper helper, net.minecraft.world.level.block.entity.BlockEntity machine,
                                    List<ItemStack> inputs, List<ItemStack> outputs) {
        Map<AEItemKey, Long> grouped = new LinkedHashMap<>();
        inputs.forEach(stack -> grouped.merge(AEItemKey.of(stack), (long) stack.getCount(), Math::addExact));
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(grouped.entrySet().stream()
                        .map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList(),
                outputs.stream().map(GenericStack::fromItemStack).toList());
        var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        if (pattern == null) return false;
        KeyCounter[] delivered = grouped.entrySet().stream().map(entry -> {
            KeyCounter counter = new KeyCounter();
            counter.add(entry.getKey(), entry.getValue());
            return counter;
        }).toArray(KeyCounter[]::new);
        ICraftingMachine external = ICraftingMachine.of(machine, Direction.UP);
        return external != null && external.acceptsPlans()
                && external.pushPattern(pattern, delivered, Direction.UP);
    }

    private static void assertUserError(GameTestHelper helper, IRecipeTransferError error, String key) {
        helper.assertTrue(error != null && error.getType() == IRecipeTransferError.Type.USER_FACING
                        && !error.getTooltip().isEmpty()
                        && error.getTooltip().getFirst().getContents() instanceof TranslatableContents text
                        && key.equals(text.getKey()),
                "Expected JEI user error " + key + ", got " + error);
    }

    @SuppressWarnings("unchecked")
    private static <R> IRecipeTransferHandler<PatternEncodingTermMenu, R> handler(
            Map<RecipeType<?>, IRecipeTransferHandler<?, ?>> handlers, RecipeType<R> type) {
        return (IRecipeTransferHandler<PatternEncodingTermMenu, R>) handlers.get(type);
    }

    private static Map<RecipeType<?>, IRecipeTransferHandler<?, ?>> handlers() {
        Map<RecipeType<?>, IRecipeTransferHandler<?, ?>> result = new LinkedHashMap<>();
        var registration = (IRecipeTransferRegistration) Proxy.newProxyInstance(
                Ae2AddonMachineJeiGameTests.class.getClassLoader(),
                new Class<?>[]{IRecipeTransferRegistration.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getTransferHelper" -> Ae2PatternEncodingTestSupport.transferHelper();
                    case "addRecipeTransferHandler" -> {
                        result.put((RecipeType<?>) args[1], (IRecipeTransferHandler<?, ?>) args[0]);
                        yield null;
                    }
                    case "toString" -> "AE JEI registration capture";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Ae2AddonMachineJeiTransfer.registerNature(registration);
        Ae2AddonMachineJeiTransfer.registerForbidden(registration);
        Ae2NatureAltarJeiTransfer.register(registration);
        return result;
    }
}
