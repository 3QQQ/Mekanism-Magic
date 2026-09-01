package com.example.mekanismmagic.integration.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Optional structural endpoints for the Source transmitter model. */
public final class MagicSourceExternalEndpointHooks {
    private static final List<Endpoint> ENDPOINTS =
            new CopyOnWriteArrayList<>();

    private MagicSourceExternalEndpointHooks() {
    }

    public static void register(Endpoint endpoint) {
        if (endpoint != null) {
            ENDPOINTS.add(endpoint);
        }
    }

    public static boolean isEndpoint(Level level, BlockPos pos,
                                     Direction queriedSide) {
        for (Endpoint endpoint : ENDPOINTS) {
            if (endpoint.isEndpoint(level, pos, queriedSide)) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface Endpoint {
        boolean isEndpoint(Level level, BlockPos pos,
                           Direction queriedSide);
    }
}
