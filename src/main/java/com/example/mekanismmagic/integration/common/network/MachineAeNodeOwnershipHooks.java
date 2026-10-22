package com.example.mekanismmagic.integration.common.network;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Optional upgrade integrations can reserve sole ownership of a machine AE node. */
public final class MachineAeNodeOwnershipHooks {
    private static final List<Predicate<BlockEntity>> OWNERS =
            new CopyOnWriteArrayList<>();

    private MachineAeNodeOwnershipHooks() { }

    public static void register(Predicate<BlockEntity> owner) {
        if (owner != null) OWNERS.add(owner);
    }

    public static boolean externallyOwned(BlockEntity tile) {
        for (Predicate<BlockEntity> owner : OWNERS) {
            if (owner.test(tile)) return true;
        }
        return false;
    }
}
