package com.example.mekanismmagic.integration.naturesaura;

/** Pure arithmetic for the offering table's one-start-item, whole-stack trigger. */
final class OfferingBatchPolicy {
    private OfferingBatchPolicy() {
    }

    static int operations(int inputCount, int ingredientAmount) {
        return inputCount <= 0 || ingredientAmount <= 0
                ? 0 : inputCount / ingredientAmount;
    }

    static int outputCount(int operations, int outputPerOperation) {
        long count = (long) operations * outputPerOperation;
        return operations <= 0 || outputPerOperation <= 0
                || count > Integer.MAX_VALUE ? 0 : (int) count;
    }

    static int consumedInput(int inputCount, int ingredientAmount) {
        return operations(inputCount, ingredientAmount) * ingredientAmount;
    }

    static int witherRoseBonus(int randomValue) {
        if (randomValue < 0 || randomValue >= 5) {
            throw new IllegalArgumentException("Expected nextInt(5)");
        }
        // Original loop starts at nextInt(5) + 3 and includes zero.
        return randomValue + 4;
    }
}
