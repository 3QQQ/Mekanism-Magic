package com.example.mekanismmagic.integration.mekenergistics;

import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Weak registry used to re-decode MekE providers after /reload. */
public final class MekEnergisticsNaturePatternCacheRefresher {
    private static final Map<AbstractMeAeSupport<?>, Boolean> PROVIDERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MekEnergisticsNaturePatternCacheRefresher() {
    }

    public static void track(AbstractMeAeSupport<?> provider) {
        if (provider != null) {
            PROVIDERS.put(provider, Boolean.TRUE);
        }
    }

    public static int refreshAll() {
        List<AbstractMeAeSupport<?>> snapshot;
        synchronized (PROVIDERS) {
            snapshot = List.copyOf(PROVIDERS.keySet());
        }
        int refreshed = 0;
        for (AbstractMeAeSupport<?> provider : snapshot) {
            if (provider == null) {
                continue;
            }
            // updatePatterns rebuilds decoded wrappers and invokes MekE's
            // private requestCraftingUpdate through rebuildPatternCache(true).
            provider.updatePatterns();
            refreshed++;
        }
        return refreshed;
    }
}
