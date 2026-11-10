package com.example.mekanismmagic.integration;

/** Metadata policy only; real class/member compatibility has its own bytecode-verifier tests. */
public final class ForbiddenCompatibilitySelfTest {
    public static void main(String[] args) {
        check(ModCompatibility.forbiddenArcanusVersionsSupported("2.6.1", "1.1.5"), "compile baseline rejected");
        ModCompatibility.requireForbiddenContentVersions(null, null);
        ModCompatibility.requireForbiddenContentVersions(null, "9.9.9");
        ModCompatibility.requireForbiddenContentVersions("2.6.1", "1.1.5");
        for (String forbidden : new String[]{null, "", " ", "\t"}) {
            check(!ModCompatibility.forbiddenArcanusVersionsSupported(forbidden, "1.1.5"), "missing F&A metadata accepted: " + forbidden);
            if (forbidden != null) requireStartupFailure(forbidden, "1.1.5");
        }
        for (String core : new String[]{null, "", " ", "\t"}) {
            check(!ModCompatibility.forbiddenArcanusVersionsSupported("2.6.1", core), "missing Core metadata accepted: " + core);
            requireStartupFailure("2.6.1", core);
        }
        int accepted = 0;
        // These are version labels, not claims that the real published builds share an ABI.
        // A different label must reach the separate ABI gate instead of being rejected here.
        for (String forbidden : new String[]{"2.5.2-beta", "2.6.0", "2.6.2", "2.6.1-dev", "12.6.1", "unversioned-build"}) {
            for (String core : new String[]{"1.1.4", "1.1.6", "1.1.5-dev", "9.9.9"}) {
                check(ModCompatibility.forbiddenArcanusVersionsSupported(forbidden, core),
                        "Version labels were mistaken for an ABI failure: " + forbidden + "/" + core);
                ModCompatibility.requireForbiddenContentVersions(forbidden, core);
                accepted++;
            }
        }
        System.out.println("Forbidden metadata policy passed: baseline, optional absence, missing dependencies and "
                + accepted + " independent version labels; ABI checks are separate");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void requireStartupFailure(String forbidden, String core) {
        try {
            ModCompatibility.requireForbiddenContentVersions(forbidden, core);
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("saved"), "dependency error must explain saved registry protection");
            return;
        }
        throw new AssertionError("Invalid dependency metadata may silently omit saved content: " + forbidden + "/" + core);
    }
}
