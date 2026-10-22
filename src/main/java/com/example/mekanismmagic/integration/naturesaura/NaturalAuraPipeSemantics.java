package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Small deterministic rules shared by runtime networking and offline tests. */
final class NaturalAuraPipeSemantics {
    private NaturalAuraPipeSemantics() {
    }

    static boolean compatible(@Nullable IAuraType first,
                              @Nullable IAuraType second) {
        return first != null && second != null && first.isSimilar(second);
    }

    static int saveShare(int remaining, int segmentCapacity) {
        return Math.min(Math.max(0, remaining),
                Math.max(0, segmentCapacity));
    }

    static boolean mergeRepresentable(int first, int second) {
        return first >= 0 && second >= 0
                && (long) first + second <= Integer.MAX_VALUE;
    }

    static boolean preserveLocalShare(int localStored) {
        return localStored > 0;
    }

    static @Nullable ResourceLocation parseOptionalId(
            @Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null || parsed.getPath().isBlank()
                ? null : parsed;
    }
}
