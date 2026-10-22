package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Integer-safe Aura buffer used by both isolated pipe segments and complete
 * transmitter networks. Its capacity may be dynamic because Mekanism derives
 * network capacity from all connected segments.
 */
final class NaturalAuraBuffer implements IAuraContainer {
    private static final int FALLBACK_COLOR = 0x42A6BC;

    private final Supplier<? extends IAuraType> typeSupplier;
    private final IntSupplier capacitySupplier;
    private final Runnable listener;
    private int aura;

    NaturalAuraBuffer(Supplier<? extends IAuraType> typeSupplier,
                      IntSupplier capacitySupplier, Runnable listener) {
        this.typeSupplier = Objects.requireNonNull(
                typeSupplier, "typeSupplier");
        this.capacitySupplier = Objects.requireNonNull(
                capacitySupplier, "capacitySupplier");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public synchronized int storeAura(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int space = Math.max(0, getMaxAura() - aura);
        int accepted = Math.min(amount, space);
        if (!simulate && accepted > 0) {
            aura += accepted;
            listener.run();
        }
        return accepted;
    }

    @Override
    public synchronized int drainAura(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int drained = Math.min(amount, aura);
        if (!simulate && drained > 0) {
            aura -= drained;
            listener.run();
        }
        return drained;
    }

    @Override
    public synchronized int getStoredAura() {
        return aura;
    }

    @Override
    public int getMaxAura() {
        return Math.max(0, capacitySupplier.getAsInt());
    }

    @Override
    public int getAuraColor() {
        IAuraType type = currentType();
        return type == null ? FALLBACK_COLOR : type.getColor();
    }

    @Override
    public boolean isAcceptableType(IAuraType candidate) {
        IAuraType type = currentType();
        return candidate != null && type != null
                && candidate.isSimilar(type);
    }

    synchronized void setAura(int amount) {
        int clamped = Math.max(0, Math.min(getMaxAura(), amount));
        if (clamped != aura) {
            aura = clamped;
            listener.run();
        }
    }

    synchronized void setAuraSilently(int amount) {
        aura = Math.max(0, Math.min(getMaxAura(), amount));
    }

    IAuraType currentType() {
        return typeSupplier.get();
    }
}
