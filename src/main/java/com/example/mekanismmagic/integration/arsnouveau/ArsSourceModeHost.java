package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.core.Direction;

/**
 * Common side-mode contract for machines that expose Ars Nouveau Source.
 */
public interface ArsSourceModeHost {
    ArsSourceMachineBlockEntity.SourceMode getSourceMode(Direction side);

    void cycleSourceMode(int index, int delta);

    default void cycleSourceMode(int index) {
        cycleSourceMode(index, 1);
    }

    default ArsSourceMachineBlockEntity.SourceMode sourceModeTarget(
            int delta) {
        Direction[] directions = Direction.values();
        ArsSourceMachineBlockEntity.SourceMode common =
                getSourceMode(directions[0]);
        for (int index = 1; index < directions.length; index++) {
            if (getSourceMode(directions[index]) != common) {
                return ArsSourceMachineBlockEntity.SourceMode.NONE;
            }
        }
        return common.shift(delta);
    }

    default void shiftAllSourceModes(int delta) {
        ArsSourceMachineBlockEntity.SourceMode target =
                sourceModeTarget(delta);
        Direction[] directions = Direction.values();
        for (int index = 0; index < directions.length; index++) {
            ArsSourceMachineBlockEntity.SourceMode current =
                    getSourceMode(directions[index]);
            cycleSourceMode(index,
                    target.ordinal() - current.ordinal());
        }
    }
}
