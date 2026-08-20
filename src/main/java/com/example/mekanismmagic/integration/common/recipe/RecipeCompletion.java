package com.example.mekanismmagic.integration.common.recipe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional server-side action performed when a machine recipe completes.
 */
@FunctionalInterface
public interface RecipeCompletion {
    RecipeCompletion NONE = (level, position) -> true;

    boolean complete(ServerLevel level, BlockPos position);
}
