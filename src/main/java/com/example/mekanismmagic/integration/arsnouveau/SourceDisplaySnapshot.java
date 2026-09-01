package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Server-authoritative values used only by the Source GUI. */
public final class SourceDisplaySnapshot {
    private int capacity = -1;
    private boolean creative;
    private boolean creativeKnown;

    public void addTrackers(
            MekanismContainer container,
            IntSupplier capacitySupplier,
            BooleanSupplier creativeSupplier) {
        container.track(SyncableInt.create(
                capacitySupplier::getAsInt,
                value -> capacity = Math.max(0, value)));
        container.track(SyncableBoolean.create(
                creativeSupplier::getAsBoolean,
                value -> {
                    creative = value;
                    creativeKnown = true;
                }));
    }

    public int capacityOr(int fallback) {
        return capacity >= 0 ? capacity : Math.max(0, fallback);
    }

    public boolean creativeOr(boolean fallback) {
        return creativeKnown ? creative : fallback;
    }
}
