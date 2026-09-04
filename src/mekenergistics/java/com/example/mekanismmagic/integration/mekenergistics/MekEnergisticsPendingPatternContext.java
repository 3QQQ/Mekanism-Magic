package com.example.mekanismmagic.integration.mekenergistics;

import appeng.api.stacks.AEKey;

/** Carries a legacy pending pattern definition into MekE's anonymous feeder. */
public final class MekEnergisticsPendingPatternContext {
    private static final ThreadLocal<AEKey> DEFINITION =
            new ThreadLocal<>();

    private MekEnergisticsPendingPatternContext() {
    }

    public static void capture(AEKey definition) {
        if (definition == null) {
            DEFINITION.remove();
        } else {
            DEFINITION.set(definition);
        }
    }

    public static AEKey take() {
        AEKey definition = DEFINITION.get();
        DEFINITION.remove();
        return definition;
    }
}
