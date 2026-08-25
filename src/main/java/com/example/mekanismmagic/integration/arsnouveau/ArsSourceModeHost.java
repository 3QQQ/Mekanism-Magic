package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.core.Direction;

/**
 * Common side-mode contract for machines that expose Ars Nouveau Source.
 */
public interface ArsSourceModeHost {
    ArsSourceMachineBlockEntity.SourceMode getSourceMode(Direction side);

    void cycleSourceMode(int index);
}
