package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** A cached capability view that follows its transmitter's current network. */
final class DynamicNaturalAuraView implements IAuraContainer {
    private final Supplier<? extends IAuraContainer> aura;
    private final boolean receive;
    private final boolean extract;
    private final BooleanSupplier compatible;

    DynamicNaturalAuraView(Supplier<? extends IAuraContainer> aura,
                           boolean receive, boolean extract,
                           BooleanSupplier compatible) {
        this.aura = Objects.requireNonNull(aura, "aura");
        this.receive = receive;
        this.extract = extract;
        this.compatible = Objects.requireNonNull(
                compatible, "compatible");
    }

    @Override
    public int storeAura(int amount, boolean simulate) {
        return receive && compatible.getAsBoolean()
                ? aura.get().storeAura(amount, simulate) : 0;
    }

    @Override
    public int drainAura(int amount, boolean simulate) {
        return extract && compatible.getAsBoolean()
                ? aura.get().drainAura(amount, simulate) : 0;
    }

    @Override
    public int getStoredAura() {
        return aura.get().getStoredAura();
    }

    @Override
    public int getMaxAura() {
        return aura.get().getMaxAura();
    }

    @Override
    public int getAuraColor() {
        return aura.get().getAuraColor();
    }

    @Override
    public boolean isAcceptableType(IAuraType type) {
        return compatible.getAsBoolean()
                && aura.get().isAcceptableType(type);
    }
}
