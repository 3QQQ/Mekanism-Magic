package com.example.mekanismmagic.integration;

/** Runs without either optional implementation JAR on the test classpath. */
public final class ForbiddenCompatibilitySelfTest {
    public static void main(String[] args) {
        check(ModCompatibility.forbiddenArcanusVersionsSupported("2.6.1", "1.1.5"), "exact baseline rejected");
        ModCompatibility.requireForbiddenContentVersions(null, null);
        ModCompatibility.requireForbiddenContentVersions(null, "9.9.9");
        ModCompatibility.requireForbiddenContentVersions("2.6.1", "1.1.5");
        for (String forbidden : new String[]{null, "", "2.5.2-beta", "2.6.0", "2.6.2", "2.6.1-dev", "12.6.1"}) {
            check(!ModCompatibility.forbiddenArcanusVersionsSupported(forbidden, "1.1.5"), "unsupported F&A accepted: " + forbidden);
            if (forbidden != null) requireStartupFailure(forbidden, "1.1.5");
        }
        for (String core : new String[]{null, "", "1.1.4", "1.1.6", "1.1.5-dev"}) {
            check(!ModCompatibility.forbiddenArcanusVersionsSupported("2.6.1", core), "unverified Core accepted: " + core);
            requireStartupFailure("2.6.1", core);
        }
        System.out.println("Forbidden exact-version isolation policy passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void requireStartupFailure(String forbidden, String core) {
        try {
            ModCompatibility.requireForbiddenContentVersions(forbidden, core);
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("saved Clibano"), "version error must explain saved registry protection");
            return;
        }
        throw new AssertionError("Unverified installed content may silently disappear: " + forbidden + "/" + core);
    }
}
