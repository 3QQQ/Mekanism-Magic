package com.example.mekanismmagic.integration.naturesaura;

/** Offline checks for FE/J conversion, batch rounding and the native spawn interval floor. */
public final class NatureMachineEnergyMathSelfTest {
    private NatureMachineEnergyMathSelfTest() {
    }

    public static void main(String[] args) {
        require(NatureMachineEnergyMath.fieldBatchJoules(5_000, 20, 2.5) == 250_000,
                "The field's 100,000 FE batch was treated as 100,000 Joules");
        require(NatureMachineEnergyMath.averageFieldJoulesPerTick(5_000, 20, 20, 2.5) == 12_500,
                "The field's default 5,000 FE/t average changed");
        require(NatureMachineEnergyMath.fieldBatchJoules(5_000, 2, 2.5) == 25_000,
                "Energy upgrades were applied after lossy FE/J conversion");
        require(NatureMachineEnergyMath.fieldBatchJoules(7, 20, 0.12525) == 18
                        && NatureMachineEnergyMath.fieldBatchJoules(8, 20, 0.12525) == 21,
                "Fractional Joules were rounded per Aura instead of once per batch");
        require(NatureMachineEnergyMath.affordableFieldAura(5_000, 20, 20, 0.12525) == 7,
                "Partial funding did not choose the largest exactly payable Aura batch");
        require(NatureMachineEnergyMath.fieldBatchJoules(1, 1, 0.0001) == 1
                        && NatureMachineEnergyMath.fieldBatchJoules(5_000, 1, 0.0001) == 1,
                "Sub-Joule prices produced free Aura or charged each unit a whole Joule");
        require(NatureMachineEnergyMath.affordableFieldAura(5_000, 0, 1, 0.0001) == 0,
                "An unfunded fractional batch was accepted");
        for (double conversion : new double[]{0.0001, 0.12525, 0.5, 2.5, 10_000}) {
            for (int energy = 0; energy <= 500; energy++) {
                int actual = NatureMachineEnergyMath.affordableFieldAura(100, energy, 20, conversion);
                require(NatureMachineEnergyMath.fieldBatchJoules(actual, 20, conversion) <= energy,
                        "Binary-search affordability overspent the reserve");
                require(actual == 100
                                || NatureMachineEnergyMath.fieldBatchJoules(actual + 1, 20, conversion) > energy,
                        "Binary-search affordability left an affordable whole Aura unprocessed");
            }
        }
        require(NatureMachineEnergyMath.fieldBatchJoules(0, 20, 2.5) == 0
                        && NatureMachineEnergyMath.affordableFieldAura(100, -1, 20, 2.5) == 0
                        && NatureMachineEnergyMath.fieldBatchJoules(1, 0, 2.5) == Long.MAX_VALUE
                        && NatureMachineEnergyMath.fieldBatchJoules(1, 20, Double.NaN) == Long.MAX_VALUE
                        && NatureMachineEnergyMath.affordableFieldAura(Integer.MAX_VALUE, Long.MAX_VALUE,
                        Long.MAX_VALUE, Double.MAX_VALUE) == 0,
                "Invalid or overflowed field costs could be committed");

        require(!capped(60, 1) && !capped(60, 3) && !capped(200, Math.sqrt(10))
                        && !capped(200, 10),
                "Uncapped animal work did not retain the original Mekanism pricing path");
        require(capped(60, 10) && capped(22, Math.sqrt(10)) && capped(10, 10),
                "Animal work exceeding the realizable speed missed its pricing cap");
        require(animal(60, 1) == 5_400 && animal(60, 10) == 540,
                "Cow speed upgrades paid for more than the realizable threefold acceleration");
        require(animal(10, 1) == 600 && animal(20, 10) == 60,
                "Recipes already at the minimum interval were charged for unusable acceleration");
        require(animal(22, 1) == 726 && animal(22, 10) == 73,
                "Squaring the rounded 1.1 speed cap added a false Joule or lost a real fraction");
        require(animal(65, 1) == 6_338 && animal(65, 10) == 634,
                "A real fractional Joule at the speed cap was not rounded up");
    }

    private static boolean capped(int nativeTicks, double speed) {
        return NatureMachineEnergyMath.isAnimalSpeedCapped(nativeTicks, 20, speed);
    }

    private static long animal(int nativeTicks, double energyReduction) {
        return NatureMachineEnergyMath.cappedAnimalJoulesPerTick(600, nativeTicks, 20, energyReduction);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
