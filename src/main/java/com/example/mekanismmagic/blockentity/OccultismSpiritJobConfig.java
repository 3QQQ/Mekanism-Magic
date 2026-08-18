package com.example.mekanismmagic.blockentity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads Occultism's live worker-spirit server configuration without linking
 * its implementation classes into this addon's bytecode.
 */
final class OccultismSpiritJobConfig {
    private OccultismSpiritJobConfig() {
    }

    record WorkerSettings(int recipeTier, float timeMultiplier,
                          float outputMultiplier, int operationCount) {
    }

    static WorkerSettings settings(String recipeType, int spiritTier) {
        WorkerSettings fallback = defaults(recipeType, spiritTier);
        String settingsField = settingsField(recipeType, spiritTier);
        if (settingsField.isEmpty()) {
            return fallback;
        }
        try {
            Class<?> occultism = Class.forName("com.klikli_dev.occultism.Occultism");
            Object serverConfig = occultism.getField("SERVER_CONFIG").get(null);
            Object spiritJobs = field(serverConfig, "spiritJobs");
            Object worker = field(spiritJobs, settingsField);
            int recipeTier = intConfig(worker, "tier", fallback.recipeTier());
            float timeMultiplier = floatConfig(worker, "timeMultiplier",
                    fallback.timeMultiplier());
            float outputMultiplier = floatConfig(worker, "outputMultiplier",
                    fallback.outputMultiplier());
            int operationCount = intConfig(worker, "operationCount",
                    fallback.operationCount());
            return new WorkerSettings(Math.max(1, recipeTier),
                    Math.max(0, timeMultiplier),
                    Math.max(0, outputMultiplier),
                    Math.max(0, operationCount));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static String settingsField(String recipeType, int spiritTier) {
        String spirit = switch (spiritTier) {
            case 1 -> "Foliot";
            case 2 -> "Djinni";
            case 3 -> "Afrit";
            case 4 -> "Marid";
            default -> "";
        };
        if (spirit.isEmpty()) {
            return "";
        }
        return switch (recipeType) {
            case "crushing" -> "crusher" + spirit;
            case "crystallize" -> "crystallizer" + spirit;
            default -> "";
        };
    }

    private static WorkerSettings defaults(String recipeType, int spiritTier) {
        float outputMultiplier = switch (spiritTier) {
            case 2 -> 1.5F;
            case 3 -> 2.0F;
            case 4 -> 3.0F;
            default -> 1.0F;
        };
        float timeMultiplier = switch (recipeType) {
            case "crushing" -> switch (spiritTier) {
                case 1 -> 2.0F;
                case 3 -> 0.5F;
                case 4 -> 0.3F;
                default -> 1.0F;
            };
            case "crystallize" -> switch (spiritTier) {
                case 2 -> 0.5F;
                case 3 -> 0.3F;
                case 4 -> 0.1F;
                default -> 1.0F;
            };
            default -> 1.0F;
        };
        return new WorkerSettings(Math.max(1, spiritTier), timeMultiplier,
                outputMultiplier, 1);
    }

    private static Object field(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getField(name);
        return field.get(target);
    }

    private static int intConfig(Object settings, String field, int fallback)
            throws ReflectiveOperationException {
        Object value = configValue(settings, field);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static float floatConfig(Object settings, String field, float fallback)
            throws ReflectiveOperationException {
        Object value = configValue(settings, field);
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static Object configValue(Object settings, String fieldName)
            throws ReflectiveOperationException {
        Object configValue = field(settings, fieldName);
        Method get = configValue.getClass().getMethod("get");
        return get.invoke(configValue);
    }
}
