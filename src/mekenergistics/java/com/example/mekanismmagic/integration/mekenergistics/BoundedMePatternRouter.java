package com.example.mekanismmagic.integration.mekenergistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.beipuo.mekenergistics.blockentity.support.io.MeInputPort;
import com.beipuo.mekenergistics.blockentity.support.io.MeInputLayout;
import com.example.mekanismmagic.integration.common.recipe
        .BoundedRequestRouter;
import mekanism.api.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Bounded replacement for Mek Energistics' permutation input router. */
public final class BoundedMePatternRouter {
    private static final Map<MeInputLayout, Boolean> PROTECTED_LAYOUTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BoundedMePatternRouter() {
    }

    public static void protectLayout(MeInputLayout layout) {
        if (layout != null) {
            PROTECTED_LAYOUTS.put(layout, Boolean.TRUE);
        }
    }

    public static boolean isProtectedLayout(MeInputLayout layout) {
        return layout != null && PROTECTED_LAYOUTS.containsKey(layout);
    }

    public static boolean route(KeyCounter[] inputs,
                                List<? extends MeInputPort> ports) {
        Map<AEKey, Long> requests = normalizeInputs(inputs);
        if (requests == null) {
            return false;
        }
        Optional<List<BoundedRequestRouter.Allocation<AEKey, MeInputPort>>>
                plan = planRequests(requests, ports);
        if (plan.isEmpty()) {
            return false;
        }
        return executePlan(plan.get(), ports);
    }

    static boolean executePlan(
            List<BoundedRequestRouter.Allocation<AEKey, MeInputPort>> plan,
            List<? extends MeInputPort> ports) {
        IdentityHashMap<MeInputPort, Object> snapshots =
                snapshotPorts(ports);
        try {
            for (BoundedRequestRouter.Allocation<AEKey, MeInputPort>
                    allocation : plan) {
                long inserted = allocation.port().insert(
                        allocation.key(), allocation.amount(),
                        Action.EXECUTE);
                if (inserted != allocation.amount()) {
                    restore(snapshots);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | Error failure) {
            restore(snapshots);
            throw failure;
        }
    }

    public static long maxAcceptedCopies(
            KeyCounter[] inputs, List<? extends MeInputPort> ports) {
        Map<AEKey, Long> base = normalizeInputs(inputs);
        if (base == null || ports == null || ports.isEmpty()) {
            return 0;
        }
        long upperBound = Long.MAX_VALUE;
        for (Map.Entry<AEKey, Long> request : base.entrySet()) {
            long capacity = 0;
            for (MeInputPort port : uniquePorts(ports)) {
                if (port.supports(request.getKey())) {
                    long probeAmount = request.getKey() instanceof AEItemKey
                            || request.getKey() instanceof AEFluidKey
                            ? Integer.MAX_VALUE : Long.MAX_VALUE;
                    capacity = saturatingAdd(capacity,
                            Math.max(0, port.insert(request.getKey(),
                                    probeAmount, Action.SIMULATE)));
                }
            }
            upperBound = Math.min(upperBound,
                    capacity / request.getValue());
        }
        if (upperBound <= 0) {
            return 0;
        }

        long low = 0;
        long high = upperBound;
        while (low < high) {
            long midpoint = low + ((high - low) >>> 1);
            if (midpoint == low) {
                midpoint = high;
            }
            Map<AEKey, Long> scaled = scale(base, midpoint);
            if (scaled != null
                    && planRequests(scaled, ports).isPresent()) {
                low = midpoint;
            } else {
                high = midpoint - 1;
            }
        }
        return low;
    }

    static Optional<List<BoundedRequestRouter.Allocation<
            AEKey, MeInputPort>>> planRequests(
            Map<AEKey, Long> requests,
            List<? extends MeInputPort> ports) {
        return BoundedRequestRouter.plan(requests, ports,
                (port, key, alreadyPlanned, requested) -> {
                    if (!port.supports(key)) {
                        return 0;
                    }
                    long totalRequest = saturatingAdd(
                            alreadyPlanned, requested);
                    long totalAccepted = Math.max(0,
                            port.insert(key, totalRequest,
                                    Action.SIMULATE));
                    return Math.max(0,
                            totalAccepted - alreadyPlanned);
                });
    }

    static Map<AEKey, Long> normalizeInputs(KeyCounter[] inputs) {
        if (inputs == null || inputs.length == 0) {
            return null;
        }
        Map<AEKey, Long> requests = new LinkedHashMap<>();
        try {
            for (KeyCounter counter : inputs) {
                if (counter == null || counter.size() != 1) {
                    return null;
                }
                var iterator = counter.iterator();
                if (!iterator.hasNext()) {
                    return null;
                }
                var entry = iterator.next();
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (key == null || amount <= 0 || iterator.hasNext()) {
                    return null;
                }
                requests.merge(key, amount, Math::addExact);
            }
        } catch (ArithmeticException overflow) {
            return null;
        }
        return requests.isEmpty() ? null : requests;
    }

    private static Map<AEKey, Long> scale(
            Map<AEKey, Long> base, long copies) {
        if (copies <= 0) {
            return null;
        }
        Map<AEKey, Long> scaled = new LinkedHashMap<>();
        try {
            for (Map.Entry<AEKey, Long> entry : base.entrySet()) {
                scaled.put(entry.getKey(), Math.multiplyExact(
                        entry.getValue(), copies));
            }
        } catch (ArithmeticException overflow) {
            return null;
        }
        return scaled;
    }

    private static IdentityHashMap<MeInputPort, Object> snapshotPorts(
            List<? extends MeInputPort> ports) {
        IdentityHashMap<MeInputPort, Object> snapshots =
                new IdentityHashMap<>();
        for (MeInputPort port : uniquePorts(ports)) {
            snapshots.put(port, port.snapshot());
        }
        return snapshots;
    }

    private static List<MeInputPort> uniquePorts(
            List<? extends MeInputPort> ports) {
        IdentityHashMap<MeInputPort, Boolean> seen =
                new IdentityHashMap<>();
        List<MeInputPort> unique = new ArrayList<>();
        if (ports != null) {
            for (MeInputPort port : ports) {
                if (port != null
                        && seen.put(port, Boolean.TRUE) == null) {
                    unique.add(port);
                }
            }
        }
        return unique;
    }

    private static void restore(
            IdentityHashMap<MeInputPort, Object> snapshots) {
        snapshots.forEach(MeInputPort::restore);
    }

    private static long saturatingAdd(long left, long right) {
        if (left < 0 || right < 0) {
            return 0;
        }
        return left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }
}
