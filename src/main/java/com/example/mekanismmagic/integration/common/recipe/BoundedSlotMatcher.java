package com.example.mekanismmagic.integration.common.recipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Polynomial-capacity matcher for assigning unit recipe ingredients to
 * inventory slots. This replaces recursive permutation search when several
 * tag ingredients can all use the same slots.
 */
public final class BoundedSlotMatcher {
    private BoundedSlotMatcher() {
    }

    /**
     * @param compatibility one row per unit ingredient, one column per slot
     * @param slotCapacities available item count in each slot
     * @return consumed count per slot, or empty if no complete assignment
     */
    public static Optional<int[]> match(
            boolean[][] compatibility, int[] slotCapacities) {
        return assign(compatibility, slotCapacities)
                .map(Assignment::slotUse);
    }

    /**
     * Returns both the aggregate slot consumption and the concrete slot used
     * for each unit ingredient. Dynamic recipes use the latter to preserve
     * components from the specific item they transform.
     */
    public static Optional<Assignment> assign(
            boolean[][] compatibility, int[] slotCapacities) {
        if (compatibility == null || slotCapacities == null) {
            return Optional.empty();
        }
        int ingredientCount = compatibility.length;
        int slotCount = slotCapacities.length;
        for (boolean[] row : compatibility) {
            if (row == null || row.length != slotCount) {
                throw new IllegalArgumentException(
                        "Compatibility rows must match the slot count");
            }
        }
        int[] used = new int[slotCount];
        if (ingredientCount == 0) {
            return Optional.of(new Assignment(used, new int[0]));
        }

        long available = 0;
        for (int capacity : slotCapacities) {
            available += Math.min(ingredientCount,
                    Math.max(0, capacity));
        }
        if (available < ingredientCount || slotCount == 0) {
            return Optional.empty();
        }

        int source = 0;
        int ingredientStart = 1;
        int slotStart = ingredientStart + ingredientCount;
        int sink = slotStart + slotCount;
        FlowNetwork network = new FlowNetwork(sink + 1);
        Edge[][] ingredientSlotEdges =
                new Edge[ingredientCount][slotCount];
        for (int ingredient = 0; ingredient < ingredientCount;
             ingredient++) {
            int ingredientNode = ingredientStart + ingredient;
            network.addEdge(source, ingredientNode, 1);
            boolean hasCandidate = false;
            for (int slot = 0; slot < slotCount; slot++) {
                if (compatibility[ingredient][slot]
                        && slotCapacities[slot] > 0) {
                    ingredientSlotEdges[ingredient][slot] =
                            network.addEdge(ingredientNode,
                            slotStart + slot, 1);
                    hasCandidate = true;
                }
            }
            if (!hasCandidate) {
                return Optional.empty();
            }
        }

        Edge[] slotEdges = new Edge[slotCount];
        for (int slot = 0; slot < slotCount; slot++) {
            int capacity = Math.min(ingredientCount,
                    Math.max(0, slotCapacities[slot]));
            slotEdges[slot] = network.addEdge(
                    slotStart + slot, sink, capacity);
        }
        if (network.maxFlow(source, sink) != ingredientCount) {
            return Optional.empty();
        }
        for (int slot = 0; slot < slotCount; slot++) {
            used[slot] = slotEdges[slot].originalCapacity
                    - slotEdges[slot].capacity;
        }
        int[] ingredientSlots = new int[ingredientCount];
        Arrays.fill(ingredientSlots, -1);
        for (int ingredient = 0; ingredient < ingredientCount;
             ingredient++) {
            for (int slot = 0; slot < slotCount; slot++) {
                Edge edge = ingredientSlotEdges[ingredient][slot];
                if (edge != null && edge.originalCapacity > edge.capacity) {
                    ingredientSlots[ingredient] = slot;
                    break;
                }
            }
            if (ingredientSlots[ingredient] < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new Assignment(used, ingredientSlots));
    }

    public record Assignment(int[] slotUse, int[] ingredientSlots) {
        public Assignment {
            slotUse = slotUse == null ? new int[0] : slotUse.clone();
            ingredientSlots = ingredientSlots == null
                    ? new int[0] : ingredientSlots.clone();
        }

        @Override
        public int[] slotUse() {
            return slotUse.clone();
        }

        @Override
        public int[] ingredientSlots() {
            return ingredientSlots.clone();
        }

        public int ingredientSlot(int ingredient) {
            return ingredient < 0 || ingredient >= ingredientSlots.length
                    ? -1 : ingredientSlots[ingredient];
        }
    }

    private static final class FlowNetwork {
        private final List<List<Edge>> graph;
        private final int[] level;
        private final int[] nextEdge;

        private FlowNetwork(int nodes) {
            graph = new ArrayList<>(nodes);
            for (int node = 0; node < nodes; node++) {
                graph.add(new ArrayList<>());
            }
            level = new int[nodes];
            nextEdge = new int[nodes];
        }

        private Edge addEdge(int from, int to, int capacity) {
            Edge forward = new Edge(to, graph.get(to).size(), capacity);
            Edge reverse = new Edge(from, graph.get(from).size(), 0);
            graph.get(from).add(forward);
            graph.get(to).add(reverse);
            return forward;
        }

        private int maxFlow(int source, int sink) {
            int flow = 0;
            while (buildLevels(source, sink)) {
                Arrays.fill(nextEdge, 0);
                int pushed;
                while ((pushed = push(source, sink,
                        Integer.MAX_VALUE)) > 0) {
                    flow += pushed;
                }
            }
            return flow;
        }

        private boolean buildLevels(int source, int sink) {
            Arrays.fill(level, -1);
            level[source] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);
            while (!queue.isEmpty()) {
                int node = queue.removeFirst();
                for (Edge edge : graph.get(node)) {
                    if (edge.capacity > 0 && level[edge.to] < 0) {
                        level[edge.to] = level[node] + 1;
                        queue.addLast(edge.to);
                    }
                }
            }
            return level[sink] >= 0;
        }

        private int push(int node, int sink, int available) {
            if (node == sink) {
                return available;
            }
            List<Edge> edges = graph.get(node);
            for (; nextEdge[node] < edges.size(); nextEdge[node]++) {
                Edge edge = edges.get(nextEdge[node]);
                if (edge.capacity <= 0
                        || level[edge.to] != level[node] + 1) {
                    continue;
                }
                int pushed = push(edge.to, sink,
                        Math.min(available, edge.capacity));
                if (pushed <= 0) {
                    continue;
                }
                edge.capacity -= pushed;
                graph.get(edge.to).get(edge.reverse).capacity += pushed;
                return pushed;
            }
            return 0;
        }
    }

    private static final class Edge {
        private final int to;
        private final int reverse;
        private final int originalCapacity;
        private int capacity;

        private Edge(int to, int reverse, int capacity) {
            this.to = to;
            this.reverse = reverse;
            this.capacity = capacity;
            originalCapacity = capacity;
        }
    }
}
