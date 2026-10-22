package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import java.util.ArrayList;
import java.util.List;

/** Standalone checks for stable registration identities across recipe reloads. */
public final class ForbiddenJeiRecipeReconcilerSelfTest {
    private ForbiddenJeiRecipeReconcilerSelfTest() {
    }

    public static void main(String[] args) {
        unchangedRecipesReusePreviousObjects();
        changesAddFreshObjectsAndRemoveExactOldObjects();
        reorderingOnlyChangesRegisteredOrder();
        duplicateFreshIdsAreRejected();
        duplicatePreviousIdsAreRejected();
        mutableSourceListsCannotChangeResults();
        directChangeConstructionAlsoSnapshotsLists();
        repeatedReloadsAndEmptyCatalogsDoNotAccumulateRecipes();
        System.out.println("ForbiddenJeiRecipeReconcilerSelfTest: 8 checks passed");
    }

    private static void unchangedRecipesReusePreviousObjects() {
        Recipe previous = recipe("a", "v1");
        Recipe fresh = recipe("a", "v1");
        var change = reconcile(List.of(previous), List.of(fresh));
        require(previous != fresh && change.registered().getFirst() == previous,
                "An unchanged ID/signature did not reuse the exact previously registered object");
        require(change.added().isEmpty() && change.removed().isEmpty(),
                "Unchanged recipes produced registration changes");
    }

    private static void changesAddFreshObjectsAndRemoveExactOldObjects() {
        Recipe oldChanged = recipe("changed", "old");
        Recipe deleted = recipe("deleted", "v1");
        Recipe retained = recipe("retained", "v1");
        Recipe newChanged = recipe("changed", "new");
        Recipe added = recipe("added", "v1");
        var change = reconcile(List.of(oldChanged, deleted, retained),
                List.of(recipe("retained", "v1"), newChanged, added));
        sameObjects(change.registered(), retained, newChanged, added);
        sameObjects(change.added(), newChanged, added);
        sameObjects(change.removed(), oldChanged, deleted);
    }

    private static void reorderingOnlyChangesRegisteredOrder() {
        Recipe first = recipe("first", "v1");
        Recipe second = recipe("second", "v1");
        var change = reconcile(List.of(first, second),
                List.of(recipe("second", "v1"), recipe("first", "v1")));
        sameObjects(change.registered(), second, first);
        require(change.added().isEmpty() && change.removed().isEmpty(),
                "A catalog reorder re-registered unchanged recipes");
    }

    private static void duplicateFreshIdsAreRejected() {
        Recipe first = recipe("duplicate", "v1");
        expectIllegalArgument(() -> reconcile(List.of(), List.of(first, first)));
        expectIllegalArgument(() -> reconcile(List.of(first),
                List.of(recipe("duplicate", "v1"), recipe("duplicate", "v2"))));
    }

    private static void duplicatePreviousIdsAreRejected() {
        expectIllegalArgument(() -> reconcile(List.of(recipe("duplicate", "v1"),
                recipe("duplicate", "v2")), List.of()));
    }

    private static void mutableSourceListsCannotChangeResults() {
        Recipe old = recipe("a", "old");
        Recipe fresh = recipe("a", "fresh");
        List<Recipe> previousSource = new ArrayList<>(List.of(old));
        List<Recipe> freshSource = new ArrayList<>(List.of(fresh));
        var change = reconcile(previousSource, freshSource);
        previousSource.clear();
        freshSource.set(0, recipe("unrelated", "v1"));
        sameObjects(change.registered(), fresh);
        sameObjects(change.added(), fresh);
        sameObjects(change.removed(), old);
        assertImmutable(change);
    }

    private static void directChangeConstructionAlsoSnapshotsLists() {
        Recipe registered = recipe("registered", "v1");
        Recipe added = recipe("added", "v1");
        Recipe removed = recipe("removed", "v1");
        List<Recipe> registeredSource = new ArrayList<>(List.of(registered));
        List<Recipe> addedSource = new ArrayList<>(List.of(added));
        List<Recipe> removedSource = new ArrayList<>(List.of(removed));
        var change = new ForbiddenJeiRecipeReconciler.Change<>(registeredSource, addedSource, removedSource);
        registeredSource.clear();
        addedSource.clear();
        removedSource.clear();
        sameObjects(change.registered(), registered);
        sameObjects(change.added(), added);
        sameObjects(change.removed(), removed);
        assertImmutable(change);
    }

    private static void repeatedReloadsAndEmptyCatalogsDoNotAccumulateRecipes() {
        Recipe first = recipe("a", "v1");
        Recipe second = recipe("b", "v1");
        var initial = reconcile(List.of(), List.of(first, second));
        sameObjects(initial.registered(), first, second);
        sameObjects(initial.added(), first, second);
        require(initial.removed().isEmpty(), "Initial recipes removed nonexistent registrations");
        var repeated = reconcile(initial.registered(), List.of(recipe("a", "v1"), recipe("b", "v1")));
        sameObjects(repeated.registered(), first, second);
        require(repeated.added().isEmpty() && repeated.removed().isEmpty(),
                "Reloading the same catalog accumulated duplicate registrations");
        var cleared = reconcile(repeated.registered(), List.of());
        require(cleared.registered().isEmpty() && cleared.added().isEmpty(),
                "An empty fresh catalog retained recipes");
        sameObjects(cleared.removed(), first, second);
        var empty = reconcile(List.of(), List.of());
        require(empty.registered().isEmpty() && empty.added().isEmpty() && empty.removed().isEmpty(),
                "Empty catalogs generated changes");
        assertImmutable(empty);
    }

    private static ForbiddenJeiRecipeReconciler.Change<Recipe> reconcile(List<Recipe> previous, List<Recipe> fresh) {
        return ForbiddenJeiRecipeReconciler.reconcile(previous, fresh, Recipe::id, Recipe::signature);
    }

    private static Recipe recipe(String id, String signature) {
        return new Recipe(id, signature);
    }

    private static void sameObjects(List<Recipe> actual, Recipe... expected) {
        require(actual.size() == expected.length, "Wrong result size: " + actual);
        for (int index = 0; index < expected.length; index++) {
            require(actual.get(index) == expected[index], "Wrong recipe object at index " + index);
        }
    }

    private static void assertImmutable(ForbiddenJeiRecipeReconciler.Change<Recipe> change) {
        for (List<Recipe> list : List.of(change.registered(), change.added(), change.removed())) {
            try {
                list.add(recipe("mutation", "v1"));
                throw new AssertionError("Returned list is mutable");
            } catch (UnsupportedOperationException expected) {
                // Registration history must not change through its returned views.
            }
        }
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Duplicate IDs were silently discarded");
        } catch (IllegalArgumentException expected) {
            // Invalid catalogs must fail before registration changes are applied.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Recipe(String id, String signature) {
    }
}
