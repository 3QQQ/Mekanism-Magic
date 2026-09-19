package com.example.mekanismmagic.integration.common.energy;

/** Fixed reference tiers must not drift back into per-machine arbitrary prices or FE/J mixing. */
public final class MagicMachineEnergyProfilesSelfTest {
    public static void main(String[] args) {
        int[] tiers = {MagicMachineEnergyProfiles.MATERIAL_PROCESSING, MagicMachineEnergyProfiles.ASSEMBLY,
                MagicMachineEnergyProfiles.MAGIC_REACTION, MagicMachineEnergyProfiles.ADVANCED_RITUAL,
                MagicMachineEnergyProfiles.RESOURCE_GATHERING};
        int[] expected = {50, 100, 200, 400, 1_000};
        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i] != expected[i]) throw new AssertionError("Mek default reference tier changed: " + i);
        }
        for (int lanes : new int[]{3, 5, 7, 9, 11, 13, 15, 17}) {
            long fullJoules = (long) MagicMachineEnergyProfiles.MATERIAL_PROCESSING * lanes;
            if (fullJoules / 2.5 != 20D * lanes) throw new AssertionError("Factory profile mixed FE and Joules");
        }
        System.out.println("Mek default power profile references passed");
    }
}
