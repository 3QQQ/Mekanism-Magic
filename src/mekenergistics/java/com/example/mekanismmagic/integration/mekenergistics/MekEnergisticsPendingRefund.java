package com.example.mekanismmagic.integration.mekenergistics;

import appeng.api.stacks.AEKey;

/** Safe access to MekE's network-or-recovery-buffer refund path. */
public interface MekEnergisticsPendingRefund {
    void mekanismMagic$refundPending(AEKey key, long amount);
}
