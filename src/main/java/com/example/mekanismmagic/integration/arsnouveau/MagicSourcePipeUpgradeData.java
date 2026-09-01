package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.upgrade.transmitter.TransmitterUpgradeData;

/** Data that must survive an in-world alloy upgrade of a Source pipe. */
public final class MagicSourcePipeUpgradeData extends TransmitterUpgradeData {
    public final int source;

    public MagicSourcePipeUpgradeData(boolean redstoneReactive,
                                      ConnectionType[] connectionTypes,
                                      int source) {
        super(redstoneReactive, connectionTypes);
        this.source = Math.max(0, source);
    }
}
