package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.event.MachineDropPreserver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Test-only world probes and real block-drop persistence fixtures. Never packaged in the mod. */
final class InternalNatureTestSupport {
    private InternalNatureTestSupport() { }

    @SuppressWarnings("unchecked")
    static <T extends NativeMagicMachineBlockEntity> T breakAndReplace(GameTestHelper helper, T tile) {
        BlockPos position = tile.getBlockPos();
        var block = tile.getBlockState().getBlock();
        var facing = tile.getDirection();
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(new ItemEntity(helper.getLevel(), position.getX(), position.getY(), position.getZ(), new ItemStack(block, 3)));
        MachineDropPreserver.onBlockDrops(new BlockDropsEvent(helper.getLevel(), position,
                tile.getBlockState(), tile, drops, null, ItemStack.EMPTY));
        var stateful = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA)).toList();
        helper.assertTrue(stateful.size() == 1 && stateful.getFirst().getCount() == 1
                        && drops.stream().mapToInt(drop -> drop.getItem().getCount()).sum() == 3,
                "A multi-drop machine copied its stored work into more than one item");
        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(position, block.defaultBlockState());
        ItemStack item = stateful.getFirst();
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, position, item), "Machine item lost custom state");
        T restored = (T) helper.getLevel().getBlockEntity(position);
        restored.applyComponentsFromItemStack(item);
        restored.setFacing(facing);
        return restored;
    }

    static Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 3, 4))) {
            if (!pos.equals(center)) result.put(pos.immutable(), level.getBlockState(pos));
        }
        return result;
    }

    static void assertUnchanged(GameTestHelper helper, Map<BlockPos, BlockState> snapshot) {
        snapshot.forEach((pos, state) -> helper.assertTrue(helper.getLevel().getBlockState(pos).equals(state),
                "Internal processing changed an external block at " + pos));
    }

    static final class NoWorldEvents implements AutoCloseable {
        private int placements, breaks, joins, initializations;
        private final Consumer<BlockEvent.EntityPlaceEvent> placing;
        private final Consumer<BlockEvent.BreakEvent> breaking;
        private final Consumer<EntityJoinLevelEvent> joining;
        private final Consumer<FinalizeSpawnEvent> initializing;

        NoWorldEvents(ServerLevel level, BlockPos machine) {
            placing = event -> { if (event.getLevel() == level && !event.getPos().equals(machine)) { placements++; event.setCanceled(true); } };
            breaking = event -> { if (event.getLevel() == level && !event.getPos().equals(machine)) { breaks++; event.setCanceled(true); } };
            joining = event -> { if (event.getLevel() == level) { joins++; event.setCanceled(true); } };
            initializing = event -> { if (event.getEntity().level() == level) { initializations++; event.setSpawnCancelled(true); } };
            NeoForge.EVENT_BUS.addListener(placing);
            NeoForge.EVENT_BUS.addListener(breaking);
            NeoForge.EVENT_BUS.addListener(joining);
            NeoForge.EVENT_BUS.addListener(initializing);
        }

        void assertUnused(GameTestHelper helper) {
            helper.assertTrue(placements == 0 && breaks == 0 && joins == 0 && initializations == 0,
                    "Machine touched world events: place=" + placements + ", break=" + breaks + ", join=" + joins + ", init=" + initializations);
        }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(placing);
            NeoForge.EVENT_BUS.unregister(breaking);
            NeoForge.EVENT_BUS.unregister(joining);
            NeoForge.EVENT_BUS.unregister(initializing);
        }
    }
}
