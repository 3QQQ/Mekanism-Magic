package com.example.mekanismmagic.blockentity;

/** Offline decision-table coverage for direct persistent-slot mutations. */
public final class PersistentInputMutationGuardSelfTest {
    private PersistentInputMutationGuardSelfTest() {
    }

    public static void main(String[] args) {
        expect(PersistentInputMutationGuard.Mutation.NONE,
                true, true, false, 0, 0);
        expect(PersistentInputMutationGuard.Mutation.INSERT,
                true, false, false, 0, 1);
        expect(PersistentInputMutationGuard.Mutation.CLEAR,
                false, true, false, 1, 0);
        expect(PersistentInputMutationGuard.Mutation.REPLACE,
                false, false, false, 1, 1);
        expect(PersistentInputMutationGuard.Mutation.GROW,
                false, false, true, 1, 2);
        expect(PersistentInputMutationGuard.Mutation.SHRINK,
                false, false, true, 2, 1);
        expect(PersistentInputMutationGuard.Mutation.NONE,
                false, false, true, 2, 2);

    }

    private static void expect(
            PersistentInputMutationGuard.Mutation expected,
            boolean currentEmpty, boolean replacementEmpty,
            boolean same, int currentCount, int replacementCount) {
        PersistentInputMutationGuard.Mutation actual =
                PersistentInputMutationGuard.classify(currentEmpty,
                        replacementEmpty, same, currentCount,
                        replacementCount);
        if (actual != expected) {
            throw new AssertionError("expected " + expected
                    + " but got " + actual);
        }
    }
}
