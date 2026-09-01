package com.example.mekanismmagic.event;

import com.example.mekanismmagic.MekanismMagic;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

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

        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().is(machineItem)) {
                // BlockEntity#saveToItem deliberately removes state already
                // represented by data components from BLOCK_ENTITY_DATA, then
                // applies those components separately. This preserves custom
                // machine state (long output buffers, Source and recipe
                // selection) without duplicating Mekanism inventory/energy.
                machine.saveToItem(drop.getItem(),
                        event.getLevel().registryAccess());
                // Apply to one machine item only. A modified loot table that
                // creates duplicate block drops must not duplicate inventory.
                break;
            }
        }
    }
}
