package com.example.mekanismmagic.integration.ae2;

import appeng.helpers.patternprovider.PatternProviderLogic;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Weak registry used to re-decode vanilla AE providers after /reload. */
public final class Ae2NaturePatternCacheRefresher {
    private static final Map<PatternProviderLogic, Boolean> PROVIDERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Ae2NaturePatternCacheRefresher() {
    }

    public static void track(PatternProviderLogic provider) {
        if (provider != null && !provider.isClientSide()) {
            PROVIDERS.put(provider, Boolean.TRUE);
        }
    }

    public static int refreshAll() {
        List<PatternProviderLogic> snapshot;
        synchronized (PROVIDERS) {
            snapshot = List.copyOf(PROVIDERS.keySet());
        }
        int refreshed = 0;
        for (PatternProviderLogic provider : snapshot) {
            if (provider == null || provider.isClientSide()) {
                continue;
            }
            provider.updatePatterns();
            refreshed++;
        }
        return refreshed;
    }
}
