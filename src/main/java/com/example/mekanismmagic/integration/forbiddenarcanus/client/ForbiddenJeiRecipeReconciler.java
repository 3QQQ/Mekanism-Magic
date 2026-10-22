package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Computes recipe registration changes without depending on a client or JEI runtime. */
public final class ForbiddenJeiRecipeReconciler {
    private ForbiddenJeiRecipeReconciler() {
    }

    /**
     * Keeps the previous object when its ID and signature are unchanged. Registered and added
     * recipes follow fresh order; removed recipes follow previous order. Both inputs must have
     * unique IDs. Returned lists are immutable snapshots; recipe objects are intentionally reused.
     */
    public static <T, K> Change<T> reconcile(List<T> previous, List<T> fresh,
                                            Function<T, K> id, Function<T, String> signature) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(signature, "signature");
        List<T> previousSnapshot = List.copyOf(previous);
        List<T> freshSnapshot = List.copyOf(fresh);
        Map<K, T> previousById = index(previousSnapshot, id, "previous");
        Map<K, T> freshById = index(freshSnapshot, id, "fresh");
        List<T> registered = new ArrayList<>(freshSnapshot.size());
        List<T> added = new ArrayList<>();
        Set<K> retained = new HashSet<>();
        for (Map.Entry<K, T> entry : freshById.entrySet()) {
            T oldRecipe = previousById.get(entry.getKey());
            T freshRecipe = entry.getValue();
            if (oldRecipe != null && Objects.equals(signature.apply(oldRecipe), signature.apply(freshRecipe))) {
                registered.add(oldRecipe);
                retained.add(entry.getKey());
            } else {
                registered.add(freshRecipe);
                added.add(freshRecipe);
            }
        }
        List<T> removed = new ArrayList<>();
        for (Map.Entry<K, T> entry : previousById.entrySet()) {
            if (!retained.contains(entry.getKey())) {
                removed.add(entry.getValue());
            }
        }
        return new Change<>(registered, added, removed);
    }

    private static <T, K> Map<K, T> index(List<T> recipes, Function<T, K> id, String source) {
        Map<K, T> indexed = new LinkedHashMap<>();
        for (T recipe : recipes) {
            K key = id.apply(recipe);
            if (indexed.putIfAbsent(key, recipe) != null) {
                throw new IllegalArgumentException("Duplicate recipe ID in " + source + ": " + key);
            }
        }
        return indexed;
    }

    public record Change<T>(List<T> registered, List<T> added, List<T> removed) {
        public Change {
            registered = List.copyOf(registered);
            added = List.copyOf(added);
            removed = List.copyOf(removed);
        }
    }
}
