package com.example.mekanismmagic.integration.naturesaura;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Pure lane-lock reconciliation used by the factory and offline tests. */
public final class NatureAltarFactoryLanePolicy {
    private NatureAltarFactoryLanePolicy() {
    }

    /**
     * Resolves a lane against the current recipe catalog. Progress belongs to
     * one locked recipe only and is reset when that identity disappears or
     * changes after a reload.
     */
    public static <T> LaneState<T> reconcile(
            @Nullable T previousLock,
            @Nullable T selectedRecipe,
            int progress) {
        if (selectedRecipe == null) {
            return new LaneState<>(null, 0);
        }
        boolean sameRecipe = Objects.equals(previousLock, selectedRecipe);
        return new LaneState<>(selectedRecipe,
                sameRecipe ? Math.max(0, progress) : 0);
    }

    public static <T> LaneState<T> completed() {
        return new LaneState<>(null, 0);
    }

    public record LaneState<T>(@Nullable T lockedRecipe, int progress) {
        public LaneState {
            progress = Math.max(0, progress);
        }
    }
}
