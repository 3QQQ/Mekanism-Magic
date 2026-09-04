package com.example.mekanismmagic.event;

import com.example.mekanismmagic.MekanismMagic;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime safety net for every Mekanism-style machine owned by this mod.
 *
 * <p>Mekanism normally transfers a tile's persistent data components through
 * each block's loot table. Keeping this invariant here as well means a future
 * machine cannot lose its inventory merely because its initial loot table was
 * generated without {@code minecraft:copy_components}.</p>
 */
public final class MachineDropPreserver {
    private MachineDropPreserver() {
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        Block block = event.getState().getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (!MekanismMagic.MOD_ID.equals(blockId.getNamespace())) {
            return;
        }

        BlockEntity blockEntity = event.getBlockEntity();
        if (!(blockEntity instanceof TileEntityMekanism machine)) {
            return;
        }

        Item machineItem = block.asItem();
        if (machineItem == Items.AIR) {
            return;
        }

        boolean stateAssigned = false;
        List<ItemEntity> splitDrops = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack droppedStack = drop.getItem();
            if (!droppedStack.is(machineItem) || droppedStack.isEmpty()) {
                continue;
            }
            int count = droppedStack.getCount();
            if (!stateAssigned) {
                ItemStack statefulMachine = droppedStack.copyWithCount(1);
                // BlockEntity#saveToItem deliberately removes state already
                // represented by data components from BLOCK_ENTITY_DATA, then
                // applies those components separately. This preserves custom
                // machine state (long output buffers, Source and recipe
                // selection) without duplicating Mekanism inventory/energy.
                machine.saveToItem(statefulMachine,
                        event.getLevel().registryAccess());
                drop.setItem(statefulMachine);
                stateAssigned = true;

                // Data components belong to the whole ItemStack. Leaving a
                // stateful stack at count > 1 would clone the complete machine
                // inventory whenever a player splits it. Keep exactly one
                // stateful item and move the remaining count to a fresh stack.
                if (count > 1) {
                    var velocity = drop.getDeltaMovement();
                    ItemEntity remainder = new ItemEntity(
                            event.getLevel(), drop.getX(), drop.getY(),
                            drop.getZ(), new ItemStack(machineItem, count - 1),
                            velocity.x, velocity.y, velocity.z);
                    remainder.setDefaultPickUpDelay();
                    splitDrops.add(remainder);
                }
            } else {
                // A loot modifier may contribute more matching drops after
                // the normal block loot. They remain ordinary empty machines;
                // only the first physical item is allowed to carry the tile.
                drop.setItem(new ItemStack(machineItem, count));
            }
        }
        if (!splitDrops.isEmpty()) {
            event.getDrops().addAll(splitDrops);
        }
    }
}
