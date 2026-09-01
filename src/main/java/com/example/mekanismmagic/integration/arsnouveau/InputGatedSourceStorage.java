package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.common.capability.SourceStorage;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Source storage that can dynamically reject input without losing contents. */
public final class InputGatedSourceStorage extends SourceStorage {
    private final BooleanSupplier inputAllowed;
    private final Runnable contentsChanged;

    public InputGatedSourceStorage(
            int capacity, int maxReceive, int maxExtract,
            BooleanSupplier inputAllowed, Runnable contentsChanged) {
        super(capacity, maxReceive, maxExtract);
        this.inputAllowed = Objects.requireNonNull(inputAllowed);
        this.contentsChanged = Objects.requireNonNull(contentsChanged);
    }

    private boolean acceptsInput() {
        return inputAllowed.getAsBoolean();
    }

    @Override
    public int getMaxReceive() {
        return acceptsInput() ? super.getMaxReceive() : 0;
    }

    @Override
    public boolean canAcceptSource(int amount) {
        return acceptsInput() && super.canAcceptSource(amount);
    }

    @Override
    public boolean canReceive() {
        return acceptsInput() && super.canReceive();
    }

    @Override
    public int receiveSource(int amount, boolean simulate) {
        return acceptsInput()
                ? super.receiveSource(amount, simulate) : 0;
    }

    @Override
    public void onContentsChanged() {
        contentsChanged.run();
    }
}
