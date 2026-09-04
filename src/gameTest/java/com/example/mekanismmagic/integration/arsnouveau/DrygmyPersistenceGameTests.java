package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import mekanism.api.Action;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Runtime coverage for manual jar access and prepared Drygmy loot. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrygmyPersistenceGameTests {
    private static final BlockPos SOURCE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos RESTORED_POS = new BlockPos(3, 1, 1);

    private DrygmyPersistenceGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 200)
    public static void manualJarsAndPreparedLootSurvivePlacement(
            GameTestHelper helper) {
        helper.setBlock(SOURCE_POS,
                ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        DrygmySimulatorBlockEntity machine =
                helper.getBlockEntity(SOURCE_POS);
        Upgrade stackUpgrade = NativeMekanismRegistries
                .dimensionMinerStackUpgrade();
        helper.assertTrue(stackUpgrade != null,
                "Drygmy stack upgrade is unavailable");
        machine.getComponent().addUpgrades(stackUpgrade, 8);
        machine.getComponent().addUpgrades(Upgrade.SPEED, 8);
        helper.assertTrue(machine.getComponent().getUpgrades(stackUpgrade)
                        == 8 && machine.getComponent().getUpgrades(
                        Upgrade.SPEED) == 8,
                "Unable to seed Drygmy stack/speed upgrades");
        int prepared = machine.seedDevelopmentTest(helper.getLevel(),
                ResourceLocation.withDefaultNamespace("cow"));
        helper.assertTrue(prepared > 0,
                "Drygmy simulator could not prepare cow loot");
        List<ItemStack> expectedOutputs =
                machine.developmentPendingOutputs();
        helper.assertTrue(expectedOutputs.stream()
                        .anyMatch(stack -> stack.getCount() > 99),
                "Stacked Drygmy did not produce an oversized pending batch");
        int expectedMultiplier =
                machine.developmentPendingOperationMultiplier();
        int expectedSourceCost = machine.developmentSourceCost();
        String expectedSignature = machine.developmentPendingSignature();
        machine.getComponent().removeUpgrade(stackUpgrade, true);
        machine.getComponent().removeUpgrade(Upgrade.SPEED, true);
        helper.assertTrue(machine.developmentPendingOperationMultiplier()
                        == expectedMultiplier
                        && machine.developmentSourceCost()
                        == expectedSourceCost
                        && sameStacks(expectedOutputs,
                        machine.developmentPendingOutputs()),
                "Removing upgrades changed Drygmy pending output or cost");
        int expectedProgress = machine.getProgress();
        long expectedEnergy = machine.developmentPendingEnergyPerTick();
        helper.assertTrue(machine.getMaxSource() >= expectedSourceCost,
                "Removing upgrades made pending Source cost impossible");
        machine.getNativeEnergyContainer().setEnergy(0);
        machine.setSource(0);
        List<IInventorySlot> jars = machine.mekanismMagicPersistentInputs();
        ItemStack expectedJar = jars.getFirst().getStack().copy();
        int occupiedIndex = unsidedIndex(machine, jars.getFirst());
        int emptyIndex = unsidedIndex(machine, jars.get(1));
        helper.assertTrue(occupiedIndex >= 0 && emptyIndex >= 0,
                "Drygmy jars are absent from the unsided capability");

        helper.assertTrue(machine.extractItem(occupiedIndex, 1, null,
                        Action.EXECUTE).isEmpty(),
                "Unsided capability extracted a manual-only mob jar");
        machine.setStackInSlot(occupiedIndex, ItemStack.EMPTY, null);
        helper.assertTrue(ItemStack.isSameItemSameComponents(expectedJar,
                        jars.getFirst().getStack()),
                "Unsided direct set cleared a manual-only mob jar");
        // Drygmy jars deliberately allow automated insertion but only manual
        // extraction. The unsided view must match that real predicate instead
        // of being promoted to unrestricted INTERNAL access.
        ItemStack simulatedRemainder = machine.insertItem(emptyIndex,
                expectedJar.copy(), null, Action.SIMULATE);
        helper.assertTrue(simulatedRemainder.isEmpty()
                        && jars.get(1).getStack().isEmpty(),
                "Unsided capability did not preserve jar insertion rules");
        helper.assertTrue(machine.isItemValid(emptyIndex,
                        expectedJar, null),
                "Unsided capability hid a valid automated jar insertion");

        ItemStack placedStack = new ItemStack(
                machine.getBlockState().getBlock());
        machine.saveToItem(placedStack,
                helper.getLevel().registryAccess());
        helper.setBlock(RESTORED_POS,
                ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        BlockPos restoredAbsolute = helper.absolutePos(RESTORED_POS);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(
                        helper.getLevel(), null, restoredAbsolute,
                        placedStack),
                "Drygmy item did not restore its custom state");
        DrygmySimulatorBlockEntity restored =
                helper.getBlockEntity(RESTORED_POS);
        restored.applyComponentsFromItemStack(placedStack);

        // The first server tick performs the guarded, signature-checked
        // restoration after Mekanism's inventory components are present.
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(sameStacks(expectedOutputs,
                                    restored.developmentPendingOutputs()),
                            "Prepared Drygmy loot changed during break/place");
                    helper.assertTrue(expectedSignature.equals(
                                    restored.developmentPendingSignature()),
                            "Drygmy jar signature changed during break/place");
                    helper.assertTrue(restored.getProgress()
                                    == expectedProgress,
                            "Drygmy progress changed during break/place");
                    helper.assertTrue(restored
                                    .developmentPendingOperationMultiplier()
                                    == expectedMultiplier
                                    && restored.developmentSourceCost()
                                    == expectedSourceCost
                                    && restored.developmentPendingEnergyPerTick()
                                    == expectedEnergy,
                            "Drygmy pending cost snapshot changed during "
                                    + "break/place");
                    helper.assertTrue(restored.getMaxSource()
                                    >= expectedSourceCost,
                            "Restored Drygmy cannot refill its pending "
                                    + "Source cost");
                    helper.assertTrue(ItemStack.isSameItemSameComponents(
                                    expectedJar,
                                    restored.mekanismMagicPersistentInputs()
                                            .getFirst().getStack()),
                            "Drygmy jar changed during break/place");
                })
                .thenSucceed();
    }

    private static int unsidedIndex(DrygmySimulatorBlockEntity machine,
                                    IInventorySlot target) {
        List<IInventorySlot> slots = machine.getInventorySlots(null);
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    private static boolean sameStacks(List<ItemStack> expected,
                                      List<ItemStack> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            ItemStack left = expected.get(index);
            ItemStack right = actual.get(index);
            if (left.getCount() != right.getCount()
                    || !ItemStack.isSameItemSameComponents(left, right)) {
                return false;
            }
        }
        return true;
    }
}
