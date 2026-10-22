package com.example.mekanismmagic.integration.naturesaura;

import net.minecraft.core.Direction;

import java.util.Map;

/** Shared Aura configuration and display contract across both factory trees. */
public interface AuraSideModeHost {
    NatureAuraMachineBlockEntity.AuraSideMode getAuraSideMode(Direction side);

    void setAuraSideMode(Direction side,
                         NatureAuraMachineBlockEntity.AuraSideMode mode);

    /** Applies a batch with one persistence/capability notification. */
    void setAuraSideModes(Map<Direction,
            NatureAuraMachineBlockEntity.AuraSideMode> modes);

    int getStoredAura();

    int getAuraCapacity();

    int getAuraColor();

    boolean isCreativeAuraActive();
}
