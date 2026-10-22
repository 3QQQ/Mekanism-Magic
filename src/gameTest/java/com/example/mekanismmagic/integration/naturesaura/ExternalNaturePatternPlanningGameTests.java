package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.ItemInputGroup;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.integration.common.recipe.ExternalItemPatternRouter;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure planning checks for stock AE2 deliveries with overlapping item roles. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExternalNaturePatternPlanningGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    private ExternalNaturePatternPlanningGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void offeringSeparatesOneStartItemFromWholeInputBatch(
            GameTestHelper helper) {
        helper.setBlock(MACHINE,
                NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK.get());
        OfferingProcessorBlockEntity machine =
                helper.getBlockEntity(MACHINE);
        List<IInventorySlot> ports = machine.mekanismMagicPatternInputs();
        helper.assertTrue(ports.size() == 2,
                "Offering did not expose its two consumable roles");
        ItemStack retainedRose = new ItemStack(Items.WITHER_ROSE,
                OfferingProcessorBlockEntity.WITHER_ROSES_FOR_BONUS);
        machine.getWitherRoseSlot().setStack(retainedRose);
        for (OfferingRecipeBridge.OfferingRecipeView recipe :
                NatureAuraRecipeScanner.scanOfferings(
                        helper.getLevel().getRecipeManager())) {
            ItemStack material = firstChoice(recipe.input());
            ItemStack start = firstChoice(recipe.startItem());
            if (material.isEmpty() || start.isEmpty()) {
                continue;
            }
            List<PatternStack> inputs = List.of(
                    new PatternStack(material, recipe.inputAmount()),
                    new PatternStack(start, 1));
            List<PatternStack> outputs = List.of(new PatternStack(
                    recipe.output(), recipe.output().getCount()));
            Optional<List<ItemInputGroup>> plan = machine
                    .mekanismMagicPlanExternalItemInputs(inputs, outputs);
            if (plan.isEmpty()) {
                continue;
            }
            List<ItemInputGroup> groups = plan.get();
            helper.assertTrue(groups.size() == 2
                            && groups.get(0).slots().equals(List.of(ports.get(1)))
                            && groups.get(1).slots().equals(List.of(ports.get(0)))
                            && amount(groups.get(0)) == 1
                            && amount(groups.get(1)) == recipe.inputAmount(),
                    "Offering plan did not split the start item from all materials");
            List<PatternStack> wrongOutput = List.of(new PatternStack(
                    new ItemStack(Items.BARRIER), 1));
            helper.assertTrue(!ExternalItemPatternRouter.push(machine,
                    machine.getInventorySlots(Direction.UP), inputs,
                    wrongOutput),
                    "Offering accepted a false declared output batch");
            helper.assertTrue(ports.stream().allMatch(IInventorySlot::isEmpty),
                    "Rejected Offering batch changed consumable slots");
            helper.assertTrue(ItemStack.matches(
                    retainedRose, machine.getWitherRoseSlot().getStack()),
                    "Rejected Offering batch changed persistent roses");
            helper.assertTrue(ExternalItemPatternRouter.push(machine,
                    machine.getInventorySlots(Direction.UP), inputs,
                    outputs),
                    "Offering one-face batch was not committed");
            helper.assertTrue(ports.get(0).getCount()
                            == recipe.inputAmount()
                            && ItemStack.isSameItemSameComponents(
                            ports.get(0).getStack(), material)
                            && ports.get(1).getCount() == 1
                            && ItemStack.isSameItemSameComponents(
                            ports.get(1).getStack(), start)
                            && ItemStack.matches(retainedRose,
                            machine.getWitherRoseSlot().getStack()),
                    "Offering committed to the wrong role or altered roses");
            helper.succeed();
            return;
        }
        helper.fail("No real Offering recipe produced an unambiguous external plan");
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void treeRitualReservesSaplingAndSixteenGoldPowder(
            GameTestHelper helper) {
        helper.setBlock(MACHINE,
                NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_BLOCK.get());
        TreeRitualProcessorBlockEntity machine =
                helper.getBlockEntity(MACHINE);
        List<IInventorySlot> ports = machine.mekanismMagicPatternInputs();
        helper.assertTrue(ports.size() == TreeRitualRecipeBridge.MATERIAL_SLOTS + 2,
                "Tree ritual did not expose its material, sapling and gold ports");
        for (TreeRitualRecipeBridge.TreeRitualRecipeView recipe :
                TreeRitualRecipeBridge.scan(
                        helper.getLevel().getRecipeManager())) {
            ItemStack sapling = firstChoice(recipe.sapling());
            if (sapling.isEmpty()) {
                continue;
            }
            List<PatternStack> inputs = new ArrayList<>();
            inputs.add(new PatternStack(sapling, 1));
            inputs.add(new PatternStack(new ItemStack(
                    de.ellpeck.naturesaura.blocks.ModBlocks.GOLD_POWDER
                            .asItem()),
                    TreeRitualRecipeBridge.GOLD_POWDER_PER_OPERATION));
            boolean complete = true;
            for (Ingredient ingredient : recipe.ingredients()) {
                ItemStack choice = firstChoice(ingredient);
                if (choice.isEmpty()) {
                    complete = false;
                    break;
                }
                inputs.add(new PatternStack(choice, 1));
            }
            if (!complete) {
                continue;
            }
            List<PatternStack> outputs = List.of(new PatternStack(
                    recipe.output(), recipe.output().getCount()));
            Optional<List<ItemInputGroup>> plan = machine
                    .mekanismMagicPlanExternalItemInputs(inputs, outputs);
            if (plan.isEmpty()) {
                continue;
            }
            List<ItemInputGroup> groups = plan.get();
            helper.assertTrue(groups.size() == 3
                            && groups.get(0).slots().equals(List.of(
                            ports.get(TreeRitualRecipeBridge.MATERIAL_SLOTS)))
                            && groups.get(1).slots().equals(List.of(
                            ports.get(TreeRitualRecipeBridge.MATERIAL_SLOTS + 1)))
                            && groups.get(2).slots().equals(ports.subList(
                            0, TreeRitualRecipeBridge.MATERIAL_SLOTS))
                            && amount(groups.get(0)) == 1
                            && amount(groups.get(1)) == TreeRitualRecipeBridge
                            .GOLD_POWDER_PER_OPERATION
                            && amount(groups.get(2)) == recipe.ingredients().size(),
                    "Tree plan lost or misplaced a consumable role");
            List<PatternStack> shortGold = new ArrayList<>(inputs);
            shortGold.set(1, new PatternStack(inputs.get(1).stack(), 15));
            helper.assertTrue(!ExternalItemPatternRouter.push(machine,
                    machine.getInventorySlots(Direction.UP), inputs,
                    List.of(new PatternStack(new ItemStack(
                            Items.BARRIER), 1))),
                    "Tree accepted a false declared output batch");
            helper.assertTrue(!ExternalItemPatternRouter.push(machine,
                    machine.getInventorySlots(Direction.UP), shortGold,
                    outputs),
                    "Tree plan accepted fewer than sixteen gold powder");
            helper.assertTrue(ports.stream().allMatch(IInventorySlot::isEmpty),
                    "Rejected Tree batch changed physical inputs");
            helper.assertTrue(ExternalItemPatternRouter.push(machine,
                    machine.getInventorySlots(Direction.UP), inputs,
                    outputs),
                    "Tree one-face batch was not committed");
            long deliveredMaterials = ports.subList(0,
                    TreeRitualRecipeBridge.MATERIAL_SLOTS).stream()
                    .mapToLong(IInventorySlot::getCount).sum();
            helper.assertTrue(deliveredMaterials == recipe.ingredients().size()
                            && ports.get(TreeRitualRecipeBridge.MATERIAL_SLOTS)
                            .getCount() == 1
                            && ItemStack.isSameItemSameComponents(
                            ports.get(TreeRitualRecipeBridge.MATERIAL_SLOTS)
                                    .getStack(), sapling)
                            && ports.get(TreeRitualRecipeBridge.MATERIAL_SLOTS + 1)
                            .getCount() == TreeRitualRecipeBridge
                            .GOLD_POWDER_PER_OPERATION,
                    "Tree batch did not reach its material, sapling and gold roles");
            helper.succeed();
            return;
        }
        helper.fail("No real Tree Ritual recipe produced an unambiguous external plan");
    }

    private static ItemStack firstChoice(Ingredient ingredient) {
        ItemStack[] choices = ingredient.getItems();
        return choices.length == 0 ? ItemStack.EMPTY
                : choices[0].copyWithCount(1);
    }

    private static long amount(ItemInputGroup group) {
        return group.inputs().stream().mapToLong(PatternStack::amount).sum();
    }
}
