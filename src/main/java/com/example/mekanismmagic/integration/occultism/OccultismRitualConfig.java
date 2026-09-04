package com.example.mekanismmagic.integration.occultism;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads Occultism's live ritual timing without a hard runtime link. */
final class OccultismRitualConfig {
    private OccultismRitualConfig() {
    }

    static double durationMultiplier() {
        try {
            Class<?> occultism = Class.forName(
                    "com.klikli_dev.occultism.Occultism");
            Object serverConfig = occultism.getField(
                    "SERVER_CONFIG").get(null);
            Object rituals = field(serverConfig, "rituals");
            Object value = configValue(rituals,
                    "ritualDurationMultiplier");
            return value instanceof Number number
                    ? sanitize(number.doubleValue()) : 1D;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 1D;
        }
    }

    static double sanitize(double multiplier) {
        return Double.isFinite(multiplier) && multiplier > 0D
                ? multiplier : 1D;
    }

    private static Object field(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getField(name);
        return field.get(target);
    }

    private static Object configValue(Object settings, String fieldName)
            throws ReflectiveOperationException {
        Object configValue = field(settings, fieldName);
        Method get = configValue.getClass().getMethod("get");
        return get.invoke(configValue);
    }
}
