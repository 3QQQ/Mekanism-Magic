package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ApplyModifierRecipeView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ClibanoRecipeView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ResidueView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detached display data, built only at recipe-viewer registration/reload boundaries.
 * The supplied manager and registries may be remote-client data. Nothing is written
 * to the server catalog, and no server, world, recipe holder or predicate is retained.
 */
public final class ForbiddenRecipeDisplayCatalog {
    private ForbiddenRecipeDisplayCatalog() {
    }

    public static DisplaySnapshot read(RecipeManager manager, RegistryAccess registries) {
        return ForbiddenArcanus261Bridge.readDisplay(manager, registries);
    }

    public record DisplaySnapshot(List<ClibanoDisplay> clibano, List<ModifierDisplay> modifiers,
                                  List<RitualDisplay> rituals) {
        public DisplaySnapshot {
            clibano = List.copyOf(clibano);
            modifiers = List.copyOf(modifiers);
            rituals = List.copyOf(rituals);
        }
    }

    /** A pair, never two independently cycled ingredient lists. */
    public record TransformExample(ItemStack input, ItemStack output) {
        public TransformExample {
            input = input.copyWithCount(1);
            output = output.copy();
        }

        @Override public ItemStack input() { return input.copy(); }
        @Override public ItemStack output() { return output.copy(); }
    }

    public record ClibanoDisplay(ClibanoRecipeView recipe, List<ItemStack> enhancerExamples,
                                Optional<ResidueView> residue, Map<FireType, List<ItemStack>> fireMedia,
                                String signature) {
        public ClibanoDisplay {
            enhancerExamples = copies(enhancerExamples);
            fireMedia = copyFireMedia(fireMedia);
        }

        @Override public List<ItemStack> enhancerExamples() { return copies(enhancerExamples); }
        @Override public Map<FireType, List<ItemStack>> fireMedia() { return copyFireMedia(fireMedia); }
    }

    /** Examples are legal default-item inputs; arbitrary component-bearing inputs still need native validation. */
    public record ModifierDisplay(ApplyModifierRecipeView recipe, List<TransformExample> examples, String signature) {
        public ModifierDisplay {
            examples = List.copyOf(examples);
        }
    }

    /**
     * The machine owns its tier. Upgrade examples contain a real main material
     * and no item output; supportedTiers are the internal source levels.
     * Essence values are RAW requirements; remote enhancer definitions omit executable effects.
     */
    public record RitualDisplay(RitualView recipe, List<TransformExample> examples,
                                List<Integer> supportedTiers,
                                List<List<ItemStack>> enhancers, int upgradeTier,
                                String signature) {
        public RitualDisplay {
            examples = List.copyOf(examples);
            supportedTiers = List.copyOf(supportedTiers);
            enhancers = copyGroups(enhancers);
        }

        @Override public List<List<ItemStack>> enhancers() { return copyGroups(enhancers); }
        public Optional<ResourceLocation> magicCircle() { return Optional.ofNullable(recipe.magicCircle()); }
    }

    static String signature(RegistryAccess registries, Object... values) {
        JsonArray data = new JsonArray();
        for (Object value : values) data.add(json(registries, value));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(data).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Only our detached record values are inspected, never native recipes or executable predicates. */
    private static JsonElement json(RegistryAccess registries, Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof ItemStack stack) {
            return ItemStack.OPTIONAL_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), stack).getOrThrow();
        }
        if (value instanceof Optional<?> optional) return json(registries, optional.orElse(null));
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Map<?, ?> map) {
            JsonObject result = new JsonObject();
            map.forEach((key, entry) -> result.add(key.toString(), json(registries, entry)));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonArray result = new JsonArray();
            iterable.forEach(entry -> result.add(json(registries, entry)));
            return result;
        }
        if (value.getClass().isRecord()) {
            JsonObject result = new JsonObject();
            try {
                for (var component : value.getClass().getRecordComponents()) {
                    result.add(component.getName(), json(registries, component.getAccessor().invoke(value)));
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to fingerprint display metadata", exception);
            }
            return result;
        }
        return new JsonPrimitive(value.toString());
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.add(entry.getKey(), canonical(entry.getValue())));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(entry -> result.add(canonical(entry)));
            return result;
        }
        return value;
    }

    private static List<ItemStack> copies(List<ItemStack> values) {
        return values.stream().map(ItemStack::copy).toList();
    }

    private static List<List<ItemStack>> copyGroups(List<List<ItemStack>> groups) {
        return groups.stream().map(ForbiddenRecipeDisplayCatalog::copies).toList();
    }

    private static Map<FireType, List<ItemStack>> copyFireMedia(Map<FireType, List<ItemStack>> values) {
        Map<FireType, List<ItemStack>> result = new EnumMap<>(FireType.class);
        values.forEach((fire, examples) -> result.put(fire, copies(examples)));
        return Collections.unmodifiableMap(result);
    }
}
