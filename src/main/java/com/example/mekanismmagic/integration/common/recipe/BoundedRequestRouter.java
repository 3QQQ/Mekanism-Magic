package com.example.mekanismmagic.integration.common.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded deterministic planner for routing keyed requests into ports that
 * may each hold only one key. It may conservatively reject an exotic layout,
 * but never performs permutation backtracking on the server thread.
 */
public final class BoundedRequestRouter {
    private BoundedRequestRouter() {
    }

    @FunctionalInterface
    public interface CapacityProbe<KEY, PORT> {
        long additionalCapacity(PORT port, KEY key,
                                long alreadyPlanned, long requested);
    }

    public record Allocation<KEY, PORT>(KEY key, PORT port, long amount) {
        public Allocation {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(port, "port");
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "Allocation amount must be positive");
            }
        }
    }

    public static <KEY, PORT> Optional<List<Allocation<KEY, PORT>>> plan(
            Map<KEY, Long> requested,
            List<? extends PORT> candidatePorts,
            CapacityProbe<KEY, PORT> capacityProbe) {
        if (requested == null || requested.isEmpty()
                || candidatePorts == null || candidatePorts.isEmpty()
                || capacityProbe == null) {
            return Optional.empty();
        }
        List<PORT> ports = uniquePorts(candidatePorts);
        if (ports.isEmpty()) {
            return Optional.empty();
        }
        List<Request<KEY>> requests = new ArrayList<>();
        int requestOrder = 0;
        for (Map.Entry<KEY, Long> entry : requested.entrySet()) {
            KEY key = entry.getKey();
            long amount = entry.getValue() == null ? 0L
                    : entry.getValue();
            if (key == null || amount <= 0) {
                return Optional.empty();
            }
            requests.add(new Request<>(key, amount,
                    requestOrder++, supportCount(
                    ports, key, amount, capacityProbe)));
        }
        if (requests.stream().anyMatch(request -> request.supports == 0)) {
            return Optional.empty();
        }

        int[] flexibility = new int[ports.size()];
        for (int port = 0; port < ports.size(); port++) {
            PORT candidate = ports.get(port);
            for (Request<KEY> request : requests) {
                if (capacity(capacityProbe, candidate, request.key,
                        0, request.amount) > 0) {
                    flexibility[port]++;
                }
            }
        }
        requests.sort(Comparator
                .comparingInt((Request<KEY> request) -> request.supports)
                .thenComparing((Request<KEY> request) -> request.amount,
                        Comparator.reverseOrder())
                .thenComparingInt(request -> request.order));

        List<Allocation<KEY, PORT>> wholeRequestPlan =
                matchWholeRequests(requests, ports, capacityProbe);
        if (wholeRequestPlan != null) {
            return Optional.of(List.copyOf(wholeRequestPlan));
        }

        IdentityHashMap<PORT, KEY> assignedKeys = new IdentityHashMap<>();
        IdentityHashMap<PORT, Long> plannedAmounts =
                new IdentityHashMap<>();
        List<Allocation<KEY, PORT>> allocations = new ArrayList<>();
        for (Request<KEY> request : requests) {
            long remaining = request.amount;
            List<Candidate<PORT>> candidates = new ArrayList<>();
            for (int index = 0; index < ports.size(); index++) {
                PORT port = ports.get(index);
                KEY assigned = assignedKeys.get(port);
                if (assigned != null && !Objects.equals(
                        assigned, request.key)) {
                    continue;
                }
                long alreadyPlanned = plannedAmounts.getOrDefault(
                        port, 0L);
                long accepted = capacity(capacityProbe, port,
                        request.key, alreadyPlanned, remaining);
                if (accepted > 0) {
                    candidates.add(new Candidate<>(port, index,
                            assigned != null, flexibility[index], accepted));
                }
            }
            candidates.sort(Comparator
                    .comparing((Candidate<PORT> candidate) ->
                            !candidate.alreadyAssigned)
                    .thenComparingInt(candidate -> candidate.flexibility)
                    .thenComparingLong(candidate -> candidate.capacity)
                    .thenComparingInt(candidate -> candidate.index));
            for (Candidate<PORT> candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                long amount = Math.min(remaining, candidate.capacity);
                assignedKeys.put(candidate.port, request.key);
                plannedAmounts.merge(candidate.port, amount,
                        BoundedRequestRouter::saturatingAdd);
                allocations.add(new Allocation<>(request.key,
                        candidate.port, amount));
                remaining -= amount;
            }
            if (remaining > 0) {
                return Optional.empty();
            }
        }
        return Optional.of(List.copyOf(allocations));
    }

    /** Exact bipartite matching for the normal one-stack-per-key case. */
    private static <KEY, PORT> List<Allocation<KEY, PORT>>
    matchWholeRequests(List<Request<KEY>> requests, List<PORT> ports,
                       CapacityProbe<KEY, PORT> probe) {
        boolean[][] compatible = new boolean[requests.size()][ports.size()];
        for (int request = 0; request < requests.size(); request++) {
            boolean hasWholePort = false;
            Request<KEY> entry = requests.get(request);
            for (int port = 0; port < ports.size(); port++) {
                compatible[request][port] = capacity(probe,
                        ports.get(port), entry.key, 0, entry.amount)
                        >= entry.amount;
                hasWholePort |= compatible[request][port];
            }
            if (!hasWholePort) {
                return null;
            }
        }
        int[] portOwners = new int[ports.size()];
        int[] requestPorts = new int[requests.size()];
        java.util.Arrays.fill(portOwners, -1);
        java.util.Arrays.fill(requestPorts, -1);
        for (int request = 0; request < requests.size(); request++) {
            if (!assignWholeRequest(request, compatible,
                    portOwners, requestPorts,
                    new boolean[ports.size()])) {
                return null;
            }
        }
        List<Allocation<KEY, PORT>> allocations =
                new ArrayList<>(requests.size());
        for (int request = 0; request < requests.size(); request++) {
            Request<KEY> entry = requests.get(request);
            allocations.add(new Allocation<>(entry.key,
                    ports.get(requestPorts[request]), entry.amount));
        }
        return allocations;
    }

    private static boolean assignWholeRequest(
            int request, boolean[][] compatible, int[] portOwners,
            int[] requestPorts, boolean[] visitedPorts) {
        for (int port = 0; port < portOwners.length; port++) {
            if (!compatible[request][port] || visitedPorts[port]) {
                continue;
            }
            visitedPorts[port] = true;
            int previous = portOwners[port];
            if (previous < 0 || assignWholeRequest(previous,
                    compatible, portOwners, requestPorts, visitedPorts)) {
                portOwners[port] = request;
                requestPorts[request] = port;
                return true;
            }
        }
        return false;
    }

    private static <KEY, PORT> int supportCount(
            List<PORT> ports, KEY key, long amount,
            CapacityProbe<KEY, PORT> probe) {
        int supports = 0;
        for (PORT port : ports) {
            if (capacity(probe, port, key, 0, amount) > 0) {
                supports++;
            }
        }
        return supports;
    }

    private static <KEY, PORT> long capacity(
            CapacityProbe<KEY, PORT> probe, PORT port, KEY key,
            long alreadyPlanned, long requested) {
        if (requested <= 0 || alreadyPlanned < 0) {
            return 0;
        }
        return Math.min(requested, Math.max(0,
                probe.additionalCapacity(port, key,
                        alreadyPlanned, requested)));
    }

    private static <PORT> List<PORT> uniquePorts(
            List<? extends PORT> candidatePorts) {
        IdentityHashMap<PORT, Boolean> seen = new IdentityHashMap<>();
        List<PORT> ports = new ArrayList<>(candidatePorts.size());
        for (PORT port : candidatePorts) {
            if (port != null && seen.put(port, Boolean.TRUE) == null) {
                ports.add(port);
            }
        }
        return ports;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    private record Request<KEY>(KEY key, Long amount,
                                int order, int supports) {
    }

    private record Candidate<PORT>(PORT port, int index,
                                   boolean alreadyAssigned,
                                   int flexibility, long capacity) {
    }
}
