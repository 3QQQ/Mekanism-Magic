package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.upgrade.transmitter.TransmitterUpgradeData;
import net.minecraft.nbt.CompoundTag;

/** Data that must survive an in-world alloy upgrade of a Source pipe. */
public final class MagicSourcePipeUpgradeData extends TransmitterUpgradeData {
    public final int source;
    public final CompoundTag resources;

    public MagicSourcePipeUpgradeData(boolean redstoneReactive,
                                      ConnectionType[] connectionTypes,
                                      int source) {
        this(redstoneReactive, connectionTypes, source, new CompoundTag());
    }

    public MagicSourcePipeUpgradeData(boolean redstoneReactive,
                                      ConnectionType[] connectionTypes,
                                      int source, CompoundTag resources) {
        super(redstoneReactive, connectionTypes);
        this.source = Math.max(0, source);
        this.resources = resources.copy();
    }
}
