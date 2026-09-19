package com.example.mekanismmagic.container;

/** Explicit presentation opt-in on an existing menu slot; inventory identity, index and permissions never change. */
public interface RetainedSlotAccess {
    void mekanismMagic$positionRetainedSlot(int x, int y, boolean visible);
    void mekanismMagic$setRetainedVisible(boolean visible);
    void mekanismMagic$restoreRetainedSlot();
}
