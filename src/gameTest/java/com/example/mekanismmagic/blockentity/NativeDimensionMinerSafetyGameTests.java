package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.event.MachineDropPreserver;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.pipez.PipezItemHandlerCompat;
import mekanism.api.Action;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/** Runtime conservation tests for the dimensional miner's prepared roll. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeDimensionMinerSafetyGameTests {
    private static final BlockPos SOURCE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos RESTORED_POS = new BlockPos(3, 1, 1);

    private NativeDimensionMinerSafetyGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 200)
    public static void pendingRollAndStackedDropRemainConserved(
            GameTestHelper helper) {
        helper.setBlock(SOURCE_POS,
                NativeMekanismRegistries.DIMENSION_MINER_BLOCK.get());
        NativeDimensionMinerBlockEntity machine =
                helper.getBlockEntity(SOURCE_POS);
        ItemStack miner = minerItem();
        helper.assertTrue(OccultismRecipeBridge.isMinerItem(miner),
                "Occultism test miner is unavailable or untagged");
        Upgrade stackUpgrade = NativeMekanismRegistries
                .dimensionMinerStackUpgrade();
        helper.assertTrue(stackUpgrade != null,
                "Dimension miner stack upgrade is unavailable");
        machine.getComponent().addUpgrades(stackUpgrade, 8);
        machine.getComponent().addUpgrades(Upgrade.SPEED, 8);
        helper.assertTrue(machine.getComponent().getUpgrades(stackUpgrade)
                        == 8 && machine.getComponent().getUpgrades(
                        Upgrade.SPEED) == 8,
                "Unable to seed miner stack/speed upgrades");
        helper.assertTrue(machine.seedDevelopmentTest(miner) > 0,
                "Dimension miner could not prepare a weighted output");

        ItemStack oversized = new ItemStack(Items.DIAMOND);
        oversized.setCount(500);
        ItemStack oversizedRoundTrip = NativeMagicMachineBlockEntity
                .loadCountedItemStack(NativeMagicMachineBlockEntity
                .saveCountedItemStack(oversized,
                        helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertTrue(oversizedRoundTrip.is(Items.DIAMOND)
                        && oversizedRoundTrip.getCount() == 500,
                "Oversized pending stack did not survive counted NBT");

        List<ItemStack> expectedOutputs =
                machine.developmentPendingOutputs();
        helper.assertTrue(expectedOutputs.stream()
                        .anyMatch(stack -> stack.getCount() > 99),
                "Stacked miner did not produce an oversized pending batch");
        int expectedMultiplier =
                machine.developmentPendingOperationMultiplier();
        ItemStack expectedInput = machine.developmentPendingInput();
        machine.getComponent().removeUpgrade(stackUpgrade, true);
        machine.getComponent().removeUpgrade(Upgrade.SPEED, true);
        helper.assertTrue(machine.developmentPendingOperationMultiplier()
                        == expectedMultiplier
                        && sameStacks(expectedOutputs,
                        machine.developmentPendingOutputs()),
                "Removing upgrades changed or rerolled pending miner output");
        int expectedProgress = machine.getProgress();
        long expectedEnergy = machine.developmentPendingEnergyPerTick();
        int inputIndex = unsidedIndex(machine,
                machine.getNativeInputSlot());
        helper.assertTrue(inputIndex >= 0,
                "Miner input is absent from the unsided capability");

        ItemStack extracted = machine.extractItem(inputIndex, 1, null,
                Action.EXECUTE);
        helper.assertTrue(extracted.isEmpty(),
                "Unsided capability extracted the persistent miner");
        machine.setStackInSlot(inputIndex, ItemStack.EMPTY, null);
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        expectedInput, machine.getNativeInputSlot().getStack()),
                "Unsided direct set cleared the persistent miner");

        BlockPos absolute = helper.absolutePos(SOURCE_POS);
        Item machineItem = machine.getBlockState().getBlock().asItem();
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(new ItemEntity(helper.getLevel(),
                absolute.getX() + 0.5, absolute.getY() + 0.5,
                absolute.getZ() + 0.5,
                new ItemStack(machineItem, 3)));
        BlockDropsEvent event = new BlockDropsEvent(helper.getLevel(),
                absolute, machine.getBlockState(), machine, drops,
                null, ItemStack.EMPTY);
        MachineDropPreserver.onBlockDrops(event);

        int total = drops.stream().map(ItemEntity::getItem)
                .mapToInt(ItemStack::getCount).sum();
        List<ItemStack> stateful = drops.stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.get(DataComponents.BLOCK_ENTITY_DATA)
                        != null)
                .toList();
        helper.assertTrue(total == 3,
                "Stacked machine drop changed item count: " + total);
        helper.assertTrue(stateful.size() == 1
                        && stateful.getFirst().getCount() == 1,
                "Exactly one physical machine must carry block-entity data");
        helper.assertTrue(drops.stream().map(ItemEntity::getItem)
                        .filter(stack -> stack.get(
                                DataComponents.BLOCK_ENTITY_DATA) == null)
                        .mapToInt(ItemStack::getCount).sum() == 2,
                "Extra machine drops must remain empty and conserved");

        ItemStack placedStack = stateful.getFirst();
        helper.setBlock(RESTORED_POS,
                NativeMekanismRegistries.DIMENSION_MINER_BLOCK.get());
        BlockPos restoredAbsolute = helper.absolutePos(RESTORED_POS);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(
                        helper.getLevel(), null, restoredAbsolute,
                        placedStack),
                "Machine item did not restore its custom state");
        NativeDimensionMinerBlockEntity restored =
                helper.getBlockEntity(RESTORED_POS);
        restored.applyComponentsFromItemStack(placedStack);

        helper.assertTrue(sameStacks(expectedOutputs,
                        restored.developmentPendingOutputs()),
                "Prepared weighted result changed during break/place");
        helper.assertTrue(ItemStack.isSameItemSameComponents(expectedInput,
                        restored.developmentPendingInput()),
                "Prepared miner input signature changed during break/place");
        helper.assertTrue(restored.getProgress() == expectedProgress,
                "Miner progress changed during break/place");
        helper.assertTrue(restored.developmentPendingOperationMultiplier()
                        == expectedMultiplier
                        && restored.developmentPendingEnergyPerTick()
                        == expectedEnergy,
                "Miner pending cost snapshot changed during break/place");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 100)
    public static void pipezBulkExtractionDrainsLongBufferConservatively(
            GameTestHelper helper) {
        helper.setBlock(SOURCE_POS,
                NativeMekanismRegistries.DIMENSION_MINER_BLOCK.get());
        NativeDimensionMinerBlockEntity machine =
                helper.getBlockEntity(SOURCE_POS);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        IInventorySlot output = machine.getInventorySlots(null).stream()
                .filter(machine::isMinerOutputSlot)
                .findFirst()
                .orElseThrow();
        int outputIndex = unsidedIndex(machine, output);
        helper.assertTrue(outputIndex >= 0,
                "Miner output is absent from the unsided capability");

        output.setStack(diamond.copyWithCount(64));
        machine.seedBufferedOutputDevelopment(diamond, 5_000_000L);
        long original = 5_000_064L;
        helper.assertTrue(machine.developmentTotalOutput(diamond) == original,
                "Development long buffer was seeded incorrectly");

        ItemStack ordinary = machine.extractItem(outputIndex, 2_000_000,
                null, Action.SIMULATE);
        helper.assertTrue(ordinary.getCount() == 64,
                "Ordinary callers bypassed the legal ItemStack limit");

        IItemHandler orderedSource =
                PipezItemHandlerCompat.wrapOrderedSource(machine);
        ItemStack simulated = orderedSource.extractItem(
                outputIndex, 2_000_000, true);
        helper.assertTrue(simulated.getCount() == 2_000_000,
                "Pipez Infinity simulation stopped at the visible slots");
        helper.assertTrue(machine.developmentTotalOutput(diamond) == original,
                "Pipez simulation mutated the long output buffer");

        ItemStack first = orderedSource.extractItem(
                outputIndex, 2_000_000, false);
        helper.assertTrue(first.getCount() == 2_000_000,
                "Pipez commit did not match its simulation");
        helper.assertTrue(machine.developmentTotalOutput(diamond)
                        == original - first.getCount(),
                "First Pipez extraction duplicated or lost output");

        ItemStack remainder = orderedSource.extractItem(
                outputIndex, Integer.MAX_VALUE, false);
        helper.assertTrue(remainder.getCount() == 3_000_064,
                "Pipez did not drain the remaining long output");
        helper.assertTrue(machine.developmentTotalOutput(diamond) == 0,
                "Long-buffer output remained after exact final extraction");
        helper.succeed();
    }

    private static ItemStack minerItem() {
        Item item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(
                        "occultism", "miner_foliot_unspecialized"));
        return new ItemStack(item);
    }

    private static int unsidedIndex(
            NativeMagicMachineBlockEntity machine, IInventorySlot target) {
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
