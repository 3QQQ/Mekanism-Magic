package com.example.mekanismmagic.integration.ae2.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry-independent cell data, including resources whose mods are absent. */
public record MagicCellContents(Map<ResourceLocation, Long> amounts,
                                List<ResourceLocation> filter) {
    public static final int MAX_TYPES = 63;
    public static final MagicCellContents EMPTY =
            new MagicCellContents(Map.of(), List.of());
    public static final Codec<MagicCellContents> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(ResourceLocation.CODEC,
                                    Codec.LONG.validate(amount -> amount > 0
                                            ? DataResult.success(amount)
                                            : DataResult.error(() -> "Magic amounts must be positive")))
                            .validate(value -> value.size() <= MAX_TYPES
                                    ? DataResult.success(value)
                                    : DataResult.error(() -> "Too many magic cell resources"))
                            .optionalFieldOf("amounts", Map.of())
                            .forGetter(MagicCellContents::amounts),
                    ResourceLocation.CODEC.listOf(0, MAX_TYPES)
                            .optionalFieldOf("filter", List.of())
                            .forGetter(MagicCellContents::filter)
            ).apply(instance, MagicCellContents::new));

    public MagicCellContents {
        Objects.requireNonNull(amounts, "amounts");
        Objects.requireNonNull(filter, "filter");
        if (amounts.size() > MAX_TYPES || filter.size() > MAX_TYPES) {
            throw new IllegalArgumentException("Too many magic cell resources");
        }
        Map<ResourceLocation, Long> copy = new LinkedHashMap<>();
        amounts.forEach((id, amount) -> {
            Objects.requireNonNull(id, "resource id");
            if (amount == null || amount <= 0) {
                throw new IllegalArgumentException("Magic amounts must be positive");
            }
            copy.put(id, amount);
        });
        amounts = Collections.unmodifiableMap(copy);
        filter = List.copyOf(new LinkedHashSet<>(filter));
    }

    public MagicCellContents withAmounts(Map<ResourceLocation, Long> replacement) {
        return new MagicCellContents(replacement, filter);
    }

    public MagicCellContents withFilter(List<ResourceLocation> replacement) {
        return new MagicCellContents(amounts, replacement);
    }
}
