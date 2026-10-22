package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;

/** Optional stored-FE source, independent of MekE and isolated from Applied Flux classes. */
public final class Ae2AppliedFluxEnergySource
        implements Ae2MachineResourceAccess.ExternalFeSource {
    private static final Ae2AppliedFluxEnergySource INSTANCE =
            new Ae2AppliedFluxEnergySource();
    private static volatile boolean attempted;
    private static AEKey feKey;
    private static boolean registered;

    private Ae2AppliedFluxEnergySource() {
    }

    public static synchronized void register() {
        if (!registered && ModCompatibility.loaded("appflux")) {
            registered = true;
            Ae2MachineResourceAccess.registerExternalFeSource(INSTANCE);
        }
    }

    /** Resolve the public FluxKey.of(EnergyType.FE) API lazily after mod startup. */
    public static AEKey keyIfAvailable() {
        if (!ModCompatibility.loaded("appflux")) return null;
        if (!attempted) {
            synchronized (Ae2AppliedFluxEnergySource.class) {
                if (!attempted) {
                    try {
                        ClassLoader loader = Ae2AppliedFluxEnergySource.class.getClassLoader();
                        Class<?> type = Class.forName(
                                "com.glodblock.github.appflux.common.me.key.type.EnergyType", true, loader);
                        Object fe = type.getField("FE").get(null);
                        Class<?> keys = Class.forName(
                                "com.glodblock.github.appflux.common.me.key.FluxKey", true, loader);
                        Object key = keys.getMethod("of", type).invoke(null, fe);
                        if (key instanceof AEKey aeKey) feKey = aeKey;
                        else MekanismMagic.LOGGER.warn(
                                "Applied Flux returned no compatible FE key; native AE power remains available");
                    } catch (ReflectiveOperationException | LinkageError failure) {
                        MekanismMagic.LOGGER.warn(
                                "Applied Flux FE-key API is unavailable; native AE power remains available", failure);
                    } finally {
                        attempted = true;
                    }
                }
            }
        }
        return feKey;
    }

    @Override
    public long extractFe(IGrid grid, IActionSource actor, long maxFe, Actionable mode) {
        AEKey key = keyIfAvailable();
        if (grid == null || actor == null || key == null || maxFe <= 0L || mode == null) return 0L;
        long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(),
                grid.getStorageService().getInventory(), key, maxFe, actor, mode);
        return Math.max(0L, Math.min(maxFe, extracted));
    }

    /** Restoring rejected FE must not charge the grid for the same transaction twice. */
    @Override
    public long refundFe(IGrid grid, IActionSource actor, long amountFe) {
        AEKey key = keyIfAvailable();
        if (grid == null || actor == null || key == null || amountFe <= 0L) return 0L;
        long restored = grid.getStorageService().getInventory().insert(
                key, amountFe, Actionable.MODULATE, actor);
        return Math.max(0L, Math.min(amountFe, restored));
    }
}
