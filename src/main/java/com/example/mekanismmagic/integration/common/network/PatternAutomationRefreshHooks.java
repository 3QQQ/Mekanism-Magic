package com.example.mekanismmagic.integration.common.network;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

/** Dependency-free refresh hook for optional processing-pattern providers. */
public final class PatternAutomationRefreshHooks {
    private PatternAutomationRefreshHooks() {
    }

    public static void request(Object machine) {
        if (!(machine instanceof BlockEntity blockEntity)
                || blockEntity.getLevel() == null
                || blockEntity.getLevel().isClientSide()) {
            return;
        }
        Object support = existingSupport(machine);
        if (support == null) {
            return;
        }
        try {
            support.getClass().getMethod("updatePatterns")
                    .invoke(support);
        } catch (ReflectiveOperationException | RuntimeException
                 | LinkageError ignored) {
            // Mek Energistics is optional, and older/newer ABIs safely no-op.
        }
    }

    /**
     * Checks the optional provider's pending queue without linking the common
     * machine code to Mek Energistics. Callers first check their own inputs and
     * progress; consequently a positive {@code isPatternBusy()} result here is
     * the hidden smart-multiplication backlog that must keep a catalyst locked.
     */
    public static boolean hasPendingPatternWork(Object machine) {
        if (!(machine instanceof BlockEntity blockEntity)
                || blockEntity.getLevel() == null
                || blockEntity.getLevel().isClientSide()) {
            return false;
        }
        Object support = existingSupport(machine);
        if (support == null) {
            return false;
        }
        try {
            Object busy = support.getClass().getMethod(
                    "isPatternBusy").invoke(support);
            return busy instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException
                 | LinkageError ignored) {
            return false;
        }
    }

    private static Object existingSupport(Object machine) {
        if (machine == null) {
            return null;
        }
        try {
            Method existingRuntime = machine.getClass().getMethod(
                    "getExistingMeUpgradeRuntime");
            Object runtime = existingRuntime.invoke(machine);
            return runtime == null ? null : runtime.getClass().getMethod(
                    "support").invoke(runtime);
        } catch (ReflectiveOperationException | RuntimeException
                 | LinkageError ignored) {
            return null;
        }
    }
}
