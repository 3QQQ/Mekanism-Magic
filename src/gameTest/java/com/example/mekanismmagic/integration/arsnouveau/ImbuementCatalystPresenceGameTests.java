package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;

/** A catalogue preview or AE receipt cannot replace a stored catalyst identifier. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ImbuementCatalystPresenceGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 2, 1);

    private ImbuementCatalystPresenceGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 300)
    public static void requiredCatalystMustBeStoredBeforeAndDuringWork(
            GameTestHelper helper) {
        RecipeHolder<ImbuementRecipe> recipe = recipe(helper,
                "imbuement_fire_essence", true);
        ImbuementProcessorBlockEntity machine = place(helper);
        IInventorySlot input = machine.mekanismMagicPatternInputs().getFirst();
        IInventorySlot output = machine.mekanismMagicPatternOutputs().getFirst();
        IInventorySlot catalyst = machine.mekanismMagicPersistentInputs()
                .getFirst();
        ItemStack reagent = ArsNouveauRecipeBridge.representativeChoice(
                recipe.value().getInput());
        ItemStack identifier = ArsNouveauRecipeBridge.createIdentifierForRecipe(
                helper.getLevel(), recipe.id());
        helper.assertTrue(!reagent.isEmpty()
                        && ArsNouveauRecipeBridge.identifierMatchesRecipe(
                        helper.getLevel(), identifier, recipe.id()),
                "Catalyst-required fixture is not a valid signed recipe");
        String catalystId = CatalystIdentifierItem.catalystId(
                identifier).toString();
        int cost = recipe.value().getSource();
        helper.assertTrue(cost > 0,
                "Catalyst-required fixture has no Source cost");

        // The GUI's catalogue selection is only a preview. With an empty
        // physical library it must not authorize ordinary manual input.
        helper.assertFalse(machine.selectCatalystIdentifierId(catalystId),
                "An empty catalyst library accepted a virtual GUI selection");
        charge(machine, cost);
        input.setStack(reagent.copy());
        tick(machine, ArsNouveauMachineConfig.IMBUEMENT_DURATION + 20);
        helper.assertTrue(ItemStack.matches(input.getStack(), reagent)
                        && output.isEmpty() && machine.getSource() == cost,
                "Virtual selection crafted without a stored catalyst");

        // A valid recipe marker is likewise only routing context, not a
        // physical catalyst. Repeated ticks must leave the receipt untouched.
        ItemStack marked = ArsNouveauRecipeBridge.markPatternInput(
                reagent, recipe.id(), ArsNouveauRecipeScanner
                        .semanticSignature(helper.getLevel().getRecipeManager(),
                                recipe.id()));
        input.setStack(marked.copy());
        tick(machine, ArsNouveauMachineConfig.IMBUEMENT_DURATION + 20);
        helper.assertTrue(ItemStack.matches(input.getStack(), marked)
                        && output.isEmpty() && machine.getSource() == cost,
                "Marked pattern input bypassed an empty catalyst library");

        catalyst.setStack(identifier.copy());
        machine.selectCatalystIdentifier(0);
        input.setStack(reagent.copy());
        untilOutput(helper, machine, recipe);
        helper.assertTrue(input.isEmpty() && machine.getSource() == 0,
                "Stored catalyst did not enable exactly one paid craft");

        output.setStack(ItemStack.EMPTY);
        charge(machine, cost);
        input.setStack(reagent.copy());
        tick(machine, 5);
        helper.assertTrue(machine.getProgress() > 0 && output.isEmpty(),
                "Catalyst fixture did not enter in-progress processing");
        int sourceBeforeRemoval = machine.getSource();
        catalyst.setStack(ItemStack.EMPTY);
        tick(machine, ArsNouveauMachineConfig.IMBUEMENT_DURATION + 20);
        helper.assertTrue(ItemStack.matches(input.getStack(), reagent)
                        && output.isEmpty()
                        && machine.getSource() == sourceBeforeRemoval,
                "Removing the stored catalyst completed or consumed the work");
        catalyst.setStack(identifier.copy());
        machine.selectCatalystIdentifier(0);
        untilOutput(helper, machine, recipe);
        helper.assertTrue(input.isEmpty() && machine.getSource() == 0,
                "Returning the stored catalyst did not resume processing");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 200)
    public static void nativeNoCatalystRecipeStillWorksWithEmptyLibrary(
            GameTestHelper helper) {
        RecipeHolder<ImbuementRecipe> recipe = recipe(helper,
                "imbuement_amethyst", false);
        ImbuementProcessorBlockEntity machine = place(helper);
        IInventorySlot input = machine.mekanismMagicPatternInputs().getFirst();
        ItemStack reagent = ArsNouveauRecipeBridge.representativeChoice(
                recipe.value().getInput());
        helper.assertTrue(machine.mekanismMagicPersistentInputs().stream()
                        .allMatch(IInventorySlot::isEmpty),
                "No-catalyst baseline unexpectedly has a stored identifier");
        charge(machine, recipe.value().getSource());
        input.setStack(reagent.copy());
        untilOutput(helper, machine, recipe);
        helper.assertTrue(input.isEmpty() && machine.getSource() == 0,
                "Native no-catalyst recipe was blocked by the presence guard");
        helper.succeed();
    }

    private static RecipeHolder<ImbuementRecipe> recipe(
            GameTestHelper helper, String path, boolean requiresCatalyst) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "ars_nouveau", path);
        RecipeHolder<ImbuementRecipe> holder = ArsNouveauRecipeScanner.find(
                helper.getLevel().getRecipeManager(), id);
        helper.assertTrue(holder != null
                        && !holder.value().getResultItem(
                        helper.getLevel().registryAccess()).isEmpty()
                        && (holder.value().getPedestalItems().isEmpty()
                        != requiresCatalyst),
                "Missing or mismatched imbuement fixture: " + id);
        return holder;
    }

    private static ImbuementProcessorBlockEntity place(
            GameTestHelper helper) {
        helper.setBlock(MACHINE,
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        return helper.getBlockEntity(MACHINE);
    }

    private static void charge(ImbuementProcessorBlockEntity machine,
                               int source) {
        machine.getNativeEnergyContainer().setEnergy(
                machine.getNativeEnergyContainer().getMaxEnergy());
        machine.setSource(source);
    }

    private static void untilOutput(GameTestHelper helper,
                                    ImbuementProcessorBlockEntity machine,
                                    RecipeHolder<ImbuementRecipe> recipe) {
        IInventorySlot output = machine.mekanismMagicPatternOutputs()
                .getFirst();
        for (int ticks = 0; ticks < 1_200 && output.isEmpty(); ticks++) {
            tick(machine, 1);
        }
        helper.assertTrue(ItemStack.matches(output.getStack(),
                        recipe.value().getResultItem(
                                helper.getLevel().registryAccess())),
                "Imbuement failed to produce the exact expected result: "
                        + recipe.id());
    }

    private static void tick(ImbuementProcessorBlockEntity machine,
                             int ticks) {
        try {
            var update = machine.getClass().getDeclaredMethod(
                    "onUpdateServer");
            update.setAccessible(true);
            for (int index = 0; index < ticks; index++) {
                update.invoke(machine);
            }
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Imbuement processing failed",
                    failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot tick imbuement processor",
                    failure);
        }
    }
}
