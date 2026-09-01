package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;

import java.util.function.Supplier;

/** Applies a machine's per-side Source mode to its shared Source storage. */
public final class SourceModeCapability implements ISourceCap {
    private final Supplier<SourceStorage> storage;
    private final Supplier<ArsSourceMachineBlockEntity.SourceMode> mode;

    public SourceModeCapability(
            Supplier<SourceStorage> storage,
            Supplier<ArsSourceMachineBlockEntity.SourceMode> mode) {
        this.storage = storage;
        this.mode = mode;
    }

    private boolean canInput() {
        return mode.get().allowsInput();
    }

    private boolean canOutput() {
        return mode.get().allowsOutput();
    }

    @Override
    public boolean canAcceptSource(int source) {
        return canInput() && storage.get().canAcceptSource(source);
    }

    @Override
    public boolean canProvideSource(int source) {
        return canOutput() && storage.get().canProvideSource(source);
    }

    @Override
    public int getMaxExtract() {
        return canOutput() ? storage.get().getMaxExtract() : 0;
    }

    @Override
    public int getMaxReceive() {
        return canInput() ? storage.get().getMaxReceive() : 0;
    }

    @Override
    public int getSource() {
        return storage.get().getSource();
    }

    @Override
    public int getSourceCapacity() {
        return storage.get().getSourceCapacity();
    }

    @Override
    public void setSource(int source) {
        // External sided views must use receiveSource/extractSource so the
        // configured mode and per-tick rate are always enforced.
    }

    @Override
    public void setMaxSource(int max) {
        // Capacity belongs to the machine/tier. Exposing this mutator through
        // a cached sided capability would let external callers bypass both
        // direction modes and upgrade-derived limits.
    }

    @Override
    public int receiveSource(int source, boolean simulate) {
        return canInput() ? storage.get().receiveSource(source, simulate) : 0;
    }

    @Override
    public int extractSource(int source, boolean simulate) {
        return canOutput() ? storage.get().extractSource(source, simulate) : 0;
    }
}
