package com.example.mekanismmagic.integration.common.network;

/** Internal Source operations, independent of the optional Ars API. */
public interface PipeSourceStorage {
    default boolean canAcceptSource(int amount) {
        return receiveSource(amount, true) > 0;
    }
    default boolean canProvideSource(int amount) {
        return extractSource(amount, true) > 0;
    }
    int getMaxExtract();
    int getMaxReceive();
    default boolean canExtract() { return canProvideSource(1); }
    default boolean canReceive() { return canAcceptSource(1); }
    int getSource();
    int getSourceCapacity();
    default int getMaxSource() { return getSourceCapacity(); }
    void setSource(int amount);
    void setMaxSource(int amount);
    int receiveSource(int amount, boolean simulate);
    int extractSource(int amount, boolean simulate);
}
