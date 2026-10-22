package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import org.jetbrains.annotations.Nullable;

/** Cumulative type and integer-capacity guard for orphan network discovery. */
final class NaturalAuraMergeGuard {
    private @Nullable IAuraType type;
    private long stored;

    NaturalAuraMergeGuard() {
    }

    NaturalAuraMergeGuard(@Nullable IAuraType expectedType) {
        type = expectedType;
    }

    boolean accept(@Nullable IAuraType candidateType, int amount) {
        if (amount < 0 || stored + amount > Integer.MAX_VALUE) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        if (candidateType == null || type != null
                && !NaturalAuraPipeSemantics.compatible(
                type, candidateType)) {
            return false;
        }
        if (type == null) {
            type = candidateType;
        }
        stored += amount;
        return true;
    }

    long stored() {
        return stored;
    }
}
