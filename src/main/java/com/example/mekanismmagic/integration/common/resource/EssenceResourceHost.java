package com.example.mekanismmagic.integration.common.resource;

import mekanism.api.Action;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** One point is one native essence unit. All transports share the machine's single ledger. */
public interface EssenceResourceHost {
    int getStoredEssence(int channel);
    int getEssenceCapacity(int channel);

    /** Accepted points; SIMULATE must be strictly read-only. */
    int insertEssence(int channel, int requested, Action action);

    /** Withdrawn points; SIMULATE must be strictly read-only. */
    int extractEssence(int channel, int requested, Action action);

    /**
     * Returns points still owned by a caller after a short output transfer.
     * This is rollback of an extraction, never an externally available input
     * port. Callers must not refund points already accepted by their target.
     */
    default int refundExtractedEssence(int channel, int requested, Action action) {
        return 0;
    }

    default boolean canInputEssence(int channel) { return false; }
    default boolean canOutputEssence(int channel) { return false; }

    /** Pipe connection modes control routing; native essence ports do not depend on PFAA fluid sides. */
    default boolean canInputEssence(int channel, @Nullable Direction side) {
        return canInputEssence(channel);
    }

    default boolean canOutputEssence(int channel, @Nullable Direction side) {
        return canOutputEssence(channel);
    }
}
