package com.example.mekanismmagic.gametest;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.block.NativeMachineBlock;
import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;

@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineItemContainerGameTests {
    private MachineItemContainerGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void savedAssemblerInventorySurvivesContentsReadAndPlacement(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 1);
        BlockPos target = new BlockPos(3, 1, 1);
        var block = NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.get();
        helper.setBlock(source, block);
        NativeMiniRitualAssemblerBlockEntity machine = helper.getBlockEntity(source);
        var slots = machine.getInventorySlots(null);
        // First material slot follows the energy slot; the last slot is output.
        slots.get(1).setStack(new ItemStack(Items.DIAMOND, 7));
        ((BasicInventorySlot) slots.get(slots.size() - 1))
                .setStackUnchecked(new ItemStack(MekanismMagic.MINI_RITUAL.get()));
        ItemStack saved = new ItemStack(block);
        machine.saveToItem(saved, helper.getLevel().registryAccess());
        ItemStack before = saved.copy();

        // This is the exact entry point used by MekanismContentsProcessor:61.
        var contents = ContainerType.ITEM.getAttachmentContainersIfPresent(saved);
        helper.assertTrue(contents.size() == slots.size(), "Saved slot count changed");
        for (int i = 0; i < slots.size(); i++) {
            helper.assertTrue(ItemStack.matches(slots.get(i).getStack(), contents.get(i).getStack()),
                    "Saved inventory mismatch at slot " + i);
        }
        helper.assertTrue(ItemStack.matches(before, saved), "Contents inspection mutated the item");

        helper.setBlock(target, block);
        NativeMiniRitualAssemblerBlockEntity restored = helper.getBlockEntity(target);
        restored.applyComponentsFromItemStack(saved);
        var restoredSlots = restored.getInventorySlots(null);
        for (int i = 0; i < slots.size(); i++) {
            helper.assertTrue(ItemStack.matches(slots.get(i).getStack(), restoredSlots.get(i).getStack()),
                    "Placed inventory mismatch at slot " + i);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allNativeMachineItemsReadSavedLayoutsWithoutChangingFreshItems(GameTestHelper helper) {
        int checked = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem blockItem)
                    || !(blockItem.getBlock() instanceof NativeMachineBlock<?>)
                    || !MekanismMagic.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                continue;
            }
            checked++;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            ItemStack fresh = new ItemStack(item);
            helper.assertTrue(ContainerType.ITEM.getAttachmentContainersIfPresent(fresh).isEmpty(),
                    "Fresh machine unexpectedly contains slots: " + id);
            helper.assertTrue(ContainerType.ITEM.createHandler(fresh) == null,
                    "Fresh machine unexpectedly creates an inventory: " + id);
            for (int size : new int[]{1, 34, 73}) {
                var stacks = new ArrayList<ItemStack>();
                for (int i = 0; i < size; i++) {
                    stacks.add(i % 3 == 0 ? ItemStack.EMPTY : new ItemStack(Items.DIAMOND, i + 1));
                }
                ItemStack saved = new ItemStack(item);
                saved.set(MekanismDataComponents.ATTACHED_ITEMS, new AttachedItems(stacks));
                ItemStack before = saved.copy();
                var contents = ContainerType.ITEM.getAttachmentContainersIfPresent(saved);
                helper.assertTrue(contents.size() == size, "Layout truncated: " + id);
                for (int i = 0; i < size; i++) {
                    helper.assertTrue(ItemStack.matches(stacks.get(i), contents.get(i).getStack()),
                            "Layout mismatch: " + id + " slot " + i);
                }
                helper.assertTrue(ItemStack.matches(before, saved), "Read mutated saved contents: " + id);
            }
        }
        helper.assertTrue(checked > 0, "No native machines were tested");
        helper.assertTrue(!ContainerType.ITEM.supports(new ItemStack(Items.STONE)),
                "Unrelated item acquired Mekanism inventory support");
        MekanismMagic.LOGGER.info("Machine item regression: checked {} registered machines", checked);
        helper.succeed();
    }
}
